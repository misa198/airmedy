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

// analyzerVersion is the algorithm/schema version stamped on every analysis
// result; tracks with tracks.analyzed_version < this are pending. Must be
// kept in sync with audio.AnalyzerVersion (internal/infra/audio/analyzer.go).
const analyzerVersion = 3

// moodVersion is the algorithm version for the derived mood formulas
// (energy/danceability). Bump on formula/weight changes only — independent
// of analyzerVersion (raw DSP) and app_settings.mood_derivation_version
// (corpus-percentile staleness, bumped at runtime on recompute).
const moodVersion = 1

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
	enabled      bool            // true while the worker pool is running
	runCtx       context.Context
	runCancel    context.CancelFunc

	progMu sync.Mutex
	done   int

	// moodMu guards the mood-derivation state below, kept separate from mu
	// (the queue lock) so it never contends with the hot worker-pool path.
	moodMu            sync.RWMutex
	moodPctl          mood.PercentileSet // nil until first successful load/recompute (cold start)
	moodChangeCounter int                // combined add+delete count since last percentile recompute
	moodDebounceTimer *time.Timer        // reset on every add/delete event; fires recompute after a quiet period
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
		s.startPool()
	} else {
		s.stopPool()
	}
	s.logger.Debug("analysis: library analysis enabled changed", "enabled", enabled)
	return nil
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
	workers := max(runtime.NumCPU()/2, 1)
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

// backfill enqueues every track with analyzed_version < analyzerVersion once
// at pool startup. Track-ID strings are cheap even at large library sizes (no
// need to paginate repeatedly); new imports arrive incrementally via Enqueue
// from the library listener.
func (s *AnalysisService) backfill(ctx context.Context) {
	ids, err := s.analysisRepo.ListPending(ctx, analyzerVersion, 1_000_000)
	if err != nil {
		s.logger.Warn("failed to list pending analysis for backfill", "error", err)
		return
	}
	for _, id := range ids {
		s.Enqueue(id, false)
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

	if len(pctl) > 0 {
		if feat, err := s.analysisRepo.GetFeatures(ctx, trackID); err == nil && feat != nil {
			energy, dance := mood.Derive(feat, pctl)
			if err := s.analysisRepo.UpsertMoodFeatures(ctx, trackID, energy, dance, moodVersion); err != nil {
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

// recomputePercentilesAndBump recomputes corpus percentiles, hot-swaps the
// in-memory cache, and — if the corpus is non-empty — bumps
// app_settings.mood_derivation_version so every track's mood_derived_version
// becomes stale and gets re-derived against the fresh percentiles via
// backfillMood (correctness over cost: the formulas are cheap arithmetic
// over already-decoded raw features).
func (s *AnalysisService) recomputePercentilesAndBump(ctx context.Context) {
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
		for _, id := range ids {
			feat, err := s.analysisRepo.GetFeatures(ctx, id)
			if err != nil || feat == nil {
				continue
			}
			energy, dance := mood.Derive(feat, pctl)
			if err := s.analysisRepo.UpsertMoodFeatures(ctx, id, energy, dance, currentMoodVersion); err != nil {
				s.logger.Warn("mood: failed to persist derived mood features during backfill", "track_id", id, "error", err)
			}
		}
	}
}

func (s *AnalysisService) emitProgress(state string) {
	s.progMu.Lock()
	done := s.done
	s.progMu.Unlock()

	// Always a fresh background context: this may be called after the pool's
	// own context was just cancelled (stopPool), where reusing it would make
	// CountPending fail immediately.
	pending, err := s.analysisRepo.CountPending(context.Background(), analyzerVersion)
	if err != nil {
		pending = -1 // count unknown; resolveProgress keeps the caller's state as-is
	}
	total, state := resolveProgress(pending, done, state)

	libraryTotal, libraryDone := 0, 0
	if allTracks, err := s.analysisRepo.CountAll(context.Background()); err == nil {
		libraryTotal = allTracks
		if pending >= 0 {
			libraryDone = allTracks - pending
		}
	}

	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("analysis:progress", domain.AnalysisProgress{
			Done:         done,
			Total:        total,
			State:        state,
			LibraryDone:  libraryDone,
			LibraryTotal: libraryTotal,
		})
	}
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
