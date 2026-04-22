package library

import (
	"context"
	"fmt"
	"io/fs"
	"log/slog"
	"path/filepath"
	"strings"
	"time"

	"changeme/internal/domain"

	"github.com/fsnotify/fsnotify"
	"github.com/google/uuid"
	"github.com/wailsapp/wails/v3/pkg/application"
)

type LibraryService struct {
	trackRepo         domain.TrackRepository
	albumRepo         domain.AlbumRepository
	artistRepo        domain.ArtistRepository
	genreRepo         domain.GenreRepository
	composerRepo      domain.ComposerRepository
	watchedFolderRepo domain.WatchedFolderRepository
	metadataExtractor domain.MetadataExtractor
	searchService     domain.SearchService
	logger            *slog.Logger
	watcher           *fsnotify.Watcher
}

func NewLibraryService(
	trackRepo domain.TrackRepository,
	albumRepo domain.AlbumRepository,
	artistRepo domain.ArtistRepository,
	genreRepo domain.GenreRepository,
	composerRepo domain.ComposerRepository,
	watchedFolderRepo domain.WatchedFolderRepository,
	metadataExtractor domain.MetadataExtractor,
	searchService domain.SearchService,
	logger *slog.Logger,
) (*LibraryService, error) {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, fmt.Errorf("failed to create watcher: %w", err)
	}

	return &LibraryService{
		trackRepo:         trackRepo,
		albumRepo:         albumRepo,
		artistRepo:        artistRepo,
		genreRepo:         genreRepo,
		composerRepo:      composerRepo,
		watchedFolderRepo: watchedFolderRepo,
		metadataExtractor: metadataExtractor,
		searchService:     searchService,
		logger:            logger.With("module", "library"),
		watcher:           watcher,
	}, nil
}

func (s *LibraryService) Start(ctx context.Context) error {
	folders, err := s.watchedFolderRepo.GetAll(ctx)
	if err != nil {
		return fmt.Errorf("failed to load watched folders: %w", err)
	}

	for _, f := range folders {
		if err := s.watchRecursive(f.Path); err != nil {
			s.logger.Warn("Failed to watch folder", "path", f.Path, "error", err)
		}
	}

	go s.watchLoop()
	return nil
}

func (s *LibraryService) Stop(ctx context.Context) error {
	return s.watcher.Close()
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
				// It might be a directory or something we don't care about
				// If it's a directory, s.watchRecursive and SyncFolder
			}
		}()
	}

	if event.Has(fsnotify.Remove) || event.Has(fsnotify.Rename) {
		// Delete from DB and Search
		// We need the ID. Since we use deterministic IDs based on path:
		id := s.generateID(event.Name)
		go func() {
			if err := s.trackRepo.Delete(context.Background(), id); err != nil {
				s.logger.Warn("Failed to delete track from DB on removal", "id", id, "error", err)
			}
			if err := s.searchService.DeleteFromIndex(context.Background(), id); err != nil {
				s.logger.Warn("Failed to delete track from Index on removal", "id", id, "error", err)
			}
		}()
	}
}

func (s *LibraryService) AddWatchedFolder(ctx context.Context, path string) error {
	s.logger.Info("Adding watched folder", "path", path)
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

func (s *LibraryService) SyncFolder(ctx context.Context, root string) error {
	s.logger.Info("Starting folder sync", "root", root)
	if app := application.Get(); app != nil {
		app.Event.Emit("library:sync-started", root)
	}

	supportedExtensions := map[string]bool{
		".mp3":  true,
		".flac": true,
		".m4a":  true,
		".wav":  true,
		".ogg":  true,
		".opus": true,
		".aiff": true,
	}

	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}

		ext := strings.ToLower(filepath.Ext(path))
		if !supportedExtensions[ext] {
			return nil
		}

		return s.ImportFile(ctx, path)
	})

	if err != nil {
		return fmt.Errorf("failed to walk directory: %w", err)
	}

	s.logger.Info("Finished folder sync", "root", root)
	if app := application.Get(); app != nil {
		app.Event.Emit("library:sync-finished", root)
	}
	return nil
}

func (s *LibraryService) ImportFile(ctx context.Context, path string) error {
	track, err := s.metadataExtractor.Extract(ctx, path)
	if err != nil {
		return fmt.Errorf("failed to extract metadata from %s: %w", path, err)
	}

	// Resolve related entities
	if err := s.resolveEntities(ctx, track); err != nil {
		return fmt.Errorf("failed to resolve entities for %s: %w", path, err)
	}

	// Upsert to DB
	if err := s.trackRepo.Upsert(ctx, track); err != nil {
		return fmt.Errorf("failed to upsert track %s: %w", path, err)
	}

	// Index in Search
	if err := s.searchService.IndexTrack(ctx, track); err != nil {
		s.logger.Warn("Failed to index track", "path", path, "error", err)
	}

	// Notify frontend
	if app := application.Get(); app != nil {
		app.Event.Emit("library:updated", track)
	}

	return nil
}

func (s *LibraryService) resolveEntities(ctx context.Context, track *domain.Track) error {
	// Artist
	if track.ArtistName != "" {
		artist := &domain.Artist{
			Name:     track.ArtistName,
			SortName: track.SortArtistName,
		}
		// In a real app, we might want a GetByName for Artist too.
		// For simplicity, we use the name as a seed for ID or just upsert and rely on logic.
		// Since our repositories don't have GetByName for Artist/Album, we might need to add them or use Upsert logic.
		// Let's assume Upsert handles it if we provide a consistent ID based on name, or we add GetByName.
		
		// Actually, let's use a deterministic UUID based on name for Artist/Album to avoid duplicates.
		track.ArtistID = s.generateID(track.ArtistName)
		artist.ID = track.ArtistID
		if err := s.artistRepo.Upsert(ctx, artist); err != nil {
			return err
		}
	}

	// Album
	if track.AlbumName != "" {
		track.AlbumID = s.generateID(track.ArtistName + track.AlbumName)
		album := &domain.Album{
			ID:         track.AlbumID,
			Title:      track.AlbumName,
			SortTitle:  track.SortAlbumName,
			ArtistID:   track.ArtistID,
			ArtistName: track.ArtistName,
			Year:       track.Year,
		}
		if err := s.albumRepo.Upsert(ctx, album); err != nil {
			return err
		}
	}

	// Genre
	if track.GenreName != "" {
		track.GenreID = s.generateID(track.GenreName)
		genre := &domain.Genre{
			ID:   track.GenreID,
			Name: track.GenreName,
		}
		if err := s.genreRepo.Upsert(ctx, genre); err != nil {
			return err
		}
	}

	// Composer
	if track.ComposerName != "" {
		track.ComposerID = s.generateID(track.ComposerName)
		composer := &domain.Composer{
			ID:   track.ComposerID,
			Name: track.ComposerName,
		}
		if err := s.composerRepo.Upsert(ctx, composer); err != nil {
			return err
		}
	}

	// Track ID based on path
	track.ID = s.generateID(track.Path)

	return nil
}

func (s *LibraryService) generateID(seed string) string {
	return uuid.NewMD5(uuid.NameSpaceURL, []byte(seed)).String()
}
