package wails

import (
	"context"

	"airmedy/internal/app/normalization"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"
)

type NormalizationService struct {
	service *normalization.NormalizationService
	player  *player.PlayerService
}

func NewNormalizationService(service *normalization.NormalizationService, player *player.PlayerService) *NormalizationService {
	return &NormalizationService{service: service, player: player}
}

func (s *NormalizationService) GetSettings() (*domain.AppSettings, error) {
	return s.service.GetSettings(context.Background())
}

func (s *NormalizationService) SetEnabled(enabled bool) error {
	if err := s.service.SetEnabled(context.Background(), enabled); err != nil {
		return err
	}
	s.player.ReapplyNormalization()
	return nil
}

func (s *NormalizationService) SetMode(mode string) error {
	if err := s.service.SetMode(context.Background(), mode); err != nil {
		return err
	}
	s.player.ReapplyNormalization()
	return nil
}

func (s *NormalizationService) SetTarget(targetLUFS float64) error {
	if err := s.service.SetTarget(context.Background(), targetLUFS); err != nil {
		return err
	}
	s.player.ReapplyNormalization()
	return nil
}

func (s *NormalizationService) SetPreventClip(enabled bool) error {
	if err := s.service.SetPreventClip(context.Background(), enabled); err != nil {
		return err
	}
	s.player.ReapplyNormalization()
	return nil
}
