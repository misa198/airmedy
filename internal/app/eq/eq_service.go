package eq

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"sort"

	"airmedy/internal/domain"

	"github.com/google/uuid"
)

// Standard 10-band EQ frequencies (ISO standard)
var eqFrequencies = []float64{32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000}

// defaultPresets defines the bundled EQ presets.
// Gains are in dB; bandwidth Q = 1.0 for all bands.
var defaultPresets = []struct {
	key  string
	name string
	gain []float64
}{
	{"flat", "Flat", []float64{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}}, {"classical", "Classical", []float64{0, 0, 0, 0, 0, 0, -7, -7, -7, -9.5}}, {"club", "Club", []float64{0, 0, 8, 5.5, 5.5, 5.5, 3, 0, 0, 0}}, {"dance", "Dance", []float64{9.5, 7, 2.5, 0, 0, -5.5, -7, -7, 0, 0}},
	{"full_bass", "Full Bass", []float64{-8, 9.5, 9.5, 5.5, 1.5, -4, -8, -10.5, -11, -11}}, {"full_bass_treble", "Full Bass & Treble", []float64{7, 5.5, 0, -7, -5, 1.5, 8, 11, 12, 12}}, {"full_treble", "Full Treble", []float64{-9.5, -9.5, -9.5, -4, 2.5, 11, 12, 12, 12, 12}}, {"headphones", "Headphones", []float64{5, 11, 5.5, -3, -2.5, 1.5, 5, 9.5, 12, 12}},
	{"large_hall", "Large Hall", []float64{10.5, 10.5, 5.5, 5.5, 0, -5, -5, -5, 0, 0}}, {"live", "Live", []float64{-5, 0, 4, 5.5, 5.5, 5.5, 4, 2.5, 2.5, 2.5}}, {"party", "Party", []float64{7, 7, 0, 0, 0, 0, 0, 0, 7, 7}}, {"pop", "Pop", []float64{-1.5, 5, 7, 8, 5.5, 0, -2.5, -2.5, -1.5, -1.5}},
	{"reggae", "Reggae", []float64{0, 0, 0, -5.5, 0, 6.5, 6.5, 0, 0, 0}}, {"rock", "Rock", []float64{8, 5, -5.5, -8, -3, 4, 9, 11, 11, 11}}, {"ska", "Ska", []float64{-2.5, -5, -4, 0, 4, 5.5, 9, 9.5, 11, 9.5}}, {"soft", "Soft", []float64{5, 1.5, 0, -2.5, 0, 4, 8, 9.5, 11, 12}},
	{"soft_rock", "Soft Rock", []float64{4, 4, 2.5, 0, -4, -5.5, -3, 0, 2.5, 9}}, {"techno", "Techno", []float64{8, 5.5, 0, -5.5, -5, 0, 8, 9.5, 9.5, 9}}, {"harman_target", "Harman Target", []float64{3.5, 3, 1.5, 0, 0, 0.5, 1.5, 2.5, 3.5, 4}}, {"bass_booster", "Bass Booster", []float64{5.5, 4.5, 3, 1.5, 0, 0, 0, 0, 0, 0}},
	{"treble_booster", "Treble Booster", []float64{0, 0, 0, 0, 0, 0, 1.5, 3, 4.5, 5.5}}, {"acoustic_vocal", "Acoustic / Vocal", []float64{2.5, 1.5, 0.5, 0, 1, 2, 2.5, 2, 1.5, 1}}, {"sony_excited", "Sony Excited", []float64{4, 3, 1, 0, 0, 1, 2, 3, 4, 5}}, {"sony_mellow", "Sony Mellow", []float64{2, 1.5, 1, 0, -1, -1, 0, 1, 1.5, 2}},
	{"electronic_dance", "Electronic / Dance", []float64{4.5, 3.5, 1.5, -0.5, -1.5, 0, 1.5, 2.5, 3.5, 4}}, {"rnb_soul", "R&B / Soul", []float64{4.5, 3.5, 1.5, 0, 1, 1.5, 2, 1.5, 2.5, 3}}, {"vocal_booster", "Vocal Booster", []float64{-2, -1, 0, 1, 2, 3, 2.5, 1.5, 1, 0}}, {"loudness", "Loudness", []float64{5, 3.5, 1.5, 0, -1, 0, 1, 2, 3.5, 4.5}}, {"spoken_word_podcast", "Spoken Word / Podcast", []float64{-3, -2, -1, 1, 2.5, 3.5, 3, 2, 1, 0}},
	{"jazz", "Jazz", []float64{3, 2, 1, 2, -1, -1, 0, 1, 2, 3}}, {"hip_hop", "Hip-Hop", []float64{5, 4, 3, 1, -1, -1, 0, -1, 1, 2}},
}

// defaultPresetOrder is the canonical order returned to every UI consumer.
var defaultPresetOrder = []string{
	"flat", "classical", "club", "dance", "full_bass", "full_treble", "full_bass_treble",
	"headphones", "large_hall", "live", "party", "pop", "reggae", "rock", "jazz", "hip_hop", "ska", "soft",
	"soft_rock", "techno", "bass_booster", "treble_booster", "acoustic_vocal", "electronic_dance",
	"rnb_soul", "vocal_booster", "loudness", "spoken_word_podcast", "harman_target", "sony_excited",
	"sony_mellow",
}

type EQService struct {
	repo     domain.EQRepository
	settings domain.SettingsRepository
	player   domain.EQController          // nil if the audio player doesn't support EQ
	preamp   domain.EQPreampController    // nil if the audio player doesn't support a user EQ preamp
	width    domain.StereoWidthController // nil if the audio player doesn't support stereo width
	logger   *slog.Logger
}

func NewEQService(repo domain.EQRepository, settings domain.SettingsRepository, player domain.AudioPlayer, logger *slog.Logger) *EQService {
	var ctrl domain.EQController
	if c, ok := player.(domain.EQController); ok {
		ctrl = c
	}
	var preamp domain.EQPreampController
	if c, ok := player.(domain.EQPreampController); ok {
		preamp = c
	}
	var width domain.StereoWidthController
	if c, ok := player.(domain.StereoWidthController); ok {
		width = c
	}
	s := &EQService{repo: repo, settings: settings, player: ctrl, preamp: preamp, width: width, logger: logger}
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

	settings, _ := s.settings.Load(ctx)
	enabled := true
	if settings != nil {
		enabled = settings.EQEnabled
	}
	if s.player != nil {
		for _, band := range p.Bands {
			_ = s.player.SetEQBand(band.Index, band.Frequency, band.Gain, band.Bandwidth)
		}
		_ = s.player.SetEQEnabled(enabled)
	}
	return s.applyGlobalControls(settings)
}

func (s *EQService) SeedDefaults(ctx context.Context) error {
	all, err := s.repo.GetAll(ctx)
	if err != nil {
		return err
	}

	// Map existing defaults by stable key, falling back to their unchanged legacy name.
	// Built-ins no longer in the catalog (such as the retired Electronic preset) are removed.
	existingDefaults := make(map[string]*domain.EQProfile)
	presetKeys := make(map[string]bool, len(defaultPresets))
	presetKeysByName := make(map[string]string, len(defaultPresets))
	for _, preset := range defaultPresets {
		presetKeys[preset.key] = true
		presetKeysByName[preset.name] = preset.key
	}
	for _, p := range all {
		if !p.IsDefault {
			continue
		}
		key := p.Key
		if key == "" {
			key = presetKeysByName[p.Name]
		}
		if !presetKeys[key] {
			_ = s.repo.Delete(ctx, p.ID)
			continue
		}
		existingDefaults[key] = p
	}

	// Determine if there is already an active profile
	hasActive := false
	for _, p := range all {
		if !p.IsActive {
			continue
		}
		if !p.IsDefault {
			hasActive = true
			break
		}
		key := p.Key
		if key == "" {
			key = presetKeysByName[p.Name]
		}
		if presetKeys[key] {
			hasActive = true
			break
		}
	}

	// Seed or update the new list of presets
	for i, preset := range defaultPresets {
		if p, exists := existingDefaults[preset.key]; exists {
			// Presets contain only the ten band gains.
			p.Key = preset.key
			p.Name = preset.name
			if !hasActive && i == 0 {
				p.IsActive = true
			}
			p.Bands = makeBands(preset.gain)
			if err := s.repo.Save(ctx, p); err != nil {
				return fmt.Errorf("failed to update preset %s: %w", preset.name, err)
			}
		} else {
			// Create a new preset
			p := &domain.EQProfile{
				ID:        uuid.New().String(),
				Key:       preset.key,
				Name:      preset.name,
				IsActive:  !hasActive && i == 0, // Set active if nothing is active and this is Flat
				IsDefault: true,
				Bands:     makeBands(preset.gain),
			}
			if err := s.repo.Save(ctx, p); err != nil {
				return fmt.Errorf("failed to seed preset %s: %w", preset.name, err)
			}
		}
	}
	return nil
}

func (s *EQService) GetActiveProfile(ctx context.Context) (*domain.EQProfile, error) {
	return s.repo.GetActive(ctx)
}

func (s *EQService) GetAllProfiles(ctx context.Context) ([]*domain.EQProfile, error) {
	profiles, err := s.repo.GetAll(ctx)
	if err != nil {
		return nil, err
	}
	return sortProfiles(profiles), nil
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
	var settings *domain.AppSettings
	if loadedSettings, err := s.settings.Load(ctx); err == nil {
		settings = loadedSettings
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
	return s.applyGlobalControls(settings)
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
	p.Bands[bandIndex].Gain = normalizeGain(gain)
	if err := s.repo.Save(ctx, p); err != nil {
		return err
	}
	// Apply live if this is the active profile and the player supports EQ.
	if p.IsActive && s.player != nil {
		_ = s.player.SetEQBand(bandIndex, p.Bands[bandIndex].Frequency, p.Bands[bandIndex].Gain, p.Bands[bandIndex].Bandwidth)
	}
	return nil
}

// SetPreamp persists the global EQ preamp gain (dB), clamped to [-12, 12].
func (s *EQService) SetPreamp(ctx context.Context, gainDB float64) error {
	if gainDB < -12 {
		gainDB = -12
	} else if gainDB > 12 {
		gainDB = 12
	}
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return err
	}
	settings.EQPreamp = gainDB
	if err := s.settings.Save(ctx, settings); err != nil {
		return err
	}
	if s.logger != nil {
		s.logger.Debug("updated eq preamp", "saved_db", gainDB, "effective_db", effectivePreamp(settings), "eq_enabled", settings.EQEnabled)
	}
	if s.preamp != nil {
		return s.preamp.SetEQPreamp(effectivePreamp(settings))
	}
	return nil
}

// GetStereoWidth returns the global stereo-width setting (100 = neutral).
func (s *EQService) GetStereoWidth(ctx context.Context) (float64, error) {
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return 100, nil
	}
	return settings.StereoWidth, nil
}

// SetStereoWidth persists and applies the global stereo-width setting, clamped to [0, 200].
func (s *EQService) SetStereoWidth(ctx context.Context, widthPercent float64) error {
	if widthPercent < 0 {
		widthPercent = 0
	} else if widthPercent > 200 {
		widthPercent = 200
	}
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return err
	}
	settings.StereoWidth = widthPercent
	if err := s.settings.Save(ctx, settings); err != nil {
		return err
	}
	if s.logger != nil {
		s.logger.Debug("updated eq stereo width", "saved_percent", widthPercent, "effective_percent", effectiveStereoWidth(settings), "eq_enabled", settings.EQEnabled)
	}
	if s.width != nil {
		return s.width.SetStereoWidth(effectiveStereoWidth(settings))
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
	if s.logger != nil {
		s.logger.Debug("updated eq enabled state", "enabled", enabled, "saved_preamp_db", settings.EQPreamp, "saved_stereo_width_percent", settings.StereoWidth)
	}

	if s.player != nil {
		if err := s.player.SetEQEnabled(enabled); err != nil {
			return err
		}
	}
	return s.applyGlobalControls(settings)
}

// applyGlobalControls applies the preamp and stereo-width values that are effective
// for the current EQ state. Their saved values are retained while EQ is off so they
// can be restored unchanged when it is enabled again.
func (s *EQService) applyGlobalControls(settings *domain.AppSettings) error {
	if settings == nil {
		return nil
	}
	preamp := effectivePreamp(settings)
	width := effectiveStereoWidth(settings)
	if s.logger != nil {
		s.logger.Debug("applied eq global controls", "eq_enabled", settings.EQEnabled, "preamp_db", preamp, "stereo_width_percent", width)
	}
	if s.preamp != nil {
		if err := s.preamp.SetEQPreamp(preamp); err != nil {
			return err
		}
	}
	if s.width != nil {
		if err := s.width.SetStereoWidth(width); err != nil {
			return err
		}
	}
	return nil
}

func effectivePreamp(settings *domain.AppSettings) float64 {
	if !settings.EQEnabled {
		return 0
	}
	return settings.EQPreamp
}

func effectiveStereoWidth(settings *domain.AppSettings) float64 {
	if !settings.EQEnabled {
		return 100
	}
	return settings.StereoWidth
}

func makeBands(gains []float64) []domain.EQBand {
	bands := make([]domain.EQBand, 10)
	for i, freq := range eqFrequencies {
		gain := 0.0
		if i < len(gains) {
			gain = normalizeGain(gains[i])
		}
		bands[i] = domain.EQBand{Index: i, Frequency: freq, Gain: gain, Bandwidth: 1.0}
	}
	return bands
}

func normalizeGain(gain float64) float64 {
	if gain < -12 {
		gain = -12
	} else if gain > 12 {
		gain = 12
	}
	return math.Round(gain*2) / 2
}

func sortProfiles(profiles []*domain.EQProfile) []*domain.EQProfile {
	order := make(map[string]int, len(defaultPresetOrder))
	for index, key := range defaultPresetOrder {
		order[key] = index
	}
	sort.SliceStable(profiles, func(i, j int) bool {
		left, leftDefault := order[profiles[i].Key]
		right, rightDefault := order[profiles[j].Key]
		if leftDefault && rightDefault {
			return left < right
		}
		if leftDefault != rightDefault {
			return leftDefault
		}
		return profiles[i].Name < profiles[j].Name
	})
	return profiles
}
