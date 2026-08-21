package lyrics

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"airmedy/internal/domain"
)

type syncLyricCandidate struct{ path, source string }
type syncLyricFileState struct {
	exists      bool
	size, mtime int64
}

// ResolveForMobileSync resolves exactly one display lyric per track. It indexes
// each candidate directory once, fingerprints only file metadata, and reads
// lyric content only for cache misses or changed inputs.
func (s *LyricsService) ResolveForMobileSync(ctx context.Context, tracks []*domain.TrackDTO, cache domain.MobileSyncLyricCacheRepository) (map[string]*domain.Lyric, error) {
	if len(tracks) == 0 {
		return map[string]*domain.Lyric{}, nil
	}
	settings := &domain.AppSettings{}
	if s.settingsRepo != nil {
		if loaded, err := s.settingsRepo.Load(ctx); err == nil && loaded != nil {
			settings = loaded
		}
	}
	ids := make([]string, 0, len(tracks))
	for _, track := range tracks {
		ids = append(ids, track.ID)
	}
	dbLyrics, err := s.repo.GetByTrackIDs(ctx, ids)
	if err != nil {
		return nil, err
	}
	cached, err := cache.GetByTrackIDs(ctx, ids)
	if err != nil {
		return nil, err
	}

	candidates := make(map[string][]syncLyricCandidate, len(tracks))
	dirNames := make(map[string]map[string]struct{})
	for _, track := range tracks {
		// Provider content is the final display result in this mode, so local
		// sidecars cannot affect the mobile snapshot and need no filesystem I/O.
		if !settings.PreferLocalLyrics && dbLyrics[track.ID] != nil && dbLyrics[track.ID].Content != "" {
			continue
		}
		dirs := []string{filepath.Dir(track.Path)}
		if settings.LyricsSubfolderEnabled && ValidSubfolderName(settings.LyricsSubfolderName) {
			dirs = append(dirs, ResolveSubdir(filepath.Dir(track.Path), settings.LyricsSubfolderName))
		}
		if settings.LyricsFolderEnabled && settings.LyricsFolderPath != "" {
			dirs = append(dirs, settings.LyricsFolderPath)
		}
		base := strings.TrimSuffix(filepath.Base(track.Path), filepath.Ext(track.Path))
		for _, dir := range dirs {
			for _, ext := range []struct{ ext, source string }{{".lrc", "local-lrc"}, {".txt", "local-txt"}} {
				path := filepath.Join(dir, base+ext.ext)
				candidates[track.ID] = append(candidates[track.ID], syncLyricCandidate{path, ext.source})
				if dirNames[dir] == nil {
					dirNames[dir] = map[string]struct{}{}
				}
				dirNames[dir][base+ext.ext] = struct{}{}
			}
		}
	}

	states := make(map[string]syncLyricFileState)
	for dir, names := range dirNames {
		entries, readErr := os.ReadDir(dir)
		if readErr != nil {
			continue
		}
		for _, entry := range entries {
			if _, wanted := names[entry.Name()]; !wanted {
				continue
			}
			info, infoErr := entry.Info()
			if infoErr == nil && !info.IsDir() {
				states[filepath.Join(dir, entry.Name())] = syncLyricFileState{true, info.Size(), info.ModTime().UnixNano()}
			}
		}
	}

	settingSig := fmt.Sprintf("local=%t|sub=%t:%s|folder=%t:%s", settings.PreferLocalLyrics, settings.LyricsSubfolderEnabled, settings.LyricsSubfolderName, settings.LyricsFolderEnabled, settings.LyricsFolderPath)
	resolved := make(map[string]*domain.Lyric, len(tracks))
	for _, track := range tracks {
		dbLyric := dbLyrics[track.ID]
		fingerprint := syncLyricFingerprint(settingSig, dbLyric, candidates[track.ID], states)
		if entry := cached[track.ID]; entry != nil && entry.Fingerprint == fingerprint {
			if entry.HasLyric {
				resolved[track.ID] = &domain.Lyric{TrackID: track.ID, Content: entry.Content, Source: entry.Source}
			}
			continue
		}
		lyric := resolveSyncLyric(track.ID, dbLyric, candidates[track.ID], states, settings.PreferLocalLyrics)
		entry := &domain.MobileSyncLyricCache{TrackID: track.ID, Fingerprint: fingerprint}
		if lyric != nil {
			entry.HasLyric, entry.Content, entry.Source = true, lyric.Content, lyric.Source
			entry.Version = lyricVersion(lyric.Content, lyric.Source)
			resolved[track.ID] = lyric
		}
		if err := cache.Upsert(ctx, entry); err != nil {
			return nil, fmt.Errorf("cache resolved lyric: %w", err)
		}
	}
	return resolved, nil
}

func syncLyricFingerprint(settings string, lyric *domain.Lyric, candidates []syncLyricCandidate, states map[string]syncLyricFileState) string {
	var b strings.Builder
	b.WriteString(settings)
	if lyric != nil {
		fmt.Fprintf(&b, "|db=%s|%s|%s|%s|%d", lyric.Content, lyric.Source, lyric.MetaContent, lyric.MetaSource, lyric.UpdatedAt.UnixNano())
	}
	for _, candidate := range candidates {
		state := states[candidate.path]
		fmt.Fprintf(&b, "|%s:%t:%d:%d", candidate.path, state.exists, state.size, state.mtime)
	}
	return lyricVersion(b.String(), "inputs")
}

func resolveSyncLyric(trackID string, dbLyric *domain.Lyric, candidates []syncLyricCandidate, states map[string]syncLyricFileState, preferLocal bool) *domain.Lyric {
	var local *domain.Lyric
	for _, candidate := range candidates {
		if !states[candidate.path].exists {
			continue
		}
		content, err := os.ReadFile(candidate.path)
		if err != nil {
			continue
		}
		value := strings.TrimSpace(strings.TrimPrefix(string(content), "\ufeff"))
		if value != "" {
			local = &domain.Lyric{TrackID: trackID, Content: value, Source: candidate.source}
			break
		}
	}
	if local == nil && dbLyric != nil && dbLyric.MetaContent != "" {
		local = &domain.Lyric{TrackID: trackID, Content: dbLyric.MetaContent, Source: dbLyric.MetaSource}
	}
	var provider *domain.Lyric
	if dbLyric != nil && dbLyric.Content != "" {
		provider = &domain.Lyric{TrackID: trackID, Content: dbLyric.Content, Source: dbLyric.Source}
	}
	if preferLocal {
		if local != nil {
			return local
		}
		return provider
	}
	if provider != nil {
		return provider
	}
	return local
}

func lyricVersion(content, source string) string {
	sum := sha256.Sum256([]byte(source + "\x00" + content))
	return hex.EncodeToString(sum[:])
}
