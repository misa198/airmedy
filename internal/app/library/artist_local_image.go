package library

import (
	"context"
	"fmt"
	"io/fs"
	"mime"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"sync"

	"airmedy/internal/app/config"
	"airmedy/internal/domain"

	"github.com/blang/semver"
	"github.com/wailsapp/wails/v3/pkg/application"
)

// artistImageBaseNames lists the accepted local artist image files in priority
// order: jpg > jpeg > png.
var artistImageBaseNames = []string{"artist.jpg", "artist.jpeg", "artist.png"}

// findArtistImageFile returns the path of the highest-priority artist image in
// dir, or "" if none exists.
func findArtistImageFile(dir string) string {
	for _, name := range artistImageBaseNames {
		// Case-insensitive on macOS/Windows; on Linux honour the exact lower-case
		// name plus a couple of common casings.
		for _, candidate := range []string{name, capitalize(name)} {
			p := filepath.Join(dir, candidate)
			if info, err := os.Stat(p); err == nil && !info.IsDir() {
				return p
			}
		}
	}
	return ""
}

func capitalize(s string) string {
	if s == "" {
		return s
	}
	return string(s[0]-32) + s[1:] // "artist.jpg" -> "Artist.jpg"
}

// isArtistImageFile reports whether a file name is one of the accepted artist
// image files (case-insensitive).
func isArtistImageFile(name string) bool {
	return slices.Contains(artistImageBaseNames, strings.ToLower(name))
}

// albumArtistIDsInDir returns the distinct album-artist IDs of tracks located in
// dir or any of its subdirectories. An artist.jpg belongs to the folder's album
// artist (its discography), so guest/track artists are ignored on purpose.
func (s *LibraryService) albumArtistIDsInDir(ctx context.Context, dir string) []string {
	prefix := dir
	if !strings.HasSuffix(prefix, string(os.PathSeparator)) {
		prefix += string(os.PathSeparator)
	}
	ids, err := s.trackRepo.AlbumArtistIDsByPathPrefix(ctx, prefix)
	if err != nil {
		s.logger.Warn("Failed to list album artists for artist image dir", "dir", dir, "error", err)
		return nil
	}
	return ids
}

// artistIDsForImageDir maps an artist-image directory to the album artists it
// belongs to. The album artists of contained tracks are authoritative; the
// folder name is only consulted as a fallback when the directory has no album
// artists of its own (e.g. an artist folder holding only the image, or tracks
// with no ALBUMARTIST tag). This keeps a role-agnostic folder-name match from
// pulling in unrelated (e.g. guest) artists and causing false ambiguity.
//
// Only artists that actually exist in the library are returned.
func (s *LibraryService) artistIDsForImageDir(ctx context.Context, dir string) []string {
	// 1. Album artists of tracks within the directory (authoritative).
	if ids := s.albumArtistIDsInDir(ctx, dir); len(ids) > 0 {
		return ids
	}

	// 2. Fallback: folder name == artist name.
	key := domain.NormalizationKey(filepath.Base(dir))
	if key != "" {
		if artist, err := s.artistRepo.GetByNormalizationKey(ctx, key); err == nil && artist != nil {
			return []string{artist.ID}
		}
	}
	return nil
}

// artistImageForTrack looks for an artist image next to the track (image-with-
// songs layout) and then in the parent directory (artist-folder layout).
func artistImageForTrack(trackPath string) string {
	dir := filepath.Dir(trackPath)
	if p := findArtistImageFile(dir); p != "" {
		return p
	}
	return findArtistImageFile(filepath.Dir(dir))
}

func (s *LibraryService) artistLock(artistID string) *sync.Mutex {
	m, _ := s.artistArtworkLocks.LoadOrStore(artistID, &sync.Mutex{})
	return m.(*sync.Mutex)
}

// writeArtistArtworkSource stores artwork for one source on an artist. Every
// source is kept independently — no cross-source precedence at write time (the
// shown image is chosen at read time). The bytes are produced lazily by load so
// a file is only read when needed. Returns the artwork URL and whether anything
// changed.
func (s *LibraryService) writeArtistArtworkSource(
	ctx context.Context,
	artistID, source string,
	load func() (data []byte, mimeType string, err error),
) (string, bool, error) {
	data, mimeType, err := load()
	if err != nil {
		return "", false, err
	}

	key, err := s.artworkCache.Save(ctx, data, mimeType)
	if err != nil {
		return "", false, fmt.Errorf("failed to save artist artwork: %w", err)
	}

	changed, err := s.applyArtistArtworkKey(ctx, artistID, source, key)
	return fmt.Sprintf("/artwork/%s", key), changed, err
}

// applyArtistArtworkKey points one of an artist's artwork sources at an
// already-cached key (locked per artist). Skips when unchanged. The cache save
// is done by the caller so the same image can be reused across many artists
// without re-reading the file.
func (s *LibraryService) applyArtistArtworkKey(ctx context.Context, artistID, source, key string) (bool, error) {
	lock := s.artistLock(artistID)
	lock.Lock()
	defer lock.Unlock()

	artist, err := s.artistRepo.GetByID(ctx, artistID)
	if err != nil {
		return false, err
	}
	if artist == nil {
		return false, nil
	}
	if cur := artist.ArtworkKeyForSource(source); cur != nil && *cur == key {
		return false, nil
	}
	if err := s.artistRepo.SetArtworkSource(ctx, artistID, source, &key); err != nil {
		return false, err
	}
	s.emitArtistArtworkUpdated(artistID, source, key)
	return true, nil
}

// applyLocalArtistImagesForDirs is the bulk artist-image pass used during a full
// sync. imageDirs is the set of directories that actually contain an
// artist.{jpg,jpeg,png} file. Each image is cached once and applied to every
// artist the directory maps to (by folder name and/or by contained tracks), so a
// whole library is processed without a per-track stat/read storm. Runs after all
// tracks are imported, so the by-name lookup only matches existing artists.
func (s *LibraryService) applyLocalArtistImagesForDirs(ctx context.Context, imageDirs map[string]bool) {
	imgKey := make(map[string]string) // image path -> cache key (saved once)
	for dir := range imageDirs {
		imagePath := findArtistImageFile(dir)
		if imagePath == "" {
			continue
		}

		artistIDs := s.artistIDsForImageDir(ctx, dir)
		if len(artistIDs) == 0 {
			continue
		}
		// Ambiguous: one artist.jpg mapping to several artists (e.g. a
		// various-artists or parent folder) isn't artist-specific — skip it.
		if len(artistIDs) > 1 {
			s.logger.Info("Skipping ambiguous artist image (maps to multiple artists)", "image", imagePath, "artists", len(artistIDs))
			continue
		}

		key, ok := imgKey[imagePath]
		if !ok {
			data, mimeType, err := loadImageFile(imagePath)()
			if err != nil {
				s.logger.Warn("Failed to read artist image during scan", "image", imagePath, "error", err)
				continue
			}
			k, err := s.artworkCache.Save(ctx, data, mimeType)
			if err != nil {
				s.logger.Warn("Failed to cache artist image during scan", "image", imagePath, "error", err)
				continue
			}
			key = k
			imgKey[imagePath] = k
		}

		for _, id := range artistIDs {
			if _, err := s.applyArtistArtworkKey(ctx, id, domain.ArtworkSourceLocalFile, key); err != nil {
				s.logger.Warn("Failed to apply local artist image", "artistID", id, "error", err)
			}
		}
	}
}

func loadImageFile(imagePath string) func() ([]byte, string, error) {
	return func() ([]byte, string, error) {
		data, err := os.ReadFile(imagePath)
		if err != nil {
			return nil, "", fmt.Errorf("failed to read image %s: %w", imagePath, err)
		}
		mimeType := mime.TypeByExtension(filepath.Ext(imagePath))
		if mimeType == "" {
			mimeType = "image/jpeg"
		}
		return data, mimeType, nil
	}
}

// setLocalArtistImage applies a scanned artist image file to an artist's
// local_file source.
func (s *LibraryService) setLocalArtistImage(ctx context.Context, artistID, imagePath string) (bool, error) {
	_, changed, err := s.writeArtistArtworkSource(ctx, artistID, domain.ArtworkSourceLocalFile, loadImageFile(imagePath))
	return changed, err
}

// SetArtistArtworkFromFile stores a user-chosen image as the artist's manual
// artwork (always shown over local/online). Returns the artwork URL.
func (s *LibraryService) SetArtistArtworkFromFile(ctx context.Context, artistID, imagePath string) (string, error) {
	url, _, err := s.writeArtistArtworkSource(ctx, artistID, domain.ArtworkSourceManual, loadImageFile(imagePath))
	return url, err
}

// RemoveArtistArtwork clears the user's custom (manual) artwork, reverting to the
// local/online image per preference.
func (s *LibraryService) RemoveArtistArtwork(ctx context.Context, artistID string) error {
	return s.clearArtistArtworkSource(ctx, artistID, domain.ArtworkSourceManual)
}

// clearArtistArtworkSource removes one source's artwork and notifies the frontend.
func (s *LibraryService) clearArtistArtworkSource(ctx context.Context, artistID, source string) error {
	lock := s.artistLock(artistID)
	lock.Lock()
	defer lock.Unlock()

	if err := s.artistRepo.SetArtworkSource(ctx, artistID, source, nil); err != nil {
		return err
	}
	s.emitArtistArtworkUpdated(artistID, source, "")
	return nil
}

// emitArtistArtworkUpdated tells the frontend a single source's key changed (key
// is "" when cleared). The frontend re-resolves which image to show.
func (s *LibraryService) emitArtistArtworkUpdated(artistID, source, key string) {
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("artist-artwork-updated", map[string]string{
			"artist_id": artistID,
			"source":    source,
			"key":       key,
		})
	}
}

// resolveTrackArtistImages looks for a local artist image around the given track
// path and applies it to the track's album artists. Called during import so
// newly-added tracks pick up sibling/parent artist images.
func (s *LibraryService) resolveTrackArtistImages(ctx context.Context, trackPath string, artistIDs []string) {
	if len(artistIDs) == 0 {
		return
	}
	// Ambiguous: a nearby artist.jpg shared by several album artists (e.g. a
	// compilation) isn't artist-specific — skip it.
	if len(artistIDs) > 1 {
		return
	}
	imagePath := artistImageForTrack(trackPath)
	if imagePath == "" {
		return
	}
	for _, id := range artistIDs {
		if _, err := s.setLocalArtistImage(ctx, id, imagePath); err != nil {
			s.logger.Warn("Failed to apply local artist image", "artistID", id, "image", imagePath, "error", err)
		}
	}
}

// maybeRescanArtistImages runs a one-time local artist image scan when the app
// has been upgraded past the version that last ran a full scan (or never ran
// one). This lets existing libraries pick up artist.jpg/png files already on
// disk after updating. It is not run on every launch — only when the stored
// version is empty or older than the current build.
func (s *LibraryService) maybeRescanArtistImages(ctx context.Context) {
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		s.logger.Error("Failed to load settings for artist image rescan", "error", err)
		return
	}

	needScan := false
	if settings.LastScanVersion == "" {
		needScan = true
	} else {
		current, errCur := semver.Parse(config.Version)
		stored, errStored := semver.Parse(settings.LastScanVersion)
		if errCur != nil || errStored != nil {
			// Fail safe: if either version is unparseable, rescan once.
			needScan = true
		} else if current.GT(stored) {
			needScan = true
		}
	}

	if !needScan {
		return
	}

	s.logger.Info("Running one-time artist image rescan after upgrade",
		"from", settings.LastScanVersion, "to", config.Version)
	if err := s.ScanArtistImages(ctx); err != nil {
		s.logger.Error("Artist image rescan failed", "error", err)
		return
	}

	settings.LastScanVersion = config.Version
	if err := s.settingsRepo.Save(ctx, settings); err != nil {
		s.logger.Error("Failed to persist last scan version", "error", err)
	}
}

// ScanArtistImages walks the watched folders, collects every directory holding an
// artist.{jpg,jpeg,png}, and applies them via the shared batch pass (matching by
// folder name and by contained tracks). Used by the version-gated upgrade rescan.
func (s *LibraryService) ScanArtistImages(ctx context.Context) error {
	folders, err := s.watchedFolderRepo.GetAll(ctx)
	if err != nil {
		return fmt.Errorf("failed to list watched folders for image scan: %w", err)
	}

	imageDirs := make(map[string]bool)
	for _, f := range folders {
		_ = filepath.WalkDir(f.Path, func(path string, d fs.DirEntry, walkErr error) error {
			if walkErr != nil || d.IsDir() {
				return nil
			}
			if isArtistImageFile(filepath.Base(path)) {
				imageDirs[filepath.Dir(path)] = true
			}
			return nil
		})
	}

	s.logger.Info("Scanning local artist images", "imageDirs", len(imageDirs))
	s.applyLocalArtistImagesForDirs(ctx, imageDirs)
	return nil
}
