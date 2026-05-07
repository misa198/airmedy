package wails

import (
	"context"

	"airmedy/internal/app/eq"
	"airmedy/internal/domain"
)

type EQService struct {
	service *eq.EQService
}

func NewEQService(service *eq.EQService) *EQService {
	return &EQService{service: service}
}

func (s *EQService) GetActiveProfile() (*domain.EQProfile, error) {
	return s.service.GetActiveProfile(context.Background())
}

func (s *EQService) GetAllProfiles() ([]*domain.EQProfile, error) {
	return s.service.GetAllProfiles(context.Background())
}

func (s *EQService) ApplyProfile(id string) error {
	return s.service.ApplyProfile(context.Background(), id)
}

func (s *EQService) CreateProfile(name string) (*domain.EQProfile, error) {
	return s.service.CreateProfile(context.Background(), name)
}

func (s *EQService) UpdateBand(profileID string, bandIndex int, gain float64) error {
	return s.service.UpdateBand(context.Background(), profileID, bandIndex, gain)
}

func (s *EQService) RenameProfile(id, name string) error {
	return s.service.RenameProfile(context.Background(), id, name)
}

func (s *EQService) DeleteProfile(id string) error {
	return s.service.DeleteProfile(context.Background(), id)
}

func (s *EQService) SetEnabled(enabled bool) error {
	return s.service.SetEnabled(context.Background(), enabled)
}

func (s *EQService) IsEnabled() (bool, error) {
	return s.service.IsEnabled(context.Background())
}
