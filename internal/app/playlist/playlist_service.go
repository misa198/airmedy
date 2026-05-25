package playlist

import (
	"bytes"
	"context"
	"fmt"
	"log/slog"
	"mime"
	"os"
	"path/filepath"
	"strings"
	"time"

	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"

	"github.com/google/uuid"
	"github.com/misa198/lexorank-go"
)

type PlaylistService struct {
	repo          domain.PlaylistRepository
	artworkCache  domain.ArtworkCache
	searchService domain.SearchService
	logger        *slog.Logger
}

func NewPlaylistService(repo domain.PlaylistRepository, artworkCache domain.ArtworkCache, searchService domain.SearchService, logger *slog.Logger) *PlaylistService {
	return &PlaylistService{
		repo:          repo,
		artworkCache:  artworkCache,
		searchService: searchService,
		logger:        logger,
	}
}

func (s *PlaylistService) Create(ctx context.Context, name, description string) (*domain.Playlist, error) {
	if name == "" {
		return nil, fmt.Errorf("playlist name cannot be empty")
	}
	p := &domain.Playlist{
		ID:          uuid.New().String(),
		Name:        name,
		Description: description,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
	}
	if err := s.repo.Save(ctx, p); err != nil {
		return nil, err
	}
	if err := s.searchService.IndexPlaylist(ctx, p); err != nil {
		s.logger.Warn("Failed to index playlist", "name", p.Name, "error", err)
	}
	return p, nil
}

func (s *PlaylistService) Update(ctx context.Context, id, name, description string) error {
	if name == "" {
		return fmt.Errorf("playlist name cannot be empty")
	}
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", id)
	}
	p.Name = name
	p.Description = description
	p.UpdatedAt = time.Now()
	if err := s.repo.Update(ctx, p); err != nil {
		return err
	}
	if err := s.searchService.IndexPlaylist(ctx, p); err != nil {
		s.logger.Warn("Failed to index playlist", "name", p.Name, "error", err)
	}
	return nil
}

func (s *PlaylistService) Delete(ctx context.Context, id string) error {
	if err := s.repo.Delete(ctx, id); err != nil {
		return err
	}
	return s.searchService.DeleteFromIndex(ctx, id)
}

func (s *PlaylistService) GetAll(ctx context.Context) ([]*domain.Playlist, error) {
	return s.repo.GetAll(ctx)
}

func (s *PlaylistService) GetByID(ctx context.Context, id string) (*domain.Playlist, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *PlaylistService) GetTracks(ctx context.Context, playlistID string) ([]*domain.TrackDTO, error) {
	return s.repo.GetTracks(ctx, playlistID)
}

func (s *PlaylistService) AddTrack(ctx context.Context, playlistID, trackID string) error {
	maxRankStr, err := s.repo.GetMaxPosition(ctx, playlistID)
	if err != nil {
		return err
	}

	var newRank lexorank.Rank
	if maxRankStr == "" {
		newRank = lexorank.Middle()
	} else {
		maxRank, err := lexorank.ParseRank(maxRankStr)
		if err != nil {
			return err
		}
		newRank = maxRank.GenNext()
	}

	s.logger.Debug("Adding track to playlist with LexoRank",
		"playlist_id", playlistID,
		"track_id", trackID,
		"new_rank", newRank.String(),
		"prev_max", maxRankStr)

	return s.repo.AddTrack(ctx, playlistID, trackID, newRank.String())
}

func (s *PlaylistService) AddTracks(ctx context.Context, playlistID string, trackIDs []string) error {
	return s.repo.AddTracks(ctx, playlistID, trackIDs)
}

func (s *PlaylistService) RemoveTrack(ctx context.Context, playlistID, trackID string) error {
	return s.repo.RemoveTrack(ctx, playlistID, trackID)
}

func (s *PlaylistService) MoveTrack(ctx context.Context, playlistID, trackID, prevTrackID, nextTrackID string) error {
	var prevRank, nextRank lexorank.Rank
	var hasPrev, hasNext bool
	var err error

	if prevTrackID != "" {
		prevRankStr, err := s.repo.GetTrackPosition(ctx, playlistID, prevTrackID)
		if err != nil {
			return err
		}
		prevRank, err = lexorank.ParseRank(prevRankStr)
		if err != nil {
			return err
		}
		hasPrev = true
	}

	if nextTrackID != "" {
		nextRankStr, err := s.repo.GetTrackPosition(ctx, playlistID, nextTrackID)
		if err != nil {
			return err
		}
		nextRank, err = lexorank.ParseRank(nextRankStr)
		if err != nil {
			return err
		}
		hasNext = true
	}

	var newRank lexorank.Rank
	if !hasPrev {
		// Move to start
		if !hasNext {
			newRank = lexorank.Middle()
		} else {
			newRank = nextRank.GenPrev()
		}
	} else if !hasNext {
		// Move to end
		newRank = prevRank.GenNext()
	} else {
		// Move between
		newRank, err = prevRank.Between(nextRank)
		if err != nil {
			return err
		}
	}

	newRankStr := newRank.String()
	s.logger.Debug("Moving track in playlist",
		"playlist_id", playlistID,
		"track_id", trackID,
		"prev_track_id", prevTrackID,
		"next_track_id", nextTrackID,
		"new_rank", newRankStr)

	if err := s.repo.UpdateTrackPosition(ctx, playlistID, trackID, newRankStr); err != nil {
		return err
	}

	// Rebalance if rank string becomes too long
	if len(newRankStr) > 10 {
		s.logger.Info("Triggering LexoRank rebalance", "playlist_id", playlistID, "rank_length", len(newRankStr))
		return s.rebalanceRanks(ctx, playlistID)
	}

	return nil
}

func (s *PlaylistService) rebalanceRanks(ctx context.Context, playlistID string) error {
	tracks, err := s.repo.GetTracks(ctx, playlistID)
	if err != nil {
		return err
	}

	s.logger.Debug("Rebalancing playlist ranks", "playlist_id", playlistID, "track_count", len(tracks))

	updates := make(map[string]string)
	rank := lexorank.Middle()
	for _, t := range tracks {
		updates[t.ID] = rank.String()
		rank = rank.GenNext()
	}

	return s.repo.UpdateTracksPositions(ctx, playlistID, updates)
}

func (s *PlaylistService) SetArtwork(ctx context.Context, id, imagePath string) (*string, error) {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if p == nil {
		return nil, fmt.Errorf("playlist not found: %s", id)
	}

	data, err := os.ReadFile(imagePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read image: %w", err)
	}

	ext := filepath.Ext(imagePath)
	mimeType := mime.TypeByExtension(ext)
	if mimeType == "" {
		mimeType = "image/jpeg"
	}

	key, err := s.artworkCache.Save(ctx, data, mimeType)
	if err != nil {
		return nil, fmt.Errorf("failed to save artwork: %w", err)
	}

	p.ArtworkKey = &key
	if err := s.repo.Update(ctx, p); err != nil {
		return nil, err
	}

	return &key, nil
}

func (s *PlaylistService) RemoveArtwork(ctx context.Context, id string) error {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", id)
	}

	p.ArtworkKey = nil
	return s.repo.Update(ctx, p)
}

func (s *PlaylistService) GetPlaylistsForTrack(ctx context.Context, trackID string) ([]string, error) {
	return s.repo.GetPlaylistsForTrack(ctx, trackID)
}

func (s *PlaylistService) ExportM3U8(ctx context.Context, playlistID string, destPath string) error {
	p, err := s.repo.GetByID(ctx, playlistID)
	if err != nil {
		return fmt.Errorf("get playlist: %w", err)
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", playlistID)
	}

	tracks, err := s.repo.GetTracks(ctx, playlistID)
	if err != nil {
		return fmt.Errorf("get tracks: %w", err)
	}

	var buf bytes.Buffer
	buf.WriteString("#EXTM3U\n")
	buf.WriteString("#EXTENC:UTF-8\n")
	buf.WriteString(fmt.Sprintf("#PLAYLIST:%s\n", p.Name))

	for _, t := range tracks {
		if t == nil {
			continue
		}
		artist := t.RawArtistNames
		title := t.Title
		album := ""
		if t.Album != nil {
			album = t.Album.Title
		}
		genre := t.RawGenreNames

		displayName := title
		if artist != "" {
			displayName = artist + " - " + title
		}

		buf.WriteString(fmt.Sprintf("#EXTINF:%d,%s\n", t.Duration, displayName))
		if album != "" {
			buf.WriteString(fmt.Sprintf("#EXTALB:%s\n", album))
		}
		if artist != "" {
			buf.WriteString(fmt.Sprintf("#EXTART:%s\n", artist))
		}
		if genre != "" {
			buf.WriteString(fmt.Sprintf("#EXTGENRE:%s\n", strings.SplitN(genre, ";", 2)[0]))
		}
		buf.WriteString(t.Path + "\n")
	}

	if err := os.WriteFile(destPath, buf.Bytes(), 0644); err != nil {
		return fmt.Errorf("write file: %w", err)
	}
	return nil
}

func (s *PlaylistService) GetPlaylistColors(ctx context.Context, id string) (*domain.ThemeColors, error) {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if p == nil || p.ArtworkKey == nil || *p.ArtworkKey == "" {
		return nil, nil
	}

	path := s.artworkCache.GetPath(*p.ArtworkKey)
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return nil, nil
	}

	colors, err := artwork.ExtractPalette(path)
	if err != nil {
		return nil, fmt.Errorf("failed to extract palette: %w", err)
	}

	return colors, nil
}
