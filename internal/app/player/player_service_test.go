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
	loadCalls      int
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
	p.loadCalls++
	p.status.TrackID = track.ID
	p.status.Duration = float64(track.Duration)
	p.status.Position = 0
	p.status.PlaybackState = domain.PlaybackStateStopped
	p.mu.Unlock()
	return nil
}

func TestReplaceQueueKeepingCurrentTrackPreservesPlaybackPosition(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, _ := newTestService(t, fp)
	seed := &domain.TrackDTO{Track: domain.Track{ID: "seed", Duration: 120}}
	similar := &domain.TrackDTO{Track: domain.Track{ID: "similar", Duration: 180}}

	if err := s.PlayTracks([]*domain.TrackDTO{seed}, 0); err != nil {
		t.Fatalf("start seed track: %v", err)
	}
	if err := s.Seek(42); err != nil {
		t.Fatalf("seek seed track: %v", err)
	}
	if err := s.ReplaceQueueKeepingCurrentTrack([]*domain.TrackDTO{seed, similar}); err != nil {
		t.Fatalf("replace queue: %v", err)
	}

	status := fp.GetStatus()
	if status.TrackID != seed.ID || status.Position != 42 || status.PlaybackState != domain.PlaybackStatePlaying {
		t.Fatalf("playback was interrupted: %+v", status)
	}
	if fp.loadCalls != 1 {
		t.Fatalf("Load called %d times, want 1", fp.loadCalls)
	}
	queue := s.GetQueue()
	if len(queue) != 2 || queue[0].ID != seed.ID || queue[1].ID != similar.ID {
		t.Fatalf("unexpected replacement queue: %+v", queue)
	}
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
type fakeArtworkCache struct {
	existing map[string]bool
}

func (c *fakeArtworkCache) Save(_ context.Context, _ []byte, _ string) (string, error) {
	return "", nil
}

func (c *fakeArtworkCache) GetPath(key string) string           { return key }
func (c *fakeArtworkCache) GetVariantPath(key, _ string) string { return key }
func (c *fakeArtworkCache) Exists(key string) bool              { return c.existing[key] }
func (c *fakeArtworkCache) CleanupOrphaned(_ context.Context, _ map[string]bool) error {
	return nil
}

type notificationCall struct {
	title       string
	body        string
	artworkPath string
}

type fakeTrackTransitionNotifier struct {
	calls []notificationCall
}

func (n *fakeTrackTransitionNotifier) NotifyTrackAdvanced(title, body, artworkPath string) {
	n.calls = append(n.calls, notificationCall{title: title, body: body, artworkPath: artworkPath})
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
	queue := NewQueueService(slog.Default())
	var emitCount int64

	s := &PlayerService{
		player:                          player,
		queue:                           queue,
		logger:                          slog.Default(),
		artworkCache:                    &fakeArtworkCache{},
		trackRepo:                       &fakeTrackRepo{},
		stateRepo:                       &fakePlayerStateRepo{},
		tickInterval:                    10 * time.Millisecond,
		playCounted:                     make(map[string]bool),
		npReported:                      make(map[string]bool),
		posConfirmed:                    make(map[string]bool),
		autoAdvanceNotificationsEnabled: true,
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

func TestPushNowPlaying_ClearsArtworkWhenCacheEntryIsMissing(t *testing.T) {
	service, _ := newTestService(t, &fakePlayer{})
	nowPlaying := &fakeNowPlaying{}
	service.nowPlaying = nowPlaying

	service.pushNowPlaying(&domain.TrackDTO{Track: domain.Track{
		ID:         "t1",
		ArtworkKey: "missing.jpg",
	}}, 0)

	if nowPlaying.artworkPath != "" {
		t.Errorf("artwork path = %q, want empty for missing cache entry", nowPlaying.artworkPath)
	}
}

func TestPushNowPlaying_UsesExistingArtwork(t *testing.T) {
	service, _ := newTestService(t, &fakePlayer{})
	service.artworkCache = &fakeArtworkCache{existing: map[string]bool{"cover.jpg": true}}
	nowPlaying := &fakeNowPlaying{}
	service.nowPlaying = nowPlaying

	service.pushNowPlaying(&domain.TrackDTO{Track: domain.Track{
		ID:         "t1",
		ArtworkKey: "cover.jpg",
	}}, 0)

	if nowPlaying.artworkPath != "cover.jpg" {
		t.Errorf("artwork path = %q, want cover.jpg", nowPlaying.artworkPath)
	}
}

// fakeNowPlaying satisfies domain.NowPlayingController.
type fakeNowPlaying struct {
	updateFn         func()
	updatePositionFn func(float64)
	artworkPath      string
}

func (n *fakeNowPlaying) SetupRemoteCommands()                                  {}
func (n *fakeNowPlaying) SetRemoteCallbacks(_, _, _, _ func(), _ func(float64)) {}
func (n *fakeNowPlaying) UpdateNowPlaying(_ *domain.TrackDTO, _ float64, artworkPath string) {
	n.artworkPath = artworkPath
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

func TestHandleTrackEnd_NotifiesForAutomaticAdvanceOnly(t *testing.T) {
	fp := &fakeGaplessPlayer{fakePlayer: fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}}
	s, _ := newTestService(t, fp)
	notifier := &fakeTrackTransitionNotifier{}
	s.notifier = notifier

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Title: "First", Duration: 300}}
	t2 := &domain.TrackDTO{
		Track:   domain.Track{ID: "t2", Title: "Second", Duration: 300, ArtworkKey: "artwork-key"},
		Artists: []*domain.Artist{{Name: "Artist A"}, {Name: "Artist B"}},
		Album:   &domain.Album{Title: "Album A"},
	}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 0)
	s.mu.Lock()
	s.currentTrack = t1
	s.nextPreQueued = t2
	s.mu.Unlock()

	s.HandleTrackEnd()

	if len(notifier.calls) != 1 {
		t.Fatalf("expected one automatic-advance notification, got %#v", notifier.calls)
	}
	if got, want := notifier.calls[0], (notificationCall{title: "Second", body: "Artist A, Artist B - Album A", artworkPath: "artwork-key"}); got != want {
		t.Fatalf("notification payload = %#v, want %#v", got, want)
	}

	if err := s.Next(); err != nil {
		t.Fatalf("manual Next: %v", err)
	}
	if len(notifier.calls) != 1 {
		t.Fatalf("manual Next must not notify, got %#v", notifier.calls)
	}
}

func TestHandleTrackEnd_DoesNotNotifyWhenDisabledOrRepeatingSameTrack(t *testing.T) {
	fp := &fakeGaplessPlayer{fakePlayer: fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}}
	s, _ := newTestService(t, fp)
	notifier := &fakeTrackTransitionNotifier{}
	s.notifier = notifier

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Title: "Repeat", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Title: "Next", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 0)
	s.mu.Lock()
	s.currentTrack = t1
	s.nextPreQueued = t1
	s.mu.Unlock()
	s.HandleTrackEnd()
	if len(notifier.calls) != 0 {
		t.Fatalf("repeat-one must not notify, got %#v", notifier.calls)
	}

	s.SetAutoAdvanceNotificationsEnabled(false)
	s.mu.Lock()
	s.currentTrack = t1
	s.nextPreQueued = t2
	s.mu.Unlock()
	s.HandleTrackEnd()
	if len(notifier.calls) != 0 {
		t.Fatalf("disabled setting must not notify, got %#v", notifier.calls)
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
			LibraryAnalysisEnabled:  true,
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

// fakeCrossfadePlayer wraps fakeGaplessPlayer and implements CrossfadePlayer,
// recording begin/finish calls for state-machine tests.
type fakeCrossfadePlayer struct {
	fakeGaplessPlayer
	cfMu         sync.Mutex
	crossfadeSec float64
	beginTracks  []string
	beginDurs    []float64
	finishCount  int
	loadCount    int
}

func (p *fakeCrossfadePlayer) SetCrossfadeDuration(sec float64) {
	p.cfMu.Lock()
	p.crossfadeSec = sec
	p.cfMu.Unlock()
}

func (p *fakeCrossfadePlayer) BeginCrossfadeToPreloaded(track *domain.TrackDTO, durationSec, _ float64) error {
	p.cfMu.Lock()
	p.beginTracks = append(p.beginTracks, track.ID)
	p.beginDurs = append(p.beginDurs, durationSec)
	p.cfMu.Unlock()

	p.fakePlayer.mu.Lock()
	p.status.TrackID = track.ID
	p.status.Duration = float64(track.Duration)
	p.status.Position = 0
	p.status.PlaybackState = domain.PlaybackStatePlaying
	p.fakePlayer.mu.Unlock()

	// The preload is consumed by the fade.
	p.mu.Lock()
	p.enqueuedTrack = nil
	p.mu.Unlock()
	return nil
}

func (p *fakeCrossfadePlayer) FinishCrossfade() {
	p.cfMu.Lock()
	p.finishCount++
	p.cfMu.Unlock()
}

func (p *fakeCrossfadePlayer) Load(track *domain.TrackDTO) error {
	p.cfMu.Lock()
	p.loadCount++
	p.cfMu.Unlock()
	return p.fakePlayer.Load(track)
}

func (p *fakeCrossfadePlayer) counts() (begins []string, finishes, loads int) {
	p.cfMu.Lock()
	defer p.cfMu.Unlock()
	return append([]string{}, p.beginTracks...), p.finishCount, p.loadCount
}

func newCrossfadeFixture(t *testing.T, seconds int, tracks ...*domain.TrackDTO) (*PlayerService, *fakeCrossfadePlayer) {
	t.Helper()
	fp := &fakeCrossfadePlayer{
		fakeGaplessPlayer: fakeGaplessPlayer{fakePlayer: fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}},
	}
	s, _ := newTestService(t, fp)
	s.SetCrossfadeSeconds(seconds)
	s.queue.SetQueue(tracks, 0)
	s.mu.Lock()
	s.currentTrack = tracks[0]
	if len(tracks) > 1 {
		s.nextPreQueued = tracks[1]
	}
	s.mu.Unlock()
	fp.fakePlayer.mu.Lock()
	fp.status.TrackID = tracks[0].ID
	fp.status.Duration = float64(tracks[0].Duration)
	fp.status.PlaybackState = domain.PlaybackStatePlaying
	fp.fakePlayer.mu.Unlock()
	return s, fp
}

func playingStatus(trackID string, position, duration float64) domain.PlayerStatus {
	return domain.PlayerStatus{
		TrackID:       trackID,
		Position:      position,
		Duration:      duration,
		PlaybackState: domain.PlaybackStatePlaying,
	}
}

func TestCrossfade_NaturalTriggerFiresOnce(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2)

	// Too early — no fade yet.
	s.maybeStartCrossfade(playingStatus("t1", 200, 300))
	if begins, _, _ := fp.counts(); len(begins) != 0 {
		t.Fatalf("fade started too early: %v", begins)
	}

	s.maybeStartCrossfade(playingStatus("t1", 296, 300))
	begins, _, loads := fp.counts()
	if len(begins) != 1 || begins[0] != "t2" {
		t.Fatalf("expected one crossfade into t2, got %v", begins)
	}
	if loads != 0 {
		t.Errorf("expected no hard load, got %d", loads)
	}
	if ct := s.GetCurrentTrack(); ct == nil || ct.ID != "t2" {
		t.Errorf("expected currentTrack t2 at fade start")
	}
	if next := s.queue.PeekNext(); next != nil {
		t.Errorf("expected queue advanced to last track, PeekNext = %v", next.ID)
	}

	// Second tick during the fade must not start another one.
	s.maybeStartCrossfade(playingStatus("t2", 1, 300))
	s.maybeStartCrossfade(playingStatus("t1", 297, 300))
	if begins, _, _ := fp.counts(); len(begins) != 1 {
		t.Errorf("expected exactly one begin, got %v", begins)
	}
}

func TestCrossfade_EmitsArtworkLifecycle(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 10, ArtworkKey: "from"}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 10, ArtworkKey: "to"}}
	s, _ := newCrossfadeFixture(t, 6, t1, t2)

	var events []domain.ArtworkCrossfadeEvent
	s.AddArtworkCrossfadeListener(func(event domain.ArtworkCrossfadeEvent) {
		events = append(events, event)
	})

	// The effective duration is clamped to half the short track (5 seconds).
	s.maybeStartCrossfade(playingStatus("t1", 7, 10))
	if len(events) != 1 {
		t.Fatalf("expected start event, got %#v", events)
	}
	start := events[0]
	if start.Phase != "start" || start.TransitionID != 1 || start.FromArtworkKey != "from" || start.ToArtworkKey != "to" || start.DurationMS != 5000 {
		t.Fatalf("unexpected start event: %#v", start)
	}

	s.finishActiveCrossfade()
	if len(events) != 2 || events[1].Phase != "end" || events[1].TransitionID != start.TransitionID {
		t.Fatalf("expected matching end event, got %#v", events)
	}
}

func TestCrossfade_DisabledNeverBegins(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 0, t1, t2)

	s.maybeStartCrossfade(playingStatus("t1", 299, 300))
	if begins, _, _ := fp.counts(); len(begins) != 0 {
		t.Errorf("crossfade=0 must never begin a fade, got %v", begins)
	}

	// Manual next hard-loads.
	if err := s.Next(); err != nil {
		t.Fatalf("Next: %v", err)
	}
	begins, _, loads := fp.counts()
	if len(begins) != 0 || loads != 1 {
		t.Errorf("expected hard load on Next with crossfade off, begins=%v loads=%d", begins, loads)
	}
}

func TestCrossfade_ShortTrackAndLastInstantSkipped(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2)

	// Sub-2s track: never fade.
	s.maybeStartCrossfade(playingStatus("t1", 1.0, 1.5))
	// Last 0.4s: let the gapless end-callback path win.
	s.maybeStartCrossfade(playingStatus("t1", 299.8, 300))
	if begins, _, _ := fp.counts(); len(begins) != 0 {
		t.Errorf("expected no fades, got %v", begins)
	}

	// Short-but-fadeable track clamps to duration/2.
	s.maybeStartCrossfade(playingStatus("t1", 7, 10))
	begins, _, _ := fp.counts()
	if len(begins) != 1 {
		t.Fatalf("expected clamped fade to fire, got %v", begins)
	}
	fp.cfMu.Lock()
	dur := fp.beginDurs[0]
	fp.cfMu.Unlock()
	if dur != 5 {
		t.Errorf("expected fade clamped to 5s (duration/2), got %v", dur)
	}
}

// Manual Next/Previous/PlayQueueIndex never crossfade — only the natural
// end-of-track auto-advance does.
func TestCrossfade_ManualNextHardLoads(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	t3 := &domain.TrackDTO{Track: domain.Track{ID: "t3", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2, t3)

	if err := s.Next(); err != nil {
		t.Fatalf("Next: %v", err)
	}
	begins, _, loads := fp.counts()
	if len(begins) != 0 || loads != 1 {
		t.Fatalf("manual Next must hard-load without fade, begins=%v loads=%d", begins, loads)
	}
	if ct := s.GetCurrentTrack(); ct == nil || ct.ID != "t2" {
		t.Errorf("expected currentTrack t2")
	}
}

func TestCrossfade_ManualPreviousHardLoads(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2)

	if err := s.Next(); err != nil {
		t.Fatalf("Next: %v", err)
	}
	if err := s.Previous(); err != nil {
		t.Fatalf("Previous: %v", err)
	}
	begins, _, _ := fp.counts()
	if len(begins) != 0 {
		t.Errorf("manual Previous must not fade, begins=%v", begins)
	}
	if ct := s.GetCurrentTrack(); ct == nil || ct.ID != "t1" {
		t.Errorf("expected currentTrack t1")
	}
}

func TestCrossfade_PlayQueueIndexHardLoads(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	t3 := &domain.TrackDTO{Track: domain.Track{ID: "t3", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2, t3)

	if err := s.PlayQueueIndex(2); err != nil {
		t.Fatalf("PlayQueueIndex: %v", err)
	}
	begins, _, _ := fp.counts()
	if len(begins) != 0 {
		t.Errorf("PlayQueueIndex must not fade, begins=%v", begins)
	}
	if ct := s.GetCurrentTrack(); ct == nil || ct.ID != "t3" {
		t.Errorf("expected currentTrack t3")
	}
}

func TestCrossfade_PauseDuringFadeFinishes(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	t3 := &domain.TrackDTO{Track: domain.Track{ID: "t3", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2, t3)

	s.maybeStartCrossfade(playingStatus("t1", 296, 300))
	if err := s.Pause(); err != nil {
		t.Fatalf("Pause: %v", err)
	}

	_, finishes, _ := fp.counts()
	if finishes != 1 {
		t.Errorf("expected fade finished on pause, got %d finishes", finishes)
	}
	s.mu.RLock()
	fading := s.fading
	nq := s.nextPreQueued
	s.mu.RUnlock()
	if fading {
		t.Error("expected fading=false after pause")
	}
	// finishActiveCrossfade pre-enqueues the following track (t3).
	if nq == nil || nq.ID != "t3" {
		got := "<nil>"
		if nq != nil {
			got = nq.ID
		}
		t.Errorf("expected t3 pre-enqueued after finish, got %s", got)
	}
}

func TestCrossfade_HandleTrackEndDuringFadeIgnored(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	t3 := &domain.TrackDTO{Track: domain.Track{ID: "t3", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2, t3)

	s.maybeStartCrossfade(playingStatus("t1", 296, 300))

	// The outgoing track drains during the overlap — must not double-advance.
	s.HandleTrackEnd()

	if ct := s.GetCurrentTrack(); ct == nil || ct.ID != "t2" {
		t.Errorf("expected currentTrack to stay t2 after ignored end")
	}
	if next := s.queue.PeekNext(); next == nil || next.ID != "t3" {
		t.Errorf("expected queue still pointing at t2 (next=t3)")
	}
	if _, _, loads := fp.counts(); loads != 0 {
		t.Errorf("expected no hard load, got %d", loads)
	}
}

func TestCrossfade_StaleGenerationIsNoop(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2)

	s.maybeStartCrossfade(playingStatus("t1", 296, 300))
	s.mu.RLock()
	gen := s.fadeGen
	s.mu.RUnlock()

	if !s.snapCrossfade(gen) {
		t.Fatal("expected snap to complete the live fade")
	}
	// A stale completion (e.g. the scheduled AfterFunc) must be a no-op.
	s.finishCrossfade(gen)
	s.finishCrossfade(gen - 1)

	_, finishes, _ := fp.counts()
	if finishes != 1 {
		t.Errorf("expected exactly one native finish, got %d", finishes)
	}
	s.mu.RLock()
	nq := s.nextPreQueued
	s.mu.RUnlock()
	if nq != nil {
		t.Errorf("stale finish must not pre-enqueue, got %s", nq.ID)
	}
}

func TestCrossfade_RepeatOneFadesIntoSameTrack(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1, t2)
	_ = s.SetRepeatMode(domain.RepeatModeOne)
	s.mu.Lock()
	s.nextPreQueued = t1 // RepeatOne pre-queues the current track
	s.mu.Unlock()

	s.maybeStartCrossfade(playingStatus("t1", 296, 300))
	begins, _, _ := fp.counts()
	if len(begins) != 1 || begins[0] != "t1" {
		t.Fatalf("expected crossfade into t1 itself, got %v", begins)
	}
	if ct := s.GetCurrentTrack(); ct == nil || ct.ID != "t1" {
		t.Errorf("expected currentTrack t1")
	}
}

func TestCrossfade_EndOfQueueNeverBegins(t *testing.T) {
	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	s, fp := newCrossfadeFixture(t, 6, t1)

	s.maybeStartCrossfade(playingStatus("t1", 296, 300))
	if begins, _, _ := fp.counts(); len(begins) != 0 {
		t.Errorf("no pre-queued track — fade must not begin, got %v", begins)
	}
}

func TestSetCrossfadeSeconds_ResyncsPreQueue(t *testing.T) {
	fp := &fakeCrossfadePlayer{
		fakeGaplessPlayer: fakeGaplessPlayer{fakePlayer: fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}},
	}
	s, _ := newTestService(t, fp)

	t1 := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 300}}
	t2 := &domain.TrackDTO{Track: domain.Track{ID: "t2", Duration: 300}}
	s.queue.SetQueue([]*domain.TrackDTO{t1, t2}, 0)
	s.mu.Lock()
	s.nextPreQueued = t2
	s.mu.Unlock()
	_ = fp.EnqueueNext(t2)

	s.SetCrossfadeSeconds(6)

	fp.cfMu.Lock()
	sec := fp.crossfadeSec
	fp.cfMu.Unlock()
	if sec != 6 {
		t.Errorf("expected native duration 6, got %v", sec)
	}
	fp.mu.Lock()
	cleared := fp.clearCount
	enq := fp.enqueuedTrack
	fp.mu.Unlock()
	if cleared != 1 || enq == nil || enq.ID != "t2" {
		t.Errorf("expected pre-queue re-synced (cleared=1, t2 re-enqueued), got cleared=%d enq=%v", cleared, enq)
	}

	// Clamping: out-of-range values.
	s.SetCrossfadeSeconds(99)
	fp.cfMu.Lock()
	sec = fp.crossfadeSec
	fp.cfMu.Unlock()
	if sec != float64(domain.MaxCrossfadeSeconds) {
		t.Errorf("expected clamp to %d, got %v", domain.MaxCrossfadeSeconds, sec)
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
