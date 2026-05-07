package eq

import (
	"context"
	"fmt"
	"log/slog"

	"airmedy/internal/domain"

	"github.com/google/uuid"
)

// Standard 10-band EQ frequencies (ISO standard)
var eqFrequencies = []float64{32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000}

// defaultPresets defines the bundled EQ presets.
// Gains are in dB; bandwidth Q = 1.0 for all bands.
var defaultPresets = []struct {
	name string
	gain []float64
}{
	{"Flat", []float64{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}},
	{"Rock", []float64{4, 3, 2, 0, -1, 0, 2, 3, 4, 4}},
	{"Pop", []float64{-1, 2, 4, 4, 2, 0, -1, -1, -1, -1}},
	{"Jazz", []float64{3, 2, 1, 2, -1, -1, 0, 1, 2, 3}},
	{"Classical", []float64{4, 3, 2, 1, -1, -1, 0, 2, 3, 4}},
	{"Hip-Hop", []float64{5, 4, 3, 1, -1, -1, 0, -1, 1, 2}},
	{"Electronic", []float64{4, 3, 1, 0, -2, 0, 1, 2, 3, 4}},
}

type EQService struct {
	repo     domain.EQRepository
	settings domain.SettingsRepository
	player   domain.EQController // nil if the audio player doesn't support EQ
	logger   *slog.Logger
}

func NewEQService(repo domain.EQRepository, settings domain.SettingsRepository, player domain.AudioPlayer, logger *slog.Logger) *EQService {
	var ctrl domain.EQController
	if c, ok := player.(domain.EQController); ok {
		ctrl = c
	}
	s := &EQService{repo: repo, settings: settings, player: ctrl, logger: logger}
	return s
}

// SeedDefaults inserts the default presets if the profiles table is empty.
func (s *EQService) ApplyActiveProfile(ctx context.Context) error {
	p, err := s.GetActiveProfile(ctx)
	if err != nil {
		return err
	}
	if p == nil {
		return nil
	}

	enabled := true
	if settings, err := s.settings.Load(ctx); err == nil {
		enabled = settings.EQEnabled
	}

	if s.player != nil {
		for _, band := range p.Bands {
			_ = s.player.SetEQBand(band.Index, band.Frequency, band.Gain, band.Bandwidth)
		}
		_ = s.player.SetEQEnabled(enabled)
	}
	return nil
}

func (s *EQService) SeedDefaults(ctx context.Context) error {
	all, err := s.repo.GetAll(ctx)
	if err != nil {
		return err
	}
	if len(all) > 0 {
		return nil // already seeded
	}
	for i, preset := range defaultPresets {
		p := &domain.EQProfile{
			ID:        uuid.New().String(),
			Name:      preset.name,
			IsActive:  i == 0, // Flat is active by default
			IsDefault: true,
			Bands:     makeBands(preset.gain),
		}
		if err := s.repo.Save(ctx, p); err != nil {
			return fmt.Errorf("failed to seed preset %s: %w", preset.name, err)
		}
	}
	return nil
}

func (s *EQService) GetActiveProfile(ctx context.Context) (*domain.EQProfile, error) {
	return s.repo.GetActive(ctx)
}

func (s *EQService) GetAllProfiles(ctx context.Context) ([]*domain.EQProfile, error) {
	return s.repo.GetAll(ctx)
}

func (s *EQService) GetProfileByID(ctx context.Context, id string) (*domain.EQProfile, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *EQService) ApplyProfile(ctx context.Context, id string) error {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("eq profile not found: %s", id)
	}
	if err := s.repo.SetActive(ctx, id); err != nil {
		return err
	}

	// Also ensure EQ is enabled when a profile is applied
	if settings, err := s.settings.Load(ctx); err == nil {
		settings.EQEnabled = true
		_ = s.settings.Save(ctx, settings)
	}

	if s.player != nil {
		for _, band := range p.Bands {
			if err := s.player.SetEQBand(band.Index, band.Frequency, band.Gain, band.Bandwidth); err != nil {
				s.logger.Warn("failed to apply eq band", "index", band.Index, "error", err)
			}
		}
		_ = s.player.SetEQEnabled(true)
	}
	return nil
}

func (s *EQService) CreateProfile(ctx context.Context, name string) (*domain.EQProfile, error) {
	p := &domain.EQProfile{
		ID:    uuid.New().String(),
		Name:  name,
		Bands: makeBands(make([]float64, 10)), // flat
	}
	if err := s.repo.Save(ctx, p); err != nil {
		return nil, err
	}
	return p, nil
}

func (s *EQService) UpdateBand(ctx context.Context, profileID string, bandIndex int, gain float64) error {
	p, err := s.repo.GetByID(ctx, profileID)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("eq profile not found: %s", profileID)
	}
	if bandIndex < 0 || bandIndex >= len(p.Bands) {
		return fmt.Errorf("invalid band index: %d", bandIndex)
	}
	p.Bands[bandIndex].Gain = gain
	if err := s.repo.Save(ctx, p); err != nil {
		return err
	}
	// Apply live if this is the active profile and the player supports EQ.
	if p.IsActive && s.player != nil {
		_ = s.player.SetEQBand(bandIndex, p.Bands[bandIndex].Frequency, gain, p.Bands[bandIndex].Bandwidth)
	}
	return nil
}

func (s *EQService) RenameProfile(ctx context.Context, id, name string) error {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("eq profile not found: %s", id)
	}
	p.Name = name
	return s.repo.Save(ctx, p)
}

func (s *EQService) DeleteProfile(ctx context.Context, id string) error {
	return s.repo.Delete(ctx, id)
}

func (s *EQService) IsEnabled(ctx context.Context) (bool, error) {
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return true, nil
	}
	return settings.EQEnabled, nil
}

func (s *EQService) SetEnabled(ctx context.Context, enabled bool) error {
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return err
	}
	settings.EQEnabled = enabled
	if err := s.settings.Save(ctx, settings); err != nil {
		return err
	}

	if s.player != nil {
		return s.player.SetEQEnabled(enabled)
	}
	return nil
}

func makeBands(gains []float64) []domain.EQBand {
	bands := make([]domain.EQBand, 10)
	for i, freq := range eqFrequencies {
		gain := 0.0
		if i < len(gains) {
			gain = gains[i]
		}
		bands[i] = domain.EQBand{Index: i, Frequency: freq, Gain: gain, Bandwidth: 1.0}
	}
	return bands
}
