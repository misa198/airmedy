package wails

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"airmedy/internal/app/library"
	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"
	"github.com/wailsapp/wails/v3/pkg/application"
)

type LibraryService struct {
	libService   *library.LibraryService
	folderRepo   domain.WatchedFolderRepository
	trackRepo    domain.TrackRepository
	albumRepo    domain.AlbumRepository
	artistRepo   domain.ArtistRepository
	genreRepo    domain.GenreRepository
	composerRepo domain.ComposerRepository
	artworkCache domain.ArtworkCache
}

func NewLibraryService(
	libService *library.LibraryService,
	folderRepo domain.WatchedFolderRepository,
	trackRepo domain.TrackRepository,
	albumRepo domain.AlbumRepository,
	artistRepo domain.ArtistRepository,
	genreRepo domain.GenreRepository,
	composerRepo domain.ComposerRepository,
	artworkCache domain.ArtworkCache,
) *LibraryService {
	return &LibraryService{
		libService:   libService,
		folderRepo:   folderRepo,
		trackRepo:    trackRepo,
		albumRepo:    albumRepo,
		artistRepo:   artistRepo,
		genreRepo:    genreRepo,
		composerRepo: composerRepo,
		artworkCache: artworkCache,
	}
}

// DownloadArtwork prompts for a destination and writes the ORIGINAL (full-size)
// artwork bytes to disk. defaultName is the suggested filename WITHOUT extension.
func (s *LibraryService) DownloadArtwork(artworkKey, defaultName string) error {
	if artworkKey == "" {
		return fmt.Errorf("no artwork")
	}
	app := application.Get()
	if app == nil {
		return fmt.Errorf("application not initialized")
	}

	srcPath := s.artworkCache.GetPath(artworkKey)
	data, err := os.ReadFile(srcPath)
	if err != nil {
		return fmt.Errorf("failed to read artwork: %w", err)
	}

	ext := filepath.Ext(artworkKey) // ".jpg" / ".png"
	if ext == "" {
		ext = ".jpg"
	}
	name := sanitizeFilename(defaultName)
	if name == "" {
		name = "artwork"
	}

	dest, err := app.Dialog.SaveFile().
		SetMessage("Save Artwork").
		SetFilename(name+ext).
		AddFilter("Image", "*"+ext).
		PromptForSingleSelection()
	if err != nil {
		return err
	}
	if dest == "" {
		return nil // cancelled
	}
	return os.WriteFile(dest, data, 0644)
}

// sanitizeFilename strips characters illegal in filenames across platforms.
func sanitizeFilename(name string) string {
	replacer := strings.NewReplacer(
		"/", "", "\\", "", ":", "", "*", "", "?", "", "\"", "",
		"<", "", ">", "", "|", "",
	)
	return strings.Trim(replacer.Replace(name), " .")
}

func (s *LibraryService) SelectFolder() (string, error) {
	app := application.Get()
	if app == nil {
		return "", fmt.Errorf("application not initialized")
	}
	return app.Dialog.OpenFile().
		CanChooseDirectories(true).
		CanChooseFiles(false).
		SetTitle("Select Music Folder").
		PromptForSingleSelection()
}

func (s *LibraryService) GetWatchedFolders() ([]*domain.WatchedFolder, error) {
	return s.folderRepo.GetAll(context.Background())
}

func (s *LibraryService) AddFolder(path string) error {
	return s.libService.AddWatchedFolder(context.Background(), path)
}

func (s *LibraryService) RemoveFolder(id string) error {
	return s.libService.RemoveWatchedFolder(context.Background(), id, false)
}

func (s *LibraryService) SyncAll() error {
	folders, err := s.folderRepo.GetAll(context.Background())
	if err != nil {
		return err
	}

	for _, folder := range folders {
		go func(path string) {
			_ = s.libService.SyncFolder(context.Background(), path)
		}(folder.Path)
	}
	return nil
}

func (s *LibraryService) ReindexAll() error {
	go func() {
		_ = s.libService.ReindexAll(context.Background())
	}()
	return nil
}

func (s *LibraryService) ImportAll() error {
	folders, err := s.folderRepo.GetAll(context.Background())
	if err != nil {
		return err
	}

	for _, folder := range folders {
		go func(path string) {
			_ = s.libService.SyncFolder(context.Background(), path)
		}(folder.Path)
	}
	return nil
}

func (s *LibraryService) GetAllTracks() ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetAll(context.Background())
}

func (s *LibraryService) GetTrackCount() (int, error) {
	return s.trackRepo.Count(context.Background())
}

func (s *LibraryService) GetTracksPaginated(offset, limit int) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetPaginated(context.Background(), offset, limit)
}

func (s *LibraryService) GetMostListenedTracks(limit int) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetMostListened(context.Background(), limit)
}

func (s *LibraryService) GetLeastListenedTracks(limit int) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetLeastListened(context.Background(), limit)
}

func (s *LibraryService) GetRecentlyPlayedTracks(limit int) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetRecentlyPlayed(context.Background(), limit)
}

func (s *LibraryService) GetRecentlyAddedTracks(limit int) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetRecentlyAdded(context.Background(), limit)
}

func (s *LibraryService) GetTracksByAlbumID(albumID string) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetByAlbumID(context.Background(), albumID)
}

func (s *LibraryService) GetTracksByArtistID(artistID string) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetByArtistID(context.Background(), artistID)
}

func (s *LibraryService) GetTracksByGenreID(genreID string) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetByGenreID(context.Background(), genreID)
}

func (s *LibraryService) GetTracksByComposerID(composerID string) ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetByComposerID(context.Background(), composerID)
}

func (s *LibraryService) GetAllAlbums() ([]*domain.AlbumDTO, error) {
	return s.albumRepo.GetAll(context.Background())
}

func (s *LibraryService) GetAlbumByID(id string) (*domain.AlbumDTO, error) {
	return s.albumRepo.GetByID(context.Background(), id)
}

func (s *LibraryService) GetRecentlyAddedAlbums(limit int) ([]*domain.AlbumDTO, error) {
	return s.albumRepo.GetRecentlyAdded(context.Background(), limit)
}

func (s *LibraryService) GetAlbumsByArtistID(artistID string) ([]*domain.AlbumDTO, error) {
	return s.albumRepo.GetByArtistID(context.Background(), artistID)
}

func (s *LibraryService) GetAllArtists() ([]*domain.Artist, error) {
	ctx := context.Background()
	artists, err := s.artistRepo.GetAll(ctx)
	if err != nil {
		return nil, err
	}
	preferLocal := s.preferLocalArtistArtwork(ctx)
	for _, a := range artists {
		a.ArtworkKey = a.ResolveArtworkKey(preferLocal)
	}
	return artists, nil
}

func (s *LibraryService) GetArtistByID(id string) (*domain.Artist, error) {
	ctx := context.Background()
	artist, err := s.artistRepo.GetByID(ctx, id)
	if err != nil || artist == nil {
		return artist, err
	}
	artist.ArtworkKey = artist.ResolveArtworkKey(s.preferLocalArtistArtwork(ctx))
	return artist, nil
}

// preferLocalArtistArtwork reports whether local images should be preferred over
// the Deezer image. There is a single user toggle, "Online Artist Artwork":
// when off (or settings unreadable) local images win.
func (s *LibraryService) preferLocalArtistArtwork(ctx context.Context) bool {
	settings, err := s.libService.GetSettings(ctx)
	if err != nil || settings == nil {
		return true
	}
	return !settings.UseOnlineArtistArtwork
}

func (s *LibraryService) GetAllGenres() ([]*domain.Genre, error) {
	return s.genreRepo.GetAll(context.Background())
}

func (s *LibraryService) GetGenreByID(id string) (*domain.Genre, error) {
	return s.genreRepo.GetByID(context.Background(), id)
}

func (s *LibraryService) GetAllComposers() ([]*domain.Composer, error) {
	return s.composerRepo.GetAll(context.Background())
}

func (s *LibraryService) GetComposerByID(id string) (*domain.Composer, error) {
	return s.composerRepo.GetByID(context.Background(), id)
}

func (s *LibraryService) GetSyncStatus() (*domain.SyncProgress, error) {
	// This is a dummy method to ensure SyncProgress model is generated for the frontend.
	return nil, nil
}

func (s *LibraryService) GetFavoriteTracks() ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetFavorites(context.Background())
}

func (s *LibraryService) ToggleFavorite(trackID string) (bool, error) {
	return s.libService.ToggleFavorite(context.Background(), trackID)
}

func (s *LibraryService) ShowInExplorer(trackID string) error {
	return s.libService.ShowInExplorer(context.Background(), trackID)
}

func (s *LibraryService) UpdateTrackMetadata(trackID string, update domain.MetadataUpdate) error {
	return s.libService.UpdateMetadata(context.Background(), trackID, update)
}

func (s *LibraryService) GetAlbumColors(id string) (*domain.ThemeColors, error) {
	return s.libService.GetAlbumColors(context.Background(), id)
}

// GetArtistColors returns the theme colors for an artist's resolved artwork.
func (s *LibraryService) GetArtistColors(id string) (*domain.ThemeColors, error) {
	ctx := context.Background()
	artist, err := s.artistRepo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if artist == nil {
		return nil, fmt.Errorf("artist not found: %s", id)
	}

	key := artist.ResolveArtworkKey(s.preferLocalArtistArtwork(ctx))
	if key == "" {
		return nil, nil
	}

	return artwork.ExtractPalette(s.artworkCache.GetPath(key))
}

func (s *LibraryService) GetArtistArtwork(artistID, eventID string) (*string, error) {
	ctx := context.Background()
	artist, err := s.artistRepo.GetByID(ctx, artistID)
	if err != nil {
		return nil, err
	}
	if artist == nil {
		return nil, fmt.Errorf("artist not found")
	}

	settings, _ := s.libService.GetSettings(ctx)
	useOnline := settings != nil && settings.UseOnlineArtistArtwork
	preferLocal := !useOnline

	// When online is enabled and no Deezer image is cached yet, fetch it (even if a
	// local image exists, so it's ready and shown per preference).
	if useOnline && artist.ArtworkKeyOnline == nil {
		s.libService.EnqueueArtistArtwork(artistID, eventID)
	}

	if key := artist.ResolveArtworkKey(preferLocal); key != "" {
		url := fmt.Sprintf("/artwork/%s", key)
		return &url, nil
	}
	return nil, nil
}

// SelectAndSetArtistArtwork prompts the user to pick an image file and sets it
// as the artist's custom (manual) artwork. Returns the new artwork URL, or ""
// if the dialog was cancelled.
func (s *LibraryService) SelectAndSetArtistArtwork(artistID string) (string, error) {
	app := application.Get()
	if app == nil {
		return "", fmt.Errorf("application not initialized")
	}

	result, err := app.Dialog.OpenFile().
		SetTitle("Select Artist Image").
		AddFilter("Images", "*.jpg;*.jpeg;*.png").
		PromptForSingleSelection()
	if err != nil {
		return "", err
	}
	if result == "" {
		return "", nil
	}

	return s.libService.SetArtistArtworkFromFile(context.Background(), artistID, result)
}

// RemoveArtistArtwork clears the artist's custom artwork.
func (s *LibraryService) RemoveArtistArtwork(artistID string) error {
	return s.libService.RemoveArtistArtwork(context.Background(), artistID)
}
