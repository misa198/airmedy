package wails

import (
	"context"
	"changeme/internal/app/library"
	"changeme/internal/domain"
	"github.com/wailsapp/wails/v3/pkg/application"
)

type LibraryService struct {
	libService *library.LibraryService
	folderRepo domain.WatchedFolderRepository
}

func NewLibraryService(libService *library.LibraryService, folderRepo domain.WatchedFolderRepository) *LibraryService {
	return &LibraryService{
		libService: libService,
		folderRepo: folderRepo,
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
	return s.folderRepo.Delete(context.Background(), id)
}
