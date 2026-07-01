package normalization

import (
	"context"
	"log/slog"
	"testing"

	"airmedy/internal/domain"
)

type fakeSettingsRepo struct {
	settings *domain.AppSettings
}

func (r *fakeSettingsRepo) Load(_ context.Context) (*domain.AppSettings, error) {
	s := *r.settings
	return &s, nil
}

func (r *fakeSettingsRepo) Save(_ context.Context, settings *domain.AppSettings) error {
	r.settings = settings
	return nil
}

type fakeAnalysisRepo struct {
	domain.AnalysisRepository
	features map[string]*domain.TrackFeatures
}

func (r *fakeAnalysisRepo) GetFeatures(_ context.Context, trackID string) (*domain.TrackFeatures, error) {
	return r.features[trackID], nil
}

type fakeTrackRepo struct {
	domain.TrackRepository
	byAlbum map[string][]*domain.TrackDTO
}

func (r *fakeTrackRepo) GetByAlbumID(_ context.Context, albumID string) ([]*domain.TrackDTO, error) {
	return r.byAlbum[albumID], nil
}

type fakeNormController struct {
	lastGain float64
}

func (c *fakeNormController) SetPreampGain(db float64) error {
	c.lastGain = db
	return nil
}

type fakePlayerWithNorm struct {
	domain.AudioPlayer
	*fakeNormController
}

func newTestService(settings *domain.AppSettings, features map[string]*domain.TrackFeatures,
	byAlbum map[string][]*domain.TrackDTO) (*NormalizationService, *fakeNormController) {
	ctrl := &fakeNormController{}
	player := &fakePlayerWithNorm{fakeNormController: ctrl}
	s := NewNormalizationService(
		&fakeSettingsRepo{settings: settings},
		&fakeAnalysisRepo{features: features},
		&fakeTrackRepo{byAlbum: byAlbum},
		player,
		slog.Default(),
	)
	return s, ctrl
}

func baseSettings() *domain.AppSettings {
	return &domain.AppSettings{
		LibraryAnalysisEnabled:   true,
		NormalizationEnabled:     true,
		NormalizationMode:        "track",
		NormalizationTargetLUFS:  -14.0,
		NormalizationPreventClip: true,
	}
}

func TestSetEnabled_RejectsWhenLibraryAnalysisDisabled(t *testing.T) {
	settings := &domain.AppSettings{LibraryAnalysisEnabled: false}
	s, _ := newTestService(settings, nil, nil)

	if err := s.SetEnabled(context.Background(), true); err == nil {
		t.Fatal("expected error enabling normalization while library analysis is disabled")
	}
	if settings.NormalizationEnabled {
		t.Fatal("NormalizationEnabled must not have been persisted")
	}
}

func TestSetEnabled_AllowedWhenLibraryAnalysisEnabled(t *testing.T) {
	settings := &domain.AppSettings{LibraryAnalysisEnabled: true}
	s, _ := newTestService(settings, nil, nil)

	if err := s.SetEnabled(context.Background(), true); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestSetEnabled_DisablingAlwaysAllowed(t *testing.T) {
	settings := &domain.AppSettings{LibraryAnalysisEnabled: false, NormalizationEnabled: true}
	s, _ := newTestService(settings, nil, nil)

	if err := s.SetEnabled(context.Background(), false); err != nil {
		t.Fatalf("unexpected error disabling normalization: %v", err)
	}
}

func TestSetEnabled_DefaultsModeToTrackWhenFirstEnabled(t *testing.T) {
	settings := &domain.AppSettings{LibraryAnalysisEnabled: true, NormalizationMode: "off"}
	s, _ := newTestService(settings, nil, nil)

	if err := s.SetEnabled(context.Background(), true); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	got, err := s.GetSettings(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got.NormalizationMode != "track" {
		t.Fatalf("expected mode defaulted to track, got %q", got.NormalizationMode)
	}
}

func TestSetEnabled_LeavesExplicitModeUntouched(t *testing.T) {
	settings := &domain.AppSettings{LibraryAnalysisEnabled: true, NormalizationMode: "album"}
	s, _ := newTestService(settings, nil, nil)

	if err := s.SetEnabled(context.Background(), true); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	got, err := s.GetSettings(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got.NormalizationMode != "album" {
		t.Fatalf("expected mode to stay album, got %q", got.NormalizationMode)
	}
}

func TestComputeGain_TrackMode(t *testing.T) {
	features := map[string]*domain.TrackFeatures{
		"t1": {TrackID: "t1", LoudnessLUFS: -18.0, TruePeak: -1.0},
	}
	s, _ := newTestService(baseSettings(), features, nil)

	gain, hasFeatures, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1"}}, nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !hasFeatures {
		t.Fatal("expected hasFeatures=true")
	}
	// Raw gain = -14 - (-18) = 4, but PreventClip clamps to -TruePeak = 1.
	if want := 1.0; gain != want {
		t.Fatalf("gain = %v, want %v", gain, want)
	}
}

func TestComputeGain_AlbumMode_AveragesSiblings_WhenNextContinuesAlbum(t *testing.T) {
	settings := baseSettings()
	settings.NormalizationMode = "album"
	settings.NormalizationPreventClip = false

	features := map[string]*domain.TrackFeatures{
		"t1": {TrackID: "t1", LoudnessLUFS: -10.0},
		"t2": {TrackID: "t2", LoudnessLUFS: -20.0},
	}
	byAlbum := map[string][]*domain.TrackDTO{
		"alb1": {{Track: domain.Track{ID: "t1", AlbumID: "alb1"}}, {Track: domain.Track{ID: "t2", AlbumID: "alb1"}}},
	}
	s, _ := newTestService(settings, features, byAlbum)

	next := &domain.TrackDTO{Track: domain.Track{ID: "t2", AlbumID: "alb1"}}
	gain, hasFeatures, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1", AlbumID: "alb1"}}, next)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !hasFeatures {
		t.Fatal("expected hasFeatures=true")
	}
	// mean LUFS = (-10 + -20) / 2 = -15; gain = -14 - (-15) = 1
	if want := 1.0; gain != want {
		t.Fatalf("gain = %v, want %v", gain, want)
	}
}

func TestComputeGain_AlbumMode_FallsBackToSelfWhenNoSiblingAnalyzed(t *testing.T) {
	settings := baseSettings()
	settings.NormalizationMode = "album"
	settings.NormalizationPreventClip = false

	features := map[string]*domain.TrackFeatures{
		"t1": {TrackID: "t1", LoudnessLUFS: -18.0},
	}
	byAlbum := map[string][]*domain.TrackDTO{
		"alb1": {{Track: domain.Track{ID: "t1", AlbumID: "alb1"}}, {Track: domain.Track{ID: "t2", AlbumID: "alb1"}}}, // t2 unanalyzed
	}
	s, _ := newTestService(settings, features, byAlbum)

	next := &domain.TrackDTO{Track: domain.Track{ID: "t2", AlbumID: "alb1"}}
	gain, hasFeatures, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1", AlbumID: "alb1"}}, next)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !hasFeatures {
		t.Fatal("expected hasFeatures=true")
	}
	if want := -14.0 - (-18.0); gain != want {
		t.Fatalf("gain = %v, want %v", gain, want)
	}
}

func TestComputeGain_AlbumMode_FallsBackToTrackAtEndOfQueue(t *testing.T) {
	settings := baseSettings()
	settings.NormalizationMode = "album"
	settings.NormalizationPreventClip = false

	features := map[string]*domain.TrackFeatures{
		"t1": {TrackID: "t1", LoudnessLUFS: -10.0},
		"t2": {TrackID: "t2", LoudnessLUFS: -20.0},
	}
	byAlbum := map[string][]*domain.TrackDTO{
		"alb1": {{Track: domain.Track{ID: "t1", AlbumID: "alb1"}}, {Track: domain.Track{ID: "t2", AlbumID: "alb1"}}},
	}
	s, _ := newTestService(settings, features, byAlbum)

	// next == nil: last track in the queue -> must not average with the album,
	// even though this track has an album -> falls back to track LUFS.
	gain, _, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1", AlbumID: "alb1"}}, nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if want := -14.0 - (-10.0); gain != want {
		t.Fatalf("gain = %v, want %v (track LUFS, not album average)", gain, want)
	}
}

func TestComputeGain_AlbumMode_FallsBackToTrackWhenNextIsDifferentAlbum(t *testing.T) {
	settings := baseSettings()
	settings.NormalizationMode = "album"
	settings.NormalizationPreventClip = false

	features := map[string]*domain.TrackFeatures{
		"t1": {TrackID: "t1", LoudnessLUFS: -10.0},
		"t2": {TrackID: "t2", LoudnessLUFS: -20.0},
	}
	byAlbum := map[string][]*domain.TrackDTO{
		"alb1": {{Track: domain.Track{ID: "t1", AlbumID: "alb1"}}, {Track: domain.Track{ID: "t2", AlbumID: "alb1"}}},
	}
	s, _ := newTestService(settings, features, byAlbum)

	next := &domain.TrackDTO{Track: domain.Track{ID: "other", AlbumID: "alb2"}}
	gain, _, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1", AlbumID: "alb1"}}, next)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if want := -14.0 - (-10.0); gain != want {
		t.Fatalf("gain = %v, want %v (seam into a different album -> track LUFS)", gain, want)
	}
}

func TestComputeGain_AlbumMode_UntaggedTracksNeverGroupedByEmptyAlbumID(t *testing.T) {
	settings := baseSettings()
	settings.NormalizationMode = "album"
	settings.NormalizationPreventClip = false

	features := map[string]*domain.TrackFeatures{
		"t1": {TrackID: "t1", LoudnessLUFS: -10.0},
		"t2": {TrackID: "t2", LoudnessLUFS: -30.0},
	}
	s, _ := newTestService(settings, features, nil)

	// Both tracks have AlbumID == "" (no metadata). Must not be treated as
	// "same album" via the shared empty string -> track LUFS for both.
	next := &domain.TrackDTO{Track: domain.Track{ID: "t2", AlbumID: ""}}
	gain, _, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1", AlbumID: ""}}, next)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if want := -14.0 - (-10.0); gain != want {
		t.Fatalf("gain = %v, want %v (untagged tracks must use track LUFS, not grouped)", gain, want)
	}
}

func TestComputeGain_ClipClamp(t *testing.T) {
	settings := baseSettings()
	features := map[string]*domain.TrackFeatures{
		// Without clamp: gain = -14 - (-40) = 26 dB, way above clipping.
		"t1": {TrackID: "t1", LoudnessLUFS: -40.0, TruePeak: -2.0},
	}
	s, _ := newTestService(settings, features, nil)

	gain, _, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1"}}, nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if want := 2.0; gain != want { // gain + truePeak <= 0 -> gain <= -truePeak = 2.0
		t.Fatalf("gain = %v, want %v (clamped)", gain, want)
	}
}

func TestComputeGain_DisabledOrOffMode(t *testing.T) {
	features := map[string]*domain.TrackFeatures{
		"t1": {TrackID: "t1", LoudnessLUFS: -25.0},
	}

	disabled := baseSettings()
	disabled.NormalizationEnabled = false
	s, _ := newTestService(disabled, features, nil)
	gain, hasFeatures, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1"}}, nil)
	if err != nil || gain != 0 || !hasFeatures {
		t.Fatalf("disabled: gain=%v hasFeatures=%v err=%v, want 0/true/nil", gain, hasFeatures, err)
	}

	off := baseSettings()
	off.NormalizationMode = "off"
	s, _ = newTestService(off, features, nil)
	gain, hasFeatures, err = s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1"}}, nil)
	if err != nil || gain != 0 || !hasFeatures {
		t.Fatalf("off mode: gain=%v hasFeatures=%v err=%v, want 0/true/nil", gain, hasFeatures, err)
	}

	// Regression: NormalizationEnabled=true but LibraryAnalysisEnabled=false
	// (e.g. a pre-existing DB row from before this gate existed) must still
	// yield gain 0 — ComputeGain is the single enforcement point, not just
	// the SetEnabled write path.
	analysisOff := baseSettings()
	analysisOff.LibraryAnalysisEnabled = false
	s, _ = newTestService(analysisOff, features, nil)
	gain, hasFeatures, err = s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1"}}, nil)
	if err != nil || gain != 0 || !hasFeatures {
		t.Fatalf("library analysis disabled: gain=%v hasFeatures=%v err=%v, want 0/true/nil", gain, hasFeatures, err)
	}
}

func TestComputeGain_MissingFeatures(t *testing.T) {
	s, _ := newTestService(baseSettings(), nil, nil)

	gain, hasFeatures, err := s.ComputeGain(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "unanalyzed"}}, nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if hasFeatures {
		t.Fatal("expected hasFeatures=false")
	}
	if gain != 0 {
		t.Fatalf("gain = %v, want 0", gain)
	}
}

func TestApplyToPlayer_PushesGainToController(t *testing.T) {
	features := map[string]*domain.TrackFeatures{
		"t1": {TrackID: "t1", LoudnessLUFS: -18.0, TruePeak: -1.0},
	}
	s, ctrl := newTestService(baseSettings(), features, nil)

	s.ApplyToPlayer(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t1"}}, nil)

	// Raw gain = -14 - (-18) = 4, but PreventClip clamps to -TruePeak = 1.
	if want := 1.0; ctrl.lastGain != want {
		t.Fatalf("lastGain = %v, want %v", ctrl.lastGain, want)
	}
}
