package analysis

import (
	"context"
	"log/slog"
	"sync"
	"testing"
	"time"

	"airmedy/internal/domain"
)

type mockTrackRepo struct {
	domain.TrackRepository
	tracks map[string]*domain.TrackDTO
}

func (m *mockTrackRepo) GetByID(ctx context.Context, id string) (*domain.TrackDTO, error) {
	return m.tracks[id], nil
}

type mockAnalysisRepo struct {
	domain.AnalysisRepository
	mu       sync.Mutex
	versions map[string]int
	calls    []string // order of UpsertFeatures calls (analyzer-call order proxy)
}

func newMockAnalysisRepo() *mockAnalysisRepo {
	return &mockAnalysisRepo{versions: make(map[string]int)}
}

func (m *mockAnalysisRepo) UpsertFeatures(ctx context.Context, f *domain.TrackFeatures) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.versions[f.TrackID] = f.AnalyzerVersion
	m.calls = append(m.calls, f.TrackID)
	return nil
}

func (m *mockAnalysisRepo) CountPending(ctx context.Context, currentVersion int) (int, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return 0, nil
}

func (m *mockAnalysisRepo) ListPending(ctx context.Context, currentVersion, limit int) ([]string, error) {
	return nil, nil
}

func (m *mockAnalysisRepo) callCount() int {
	m.mu.Lock()
	defer m.mu.Unlock()
	return len(m.calls)
}

func (m *mockAnalysisRepo) callOrder() []string {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := make([]string, len(m.calls))
	copy(out, m.calls)
	return out
}

// mockAnalyzer blocks on a gate before returning, letting tests serialize
// otherwise-concurrent worker activity for deterministic ordering assertions.
type mockAnalyzer struct {
	gate chan struct{} // if non-nil, Analyze waits for a send before proceeding
}

func (a *mockAnalyzer) Analyze(ctx context.Context, path string) (*domain.TrackFeatures, error) {
	if a.gate != nil {
		select {
		case <-a.gate:
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
	return &domain.TrackFeatures{}, nil
}

type fakeSettingsRepo struct {
	settings domain.AppSettings
}

func (r *fakeSettingsRepo) Load(_ context.Context) (*domain.AppSettings, error) {
	s := r.settings
	return &s, nil
}

func (r *fakeSettingsRepo) Save(_ context.Context, settings *domain.AppSettings) error {
	r.settings = *settings
	return nil
}

// newTestService builds a service for tests that drive the worker pool
// mechanics directly (manually spawning svc.worker, calling svc.Enqueue)
// rather than going through Start/SetEnabled. enabled is forced true since
// Enqueue is a no-op while disabled.
func newTestService(repo *mockAnalysisRepo, analyzer domain.LoudnessAnalyzer, tracks map[string]*domain.TrackDTO) *AnalysisService {
	s := NewAnalysisService(&mockTrackRepo{tracks: tracks}, repo, analyzer,
		&fakeSettingsRepo{settings: domain.AppSettings{LibraryAnalysisEnabled: true}}, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	s.runCtx = ctx
	s.runCancel = cancel
	s.enabled = true
	return s
}

func tracksOf(ids ...string) map[string]*domain.TrackDTO {
	out := make(map[string]*domain.TrackDTO, len(ids))
	for _, id := range ids {
		out[id] = &domain.TrackDTO{Track: domain.Track{ID: id, Path: "/m/" + id + ".mp3"}}
	}
	return out
}

func waitFor(t *testing.T, timeout time.Duration, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatalf("condition not met within %v", timeout)
}

func TestEnqueueDedupesInFlightAndQueued(t *testing.T) {
	repo := newMockAnalysisRepo()
	gate := make(chan struct{})
	svc := newTestService(repo, &mockAnalyzer{gate: gate}, tracksOf("trk-1"))

	svc.wg.Add(1)
	go svc.worker(svc.runCtx)

	// Enqueue once; worker picks it up and blocks in Analyze on the gate.
	svc.Enqueue("trk-1", false)
	waitFor(t, time.Second, func() bool {
		svc.mu.Lock()
		defer svc.mu.Unlock()
		return svc.inFlight["trk-1"]
	})

	// Re-enqueue while in-flight: must be a no-op (no duplicate queue entry).
	svc.Enqueue("trk-1", false)
	svc.mu.Lock()
	queueLen := len(svc.normalQueue) + len(svc.boostQueue)
	svc.mu.Unlock()
	if queueLen != 0 {
		t.Fatalf("expected no-op enqueue while in-flight, got queue len %d", queueLen)
	}

	close(gate)
	waitFor(t, time.Second, func() bool { return repo.callCount() == 1 })

	_ = svc.Stop(context.Background())
	if got := repo.callCount(); got != 1 {
		t.Fatalf("expected exactly 1 analyze call, got %d", got)
	}
}

func TestPriorityPromotesAheadOfNormalQueue(t *testing.T) {
	repo := newMockAnalysisRepo()
	svc := newTestService(repo, &mockAnalyzer{}, tracksOf("a", "b", "c"))

	// Single worker for deterministic serialized ordering.
	svc.wg.Add(1)
	go svc.worker(svc.runCtx)

	// Block the worker on the first track so subsequent enqueues land while idle.
	svc.mu.Lock()
	svc.throttled = true
	svc.mu.Unlock()

	svc.Enqueue("a", false)
	svc.Enqueue("b", false)
	svc.Enqueue("c", true) // boosted: should run before a and b

	svc.SetThrottled(false)

	waitFor(t, time.Second, func() bool { return repo.callCount() == 3 })
	_ = svc.Stop(context.Background())

	order := repo.callOrder()
	if order[0] != "c" {
		t.Fatalf("expected boosted track first, got order %v", order)
	}
}

func TestBoostPriorityPromotesQueuedTrack(t *testing.T) {
	repo := newMockAnalysisRepo()
	svc := newTestService(repo, &mockAnalyzer{}, tracksOf("a", "b"))

	svc.mu.Lock()
	svc.throttled = true
	svc.mu.Unlock()

	svc.Enqueue("a", false)
	svc.Enqueue("b", false)
	svc.BoostPriority("b")

	svc.mu.Lock()
	front := svc.boostQueue[0]
	svc.mu.Unlock()
	if front != "b" {
		t.Fatalf("expected b promoted to front of boost queue, got %s", front)
	}
}

func TestSetThrottledBlocksNewWork(t *testing.T) {
	repo := newMockAnalysisRepo()
	svc := newTestService(repo, &mockAnalyzer{}, tracksOf("trk-1"))

	svc.wg.Add(1)
	go svc.worker(svc.runCtx)

	svc.SetThrottled(true)
	svc.Enqueue("trk-1", false)

	// Give the worker a chance to (incorrectly) run; it must not.
	time.Sleep(50 * time.Millisecond)
	if got := repo.callCount(); got != 0 {
		t.Fatalf("expected no analyze calls while throttled, got %d", got)
	}

	svc.SetThrottled(false)
	waitFor(t, time.Second, func() bool { return repo.callCount() == 1 })

	_ = svc.Stop(context.Background())
}

func TestStartResumesEnabledPool(t *testing.T) {
	repo := newMockAnalysisRepo()
	settingsRepo := &fakeSettingsRepo{settings: domain.AppSettings{LibraryAnalysisEnabled: true}}
	svc := NewAnalysisService(&mockTrackRepo{tracks: tracksOf("trk-1")}, repo, &mockAnalyzer{}, settingsRepo, slog.Default())

	if err := svc.Start(context.Background()); err != nil {
		t.Fatalf("Start: %v", err)
	}
	svc.Enqueue("trk-1", false)
	waitFor(t, time.Second, func() bool { return repo.callCount() == 1 })
	_ = svc.Stop(context.Background())
}

func TestStartLeavesPoolOffWhenDisabled(t *testing.T) {
	repo := newMockAnalysisRepo()
	settingsRepo := &fakeSettingsRepo{settings: domain.AppSettings{LibraryAnalysisEnabled: false}}
	svc := NewAnalysisService(&mockTrackRepo{tracks: tracksOf("trk-1")}, repo, &mockAnalyzer{}, settingsRepo, slog.Default())

	if err := svc.Start(context.Background()); err != nil {
		t.Fatalf("Start: %v", err)
	}
	svc.Enqueue("trk-1", false) // must be a no-op: pool never started
	time.Sleep(50 * time.Millisecond)
	if got := repo.callCount(); got != 0 {
		t.Fatalf("expected no analyze calls while disabled, got %d", got)
	}
	_ = svc.Stop(context.Background())
}

func TestSetEnabledStartsAndStopsPoolLive(t *testing.T) {
	repo := newMockAnalysisRepo()
	settingsRepo := &fakeSettingsRepo{settings: domain.AppSettings{}}
	svc := NewAnalysisService(&mockTrackRepo{tracks: tracksOf("trk-1")}, repo, &mockAnalyzer{}, settingsRepo, slog.Default())

	if err := svc.SetEnabled(context.Background(), true); err != nil {
		t.Fatalf("SetEnabled(true): %v", err)
	}
	svc.Enqueue("trk-1", false)
	waitFor(t, time.Second, func() bool { return repo.callCount() == 1 })

	if err := svc.SetEnabled(context.Background(), false); err != nil {
		t.Fatalf("SetEnabled(false): %v", err)
	}
	// Enqueue while disabled must be a no-op: no second analyze call.
	svc.Enqueue("trk-1", false)
	time.Sleep(50 * time.Millisecond)
	if got := repo.callCount(); got != 1 {
		t.Fatalf("expected exactly 1 analyze call after disabling, got %d", got)
	}

	_ = svc.Stop(context.Background())
}

func TestSetEnabledFalseForceDisablesNormalization(t *testing.T) {
	repo := newMockAnalysisRepo()
	settingsRepo := &fakeSettingsRepo{settings: domain.AppSettings{LibraryAnalysisEnabled: true, NormalizationEnabled: true}}
	svc := NewAnalysisService(&mockTrackRepo{tracks: tracksOf("trk-1")}, repo, &mockAnalyzer{}, settingsRepo, slog.Default())

	if err := svc.SetEnabled(context.Background(), false); err != nil {
		t.Fatalf("SetEnabled(false): %v", err)
	}
	if settingsRepo.settings.NormalizationEnabled {
		t.Fatal("expected NormalizationEnabled forced false when disabling library analysis")
	}
	if settingsRepo.settings.LibraryAnalysisEnabled {
		t.Fatal("expected LibraryAnalysisEnabled false")
	}
	_ = svc.Stop(context.Background())
}

func TestStopWaitsForWorkersAndLeavesNoLeak(t *testing.T) {
	repo := newMockAnalysisRepo()
	svc := newTestService(repo, &mockAnalyzer{}, tracksOf("trk-1"))

	svc.wg.Add(1)
	go svc.worker(svc.runCtx)
	svc.Enqueue("trk-1", false)
	waitFor(t, time.Second, func() bool { return repo.callCount() == 1 })

	done := make(chan struct{})
	go func() {
		_ = svc.Stop(context.Background())
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("Stop did not return promptly")
	}
}
