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

func (s *AnalyticsService) GetLibraryInsights(ctx context.Context, period string) (*domain.LibraryInsights, error) {
	return s.service.GetLibraryInsights(ctx, domain.ListeningRange(period))
}

func (s *AnalyticsService) GetListeningInsights(ctx context.Context, period, sourceDeviceID string) (*domain.ListeningInsights, error) {
	return s.service.GetListeningInsights(ctx, domain.ListeningRange(period), sourceDeviceID)
}
