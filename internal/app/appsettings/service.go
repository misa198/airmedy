package appsettings

import (
	"context"
	"fmt"
	"log/slog"
	"os"

	"airmedy/internal/app/config"
	"airmedy/internal/domain"

	"github.com/emersion/go-autostart"
	"github.com/pkg/browser"
)

type SettingsService struct {
	repo   domain.SettingsRepository
	cfg    *config.Config
	logger *slog.Logger
}

func NewSettingsService(repo domain.SettingsRepository, cfg *config.Config, logger *slog.Logger) *SettingsService {
	return &SettingsService{
		repo:   repo,
		cfg:    cfg,
		logger: logger,
	}
}

func (s *SettingsService) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	settings, err := s.repo.Load(ctx)
	if err != nil {
		s.logger.Error("failed to load app settings, using defaults", "error", err)
		return &domain.AppSettings{
			Language:        "en",
			Theme:           "system",
			StartAtLogin:    false,
			AutoCheckUpdate: true,
		}, nil
	}
	return settings, nil
}

func (s *SettingsService) SaveSettings(ctx context.Context, settings *domain.AppSettings) error {
	err := s.repo.Save(ctx, settings)
	if err != nil {
		s.logger.Error("failed to save app settings", "error", err)
		return fmt.Errorf("failed to save app settings: %w", err)
	}

	// Update autostart
	if err := s.updateAutostart(settings.StartAtLogin); err != nil {
		s.logger.Warn("failed to update autostart setting", "error", err)
	}

	return nil
}

func (s *SettingsService) OpenAppDataFolder(ctx context.Context) error {
	s.logger.Info("opening app data folder", "path", s.cfg.DataDir)
	return browser.OpenFile(s.cfg.DataDir)
}

type AppInfo struct {
	Name        string `json:"name"`
	Version     string `json:"version"`
	Description string `json:"description"`
	GitHubURL   string `json:"github_url"`
	LicenseURL  string `json:"license_url"`
}

func (s *SettingsService) GetAppInfo(ctx context.Context) *AppInfo {
	return &AppInfo{
		Name:        "Airmedy",
		Version:     domain.Version,
		Description: "A lightweight offline music player for macOS, Windows and Linux.",
		GitHubURL:   "https://github.com/misa198/airmedy",
		LicenseURL:  "https://github.com/misa198/airmedy/blob/master/LICENSE",
	}
}

func (s *SettingsService) updateAutostart(enabled bool) error {
	execPath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("failed to get executable path: %w", err)
	}

	app := &autostart.App{
		Name:        "airmedy",
		DisplayName: "Airmedy",
		Exec:        []string{execPath},
	}

	if enabled {
		if !app.IsEnabled() {
			if err := app.Enable(); err != nil {
				return fmt.Errorf("failed to enable autostart: %w", err)
			}
		}
	} else {
		if app.IsEnabled() {
			if err := app.Disable(); err != nil {
				return fmt.Errorf("failed to disable autostart: %w", err)
			}
		}
	}

	return nil
}
