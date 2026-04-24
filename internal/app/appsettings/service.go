package appsettings

import (
	"context"
	"fmt"
	"log/slog"

	"airmedy/internal/domain"
)

type SettingsService struct {
	repo   domain.SettingsRepository
	logger *slog.Logger
}

func NewSettingsService(repo domain.SettingsRepository, logger *slog.Logger) *SettingsService {
	return &SettingsService{
		repo:   repo,
		logger: logger,
	}
}

func (s *SettingsService) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	settings, err := s.repo.Load(ctx)
	if err != nil {
		s.logger.Error("failed to load app settings", "error", err)
		return nil, fmt.Errorf("failed to load app settings: %w", err)
	}
	return settings, nil
}

func (s *SettingsService) SaveSettings(ctx context.Context, settings *domain.AppSettings) error {
	err := s.repo.Save(ctx, settings)
	if err != nil {
		s.logger.Error("failed to save app settings", "error", err)
		return fmt.Errorf("failed to save app settings: %w", err)
	}
	return nil
}
