package wails

import (
	"context"
	"changeme/internal/app/library"
	"changeme/internal/domain"
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

func (s *LibraryService) GetAllAlbums() ([]*domain.AlbumDTO, error) {
	return s.albumRepo.GetAll(context.Background())
}

func (s *LibraryService) GetAllArtists() ([]*domain.Artist, error) {
	return s.artistRepo.GetAll(context.Background())
}

func (s *LibraryService) GetAllGenres() ([]*domain.Genre, error) {
	return s.genreRepo.GetAll(context.Background())
}

func (s *LibraryService) GetAllComposers() ([]*domain.Composer, error) {
	return s.composerRepo.GetAll(context.Background())
}

func (s *LibraryService) GetSyncStatus() (*domain.SyncProgress, error) {
	// This is a dummy method to ensure SyncProgress model is generated for the frontend.
	return nil, nil
}
