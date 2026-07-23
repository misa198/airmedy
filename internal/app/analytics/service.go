package analytics

import (
	"context"
	"time"

	"airmedy/internal/domain"
)

type Service struct{ repo domain.ListeningRepository }

func NewService(repo domain.ListeningRepository) *Service { return &Service{repo: repo} }

func (s *Service) GetInsights(ctx context.Context, period domain.ListeningRange) (*domain.AnalyticsInsights, error) {
	return s.repo.GetInsights(ctx, period, time.Now())
}
