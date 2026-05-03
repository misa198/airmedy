package wails

import (
	"context"
	"airmedy/internal/app/updater"
)

type UpdaterService struct {
	svc *updater.Service
}

func NewUpdaterService(svc *updater.Service) *UpdaterService {
	return &UpdaterService{svc: svc}
}

func (s *UpdaterService) CheckForUpdate(ctx context.Context) (*updater.UpdateInfo, error) {
	return s.svc.CheckForUpdate(ctx)
}

func (s *UpdaterService) DownloadAndApply(ctx context.Context) error {
	return s.svc.DownloadAndApply(ctx)
}

func (s *UpdaterService) GetCurrentVersion() string {
	return s.svc.GetCurrentVersion()
}
