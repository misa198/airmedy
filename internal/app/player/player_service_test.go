package player

import (
	"context"
	"log/slog"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"airmedy/internal/app/normalization"
	"airmedy/internal/domain"
)

// fakePlayer is a test double for domain.AudioPlayer.
type fakePlayer struct {
	mu             sync.Mutex
	status         domain.PlayerStatus
	onEnd          func()
	lastPreampGain float64
}

func (p *fakePlayer) Play() error {
	p.mu.Lock()
	p.status.PlaybackState = domain.PlaybackStatePlaying
	p.mu.Unlock()
	return nil
}

func (p *fakePlayer) Pause() error {
	p.mu.Lock()
	p.status.PlaybackState = domain.PlaybackStatePaused
	p.mu.Unlock()
	return nil
}

func (p *fakePlayer) Stop() error {
	p.mu.Lock()
	p.status.PlaybackState = domain.PlaybackStateStopped
	p.mu.Unlock()
	return nil
}

func (p *fakePlayer) Seek(pos float64) error {
	p.mu.Lock()
	p.status.Position = pos
	p.mu.Unlock()
	return nil
}

func (p *fakePlayer) SetVolume(v float64) error {
	p.mu.Lock()
	p.status.Volume = v
	p.mu.Unlock()
	return nil
}

func (p *fakePlayer) SetMuted(m bool) error {
	p.mu.Lock()
	p.status.Muted = m
	p.mu.Unlock()
	return nil
}

func (p *fakePlayer) Load(track *domain.TrackDTO) error {
	p.mu.Lock()
	p.status.TrackID = track.ID
	p.status.Duration = float64(track.Duration)
	p.status.Position = 0
	p.status.PlaybackState = domain.PlaybackStateStopped
	p.mu.Unlock()
	return nil
}

func (p *fakePlayer) Unload() error {
	p.mu.Lock()
	p.status.TrackID = ""
	p.mu.Unlock()
	return nil
}

func (p *fakePlayer) GetStatus() domain.PlayerStatus {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.status
}

func (p *fakePlayer) OnTrackEnd(cb func()) {
	p.mu.Lock()
	p.onEnd = cb
	p.mu.Unlock()
}

// SetPreampGain satisfies domain.NormalizationController so loadAndPlay's
// normalization push can be exercised in tests.
func (p *fakePlayer) SetPreampGain(db float64) error {
	p.mu.Lock()
	p.lastPreampGain = db
	p.mu.Unlock()
	return nil
}

// fakeArtworkCache is a no-op artwork cache for tests.
type fakeArtworkCache struct{}

func (c *fakeArtworkCache) Save(_ context.Context, _ []byte, _ string) (string, error) {
	return "", nil
}

func (c *fakeArtworkCache) GetPath(key string) string                { return key }
func (c *fakeArtworkCache) GetVariantPath(key, _ string) string      { return key }
func (c *fakeArtworkCache) Exists(_ string) bool                     { return false }
func (c *fakeArtworkCache) CleanupOrphaned(_ context.Context, _ map[string]bool) error {
	return nil
}

type fakeTrackRepo struct {
	domain.TrackRepository
}

func (r *fakeTrackRepo) IncrementPlayCount(_ context.Context, _ string) error { return nil }

type fakePlayerStateRepo struct {
	domain.PlayerStateRepository
}

func (r *fakePlayerStateRepo) Save(_ context.Context, _ *domain.PlayerState) error { return nil }
func (r *fakePlayerStateRepo) Load(_ context.Context) (*domain.PlayerState, error) {
	return &domain.PlayerState{Volume: 1.0}, nil
}

// newTestService builds a PlayerService with fast tick interval for tests.
// emitCount is incremented by a goroutine — callers should wait briefly.
func newTestService(t *testing.T, player domain.AudioPlayer) (*PlayerService, *int64) {
	t.Helper()
	queue := NewQueueService()
	var emitCount int64

	s := &PlayerService{
		player:       player,
		queue:        queue,
		logger:       slog.Default(),
		artworkCache: &fakeArtworkCache{},
		trackRepo:    &fakeTrackRepo{},
		stateRepo:    &fakePlayerStateRepo{},
		tickInterval: 10 * time.Millisecond,
		playCounted:  make(map[string]bool),
		npReported:   make(map[string]bool),
		posConfirmed: make(map[string]bool),
	}
	s.player.OnTrackEnd(s.HandleTrackEnd)

	// Patch emitStatus to count calls without a real Wails app
	s.emitStatusHook = func() { atomic.AddInt64(&emitCount, 1) }

	return s, &emitCount
}

func TestPositionTicker_StartsOnPlay(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, _ := newTestService(t, fp)

	var tickCount int64
	s.nowPlaying = &fakeNowPlaying{
		updatePositionFn: func(_ float64) { atomic.AddInt64(&tickCount, 1) },
	}

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 120}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	if err := s.loadAndPlay(track); err != nil {
		t.Fatalf("loadAndPlay: %v", err)
	}

	time.Sleep(60 * time.Millisecond)
	n := atomic.LoadInt64(&tickCount)
	if n < 2 {
		t.Errorf("expected ≥2 ticker ticks, got %d", n)
	}
}

func TestPositionTicker_StopsOnPause(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, _ := newTestService(t, fp)

	var tickCount int64
	s.nowPlaying = &fakeNowPlaying{
		updatePositionFn: func(_ float64) { atomic.AddInt64(&tickCount, 1) },
	}

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 120}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	_ = s.loadAndPlay(track)
	time.Sleep(40 * time.Millisecond)

	_ = s.Pause()
	before := atomic.LoadInt64(&tickCount)

	time.Sleep(40 * time.Millisecond)
	after := atomic.LoadInt64(&tickCount)

	// After pause the ticker should have stopped — allow at most 1 extra tick if it was already in flight
	if after-before > 1 {
		t.Errorf("ticker kept firing after pause: before=%d after=%d", before, after)
	}
}

func TestPositionTicker_StopsOnStop(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, _ := newTestService(t, fp)

	var tickCount int64
	s.nowPlaying = &fakeNowPlaying{
		updatePositionFn: func(_ float64) { atomic.AddInt64(&tickCount, 1) },
	}

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 120}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	_ = s.loadAndPlay(track)
	time.Sleep(40 * time.Millisecond)

	_ = s.Stop()
	before := atomic.LoadInt64(&tickCount)

	time.Sleep(40 * time.Millisecond)
	after := atomic.LoadInt64(&tickCount)

	if after-before > 1 {
		t.Errorf("ticker kept firing after stop: before=%d after=%d", before, after)
	}
}

func TestNowPlayingController_CalledOnLoadAndPlay(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, _ := newTestService(t, fp)

	var updateCalled int64
	mock := &fakeNowPlaying{updateFn: func() { atomic.AddInt64(&updateCalled, 1) }}
	s.nowPlaying = mock

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 60}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	if err := s.loadAndPlay(track); err != nil {
		t.Fatalf("loadAndPlay: %v", err)
	}

	if atomic.LoadInt64(&updateCalled) == 0 {
		t.Error("expected UpdateNowPlaying to be called after loadAndPlay")
	}
}

// fakeNowPlaying satisfies domain.NowPlayingController.
type fakeNowPlaying struct {
	updateFn         func()
	updatePositionFn func(float64)
}

func (n *fakeNowPlaying) SetupRemoteCommands()                                   {}
func (n *fakeNowPlaying) SetRemoteCallbacks(_, _, _, _ func(), _ func(float64)) {}
func (n *fakeNowPlaying) UpdateNowPlaying(_ *domain.TrackDTO, _ float64, _ string) {
	if n.updateFn != nil {
		n.updateFn()
	}
}
func (n *fakeNowPlaying) UpdateNowPlayingPosition(pos float64) {
	if n.updatePositionFn != nil {
		n.updatePositionFn(pos)
	}
}
func (n *fakeNowPlaying) ClearNowPlaying() {}

func TestPrevious_Threshold(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, _ := newTestService(t, fp)

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 1) // start at t2

	_ = s.loadAndPlay(t2)

	// Case 1: position <= 3s -> should go to t1
	fp.mu.Lock()
	fp.status.Position = 2.0
	fp.mu.Unlock()

	if err := s.Previous(); err != nil {
		t.Fatalf("Previous failed: %v", err)
	}

	if s.GetCurrentTrack().ID != "t1" {
		t.Errorf("expected track t1, got %s", s.GetCurrentTrack().ID)
	}

	// Case 2: position > 3s -> should restart t1
	fp.mu.Lock()
	fp.status.Position = 5.0
	fp.mu.Unlock()

	if err := s.Previous(); err != nil {
		t.Fatalf("Previous failed: %v", err)
	}

	if s.GetCurrentTrack().ID != "t1" {
		t.Errorf("expected track t1, got %s", s.GetCurrentTrack().ID)
	}
	if fp.GetStatus().Position != 0 {
		t.Errorf("expected position 0, got %f", fp.GetStatus().Position)
	}
}

func TestPlayerShortcuts(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 0.5, Position: 50.0, Duration: 300.0, PlaybackState: domain.PlaybackStatePlaying}}
	s, _ := newTestService(t, fp)

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1}, 0)
	s.mu.Lock()
	s.currentTrack = t1
	s.mu.Unlock()

	// Test TogglePause
	_ = s.TogglePause()
	if fp.GetStatus().PlaybackState != domain.PlaybackStatePaused {
		t.Errorf("expected paused state, got %v", fp.GetStatus().PlaybackState)
	}
	_ = s.TogglePause()
	if fp.GetStatus().PlaybackState != domain.PlaybackStatePlaying {
		t.Errorf("expected playing state, got %v", fp.GetStatus().PlaybackState)
	}

	// Test FastForward
	_ = s.FastForward()
	if fp.GetStatus().Position != 60.0 {
		t.Errorf("expected position 60, got %v", fp.GetStatus().Position)
	}

	// Test Rewind
	_ = s.Rewind()
	if fp.GetStatus().Position != 50.0 {
		t.Errorf("expected position 50, got %v", fp.GetStatus().Position)
	}

	// Test IncreaseVolume
	_ = s.IncreaseVolume()
	if fp.GetStatus().Volume != 0.55 {
		t.Errorf("expected volume 0.55, got %v", fp.GetStatus().Volume)
	}

	// Test DecreaseVolume
	_ = s.DecreaseVolume()
	if fp.GetStatus().Volume != 0.5 {
		t.Errorf("expected volume 0.5, got %v", fp.GetStatus().Volume)
	}

	// Test ToggleMute
	if fp.GetStatus().Muted {
		t.Error("expected initial muted to be false")
	}
	_ = s.ToggleMute()
	if !fp.GetStatus().Muted {
		t.Error("expected muted to be true after toggle")
	}
	_ = s.ToggleMute()
	if fp.GetStatus().Muted {
		t.Error("expected muted to be false after second toggle")
	}

	// Test Unmute on IncreaseVolume
	_ = s.SetMuted(true)
	if !fp.GetStatus().Muted {
		t.Error("expected muted to be true")
	}
	_ = s.IncreaseVolume()
	if fp.GetStatus().Muted {
		t.Error("expected muted to be false after IncreaseVolume")
	}
}

// fakeGaplessPlayer wraps fakePlayer and implements GaplessPlayer for repeat-mode tests.
type fakeGaplessPlayer struct {
	fakePlayer
	mu            sync.Mutex
	enqueuedTrack *domain.TrackDTO
	clearCount    int
}

func (p *fakeGaplessPlayer) EnqueueNext(track *domain.TrackDTO) error {
	p.mu.Lock()
	p.enqueuedTrack = track
	p.mu.Unlock()
	return nil
}

func (p *fakeGaplessPlayer) StartPreloaded(track *domain.TrackDTO) error {
	p.fakePlayer.mu.Lock()
	p.status.TrackID = track.ID
	p.fakePlayer.mu.Unlock()
	return nil
}

func (p *fakeGaplessPlayer) AutoTransitions() bool { return false }

func (p *fakeGaplessPlayer) ClearEnqueued() {
	p.mu.Lock()
	p.enqueuedTrack = nil
	p.clearCount++
	p.mu.Unlock()
}

func TestSetRepeatMode_ClearsAndRequeuesGaplessTrack(t *testing.T) {
	fp := &fakeGaplessPlayer{fakePlayer: fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}}
	s, _ := newTestService(t, fp)

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 0)
	_ = s.SetRepeatMode(domain.RepeatModeOne)

	// Simulate track loaded: nextPreQueued = t1 (RepeatOne peeks current)
	s.mu.Lock()
	s.nextPreQueued = t1
	s.mu.Unlock()
	_ = fp.EnqueueNext(t1)

	// User switches off repeat — should clear engine queue and re-enqueue t2
	_ = s.SetRepeatMode(domain.RepeatModeOff)

	fp.mu.Lock()
	cleared := fp.clearCount
	enqueued := fp.enqueuedTrack
	fp.mu.Unlock()

	if cleared != 1 {
		t.Errorf("expected ClearEnqueued called once, got %d", cleared)
	}
	if enqueued == nil || enqueued.ID != "t2" {
		got := "<nil>"
		if enqueued != nil {
			got = enqueued.ID
		}
		t.Errorf("expected nextPreQueued = t2, got %s", got)
	}

	s.mu.RLock()
	nq := s.nextPreQueued
	s.mu.RUnlock()
	if nq == nil || nq.ID != "t2" {
		got := "<nil>"
		if nq != nil {
			got = nq.ID
		}
		t.Errorf("expected service.nextPreQueued = t2, got %s", got)
	}
}

func TestSetRepeatMode_RepeatOneRequeuesCurrentTrack(t *testing.T) {
	fp := &fakeGaplessPlayer{fakePlayer: fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}}
	s, _ := newTestService(t, fp)

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 0)

	// Simulate RepeatOff pre-queue: t2 was enqueued
	s.mu.Lock()
	s.nextPreQueued = t2
	s.mu.Unlock()
	_ = fp.EnqueueNext(t2)

	// Switch to RepeatOne — should clear t2 and re-enqueue t1 (current)
	_ = s.SetRepeatMode(domain.RepeatModeOne)

	fp.mu.Lock()
	enqueued := fp.enqueuedTrack
	fp.mu.Unlock()

	if enqueued == nil || enqueued.ID != "t1" {
		got := "<nil>"
		if enqueued != nil {
			got = enqueued.ID
		}
		t.Errorf("expected nextPreQueued = t1, got %s", got)
	}
}

func TestHandleTrackEnd_RepeatOneRepeatsCurrentTrack(t *testing.T) {
	fp := &fakeGaplessPlayer{fakePlayer: fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}}
	s, _ := newTestService(t, fp)

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 0)
	_ = s.SetRepeatMode(domain.RepeatModeOne)

	s.mu.Lock()
	s.currentTrack = t1
	s.nextPreQueued = t1 // RepeatOne pre-queued current track
	s.mu.Unlock()

	s.HandleTrackEnd()

	if ct := s.GetCurrentTrack(); ct == nil || ct.ID != "t1" {
		got := "<nil>"
		if ct != nil {
			got = ct.ID
		}
		t.Errorf("expected current track t1 after RepeatOne end, got %s", got)
	}
}

func TestHandleTrackEnd_RepeatOffAdvancesToNext(t *testing.T) {
	fp := &fakeGaplessPlayer{fakePlayer: fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}}
	s, _ := newTestService(t, fp)

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 0)
	_ = s.SetRepeatMode(domain.RepeatModeOff)

	s.mu.Lock()
	s.currentTrack = t1
	s.nextPreQueued = t2
	s.mu.Unlock()

	s.HandleTrackEnd()

	if ct := s.GetCurrentTrack(); ct == nil || ct.ID != "t2" {
		got := "<nil>"
		if ct != nil {
			got = ct.ID
		}
		t.Errorf("expected current track t2 after RepeatOff end, got %s", got)
	}
}

func TestFastForward_NextTrack(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 0.5, Position: 295.0, Duration: 300.0, PlaybackState: domain.PlaybackStatePlaying}}
	s, _ := newTestService(t, fp)

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 0)
	s.mu.Lock()
	s.currentTrack = t1
	s.mu.Unlock()

	// Fast forward near end should trigger Next()
	_ = s.FastForward()
	if s.GetCurrentTrack().ID != "t2" {
		t.Errorf("expected track t2, got %s", s.GetCurrentTrack().ID)
	}
}

// fakeNormSettingsRepo and fakeNormAnalysisRepo back a real
// normalization.NormalizationService for testing loadAndPlay's gain push.
type fakeNormSettingsRepo struct {
	settings *domain.AppSettings
}

func (r *fakeNormSettingsRepo) Load(_ context.Context) (*domain.AppSettings, error) {
	s := *r.settings
	return &s, nil
}
func (r *fakeNormSettingsRepo) Save(_ context.Context, settings *domain.AppSettings) error {
	r.settings = settings
	return nil
}

type fakeNormAnalysisRepo struct {
	domain.AnalysisRepository
	features map[string]*domain.TrackFeatures
}

func (r *fakeNormAnalysisRepo) GetFeatures(_ context.Context, trackID string) (*domain.TrackFeatures, error) {
	return r.features[trackID], nil
}

func TestLoadAndPlay_AppliesNormalizationGain(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, _ := newTestService(t, fp)

	s.normSvc = normalization.NewNormalizationService(
		&fakeNormSettingsRepo{settings: &domain.AppSettings{
			NormalizationEnabled:    true,
			NormalizationMode:       "track",
			NormalizationTargetLUFS: -14.0,
		}},
		&fakeNormAnalysisRepo{features: map[string]*domain.TrackFeatures{
			"t1": {TrackID: "t1", LoudnessLUFS: -18.0},
		}},
		&fakeTrackRepo{},
		fp,
		slog.Default(),
	)

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 60}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	if err := s.loadAndPlay(track); err != nil {
		t.Fatalf("loadAndPlay: %v", err)
	}

	fp.mu.Lock()
	gotGain := fp.lastPreampGain
	fp.mu.Unlock()
	if want := -14.0 - (-18.0); gotGain != want {
		t.Errorf("preamp gain = %v, want %v", gotGain, want)
	}
}

func TestLoadAndPlay_FiresTrackLoadListener(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, _ := newTestService(t, fp)

	var gotID string
	s.AddTrackLoadListener(func(track *domain.TrackDTO) { gotID = track.ID })

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 60}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	if err := s.loadAndPlay(track); err != nil {
		t.Fatalf("loadAndPlay: %v", err)
	}

	if gotID != "t1" {
		t.Errorf("expected track-load listener to fire with t1, got %q", gotID)
	}
}
