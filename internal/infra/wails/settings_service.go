package wails

import (
	"context"

	"airmedy/internal/app/appsettings"
	"airmedy/internal/domain"
)

type SettingsService struct {
	svc *appsettings.SettingsService
}

func NewSettingsService(svc *appsettings.SettingsService) *SettingsService {
	return &SettingsService{svc: svc}
}

func (s *SettingsService) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	return s.svc.GetSettings(ctx)
}

func (s *SettingsService) SaveSettings(ctx context.Context, settings *domain.AppSettings) error {
	return s.svc.SaveSettings(ctx, settings)
}

func (s *SettingsService) OpenAppDataFolder(ctx context.Context) error {
	return s.svc.OpenAppDataFolder(ctx)
}

func (s *SettingsService) GetAppInfo(ctx context.Context) *appsettings.AppInfo {
	return s.svc.GetAppInfo(ctx)
}
