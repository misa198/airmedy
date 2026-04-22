package library

import (
	"context"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
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
	artworkCache      domain.ArtworkCache
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
	artworkCache domain.ArtworkCache,
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
		artworkCache:      artworkCache,
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
		if err := s.albumRepo.DeleteOrphaned(ctx); err != nil {
			s.logger.Warn("Failed to delete orphaned albums", "error", err)
		}
		if err := s.artistRepo.DeleteOrphaned(ctx); err != nil {
			s.logger.Warn("Failed to delete orphaned artists", "error", err)
		}
		if err := s.composerRepo.DeleteOrphaned(ctx); err != nil {
			s.logger.Warn("Failed to delete orphaned composers", "error", err)
		}
		if err := s.genreRepo.DeleteOrphaned(ctx); err != nil {
			s.logger.Warn("Failed to delete orphaned genres", "error", err)
		}

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
	if app := application.Get(); app != nil {
		app.Event.Emit("library:updated", nil)
	}

	return nil
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

	return s.artworkCache.CleanupOrphaned(ctx, activeKeys)
}

func (s *LibraryService) SyncFolder(ctx context.Context, root string) error {
	s.logger.Info("Starting folder sync", "root", root)

	supportedExtensions := map[string]bool{
		".mp3":  true,
		".flac": true,
		".m4a":  true,
		".wav":  true,
		".ogg":  true,
		".opus": true,
		".aiff": true,
	}

	// 1. Count files
	var total int
	_ = filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err == nil && !d.IsDir() {
			ext := strings.ToLower(filepath.Ext(path))
			if supportedExtensions[ext] {
				total++
			}
		}
		return nil
	})

	if app := application.Get(); app != nil {
		app.Event.Emit("library:sync-started", map[string]interface{}{
			"path":  root,
			"total": total,
		})
	}

	// 2. Import files
	var current int
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

		current++
		if app := application.Get(); app != nil {
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
	info, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("failed to stat file %s: %w", path, err)
	}

	dto, err := s.metadataExtractor.Extract(ctx, path)
	if err != nil {
		return fmt.Errorf("failed to extract metadata from %s: %w", path, err)
	}

	dto.Track.FileSize = info.Size()
	dto.Track.Mtime = info.ModTime()

	// Extract artwork if available
	artworkData, mimeType, err := s.metadataExtractor.ExtractArtwork(ctx, path)
	if err == nil && artworkData != nil {
		s.logger.Debug("Artwork extracted", "path", path, "size", len(artworkData), "mime", mimeType)
		key, err := s.artworkCache.Save(ctx, artworkData, mimeType)
		if err != nil {
			s.logger.Warn("Failed to save artwork", "path", path, "error", err)
		} else {
			s.logger.Debug("Artwork saved", "path", path, "key", key)
			dto.Track.ArtworkKey = key
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

	// Upsert to DB
	if err := s.trackRepo.Upsert(ctx, &dto.Track); err != nil {
		return fmt.Errorf("failed to upsert track %s: %w", path, err)
	}

	// Index in Search
	if err := s.searchService.IndexTrack(ctx, dto); err != nil {
		s.logger.Warn("Failed to index track", "path", path, "error", err)
	}

	// Notify frontend
	if app := application.Get(); app != nil {
		app.Event.Emit("library:updated", dto)
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
		if dto.Track.ArtworkKey == "" && existing != nil {
			dto.Album.ArtworkKey = existing.ArtworkKey
			dto.Track.ArtworkKey = existing.ArtworkKey
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
		dto.Track.AlbumID = dto.Album.ID
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
	}

	// 6. Finalize Track
	dto.Track.ID = s.generateID(dto.Track.Path)
	if err := s.trackRepo.Upsert(ctx, &dto.Track); err != nil {
		return err
	}

	// 7. Update Track Relationships
	if err := s.trackRepo.SetArtists(ctx, dto.Track.ID, artistIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetAlbumArtists(ctx, dto.Track.ID, albumArtistIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetGenres(ctx, dto.Track.ID, genreIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetComposers(ctx, dto.Track.ID, composerIDs); err != nil {
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
