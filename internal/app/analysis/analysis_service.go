package analysis

import (
	"context"
	"log/slog"
	"runtime"
	"sync"
	"time"

	"airmedy/internal/app/analysis/mood"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

// analyzerVersion is retained for legacy repositories. New raw-analysis
// freshness is tracked independently by source below.
const analyzerVersion = 4

const (
	ffmpegAnalysisVersion = 1
	aubioAnalysisVersion  = 1
)

var requiredAnalysisComponents = map[domain.AnalysisComponents]int{
	domain.AnalysisComponentFFmpeg: ffmpegAnalysisVersion,
	domain.AnalysisComponentAubio:  aubioAnalysisVersion,
}

// moodVersion is the algorithm version for the derived mood formulas
// (energy/danceability/brightness). Bump on formula/weight changes only — independent
// of analyzerVersion (raw DSP) and app_settings.mood_derivation_version
// (corpus-percentile staleness, bumped at runtime on recompute).
const moodVersion = 2

// percentileRecomputeBatchSize triggers a corpus percentile recompute once
// this many tracks have been added (successfully analyzed) or removed,
// combined, since the last recompute.
const percentileRecomputeBatchSize = 100

// percentileRecomputeDebounce triggers a corpus percentile recompute after
// this long without any further add/delete activity, even if
// percentileRecomputeBatchSize hasn't been reached — bounds staleness for
// small batches instead of waiting indefinitely for the count to fill up.
const percentileRecomputeDebounce = 30 * time.Second

// percentileStalenessThreshold triggers a corpus percentile recompute at
// AnalysisService startup if the cached stats are older than this (the app
// is an offline desktop app, not always running, so this replaces a
// nightly-cron-style scheduler that could otherwise silently never fire).
const percentileStalenessThreshold = 24 * time.Hour

// analyzeTimeout bounds a single track's decode/analysis pass. The cgo
// analyzer only polls its cancel flag once per outer decode iteration, so a
// stalled read (network mount, truncated/corrupt stream) can otherwise wedge
// a worker — and therefore stopPool's wg.Wait() — forever.
const analyzeTimeout = 3 * time.Minute

// backfillBatchSize bounds the in-memory queue while a large existing library
// is adopted after enabling analysis or upgrading an analyzer component.
const backfillBatchSize = 1_000

// stopPoolWaitTimeout bounds how long stopPool waits for in-flight workers
// before giving up and returning anyway, so a single wedged analysis (see
// analyzeTimeout) can't hang app shutdown or a disable/re-enable toggle
// indefinitely. The abandoned worker goroutine keeps running in the
// background and will exit on its own once analyzeTimeout elapses.
const stopPoolWaitTimeout = analyzeTimeout + 30*time.Second

// AnalysisService runs the background audio-analysis pipeline: a resumable,
// priority-aware worker pool that turns pending tracks into stored
// domain.TrackFeatures rows, throttled while playback is active.
//
// The pipeline is opt-in (domain.AppSettings.LibraryAnalysisEnabled, default
// off): the worker pool only exists while enabled. SetEnabled starts/stops it
// live and persists the choice; Start (fx OnStart) just resumes whatever was
// last persisted.
type AnalysisService struct {
	trackRepo    domain.TrackRepository
	analysisRepo domain.AnalysisRepository
	analyzer     domain.LoudnessAnalyzer
	settingsRepo domain.SettingsRepository
	logger       *slog.Logger

	wg sync.WaitGroup

	mu           sync.Mutex
	cond         *sync.Cond
	boostQueue   []string
	normalQueue  []string
	queued       map[string]bool // dedup: track ID currently somewhere in the queue
	inFlight     map[string]bool // dedup: track ID currently being analyzed by a worker
	active       int             // number of workers currently analyzing (< activeLimit)
	activeLimit  int             // concurrency cap; lowered (not zeroed) while playback is active
	workersTotal int             // total worker goroutines spawned by startPool
	workerCount  int             // desired worker count from settings; clamped via clampWorkerCount at startPool
	enabled      bool            // true while the worker pool is running
	runCtx       context.Context
	runCancel    context.CancelFunc

	progMu sync.Mutex
	done   int
	// libraryTotal caches CountAll (a full-table COUNT) so it isn't re-queried
	// on every per-track emitProgress during a bulk scan; refreshed at most
	// once per libraryTotalTTL. -1 = not yet populated. Guarded by progMu.
	libraryTotal   int
	libraryTotalAt time.Time

	// moodMu guards the mood-derivation state below, kept separate from mu
	// (the queue lock) so it never contends with the hot worker-pool path.
	moodMu            sync.RWMutex
	moodPctl          mood.PercentileSet // nil until first successful load/recompute (cold start)
	moodChangeCounter int                // combined add+delete count since last percentile recompute
	moodDebounceTimer *time.Timer        // reset on every add/delete event; fires recompute after a quiet period

	// recomputeMu serializes recomputePercentilesAndBump so concurrent triggers
	// (batch-size, debounce, sync-finished) can't interleave their version
	// read-modify-write or run overlapping backfills. recomputePending coalesces:
	// a trigger arriving while a recompute is in flight sets the flag so exactly
	// one more run happens afterward, against the latest corpus data.
	recomputeMu      sync.Mutex
	recomputePendMu  sync.Mutex
	recomputePending bool
}

func NewAnalysisService(
	trackRepo domain.TrackRepository,
	analysisRepo domain.AnalysisRepository,
	analyzer domain.LoudnessAnalyzer,
	settingsRepo domain.SettingsRepository,
	logger *slog.Logger,
) *AnalysisService {
	s := &AnalysisService{
		trackRepo:    trackRepo,
		analysisRepo: analysisRepo,
		analyzer:     analyzer,
		settingsRepo: settingsRepo,
		logger:       logger.With("module", "analysis"),
		queued:       make(map[string]bool),
		inFlight:     make(map[string]bool),
		libraryTotal: -1,
	}
	s.cond = sync.NewCond(&s.mu)
	return s
}

// Start reads the persisted LibraryAnalysisEnabled setting and starts the
// worker pool if it's on. The pool can be toggled live afterward via
// SetEnabled, independent of this initial read.
func (s *AnalysisService) Start(ctx context.Context) error {
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		s.logger.Warn("failed to load settings, analysis pool stays off", "error", err)
		return nil
	}

	// Warm the mood-derivation percentile cache and check staleness
	// unconditionally: this matters even if the raw-analysis pool isn't
	// running this session, so a restart against an already-analyzed
	// library still keeps corpus stats fresh.
	s.loadPercentileCache(ctx)
	s.maybeRecomputeOnStartup(ctx)

	s.mu.Lock()
	s.workerCount = clampWorkerCount(settings.LibraryAnalysisWorkerCount)
	s.mu.Unlock()

	if settings.LibraryAnalysisEnabled {
		s.startPool()
	}
	return nil
}

// loadPercentileCache populates s.moodPctl from feature_percentiles.
// Best-effort: leaves the cache as-is (nil on cold start) on error, logged.
func (s *AnalysisService) loadPercentileCache(ctx context.Context) {
	rows, err := s.analysisRepo.GetFeaturePercentiles(ctx)
	if err != nil {
		s.logger.Warn("mood: failed to load percentile cache", "error", err)
		return
	}
	if len(rows) == 0 {
		return // no corpus yet: leave the cache cold (nil) so mood derivation
		// is skipped rather than run against an empty (degenerate) percentile
		// set, which would persist a neutral 0.5 for every feature.
	}
	pctl := make(mood.PercentileSet, len(rows))
	for name, row := range rows {
		pctl[name] = mood.Percentile{P1: row.P1, P5: row.P5, P50: row.P50, P95: row.P95, P99: row.P99}
	}
	s.moodMu.Lock()
	s.moodPctl = pctl
	s.moodMu.Unlock()
}

// maybeRecomputeOnStartup triggers a corpus percentile recompute if the
// cached stats are stale (older than percentileStalenessThreshold) or don't
// exist yet at all (e.g. a fresh install pointed at an already-analyzed DB).
func (s *AnalysisService) maybeRecomputeOnStartup(ctx context.Context) {
	rows, err := s.analysisRepo.GetFeaturePercentiles(ctx)
	if err != nil {
		return
	}
	stale := len(rows) == 0
	for _, row := range rows {
		if time.Since(row.ComputedAt) > percentileStalenessThreshold {
			stale = true
			break
		}
	}
	if stale {
		go s.recomputePercentilesAndBump(context.Background())
	}
}

// Stop shuts the worker pool down (no-op if already off) and waits for any
// in-flight analysis to finish.
func (s *AnalysisService) Stop(ctx context.Context) error {
	s.stopPool()
	return nil
}

// SetEnabled persists the library-analysis toggle and starts/stops the
// worker pool to match. Disabling also force-disables Normalization, since
// it depends on data only this pipeline produces.
func (s *AnalysisService) SetEnabled(ctx context.Context, enabled bool) error {
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		return err
	}
	settings.LibraryAnalysisEnabled = enabled
	if !enabled {
		settings.NormalizationEnabled = false
	}
	if err := s.settingsRepo.Save(ctx, settings); err != nil {
		return err
	}

	if enabled {
		s.mu.Lock()
		s.workerCount = clampWorkerCount(settings.LibraryAnalysisWorkerCount)
		s.mu.Unlock()
		s.startPool()
	} else {
		s.stopPool()
	}
	s.logger.Debug("analysis: library analysis enabled changed", "enabled", enabled)
	return nil
}

// SetWorkerCount persists the desired concurrent-worker count for the
// analysis pool and applies it live. Worker goroutine count is fixed at
// pool-start, so a running pool is stopped and restarted to pick up the new
// value; a stopped pool just picks it up next time it starts.
func (s *AnalysisService) SetWorkerCount(ctx context.Context, count int) error {
	count = clampWorkerCount(count)

	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		return err
	}
	settings.LibraryAnalysisWorkerCount = count
	if err := s.settingsRepo.Save(ctx, settings); err != nil {
		return err
	}

	s.mu.Lock()
	wasEnabled := s.enabled
	s.workerCount = count
	s.mu.Unlock()

	if wasEnabled {
		s.stopPool()
		s.startPool()
	}
	s.logger.Debug("analysis: worker count changed", "count", count)
	return nil
}

// GetWorkerCount returns the currently configured worker count (clamped) and
// the maximum value the settings UI should allow.
func (s *AnalysisService) GetWorkerCount(ctx context.Context) (count, max int) {
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		return DefaultWorkerCount, MaxWorkerCount()
	}
	return clampWorkerCount(settings.LibraryAnalysisWorkerCount), MaxWorkerCount()
}

// startPool spawns the worker goroutines and kicks off the backfill scan.
// No-op if already running.
func (s *AnalysisService) startPool() {
	s.mu.Lock()
	if s.enabled {
		s.mu.Unlock()
		return
	}
	runCtx, cancel := context.WithCancel(context.Background())
	s.runCtx = runCtx
	s.runCancel = cancel
	s.enabled = true
	workers := clampWorkerCount(s.workerCount)
	s.workersTotal = workers
	s.activeLimit = workers
	s.mu.Unlock()

	for range workers {
		s.wg.Add(1)
		go s.worker(runCtx)
	}
	go s.backfill(runCtx)

	s.emitProgress(domain.AnalysisStateAnalyzing)
}

// stopPool cancels the running pool, waits for in-flight analysis to finish,
// and drops anything still queued (not yet started) — disabling means
// disabled, not "finish what's queued". Queued-but-dropped tracks are simply
// re-discovered by backfill the next time the pool starts.
func (s *AnalysisService) stopPool() {
	s.mu.Lock()
	if !s.enabled {
		s.mu.Unlock()
		return
	}
	s.enabled = false
	cancel := s.runCancel
	s.runCancel = nil
	s.runCtx = nil
	s.mu.Unlock()

	// Cancel before broadcasting so workers observe ctx.Err() != nil as soon
	// as they wake, rather than possibly re-entering cond.Wait() forever.
	if cancel != nil {
		cancel()
	}
	s.mu.Lock()
	s.cond.Broadcast()
	s.mu.Unlock()

	waitDone := make(chan struct{})
	go func() {
		s.wg.Wait()
		close(waitDone)
	}()
	select {
	case <-waitDone:
	case <-time.After(stopPoolWaitTimeout):
		s.logger.Warn("analysis: stopPool gave up waiting for in-flight workers, abandoning them", "timeout", stopPoolWaitTimeout)
	}

	s.mu.Lock()
	s.boostQueue = nil
	s.normalQueue = nil
	s.queued = make(map[string]bool)
	s.mu.Unlock()

	s.emitProgress(domain.AnalysisStateDone)
}

// backfill discovers pending tracks in bounded batches at pool startup. It
// waits for each batch to drain before fetching the next, so a large library
// does not turn into an unbounded in-memory queue. New imports still arrive
// incrementally via Enqueue from the library listener.
func (s *AnalysisService) backfill(ctx context.Context) {
	if componentRepo, ok := s.analysisRepo.(domain.ComponentAnalysisRepository); ok {
		s.backfillBatches(ctx, func() ([]string, error) {
			return componentRepo.ListPendingComponentTracks(ctx, requiredAnalysisComponents, backfillBatchSize)
		})
		return
	}
	s.backfillBatches(ctx, func() ([]string, error) {
		return s.analysisRepo.ListPending(ctx, analyzerVersion, backfillBatchSize)
	})
}

func (s *AnalysisService) backfillBatches(ctx context.Context, next func() ([]string, error)) {
	for {
		ids, err := next()
		if err != nil {
			s.logger.Warn("failed to list pending analysis for backfill", "error", err)
			return
		}
		if len(ids) == 0 {
			return
		}
		for _, id := range ids {
			s.Enqueue(id, false)
		}
		if !s.waitForQueueDrain(ctx) {
			return
		}
	}
}

func (s *AnalysisService) waitForQueueDrain(ctx context.Context) bool {
	ticker := time.NewTicker(50 * time.Millisecond)
	defer ticker.Stop()
	for {
		s.mu.Lock()
		drained := len(s.boostQueue) == 0 && len(s.normalQueue) == 0 && len(s.inFlight) == 0
		s.mu.Unlock()
		if drained {
			return true
		}
		select {
		case <-ctx.Done():
			return false
		case <-ticker.C:
		}
	}
}

// Enqueue adds a track for analysis. priority=true pushes it to the front
// (used by on-play boost); priority=false appends to the back (import +
// backfill). No-op while the pool is disabled, to avoid an unbounded queue
// building up from imports while the feature is off. Deduped: a track
// already in flight is a no-op; a track already queued is promoted to the
// front if priority=true.
func (s *AnalysisService) Enqueue(trackID string, priority bool) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if !s.enabled {
		return
	}
	if s.inFlight[trackID] {
		return // already being analyzed; let it finish
	}
	if s.queued[trackID] {
		if priority {
			s.promoteToFrontLocked(trackID)
		}
		return
	}

	s.queued[trackID] = true
	if priority {
		s.boostQueue = append([]string{trackID}, s.boostQueue...)
	} else {
		s.normalQueue = append(s.normalQueue, trackID)
	}
	s.cond.Broadcast()
}

// BoostPriority moves trackID to the front of the queue (or enqueues it at
// the front if not yet queued). Intended for the on-play hook.
func (s *AnalysisService) BoostPriority(trackID string) {
	s.Enqueue(trackID, true)
}

// Dequeue drops the given track IDs from the boost/normal queues so a
// deleted track isn't wastefully analyzed. In-flight tracks (a worker
// already decoding them) are left alone — cancelling mid-decode isn't worth
// the complexity, and analyzeOne/UpsertFeatures already no-op safely once
// the track row is gone (FK-constrained write fails harmlessly, logged).
// No-op while the pool is disabled.
func (s *AnalysisService) Dequeue(trackIDs []string) {
	if len(trackIDs) == 0 {
		return
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	if !s.enabled {
		return
	}

	remove := make(map[string]bool, len(trackIDs))
	for _, id := range trackIDs {
		remove[id] = true
		delete(s.queued, id)
	}

	filter := func(queue []string) []string {
		kept := queue[:0]
		for _, id := range queue {
			if !remove[id] {
				kept = append(kept, id)
			}
		}
		return kept
	}
	s.boostQueue = filter(s.boostQueue)
	s.normalQueue = filter(s.normalQueue)
}

// NotifyTracksDeleted bumps the delete counter that drives corpus percentile
// recompute (see percentileRecomputeEveryDeletes): deleted tracks shrink the
// corpus track_features draws from, so without this the cached percentiles
// silently drift stale until the next analysis-driven recompute or app
// restart. No-op for an empty slice.
func (s *AnalysisService) NotifyTracksDeleted(trackIDs []string) {
	if len(trackIDs) == 0 {
		return
	}

	s.notifyMoodChange(len(trackIDs))
}

func (s *AnalysisService) promoteToFrontLocked(trackID string) {
	for i, id := range s.normalQueue {
		if id == trackID {
			s.normalQueue = append(s.normalQueue[:i], s.normalQueue[i+1:]...)
			s.boostQueue = append([]string{trackID}, s.boostQueue...)
			return
		}
	}
	// Already in boostQueue or about to be picked up; nothing to do.
}

// numCPU is a var (not a direct runtime.NumCPU() call) so tests can stub it
// to exercise both the low-core and high-core throttling paths.
var numCPU = runtime.NumCPU

// DefaultWorkerCount is the concurrent-worker count applied when the user
// hasn't configured one (LibraryAnalysisWorkerCount == 0).
const DefaultWorkerCount = domain.DefaultLibraryAnalysisWorkerCount

// MaxWorkerCount returns the highest worker count the pool will honor: half
// the logical core count, at least 1. The per-track ffmpeg decode is
// embarrassingly parallel, but the aubio tempo/onset stage is serialized
// process-wide behind a single mutex (its bundled ooura FFT isn't reentrant —
// see ffmpeg_analyzer.h), so past a handful of workers the extra decoders
// don't gain throughput, they just add CPU heat and lock-convoy contention —
// which on weak / thermally-limited laptops can saturate every core and
// stall forward progress. This is the user-facing ceiling (settings slider
// bound); the user picks their own tradeoff within it.
func MaxWorkerCount() int {
	return max(numCPU()/2, 1)
}

// clampWorkerCount resolves a requested worker count to a valid value:
// unset (<= 0) falls back to DefaultWorkerCount, otherwise clamped to
// [1, MaxWorkerCount()]. Uses the stubbable numCPU so tests can exercise the
// low- and high-core paths.
func clampWorkerCount(n int) int {
	if n <= 0 {
		n = DefaultWorkerCount
	}
	return max(min(n, MaxWorkerCount()), 1)
}

// throttledLimit returns the worker concurrency cap to apply while playback
// is active. Machines with more than 4 cores have enough headroom to keep a
// reduced pool running alongside playback without audio contention; weaker
// machines fall back to a full pause (the original protective behavior).
func throttledLimit(total int) int {
	if numCPU() <= 4 {
		return 0
	}
	return max(total/2, 1)
}

// SetThrottled lowers (or restores) the worker concurrency cap while playback
// is active, to protect the audio thread from CPU contention. In-flight
// analysis finishes regardless; only the cap on newly started work changes.
func (s *AnalysisService) SetThrottled(throttled bool) {
	s.mu.Lock()
	limit := s.workersTotal
	if throttled {
		limit = throttledLimit(s.workersTotal)
	}
	s.activeLimit = limit
	s.mu.Unlock()
	s.cond.Broadcast()

	state := domain.AnalysisStateAnalyzing
	if throttled && limit == 0 {
		state = domain.AnalysisStatePaused
	}
	s.emitProgress(state)
}

// worker pulls boost-queue first, then normal-queue, blocking on s.cond when
// both are empty or the active-worker count is already at the concurrency
// cap. Exits when ctx is cancelled.
func (s *AnalysisService) worker(ctx context.Context) {
	defer s.wg.Done()

	// Background analysis must never starve the UI or the audio thread. Pin
	// this goroutine to its OS thread and drop that thread's scheduling
	// priority so the OS preempts the CPU-heavy ffmpeg/aubio decode in favour
	// of interactive work. No-op on platforms without an implementation.
	runtime.LockOSThread()
	lowerCurrentThreadPriority()

	for {
		s.mu.Lock()
		for (s.active >= s.activeLimit || (len(s.boostQueue) == 0 && len(s.normalQueue) == 0)) && ctx.Err() == nil {
			s.cond.Wait()
		}
		if ctx.Err() != nil {
			s.mu.Unlock()
			return
		}

		var trackID string
		if len(s.boostQueue) > 0 {
			trackID, s.boostQueue = s.boostQueue[0], s.boostQueue[1:]
		} else {
			trackID, s.normalQueue = s.normalQueue[0], s.normalQueue[1:]
		}
		delete(s.queued, trackID)
		s.inFlight[trackID] = true
		s.active++
		s.mu.Unlock()

		s.analyzeOne(ctx, trackID)

		s.mu.Lock()
		delete(s.inFlight, trackID)
		s.active--
		s.cond.Broadcast()
		s.mu.Unlock()
	}
}

// analyzeOne loads the track path, runs the analyzer, and writes the result.
// Per-track write -> crash-safe resumability; idempotent via UpsertFeatures.
func (s *AnalysisService) analyzeOne(ctx context.Context, trackID string) {
	track, err := s.trackRepo.GetByID(ctx, trackID)
	if err != nil || track == nil {
		s.logger.Warn("analysis: track lookup failed, skipping", "track_id", trackID, "error", err)
		return
	}
	if componentRepo, ok := s.analysisRepo.(domain.ComponentAnalysisRepository); ok {
		s.analyzeComponents(ctx, componentRepo, track)
		return
	}

	// The on-play boost hook enqueues every track it loads, whether or not it's
	// already analyzed (Enqueue only dedups in-flight/queued, not analyzed
	// state). Skip the expensive ffmpeg pass if this track already has an
	// up-to-date analyzed_version — checking that (rather than GetFeatures)
	// also covers tracks MarkFailed as permanently unanalyzable, which have no
	// features row and would otherwise be re-run on every play.
	if done, err := s.analysisRepo.IsAnalyzed(ctx, trackID, analyzerVersion); err == nil && done {
		return
	}

	analyzeCtx, cancel := context.WithTimeout(ctx, analyzeTimeout)
	defer cancel()
	feat, err := s.analyzer.Analyze(analyzeCtx, track.Path)
	if err != nil {
		if ctx.Err() != nil {
			return // shutting down
		}
		s.logger.Warn("analysis failed, marking track as done to unblock completion", "track_id", trackID, "path", track.Path, "error", err)
		// Corrupt file, unsupported codec, etc — this track will never
		// succeed on retry. Without bumping analyzed_version here it stays
		// "pending" forever: CountPending never reaches 0, so the pool can
		// never naturally report "done". No features row is written, so
		// GetFeatures still returns nil and Normalization safely no-ops for it.
		if err := s.analysisRepo.MarkFailed(ctx, trackID, analyzerVersion); err != nil {
			s.logger.Error("failed to mark track analysis as failed", "track_id", trackID, "error", err)
			return
		}
		s.emitProgress(domain.AnalysisStateAnalyzing)
		return
	}
	feat.TrackID = trackID
	feat.AnalyzerVersion = analyzerVersion

	if err := s.analysisRepo.UpsertFeatures(ctx, feat); err != nil {
		s.logger.Error("failed to persist analysis features", "track_id", trackID, "error", err)
		return
	}

	s.progMu.Lock()
	s.done++
	s.progMu.Unlock()

	s.emitProgress(domain.AnalysisStateAnalyzing)

	s.driveMoodDerivation(ctx, trackID)
}

func (s *AnalysisService) analyzeComponents(ctx context.Context, repo domain.ComponentAnalysisRepository, track *domain.TrackDTO) {
	components, _, err := repo.ComponentStatus(ctx, track.ID, requiredAnalysisComponents)
	if err != nil || components == 0 {
		if err != nil {
			s.logger.Warn("failed to check pending analysis components", "track_id", track.ID, "error", err)
		}
		return
	}

	analyzeCtx, cancel := context.WithTimeout(ctx, analyzeTimeout)
	defer cancel()
	var feat *domain.TrackFeatures
	if analyzer, ok := s.analyzer.(domain.ComponentAnalyzer); ok {
		feat, err = analyzer.AnalyzeComponents(analyzeCtx, track.Path, components)
	} else {
		feat, err = s.analyzer.Analyze(analyzeCtx, track.Path)
	}
	if err != nil {
		if ctx.Err() != nil {
			return
		}
		s.logger.Warn("analysis component failed, marking resolved", "track_id", track.ID, "components", components, "error", err)
		if markErr := repo.MarkComponentsFailed(ctx, track.ID, components, requiredAnalysisComponents); markErr != nil {
			s.logger.Error("failed to mark analysis components failed", "track_id", track.ID, "error", markErr)
		}
		s.emitProgress(domain.AnalysisStateAnalyzing)
		return
	}
	feat.TrackID = track.ID
	feat.AnalyzedAt = time.Now().UTC()
	if err := repo.UpsertComponentFeatures(ctx, feat, components, requiredAnalysisComponents); err != nil {
		s.logger.Error("failed to persist analysis components", "track_id", track.ID, "error", err)
		return
	}
	s.progMu.Lock()
	s.done++
	s.progMu.Unlock()
	s.emitProgress(domain.AnalysisStateAnalyzing)
	if _, complete, err := repo.ComponentStatus(ctx, track.ID, requiredAnalysisComponents); err == nil && complete {
		s.driveMoodDerivation(ctx, track.ID)
	}
}

// driveMoodDerivation attempts mood derivation for a track immediately after
// its raw features were written, using whatever percentile cache is
// currently loaded. If the cache is cold (true cold start: no corpus yet),
// this is a no-op — the first recomputePercentilesAndBump run will backfill
// it via backfillMood once a corpus exists. Also bumps the periodic-recompute
// counter, firing a recompute every percentileRecomputeEvery successful
// raw analyses.
func (s *AnalysisService) driveMoodDerivation(ctx context.Context, trackID string) {
	s.moodMu.RLock()
	pctl := s.moodPctl
	s.moodMu.RUnlock()

	// nil pctl means true cold start (no cache loaded yet) — skip. A non-nil
	// but empty/partial set is still a valid warm cache: Normalize/Derive
	// safely fall back to neutral 0.5 for any missing feature, so derive.
	if pctl != nil {
		if feat, err := s.analysisRepo.GetFeatures(ctx, trackID); err == nil && feat != nil {
			energy, dance, brightness := mood.Derive(feat, pctl)
			if err := s.analysisRepo.UpsertMoodFeatures(ctx, trackID, energy, dance, brightness, moodVersion); err != nil {
				s.logger.Warn("mood: failed to persist derived mood features", "track_id", trackID, "error", err)
			}
		}
	}

	s.notifyMoodChange(1)
}

// notifyMoodChange bumps the combined add/delete counter by n and drives the
// batch-or-debounce percentile recompute trigger: fires immediately once the
// counter reaches percentileRecomputeBatchSize, otherwise (re)starts a
// percentileRecomputeDebounce timer so a quiet period after a smaller batch
// still eventually recomputes instead of waiting indefinitely for the count
// to fill up.
func (s *AnalysisService) notifyMoodChange(n int) {
	s.moodMu.Lock()
	s.moodChangeCounter += n
	if s.moodChangeCounter >= percentileRecomputeBatchSize {
		s.moodChangeCounter = 0
		if s.moodDebounceTimer != nil {
			s.moodDebounceTimer.Stop()
		}
		s.moodMu.Unlock()
		go s.recomputePercentilesAndBump(context.Background())
		return
	}
	if s.moodDebounceTimer != nil {
		s.moodDebounceTimer.Stop()
	}
	s.moodDebounceTimer = time.AfterFunc(percentileRecomputeDebounce, s.fireDebouncedRecompute)
	s.moodMu.Unlock()
}

// fireDebouncedRecompute runs on the debounce timer's own goroutine once
// percentileRecomputeDebounce has elapsed with no further add/delete events.
// Guards against firing a second recompute if the batch-size branch above
// already reset the counter (Timer.Stop can race with an in-flight fire).
func (s *AnalysisService) fireDebouncedRecompute() {
	s.moodMu.Lock()
	if s.moodChangeCounter == 0 {
		s.moodMu.Unlock()
		return
	}
	s.moodChangeCounter = 0
	s.moodMu.Unlock()
	s.recomputePercentilesAndBump(context.Background())
}

// TriggerPercentileRecompute kicks off an out-of-band corpus percentile
// recompute (and mood backfill), fire-and-forget. Meant to be wired to
// library-side signals like "sync finished" so a fresh import's mood scores
// don't sit unpopulated until the batch-size/debounce triggers catch up.
func (s *AnalysisService) TriggerPercentileRecompute() {
	go func() {
		defer func() {
			if r := recover(); r != nil {
				s.logger.Error("mood: percentile recompute panicked", "panic", r)
			}
		}()
		s.recomputePercentilesAndBump(context.Background())
	}()
}

// recomputePercentilesAndBump recomputes corpus percentiles, hot-swaps the
// in-memory cache, and — if the corpus is non-empty — bumps
// app_settings.mood_derivation_version so every track's mood_derived_version
// becomes stale and gets re-derived against the fresh percentiles via
// backfillMood (correctness over cost: the formulas are cheap arithmetic
// over already-decoded raw features).
func (s *AnalysisService) recomputePercentilesAndBump(ctx context.Context) {
	// Serialize: if a recompute is already running, record that another was
	// requested (so it re-runs once against the fresher data) and return
	// instead of racing the version bump / backfill.
	if !s.recomputeMu.TryLock() {
		s.recomputePendMu.Lock()
		s.recomputePending = true
		s.recomputePendMu.Unlock()
		return
	}
	defer s.recomputeMu.Unlock()

	for {
		s.recomputePendMu.Lock()
		s.recomputePending = false
		s.recomputePendMu.Unlock()

		s.runRecomputeOnce(ctx)

		s.recomputePendMu.Lock()
		again := s.recomputePending
		s.recomputePendMu.Unlock()
		if !again {
			return
		}
	}
}

func (s *AnalysisService) runRecomputeOnce(ctx context.Context) {
	pctl, sampleCount, err := mood.RecomputeCorpusPercentiles(ctx, s.analysisRepo)
	if err != nil {
		s.logger.Warn("mood: percentile recompute failed", "error", err)
		return
	}
	if sampleCount == 0 {
		return // empty corpus; nothing to cache or bump yet
	}

	s.moodMu.Lock()
	s.moodPctl = pctl
	s.moodMu.Unlock()

	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		s.logger.Warn("mood: failed to load settings for version bump", "error", err)
		return
	}
	settings.MoodDerivationVersion++
	if err := s.settingsRepo.Save(ctx, settings); err != nil {
		s.logger.Warn("mood: failed to persist mood_derivation_version bump", "error", err)
		return
	}

	s.backfillMood(context.Background(), settings.MoodDerivationVersion)
}

// backfillMood re-derives mood for every track whose mood_derived_version is
// behind currentMoodVersion, in batches, until none remain. Deliberately not
// routed through the boost/normal-queue worker pool: that pool's
// concurrency/throttle knobs are tuned for the expensive ffmpeg decode pass,
// while mood re-derivation is cheap in-memory arithmetic over already-decoded
// features plus one UPDATE per track, so a plain sequential loop suffices.
func (s *AnalysisService) backfillMood(ctx context.Context, currentMoodVersion int) {
	const batchSize = 500
	for {
		ids, err := s.analysisRepo.ListMoodPending(ctx, currentMoodVersion, batchSize)
		if err != nil {
			s.logger.Warn("mood: failed to list pending mood derivation", "error", err)
			return
		}
		if len(ids) == 0 {
			return
		}
		s.moodMu.RLock()
		pctl := s.moodPctl
		s.moodMu.RUnlock()
		if len(pctl) == 0 {
			return // cache unexpectedly empty/cleared; next recompute cycle will retry
		}
		progressed := 0
		for _, id := range ids {
			feat, err := s.analysisRepo.GetFeatures(ctx, id)
			if err != nil || feat == nil {
				continue
			}
			energy, dance, brightness := mood.Derive(feat, pctl)
			if err := s.analysisRepo.UpsertMoodFeatures(ctx, id, energy, dance, brightness, currentMoodVersion); err != nil {
				s.logger.Warn("mood: failed to persist derived mood features during backfill", "track_id", id, "error", err)
				continue
			}
			progressed++
		}
		// UpsertMoodFeatures is what advances a track past ListMoodPending (it
		// bumps mood_derived_version). If a whole batch made zero progress, the
		// same pending IDs would be returned every iteration — a persistently
		// failing track (missing features, DB error) would spin this loop
		// forever. Bail instead; the next recompute cycle retries.
		if progressed == 0 {
			s.logger.Warn("mood: backfill made no progress, aborting to avoid spin", "pending", len(ids))
			return
		}
	}
}

// GetProgress returns a fresh snapshot of analysis progress, for the
// frontend to fetch once on mount rather than starting from a zero-valued
// state and waiting on the next "analysis:progress" event (which, before the
// first fetch, made the progress bar flash 100% while a sync was still
// under way).
func (s *AnalysisService) GetProgress() domain.AnalysisProgress {
	s.mu.Lock()
	limit := s.activeLimit
	enabled := s.enabled
	s.mu.Unlock()

	state := domain.AnalysisStateAnalyzing
	if !enabled || limit == 0 {
		state = domain.AnalysisStatePaused
	}
	return s.currentProgress(state)
}

func (s *AnalysisService) emitProgress(state string) {
	progress := s.currentProgress(state)
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("analysis:progress", progress)
	}
}

func (s *AnalysisService) currentProgress(state string) domain.AnalysisProgress {
	s.progMu.Lock()
	done := s.done
	s.progMu.Unlock()

	// Always a fresh background context: this may be called after the pool's
	// own context was just cancelled (stopPool), where reusing it would make
	// CountPending fail immediately.
	pending := 0
	var err error
	if componentRepo, ok := s.analysisRepo.(domain.ComponentAnalysisRepository); ok {
		pending, err = componentRepo.CountPendingComponentTracks(context.Background(), requiredAnalysisComponents)
	} else {
		pending, err = s.analysisRepo.CountPending(context.Background(), analyzerVersion)
	}
	if err != nil {
		pending = -1 // count unknown; resolveProgress keeps the caller's state as-is
	}
	total, state := resolveProgress(pending, done, state)

	libraryTotal, libraryDone := s.libraryTotalCached(), 0
	if libraryTotal >= 0 && pending >= 0 {
		libraryDone = libraryTotal - pending
	}
	if libraryTotal < 0 {
		libraryTotal = 0
	}

	return domain.AnalysisProgress{
		Done:         done,
		Total:        total,
		State:        state,
		LibraryDone:  libraryDone,
		LibraryTotal: libraryTotal,
	}
}

// libraryTotalTTL bounds how stale the cached CountAll may be. emitProgress
// fires per analyzed track, so caching keeps a bulk scan from issuing a
// full-table COUNT(*) per row; a progress bar tolerates a few seconds of lag
// in the library total.
const libraryTotalTTL = 3 * time.Second

// libraryTotalCached returns the total track count, re-querying CountAll only
// when the cache is empty or older than libraryTotalTTL. Returns -1 if the
// count has never been obtained (query failed and no prior value).
func (s *AnalysisService) libraryTotalCached() int {
	s.progMu.Lock()
	cached, at := s.libraryTotal, s.libraryTotalAt
	s.progMu.Unlock()

	if cached >= 0 && time.Since(at) < libraryTotalTTL {
		return cached
	}

	allTracks, err := s.analysisRepo.CountAll(context.Background())
	if err != nil {
		return cached // keep last known value (may be -1 if never populated)
	}

	s.progMu.Lock()
	s.libraryTotal = allTracks
	s.libraryTotalAt = time.Now()
	s.progMu.Unlock()
	return allTracks
}

// resolveProgress derives the (total, state) pair reported to the frontend.
// pending is the count of not-yet-analyzed tracks, or -1 if CountPending
// failed (total then falls back to just done, and state is left as the
// caller passed it — completion can't be confirmed without a pending count).
// Completion (state=done) is driven by pending==0, not total==0: total
// includes done, so it's only ever 0 before the very first track finishes —
// checking it instead of pending would mean state never naturally reaches
// "done" once analysis has processed at least one track.
func resolveProgress(pending, done int, state string) (total int, resolvedState string) {
	if pending < 0 {
		return done, state
	}
	total = pending + done
	resolvedState = state
	if pending == 0 {
		resolvedState = domain.AnalysisStateDone
	}
	return total, resolvedState
}
