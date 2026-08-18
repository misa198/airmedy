package analytics

import (
	"context"
	"time"

	"airmedy/internal/domain"
)

type Service struct{ repo domain.ListeningRepository }

func NewService(repo domain.ListeningRepository) *Service { return &Service{repo: repo} }

func (s *Service) GetLibraryInsights(ctx context.Context, period domain.ListeningRange) (*domain.LibraryInsights, error) {
	return s.repo.GetLibraryInsights(ctx, period, time.Now())
}

func (s *Service) GetListeningInsights(ctx context.Context, period domain.ListeningRange, sourceDeviceID string) (*domain.ListeningInsights, error) {
	return s.repo.GetListeningInsights(ctx, period, sourceDeviceID, time.Now())
}
