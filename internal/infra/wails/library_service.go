package wails

import (
	"context"
	"airmedy/internal/app/library"
	"airmedy/internal/domain"
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
}

func NewLibraryService(
	libService *library.LibraryService,
	folderRepo domain.WatchedFolderRepository,
	trackRepo domain.TrackRepository,
	albumRepo domain.AlbumRepository,
	artistRepo domain.ArtistRepository,
	genreRepo domain.GenreRepository,
	composerRepo domain.ComposerRepository,
) *LibraryService {
	return &LibraryService{
		libService:   libService,
		folderRepo:   folderRepo,
		trackRepo:    trackRepo,
		albumRepo:    albumRepo,
		artistRepo:   artistRepo,
		genreRepo:    genreRepo,
		composerRepo: composerRepo,
	}
}

func (s *LibraryService) SelectFolder() (string, error) {
	return application.Get().Dialog.OpenFile().
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
			if err := s.libService.SyncFolder(context.Background(), path); err != nil {
				// Log error? The service already logs it.
			}
		}(folder.Path)
	}
	return nil
}

func (s *LibraryService) GetAllTracks() ([]*domain.TrackDTO, error) {
	return s.trackRepo.GetAll(context.Background())
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
	return s.artistRepo.GetAll(context.Background())
}

func (s *LibraryService) GetArtistByID(id string) (*domain.Artist, error) {
	return s.artistRepo.GetByID(context.Background(), id)
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

func (s *LibraryService) DeleteTrack(trackID string) error {
	return s.libService.DeleteTrack(context.Background(), trackID)
}

func (s *LibraryService) UpdateTrackMetadata(trackID string, update domain.MetadataUpdate) error {
	return s.libService.UpdateMetadata(context.Background(), trackID, update)
}

func (s *LibraryService) GetAlbumColors(id string) (*domain.ThemeColors, error) {
	return s.libService.GetAlbumColors(context.Background(), id)
}
