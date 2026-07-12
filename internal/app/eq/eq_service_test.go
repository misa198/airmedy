package eq

import (
	"context"
	"testing"

	"airmedy/internal/domain"
)

type fakeEQRepository struct {
	profiles map[string]*domain.EQProfile
}

type fakeSettingsRepository struct {
	settings *domain.AppSettings
}

func (r *fakeSettingsRepository) Load(context.Context) (*domain.AppSettings, error) {
	return r.settings, nil
}

func (r *fakeSettingsRepository) Save(_ context.Context, settings *domain.AppSettings) error {
	r.settings = settings
	return nil
}

type fakePreampController struct {
	last float64
}

func (c *fakePreampController) SetEQPreamp(db float64) error {
	c.last = db
	return nil
}

type fakeStereoWidthController struct {
	last float64
}

func (c *fakeStereoWidthController) SetStereoWidth(width float64) error {
	c.last = width
	return nil
}

func (r *fakeEQRepository) GetActive(context.Context) (*domain.EQProfile, error) { return nil, nil }

func (r *fakeEQRepository) GetAll(context.Context) ([]*domain.EQProfile, error) {
	profiles := make([]*domain.EQProfile, 0, len(r.profiles))
	for _, profile := range r.profiles {
		profiles = append(profiles, profile)
	}
	return profiles, nil
}

func (r *fakeEQRepository) GetByID(_ context.Context, id string) (*domain.EQProfile, error) {
	return r.profiles[id], nil
}

func (r *fakeEQRepository) Save(_ context.Context, profile *domain.EQProfile) error {
	r.profiles[profile.ID] = profile
	return nil
}

func (r *fakeEQRepository) Delete(_ context.Context, id string) error {
	delete(r.profiles, id)
	return nil
}

func (r *fakeEQRepository) SetActive(context.Context, string) error { return nil }

func TestDefaultPresetsUseSupportedGainSteps(t *testing.T) {
	keys := make(map[string]bool, len(defaultPresets))
	for _, preset := range defaultPresets {
		if preset.key == "" || keys[preset.key] {
			t.Errorf("invalid or duplicate preset key %q", preset.key)
		}
		keys[preset.key] = true
		bands := makeBands(preset.gain)
		if len(bands) != 10 {
			t.Fatalf("%s: got %d bands, want 10", preset.name, len(bands))
		}
		for _, band := range bands {
			if band.Gain < -12 || band.Gain > 12 {
				t.Errorf("%s band %d: gain %v outside [-12, 12]", preset.name, band.Index, band.Gain)
			}
			if band.Gain*2 != float64(int(band.Gain*2)) {
				t.Errorf("%s band %d: gain %v is not a 0.5 dB increment", preset.name, band.Index, band.Gain)
			}
		}
	}
}

func TestNormalizeGainClampsAndRoundsToHalfDB(t *testing.T) {
	tests := []struct {
		input, want float64
	}{
		{-13, -12},
		{-7.2, -7},
		{5.6, 5.5},
		{12.8, 12},
	}

	for _, tt := range tests {
		if got := normalizeGain(tt.input); got != tt.want {
			t.Errorf("normalizeGain(%v) = %v, want %v", tt.input, got, tt.want)
		}
	}
}

func TestSortProfilesUsesCanonicalOrderThenUserProfileName(t *testing.T) {
	profiles := []*domain.EQProfile{
		{Key: "sony_mellow", Name: "Sony Mellow"},
		{Key: "rock", Name: "Rock"},
		{Name: "Zebra"},
		{Key: "flat", Name: "Flat"},
		{Name: "Ambient"},
	}

	sorted := sortProfiles(profiles)
	got := make([]string, len(sorted))
	for index, profile := range sorted {
		got[index] = profile.Name
	}
	want := []string{"Flat", "Rock", "Sony Mellow", "Ambient", "Zebra"}
	for index := range want {
		if got[index] != want[index] {
			t.Fatalf("position %d = %q, want %q", index, got[index], want[index])
		}
	}
}

func TestSeedDefaultsFallsBackToFlatWhenRetiredPresetWasActive(t *testing.T) {
	repo := &fakeEQRepository{profiles: map[string]*domain.EQProfile{
		"electronic": {ID: "electronic", Name: "Electronic", IsActive: true, IsDefault: true},
		"flat":       {ID: "flat", Name: "Flat", IsDefault: true},
	}}
	service := &EQService{repo: repo}

	if err := service.SeedDefaults(context.Background()); err != nil {
		t.Fatal(err)
	}
	if _, exists := repo.profiles["electronic"]; exists {
		t.Fatal("retired Electronic preset was not deleted")
	}
	flat := repo.profiles["flat"]
	if flat == nil || !flat.IsActive {
		t.Fatalf("Flat should be active after retired preset removal, got %+v", flat)
	}
}

func TestSetEnabledDisablesAndRestoresPreampAndStereoWidth(t *testing.T) {
	settings := &fakeSettingsRepository{settings: &domain.AppSettings{
		EQEnabled:   true,
		EQPreamp:    4.5,
		StereoWidth: 145,
	}}
	preamp := &fakePreampController{}
	width := &fakeStereoWidthController{}
	service := &EQService{settings: settings, preamp: preamp, width: width}

	if err := service.SetEnabled(context.Background(), false); err != nil {
		t.Fatal(err)
	}
	if preamp.last != 0 {
		t.Errorf("disabled preamp = %v, want 0", preamp.last)
	}
	if width.last != 100 {
		t.Errorf("disabled stereo width = %v, want 100", width.last)
	}
	if settings.settings.EQPreamp != 4.5 || settings.settings.StereoWidth != 145 {
		t.Errorf("saved controls = preamp %v, width %v; want 4.5, 145", settings.settings.EQPreamp, settings.settings.StereoWidth)
	}

	if err := service.SetEnabled(context.Background(), true); err != nil {
		t.Fatal(err)
	}
	if preamp.last != 4.5 {
		t.Errorf("enabled preamp = %v, want 4.5", preamp.last)
	}
	if width.last != 145 {
		t.Errorf("enabled stereo width = %v, want 145", width.last)
	}
}

func TestGlobalControlsStayNeutralWhileEQIsDisabled(t *testing.T) {
	settings := &fakeSettingsRepository{settings: &domain.AppSettings{EQEnabled: false}}
	preamp := &fakePreampController{}
	width := &fakeStereoWidthController{}
	service := &EQService{settings: settings, preamp: preamp, width: width}

	if err := service.SetPreamp(context.Background(), -6); err != nil {
		t.Fatal(err)
	}
	if err := service.SetStereoWidth(context.Background(), 40); err != nil {
		t.Fatal(err)
	}
	if preamp.last != 0 {
		t.Errorf("preamp while disabled = %v, want 0", preamp.last)
	}
	if width.last != 100 {
		t.Errorf("stereo width while disabled = %v, want 100", width.last)
	}
	if settings.settings.EQPreamp != -6 || settings.settings.StereoWidth != 40 {
		t.Errorf("saved controls = preamp %v, width %v; want -6, 40", settings.settings.EQPreamp, settings.settings.StereoWidth)
	}
}
