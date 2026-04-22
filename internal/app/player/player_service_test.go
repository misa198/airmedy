package player

import (
	"context"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"changeme/internal/domain"
)

// fakePlayer is a test double for domain.AudioPlayer.
type fakePlayer struct {
	mu     sync.Mutex
	status domain.PlayerStatus
	onEnd  func()
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

// fakeArtworkCache is a no-op artwork cache for tests.
type fakeArtworkCache struct{}

func (c *fakeArtworkCache) Save(_ context.Context, _ []byte, _ string) (string, error) {
	return "", nil
}

func (c *fakeArtworkCache) GetPath(key string) string { return key }
func (c *fakeArtworkCache) Exists(_ string) bool      { return false }
func (c *fakeArtworkCache) CleanupOrphaned(_ context.Context, _ map[string]bool) error {
	return nil
}

// fakeLifecycle satisfies fx.Lifecycle for tests by discarding hooks.
type fakeLifecycle struct{}

func (l *fakeLifecycle) Append(_ interface{ OnStart() }) {}

// newTestService builds a PlayerService with fast tick interval for tests.
// emitCount is incremented by a goroutine — callers should wait briefly.
func newTestService(t *testing.T, player domain.AudioPlayer) (*PlayerService, *int64) {
	t.Helper()
	queue := NewQueueService()
	var emitCount int64

	s := &PlayerService{
		player:       player,
		queue:        queue,
		artworkCache: &fakeArtworkCache{},
		tickInterval: 10 * time.Millisecond,
	}
	s.player.OnTrackEnd(s.HandleTrackEnd)

	// Patch emitStatus to count calls without a real Wails app
	s.emitStatusHook = func() { atomic.AddInt64(&emitCount, 1) }

	return s, &emitCount
}

func TestPositionTicker_StartsOnPlay(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, emitCount := newTestService(t, fp)

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 120}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	if err := s.loadAndPlay(track); err != nil {
		t.Fatalf("loadAndPlay: %v", err)
	}

	time.Sleep(60 * time.Millisecond)
	n := atomic.LoadInt64(emitCount)
	if n < 2 {
		t.Errorf("expected ≥2 ticker emits, got %d", n)
	}
}

func TestPositionTicker_StopsOnPause(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, emitCount := newTestService(t, fp)

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 120}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	_ = s.loadAndPlay(track)
	time.Sleep(40 * time.Millisecond)

	_ = s.Pause()
	before := atomic.LoadInt64(emitCount)

	time.Sleep(40 * time.Millisecond)
	after := atomic.LoadInt64(emitCount)

	// After pause the ticker should have stopped — allow at most 1 extra emit
	if after-before > 1 {
		t.Errorf("ticker kept firing after pause: before=%d after=%d", before, after)
	}
}

func TestPositionTicker_StopsOnStop(t *testing.T) {
	fp := &fakePlayer{status: domain.PlayerStatus{Volume: 1.0}}
	s, emitCount := newTestService(t, fp)

	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Duration: 120}}
	s.queue.SetQueue([]*domain.TrackDTO{track}, 0)

	_ = s.loadAndPlay(track)
	time.Sleep(40 * time.Millisecond)

	_ = s.Stop()
	before := atomic.LoadInt64(emitCount)

	time.Sleep(40 * time.Millisecond)
	after := atomic.LoadInt64(emitCount)

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
	updateFn func()
}

func (n *fakeNowPlaying) SetupRemoteCommands()                                             {}
func (n *fakeNowPlaying) SetRemoteCallbacks(_, _, _, _ func())                            {}
func (n *fakeNowPlaying) UpdateNowPlaying(_ *domain.TrackDTO, _ float64, _ string) {
	if n.updateFn != nil {
		n.updateFn()
	}
}
func (n *fakeNowPlaying) ClearNowPlaying() {}
