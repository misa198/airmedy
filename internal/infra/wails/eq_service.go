package wails

import (
	"context"

	"github.com/wailsapp/wails/v3/pkg/application"

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
	err := s.service.ApplyProfile(context.Background(), id)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("eq:active-profile-changed", id)
		}
	}
	return err
}

func (s *EQService) CreateProfile(name string) (*domain.EQProfile, error) {
	p, err := s.service.CreateProfile(context.Background(), name)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("eq:profiles-updated", nil)
		}
	}
	return p, err
}

func (s *EQService) UpdateBand(profileID string, bandIndex int, gain float64) error {
	return s.service.UpdateBand(context.Background(), profileID, bandIndex, gain)
}

func (s *EQService) SetPreamp(gainDB float64) error {
	return s.service.SetPreamp(context.Background(), gainDB)
}

func (s *EQService) GetStereoWidth() (float64, error) {
	return s.service.GetStereoWidth(context.Background())
}

func (s *EQService) SetStereoWidth(width float64) error {
	return s.service.SetStereoWidth(context.Background(), width)
}

func (s *EQService) RenameProfile(id, name string) error {
	err := s.service.RenameProfile(context.Background(), id, name)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("eq:profiles-updated", nil)
		}
	}
	return err
}

func (s *EQService) DeleteProfile(id string) error {
	err := s.service.DeleteProfile(context.Background(), id)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("eq:profiles-updated", nil)
		}
	}
	return err
}

func (s *EQService) SetEnabled(enabled bool) error {
	return s.service.SetEnabled(context.Background(), enabled)
}

func (s *EQService) IsEnabled() (bool, error) {
	return s.service.IsEnabled(context.Background())
}
