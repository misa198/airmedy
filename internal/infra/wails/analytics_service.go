package wails

import (
	"context"

	"airmedy/internal/app/analytics"
	"airmedy/internal/domain"
)

type AnalyticsService struct{ service *analytics.Service }

func NewAnalyticsService(service *analytics.Service) *AnalyticsService {
	return &AnalyticsService{service: service}
}

func (s *AnalyticsService) GetInsights(ctx context.Context, period string) (*domain.AnalyticsInsights, error) {
	return s.service.GetInsights(ctx, domain.ListeningRange(period))
}
