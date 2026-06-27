package library

import (
	"context"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"airmedy/internal/app/lyrics"
	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"

	"github.com/fsnotify/fsnotify"
	"github.com/google/uuid"
	"github.com/wailsapp/wails/v3/pkg/application"
)

// SupportedAudioExtensions is the set of file extensions the library accepts.
var SupportedAudioExtensions = map[string]bool{
	".mp3":  true,
	".flac": true,
	".m4a":  true,
	".wav":  true,
	".ogg":  true,
	".opus": true,
	".aiff": true,
	".aif":  true,
	".ape":  true,
	".wv":   true,
	".dsf":  true,
	".dff":  true,
}

type LibraryService struct {
	trackRepo         domain.TrackRepository
	albumRepo         domain.AlbumRepository
	artistRepo        domain.ArtistRepository
	genreRepo         domain.GenreRepository
	composerRepo      domain.ComposerRepository
	playlistRepo      domain.PlaylistRepository
	watchedFolderRepo domain.WatchedFolderRepository
	settingsRepo      domain.SettingsRepository
	metadataExtractor domain.MetadataExtractor
	metadataWriter    domain.MetadataWriter
	artworkCache      domain.ArtworkCache
	searchService     domain.SearchService
	lyricsService     *lyrics.LyricsService
	logger            *slog.Logger
	watcher           *fsnotify.Watcher

	trackUpdateListeners   []func(*domain.TrackDTO)
	artistArtworkQueue     chan artistArtworkJob
	pendingArtistArtwork   map[string]struct{}
	pendingArtistArtworkMu sync.Mutex
	artistArtworkLocks     sync.Map     // artistID -> *sync.Mutex; serializes artwork writes
	syncing                atomic.Int32 // >0 while a bulk SyncFolder runs (gates per-track work)
	artworkCleanupMu       sync.Mutex
	artworkCleanupTimer    *time.Timer // debounces orphan-artwork cleanup after watcher deletes
	selfWrites             map[string]time.Time // path -> expiry; suppresses watcher events for app's own writes
	selfWritesMu           sync.Mutex
	ctx                    context.Context
	cancel                 context.CancelFunc
	mu                     sync.RWMutex
}

type artistArtworkJob struct {
	ArtistID string
	EventID  string
}

func NewLibraryService(
	trackRepo domain.TrackRepository,
	albumRepo domain.AlbumRepository,
	artistRepo domain.ArtistRepository,
	genreRepo domain.GenreRepository,
	composerRepo domain.ComposerRepository,
	playlistRepo domain.PlaylistRepository,
	watchedFolderRepo domain.WatchedFolderRepository,
	settingsRepo domain.SettingsRepository,
	metadataExtractor domain.MetadataExtractor,
	metadataWriter domain.MetadataWriter,
	artworkCache domain.ArtworkCache,
	searchService domain.SearchService,
	lyricsService *lyrics.LyricsService,
	logger *slog.Logger,
) (*LibraryService, error) {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, fmt.Errorf("failed to create watcher: %w", err)
	}

	ctx, cancel := context.WithCancel(context.Background())

	return &LibraryService{
		trackRepo:            trackRepo,
		albumRepo:            albumRepo,
		artistRepo:           artistRepo,
		genreRepo:            genreRepo,
		composerRepo:         composerRepo,
		playlistRepo:         playlistRepo,
		watchedFolderRepo:    watchedFolderRepo,
		settingsRepo:         settingsRepo,
		metadataExtractor:    metadataExtractor,
		metadataWriter:       metadataWriter,
		artworkCache:         artworkCache,
		searchService:        searchService,
		lyricsService:        lyricsService,
		logger:               logger.With("module", "library"),
		watcher:              watcher,
		artistArtworkQueue:   make(chan artistArtworkJob, 100),
		pendingArtistArtwork: make(map[string]struct{}),
		selfWrites:           make(map[string]time.Time),
		ctx:                  ctx,
		cancel:               cancel,
	}, nil
}

func (s *LibraryService) AddTrackUpdateListener(l func(*domain.TrackDTO)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.trackUpdateListeners = append(s.trackUpdateListeners, l)
}

func (s *LibraryService) notifyTrackUpdated(track *domain.TrackDTO) {
	s.mu.RLock()
	listeners := make([]func(*domain.TrackDTO), len(s.trackUpdateListeners))
	copy(listeners, s.trackUpdateListeners)
	s.mu.RUnlock()

	for _, l := range listeners {
		l(track)
	}
}

// selfWriteWindow is how long watcher events for a path are suppressed after the
// app writes that file itself (e.g. metadata edits). Covers the taglib write plus
// the watcher's debounce delay.
const selfWriteWindow = 5 * time.Second

// markSelfWrite records that the app is about to write path, so the file watcher
// ignores the resulting Write/Remove/Rename events instead of treating them as an
// external change (which would redundantly re-import, or worse, delete the row).
func (s *LibraryService) markSelfWrite(path string) {
	s.selfWritesMu.Lock()
	s.selfWrites[filepath.Clean(path)] = time.Now().Add(selfWriteWindow)
	s.selfWritesMu.Unlock()
}

// isSelfWrite reports whether path was recently written by the app. Expired
// entries are pruned lazily on lookup.
func (s *LibraryService) isSelfWrite(path string) bool {
	key := filepath.Clean(path)
	s.selfWritesMu.Lock()
	defer s.selfWritesMu.Unlock()
	exp, ok := s.selfWrites[key]
	if !ok {
		return false
	}
	if time.Now().After(exp) {
		delete(s.selfWrites, key)
		return false
	}
	return true
}

func (s *LibraryService) Start(ctx context.Context) error {
	folders, err := s.watchedFolderRepo.GetAll(ctx)
	if err != nil {
		s.logger.Error("failed to load watched folders, starting with empty list", "error", err)
		folders = nil
	}

	for _, f := range folders {
		if err := s.watchRecursive(f.Path); err != nil {
			s.logger.Warn("Failed to watch folder", "path", f.Path, "error", err)
		}
	}

	// Clear any albums/artists/genres/composers stranded by a previous session.
	s.cleanupOrphanedEntities(ctx)

	go s.watchLoop()
	go s.StartArtistArtworkWorker(s.ctx)
	go s.maybeRescanArtistImages(s.ctx)
	return nil
}

func (s *LibraryService) Stop(ctx context.Context) error {
	s.cancel()
	return s.watcher.Close()
}

// GetSettings exposes the persisted app settings (used by the Wails layer to
// resolve artist artwork display preferences).
func (s *LibraryService) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	return s.settingsRepo.Load(ctx)
}

func (s *LibraryService) watchRecursive(root string) error {
	return filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			if err := s.watcher.Add(path); err != nil {
				return fmt.Errorf("failed to add %s to watcher: %w", path, err)
			}
		}
		return nil
	})
}

func (s *LibraryService) watchLoop() {
	for {
		select {
		case event, ok := <-s.watcher.Events:
			if !ok {
				return
			}
			s.handleEvent(event)
		case err, ok := <-s.watcher.Errors:
			if !ok {
				return
			}
			s.logger.Error("Watcher error", "error", err)
		}
	}
}

func (s *LibraryService) handleEvent(event fsnotify.Event) {
	s.logger.Debug("Received watcher event", "event", event)

	// Artist image files (artist.jpg/png) are handled separately from audio
	// tracks — they update artist artwork rather than the track table.
	if isArtistImageFile(filepath.Base(event.Name)) {
		go s.handleArtistImageEvent(event)
		return
	}

	// Skip events the app caused itself (e.g. metadata edits): UpdateMetadata
	// already re-imports the file, and a temp+rename write would otherwise be
	// misread as a deletion.
	if s.isSelfWrite(event.Name) {
		s.logger.Debug("Ignoring self-initiated write", "path", event.Name)
		return
	}

	if event.Has(fsnotify.Create) || event.Has(fsnotify.Write) {
		// If it's a directory, watch it and sync it
		// We need to check if it's a directory
		// But in Alpha fsnotify we might not know from event.
		// Use os.Stat or similar.
		// Wait, ImportFile/SyncFolder handles it.
		// But for Write, we want to debounce.

		// For simplicity, just import
		go func() {
			// Small delay to let file be written
			time.Sleep(500 * time.Millisecond)
			if err := s.ImportFile(context.Background(), event.Name); err != nil {
				s.logger.Debug("Failed to import file from watcher event", "path", event.Name, "error", err)
			}
		}()
	}

	if event.Has(fsnotify.Remove) || event.Has(fsnotify.Rename) {
		go func() {
			ctx := context.Background()

			// Atomic-save editors (and some taglib writes) replace a file via
			// temp-file + rename, which surfaces as Remove/Rename on the original
			// path even though the file still exists. Give it a moment, then
			// verify the file is really gone before deleting its row; if it's
			// still there, treat the event as a modification and re-import.
			time.Sleep(500 * time.Millisecond)
			if _, err := os.Stat(event.Name); err == nil {
				s.logger.Debug("Path still exists after remove/rename, re-importing instead of deleting", "path", event.Name)
				if err := s.ImportFile(ctx, event.Name); err != nil {
					s.logger.Debug("Failed to re-import file after remove/rename", "path", event.Name, "error", err)
				}
				return
			}

			s.logger.Info("File/Folder removed, cleaning up", "path", event.Name)

			var deletedIDs []string

			// 1. Resolve the track by its exact path so we delete the real row
			//    (deterministic ID can diverge if the stored path was normalized).
			if t, err := s.trackRepo.GetByPath(ctx, event.Name); err == nil && t != nil {
				if err := s.trackRepo.Delete(ctx, t.ID); err != nil {
					s.logger.Warn("Failed to delete track from DB on removal", "id", t.ID, "error", err)
				} else {
					deletedIDs = append(deletedIDs, t.ID)
				}
				if err := s.searchService.DeleteFromIndex(ctx, t.ID); err != nil {
					s.logger.Warn("Failed to delete track from Index on removal", "id", t.ID, "error", err)
				}
			}

			// 2. If it was a directory, delete all tracks inside it
			prefix := event.Name
			if !strings.HasSuffix(prefix, string(os.PathSeparator)) {
				prefix += string(os.PathSeparator)
			}

			tracks, err := s.trackRepo.GetByPathPrefix(ctx, prefix)
			if err == nil && len(tracks) > 0 {
				s.logger.Info("Directory removed, deleting tracks inside", "count", len(tracks), "prefix", prefix)
				for _, t := range tracks {
					if err := s.trackRepo.Delete(ctx, t.ID); err != nil {
						s.logger.Warn("Failed to delete track from DB", "id", t.ID, "error", err)
						continue
					}
					deletedIDs = append(deletedIDs, t.ID)
					if err := s.searchService.DeleteFromIndex(ctx, t.ID); err != nil {
						s.logger.Warn("Failed to delete track from Search", "id", t.ID, "error", err)
					}
				}
			}

			if len(deletedIDs) == 0 {
				return
			}

			s.cleanupOrphanedEntities(ctx)
			// Artwork cache cleanup scans the whole cache dir, so it isn't run
			// inline per event (would thrash on bulk deletes). Debounce it: bulk
			// deletes coalesce into a single cleanup a few seconds later.
			s.scheduleArtworkCleanup()

			// Notify frontend: per-track for in-place views + generic for full reloads
			if app := application.Get(); app != nil && app.Event != nil {
				for _, id := range deletedIDs {
					app.Event.Emit("library:track-deleted", id)
				}
				app.Event.Emit("library:updated", nil)
			}
		}()
	}
}

// handleArtistImageEvent reacts to a local artist image file being created,
// modified or removed while the app is running.
func (s *LibraryService) handleArtistImageEvent(event fsnotify.Event) {
	ctx := context.Background()
	dir := filepath.Dir(event.Name)

	if event.Has(fsnotify.Create) || event.Has(fsnotify.Write) {
		// Give the file a moment to finish being written.
		time.Sleep(500 * time.Millisecond)
		if _, err := os.Stat(event.Name); err != nil {
			return
		}
		ids := s.artistIDsForImageDir(ctx, dir)
		// Ambiguous: one artist.jpg mapping to several artists isn't
		// artist-specific — skip it.
		if len(ids) > 1 {
			s.logger.Info("Skipping ambiguous artist image change (maps to multiple artists)", "dir", dir, "artists", len(ids))
			return
		}
		for _, id := range ids {
			if _, err := s.setLocalArtistImage(ctx, id, event.Name); err != nil {
				s.logger.Warn("Failed to apply changed artist image", "artistID", id, "error", err)
			}
		}
		return
	}

	if event.Has(fsnotify.Remove) || event.Has(fsnotify.Rename) {
		settings, err := s.settingsRepo.Load(ctx)
		if err != nil {
			s.logger.Warn("Failed to load settings for artist image removal", "error", err)
			return
		}
		for _, id := range s.artistIDsForImageDir(ctx, dir) {
			artist, err := s.artistRepo.GetByID(ctx, id)
			if err != nil || artist == nil || artist.ArtworkKeyLocal == nil {
				continue
			}
			// Clear only the local source; manual/online remain and the display
			// re-resolves automatically.
			if err := s.clearArtistArtworkSource(ctx, id, domain.ArtworkSourceLocalFile); err != nil {
				s.logger.Warn("Failed to clear local artist artwork after image removal", "artistID", id, "error", err)
				continue
			}
			// Reflect the just-cleared local source, then fetch from Deezer only if the
			// online image would now actually be shown.
			artist.ArtworkKeyLocal = nil
			if artist.ShouldFetchOnline(settings.UseOnlineArtistArtwork, settings.PreferLocalArtistArtwork) {
				s.EnqueueArtistArtwork(id, fmt.Sprintf("artist-artwork:%s", id))
			}
		}
	}
}

// cleanupOrphanedEntities removes albums, artists, composers and genres that no
// longer reference any track. Errors are logged, not returned, since this is a
// best-effort cleanup invoked after deletions.
// cleanupOrphanedEntities removes albums/artists/composers/genres that no longer
// have any tracks, keeping the DB and the search index in sync. Genres aren't
// indexed, so only their rows are dropped.
func (s *LibraryService) cleanupOrphanedEntities(ctx context.Context) {
	if ids, err := s.albumRepo.DeleteOrphaned(ctx); err != nil {
		s.logger.Warn("Failed to delete orphaned albums", "error", err)
	} else {
		for _, id := range ids {
			if err := s.searchService.DeleteAlbumFromIndex(ctx, id); err != nil {
				s.logger.Warn("Failed to delete orphaned album from index", "id", id, "error", err)
			}
		}
	}
	if ids, err := s.artistRepo.DeleteOrphaned(ctx); err != nil {
		s.logger.Warn("Failed to delete orphaned artists", "error", err)
	} else {
		for _, id := range ids {
			if err := s.searchService.DeleteArtistFromIndex(ctx, id); err != nil {
				s.logger.Warn("Failed to delete orphaned artist from index", "id", id, "error", err)
			}
		}
	}
	if ids, err := s.composerRepo.DeleteOrphaned(ctx); err != nil {
		s.logger.Warn("Failed to delete orphaned composers", "error", err)
	} else {
		for _, id := range ids {
			if err := s.searchService.DeleteComposerFromIndex(ctx, id); err != nil {
				s.logger.Warn("Failed to delete orphaned composer from index", "id", id, "error", err)
			}
		}
	}
	if _, err := s.genreRepo.DeleteOrphaned(ctx); err != nil {
		s.logger.Warn("Failed to delete orphaned genres", "error", err)
	}
}

func (s *LibraryService) AddWatchedFolder(ctx context.Context, path string) error {
	path = filepath.Clean(path)
	s.logger.Info("Adding watched folder", "path", path)

	// Check for parent/child relationships
	existing, err := s.watchedFolderRepo.GetAll(ctx)
	if err != nil {
		return fmt.Errorf("failed to get existing watched folders: %w", err)
	}

	for _, f := range existing {
		if f.Path == path {
			return fmt.Errorf("folder already watched: %s", path)
		}

		// If new path is child of existing
		if isSubPath(f.Path, path) {
			return fmt.Errorf("folder is already covered by watched parent: %s", f.Path)
		}

		// If new path is parent of existing
		if isSubPath(path, f.Path) {
			s.logger.Info("New folder covers existing watched folder, removing child", "child", f.Path, "parent", path)
			if err := s.RemoveWatchedFolder(ctx, f.ID, true); err != nil {
				s.logger.Warn("Failed to remove child folder", "path", f.Path, "error", err)
			}
		}
	}

	folder := &domain.WatchedFolder{
		ID:        uuid.New().String(),
		Path:      path,
		CreatedAt: time.Now(),
	}

	if err := s.watchedFolderRepo.Save(ctx, folder); err != nil {
		return fmt.Errorf("failed to save watched folder: %w", err)
	}

	// Watch the new folder recursively
	if err := s.watchRecursive(path); err != nil {
		s.logger.Warn("Failed to watch new folder", "path", path, "error", err)
	}

	// Trigger initial sync in a goroutine
	go func() {
		if err := s.SyncFolder(context.Background(), path); err != nil {
			s.logger.Error("Failed to sync folder", "path", path, "error", err)
		}
	}()

	return nil
}

func (s *LibraryService) RemoveWatchedFolder(ctx context.Context, id string, keepTracks bool) error {
	folder, err := s.watchedFolderRepo.GetByID(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to get watched folder: %w", err)
	}
	if folder == nil {
		return nil
	}

	s.logger.Info("Removing watched folder", "path", folder.Path, "keepTracks", keepTracks)

	// 1. Unwatch
	if err := s.watcher.Remove(folder.Path); err != nil {
		s.logger.Warn("Failed to remove folder from watcher", "path", folder.Path, "error", err)
	}

	if !keepTracks {
		// 2. Get tracks for this folder to remove from search index
		tracks, err := s.trackRepo.GetByPathPrefix(ctx, folder.Path)
		if err == nil {
			for _, track := range tracks {
				if err := s.searchService.DeleteFromIndex(ctx, track.ID); err != nil {
					s.logger.Warn("Failed to delete track from search index", "id", track.ID, "error", err)
				}
			}
		}

		// 3. Delete tracks from DB
		if err := s.trackRepo.DeleteByPathPrefix(ctx, folder.Path); err != nil {
			return fmt.Errorf("failed to delete tracks from DB: %w", err)
		}

		// 4. Cleanup orphaned entities
		s.cleanupOrphanedEntities(ctx)

		// 5. Cleanup orphaned artworks
		if err := s.CleanupOrphanedArtworks(ctx); err != nil {
			s.logger.Warn("Failed to cleanup orphaned artworks", "error", err)
		}
	}

	// 6. Delete watched folder record
	if err := s.watchedFolderRepo.Delete(ctx, id); err != nil {
		return fmt.Errorf("failed to delete watched folder record: %w", err)
	}

	// 7. Notify frontend
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:updated", nil)
	}

	return nil
}

// scheduleArtworkCleanup debounces orphan-artwork cleanup: each call resets a
// short timer so a burst of watcher deletes triggers a single cache sweep.
func (s *LibraryService) scheduleArtworkCleanup() {
	s.artworkCleanupMu.Lock()
	defer s.artworkCleanupMu.Unlock()
	if s.artworkCleanupTimer != nil {
		s.artworkCleanupTimer.Stop()
	}
	s.artworkCleanupTimer = time.AfterFunc(5*time.Second, func() {
		if err := s.CleanupOrphanedArtworks(s.ctx); err != nil {
			s.logger.Warn("Failed to cleanup orphaned artworks (debounced)", "error", err)
		}
	})
}

func (s *LibraryService) CleanupOrphanedArtworks(ctx context.Context) error {
	keys, err := s.trackRepo.GetAllArtworkKeys(ctx)
	if err != nil {
		return err
	}

	activeKeys := make(map[string]bool)
	for _, k := range keys {
		activeKeys[k] = true
	}

	// Include artist artwork (all sources) so it isn't deleted while the artist
	// exists — and so all of an artist's images are removed once it's orphaned.
	artistKeys, err := s.artistRepo.GetAllArtworkKeys(ctx)
	if err != nil {
		return err
	}
	for _, k := range artistKeys {
		activeKeys[k] = true
	}

	return s.artworkCache.CleanupOrphaned(ctx, activeKeys)
}

func (s *LibraryService) SyncFolder(ctx context.Context, root string) error {
	s.logger.Info("Starting folder sync", "root", root)

	// Mark a bulk sync in progress so per-file ImportFile skips its own artist
	// image lookup — the batch pass below handles it far more cheaply.
	s.syncing.Add(1)
	defer s.syncing.Add(-1)

	supportedExtensions := SupportedAudioExtensions

	// 1. Count files
	var total int
	_ = filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err == nil && !d.IsDir() {
			filename := filepath.Base(path)
			if strings.HasPrefix(filename, ".") {
				return nil
			}

			ext := strings.ToLower(filepath.Ext(path))
			if supportedExtensions[ext] {
				total++
			}
		}
		return nil
	})

	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:sync-started", map[string]interface{}{
			"path":  root,
			"total": total,
		})
	}

	// 2. Import files
	var current int
	foundPaths := make(map[string]bool)
	imageDirs := make(map[string]bool) // directories containing an artist.{jpg,jpeg,png}
	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			s.logger.Warn("Error walking path", "path", path, "error", err)
			return nil
		}

		filename := filepath.Base(path)
		if strings.HasPrefix(filename, ".") {
			return nil
		}

		if d.IsDir() {
			return nil
		}

		if isArtistImageFile(filename) {
			imageDirs[filepath.Dir(path)] = true
			return nil
		}

		ext := strings.ToLower(filepath.Ext(path))
		if !supportedExtensions[ext] {
			return nil
		}

		foundPaths[filepath.Clean(path)] = true
		current++
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("library:sync-progress", domain.SyncProgress{
				Current: current,
				Total:   total,
				Path:    path,
			})
		}

		// Optimization: Check if file has changed
		info, err := d.Info()
		if err == nil {
			existing, err := s.trackRepo.GetByPath(ctx, path)
			if err == nil && existing != nil {
				if existing.FileSize == info.Size() && existing.Mtime.Unix() == info.ModTime().Unix() {
					return nil // Skip
				}
			}
		}

		if err := s.ImportFile(ctx, path); err != nil {
			s.logger.Error("Failed to import file", "path", path, "error", err)
		}
		return nil
	})

	if err != nil {
		return fmt.Errorf("failed to walk directory: %w", err)
	}

	// 2b. Apply local artist images in one batch (per image directory).
	s.applyLocalArtistImagesForDirs(ctx, imageDirs)

	// 3. Cleanup missing files
	s.logger.Info("Cleaning up missing files", "root", root)
	var deletedIDs []string
	existingTracks, err := s.trackRepo.GetByPathPrefix(ctx, root)
	if err == nil {
		for _, t := range existingTracks {
			if foundPaths[filepath.Clean(t.Path)] {
				continue
			}
			s.logger.Info("Removing missing track", "path", t.Path)
			if err := s.trackRepo.Delete(ctx, t.ID); err != nil {
				s.logger.Warn("Failed to delete missing track from DB", "path", t.Path, "error", err)
				continue
			}
			deletedIDs = append(deletedIDs, t.ID)
			if err := s.searchService.DeleteFromIndex(ctx, t.ID); err != nil {
				s.logger.Warn("Failed to delete missing track from Search", "path", t.Path, "error", err)
			}
		}
	}

	// Always clear orphans: a track removed here (or by an earlier run/path) can
	// leave an album/artist/genre/composer with no tracks.
	s.cleanupOrphanedEntities(ctx)
	if err := s.CleanupOrphanedArtworks(ctx); err != nil {
		s.logger.Warn("Failed to cleanup orphaned artworks", "error", err)
	}

	s.logger.Info("Finished folder sync", "root", root)
	if app := application.Get(); app != nil && app.Event != nil {
		for _, id := range deletedIDs {
			app.Event.Emit("library:track-deleted", id)
		}
		app.Event.Emit("library:sync-finished", root)
	}
	return nil
}

func (s *LibraryService) ImportFile(ctx context.Context, path string) error {
	info, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("failed to stat file %s: %w", path, err)
	}

	dto, err := s.metadataExtractor.Extract(ctx, path)
	if err != nil {
		return fmt.Errorf("failed to extract metadata from %s: %w", path, err)
	}

	dto.FileSize = info.Size()
	dto.Mtime = info.ModTime()

	// Extract artwork if available
	artworkData, mimeType, err := s.metadataExtractor.ExtractArtwork(ctx, path)
	if err == nil && artworkData != nil {
		s.logger.Debug("Artwork extracted", "path", path, "size", len(artworkData), "mime", mimeType)
		key, err := s.artworkCache.Save(ctx, artworkData, mimeType)
		if err != nil {
			s.logger.Warn("Failed to save artwork", "path", path, "error", err)
		} else {
			s.logger.Debug("Artwork saved", "path", path, "key", key)
			dto.ArtworkKey = key
			dto.Album.ArtworkKey = key
		}
	} else if err != nil {
		s.logger.Debug("Error extracting artwork", "path", path, "error", err)
	} else {
		s.logger.Debug("No artwork found in file", "path", path)
	}

	// Resolve related entities
	if err := s.resolveEntities(ctx, dto); err != nil {
		return fmt.Errorf("failed to resolve entities for %s: %w", path, err)
	}

	// Pick up a local artist image (artist.jpg/png) sitting next to the track or
	// in the parent (artist) folder, for the track's album artists — the folder
	// belongs to the album artist's discography, not to guest/track artists.
	// During a bulk sync this is skipped — SyncFolder's batch pass handles it
	// once per directory instead of once per track.
	if s.syncing.Load() == 0 {
		var artistIDs []string
		for _, a := range dto.AlbumArtists {
			artistIDs = append(artistIDs, a.ID)
		}
		s.resolveTrackArtistImages(ctx, path, artistIDs)
	}

	// Extract lyrics from metadata
	if s.lyricsService != nil {
		if metaLyrics, isSynced, err := s.metadataExtractor.ExtractLyrics(ctx, path); err == nil && metaLyrics != "" {
			source := "meta-plain"
			if isSynced {
				source = "meta-synced"
			}
			if err := s.lyricsService.SaveMetaLyrics(ctx, dto.ID, metaLyrics, source); err != nil {
				s.logger.Warn("Failed to save metadata lyrics", "path", path, "error", err)
			}
		}
	}

	// Index in Search
	if err := s.searchService.IndexTrack(ctx, dto); err != nil {
		s.logger.Warn("Failed to index track", "path", path, "error", err)
	}

	// Notify internal listeners and frontend
	s.notifyTrackUpdated(dto)
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:track-updated", dto)
	}

	return nil
}

func (s *LibraryService) resolveEntities(ctx context.Context, dto *domain.TrackDTO) error {
	// 1. Resolve Artists
	var artistIDs []string
	for _, artist := range dto.Artists {
		existing, _ := s.artistRepo.GetByNormalizationKey(ctx, artist.NormalizationKey)
		if existing != nil {
			artist.ID = existing.ID
		} else {
			artist.ID = s.generateID(artist.NormalizationKey)
		}
		if err := s.artistRepo.Upsert(ctx, artist); err != nil {
			return err
		}
		artistIDs = append(artistIDs, artist.ID)

		// Index artist in search
		if err := s.searchService.IndexArtist(ctx, artist); err != nil {
			s.logger.Warn("Failed to index artist", "name", artist.Name, "error", err)
		}
	}

	// 2. Resolve Album Artists
	var albumArtistIDs []string
	for _, aa := range dto.AlbumArtists {
		existing, _ := s.artistRepo.GetByNormalizationKey(ctx, aa.NormalizationKey)
		if existing != nil {
			aa.ID = existing.ID
		} else {
			aa.ID = s.generateID(aa.NormalizationKey)
		}
		if err := s.artistRepo.Upsert(ctx, aa); err != nil {
			return err
		}
		albumArtistIDs = append(albumArtistIDs, aa.ID)

		// Index album artist in search
		if err := s.searchService.IndexArtist(ctx, aa); err != nil {
			s.logger.Warn("Failed to index album artist", "name", aa.Name, "error", err)
		}
	}

	// 3. Resolve Album
	if dto.Album != nil && dto.Album.Title != "" {
		// Use first album artist or first artist as primary for album normalization
		primaryArtistID := ""
		if len(albumArtistIDs) > 0 {
			primaryArtistID = albumArtistIDs[0]
		} else if len(artistIDs) > 0 {
			primaryArtistID = artistIDs[0]
		}

		dto.Album.NormalizationKey = domain.NormalizationKey(dto.Album.Title) + "|" + primaryArtistID
		existing, _ := s.albumRepo.GetByNormalizationKey(ctx, dto.Album.NormalizationKey)
		if existing != nil {
			dto.Album.ID = existing.ID
		} else {
			dto.Album.ID = s.generateID(dto.Album.NormalizationKey)
		}

		// Try to preserve artwork
		if dto.ArtworkKey == "" && existing != nil {
			dto.Album.ArtworkKey = existing.ArtworkKey
			dto.ArtworkKey = existing.ArtworkKey
		}

		if err := s.albumRepo.Upsert(ctx, dto.Album); err != nil {
			return err
		}

		// Use album artists if available, otherwise fall back to track artists
		finalAlbumArtistIDs := albumArtistIDs
		if len(finalAlbumArtistIDs) == 0 {
			finalAlbumArtistIDs = artistIDs
		}

		if err := s.albumRepo.SetArtists(ctx, dto.Album.ID, finalAlbumArtistIDs); err != nil {
			return err
		}
		dto.AlbumID = dto.Album.ID

		// Index album in search (need full AlbumDTO with artists populated for best indexing)
		fullAlbum := &domain.AlbumDTO{
			Album: *dto.Album,
		}
		// Populate artists from resolved album artists or track artists
		if len(albumArtistIDs) > 0 {
			fullAlbum.Artists = dto.AlbumArtists
		} else {
			fullAlbum.Artists = dto.Artists
		}

		if err := s.searchService.IndexAlbum(ctx, fullAlbum); err != nil {
			s.logger.Warn("Failed to index album", "title", fullAlbum.Title, "error", err)
		}
	}

	// 4. Resolve Genres
	var genreIDs []string
	for _, g := range dto.Genres {
		existing, _ := s.genreRepo.GetByNormalizationKey(ctx, g.NormalizationKey)
		if existing != nil {
			g.ID = existing.ID
		} else {
			g.ID = s.generateID(g.NormalizationKey)
		}
		if err := s.genreRepo.Upsert(ctx, g); err != nil {
			return err
		}
		genreIDs = append(genreIDs, g.ID)
	}

	// 5. Resolve Composers
	var composerIDs []string
	for _, c := range dto.Composers {
		existing, _ := s.composerRepo.GetByNormalizationKey(ctx, c.NormalizationKey)
		if existing != nil {
			c.ID = existing.ID
		} else {
			c.ID = s.generateID(c.NormalizationKey)
		}
		if err := s.composerRepo.Upsert(ctx, c); err != nil {
			return err
		}
		composerIDs = append(composerIDs, c.ID)

		// Index composer in search
		if err := s.searchService.IndexComposer(ctx, c); err != nil {
			s.logger.Warn("Failed to index composer", "name", c.Name, "error", err)
		}
	}

	// 6. Finalize Track
	dto.ID = s.generateID(dto.Path)

	if err := s.trackRepo.Upsert(ctx, &dto.Track); err != nil {
		return err
	}

	if err := s.trackRepo.SetArtists(ctx, dto.ID, artistIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetAlbumArtists(ctx, dto.ID, albumArtistIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetGenres(ctx, dto.ID, genreIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetComposers(ctx, dto.ID, composerIDs); err != nil {
		return err
	}

	return nil
}

func (s *LibraryService) generateID(seed string) string {
	return uuid.NewMD5(uuid.NameSpaceURL, []byte(seed)).String()
}

func isSubPath(parent, child string) bool {
	rel, err := filepath.Rel(parent, child)
	if err != nil {
		return false
	}
	return !strings.HasPrefix(rel, "..") && rel != ".." && rel != "."
}

// IsPathValid returns nil if path exists on disk, has a supported extension, and
// lives under one of the app's watched folders.
func (s *LibraryService) IsPathValid(ctx context.Context, path string) error {
	if _, err := os.Stat(path); err != nil {
		return fmt.Errorf("file not found: %w", err)
	}
	ext := strings.ToLower(filepath.Ext(path))
	if !SupportedAudioExtensions[ext] {
		return fmt.Errorf("unsupported format: %s", ext)
	}
	folders, err := s.watchedFolderRepo.GetAll(ctx)
	if err != nil {
		return err
	}
	for _, f := range folders {
		if isSubPath(f.Path, path) {
			return nil
		}
	}
	return fmt.Errorf("path not under any watched folder")
}

// EnsureTrack returns the TrackDTO for the given path, importing it from disk
// if it is not yet in the library. For newly imported tracks, fallbackTitle and
// fallbackArtist are applied only when the file's own tags are empty.
func (s *LibraryService) EnsureTrack(ctx context.Context, path, fallbackTitle, fallbackArtist string) (*domain.TrackDTO, error) {
	track, err := s.trackRepo.GetByPath(ctx, path)
	if err != nil {
		return nil, err
	}
	if track != nil {
		return track, nil
	}

	if err := s.ImportFile(ctx, path); err != nil {
		return nil, err
	}
	track, err = s.trackRepo.GetByPath(ctx, path)
	if err != nil || track == nil {
		return nil, fmt.Errorf("track missing after import: %s", path)
	}

	changed := false
	if track.Title == "" && fallbackTitle != "" {
		track.Title = fallbackTitle
		changed = true
	}
	if track.RawArtistNames == "" && fallbackArtist != "" {
		track.RawArtistNames = fallbackArtist
		changed = true
	}
	if changed {
		_ = s.trackRepo.Upsert(ctx, &track.Track)
	}
	return track, nil
}

// ShowInExplorer opens the native file explorer and selects the file.
func (s *LibraryService) ShowInExplorer(ctx context.Context, id string) error {
	track, err := s.trackRepo.GetByID(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to get track: %w", err)
	}
	if track == nil {
		return fmt.Errorf("track not found: %s", id)
	}

	path := track.Path
	var cmd *exec.Cmd

	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", "-R", path)
	case "windows":
		cmd = exec.Command("explorer.exe", "/select,", path)
	default: // linux and others
		cmd = exec.Command("xdg-open", filepath.Dir(path))
	}

	return cmd.Run()
}

// ToggleFavorite toggles the favorite state of a track. Returns the new state.
func (s *LibraryService) ToggleFavorite(ctx context.Context, id string) (bool, error) {
	newState, err := s.trackRepo.ToggleFavorite(ctx, id)
	if err != nil {
		return false, fmt.Errorf("failed to toggle favorite: %w", err)
	}
	dto, err := s.trackRepo.GetByID(ctx, id)
	if err == nil && dto != nil {
		s.notifyTrackUpdated(dto)
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("library:track-updated", dto)
		}
	}
	return newState, nil
}

// UpdateMetadata writes tag changes to the audio file and re-imports to update DB and search.
func (s *LibraryService) UpdateMetadata(ctx context.Context, id string, fields domain.MetadataUpdate) error {
	track, err := s.trackRepo.GetByID(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to get track: %w", err)
	}
	if track == nil {
		return fmt.Errorf("track not found: %s", id)
	}
	// Suppress watcher events for this path: we're about to write the file
	// ourselves, then re-import explicitly below. Without this the watcher's
	// Write event redundantly re-imports, and a temp+rename write surfaces as
	// Remove/Rename and deletes the track from the DB.
	s.markSelfWrite(track.Path)

	if err := s.metadataWriter.WriteMetadata(ctx, track.Path, fields); err != nil {
		return fmt.Errorf("failed to write metadata: %w", err)
	}
	if err := s.ImportFile(ctx, track.Path); err != nil {
		return err
	}

	// A metadata edit can move the track to a different album/artist/genre, leaving
	// the originals with zero tracks. Prune those orphans (DB + search index) so
	// they don't linger, then tell the frontend to reload list views.
	s.cleanupOrphanedEntities(ctx)
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:updated", nil)
	}
	return nil
}

// GetAlbumColors returns the theme colors for an album's artwork.
func (s *LibraryService) GetAlbumColors(ctx context.Context, id string) (*domain.ThemeColors, error) {
	album, err := s.albumRepo.GetByID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to get album: %w", err)
	}
	if album == nil {
		return nil, fmt.Errorf("album not found: %s", id)
	}

	if album.ArtworkKey == "" {
		return nil, nil
	}

	path := s.artworkCache.GetPath(album.ArtworkKey)
	colors, err := artwork.ExtractPalette(path)
	if err != nil {
		return nil, fmt.Errorf("failed to extract palette: %w", err)
	}

	return colors, nil
}

func (s *LibraryService) ReindexAll(ctx context.Context) error {
	s.logger.Info("Starting full library re-indexing")

	// Calculate total items
	tracks, _ := s.trackRepo.GetAll(ctx)
	albums, _ := s.albumRepo.GetAll(ctx)
	artists, _ := s.artistRepo.GetAll(ctx)
	composers, _ := s.composerRepo.GetAll(ctx)
	playlists, _ := s.playlistRepo.GetAll(ctx)

	total := len(tracks) + len(albums) + len(artists) + len(composers) + len(playlists)
	current := 0

	emitProgress := func(path string) {
		current++
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("library:sync-progress", domain.SyncProgress{
				Current: current,
				Total:   total,
				Path:    path,
			})
		}
	}

	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:sync-started", map[string]interface{}{
			"path":  "Re-indexing Search",
			"total": total,
		})
	}

	// Resolve full album DTOs (the GetAll result lacks the relations the
	// index needs).
	albumDTOs := make([]*domain.AlbumDTO, 0, len(albums))
	for _, a := range albums {
		if dto, _ := s.albumRepo.GetByID(ctx, a.ID); dto != nil {
			albumDTOs = append(albumDTOs, dto)
		}
	}

	// Batch the writes: one fsync per chunk instead of one per document.
	// Per-document commits make a full reindex appear to hang on Windows
	// (slow NTFS fsync) for large libraries.
	err := s.searchService.BatchReindex(ctx, &domain.ReindexData{
		Tracks:    tracks,
		Albums:    albumDTOs,
		Artists:   artists,
		Composers: composers,
		Playlists: playlists,
	}, emitProgress)
	if err != nil {
		s.logger.Warn("Re-indexing did not complete", "error", err)
	}

	s.logger.Info("Finished full library re-indexing")
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:sync-finished", "Search Index")
	}
	return err
}
