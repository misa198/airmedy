# Library Analysis

## Summary

Background pipeline that decodes each track once, runs an ffmpeg `libavfilter`
graph (`ebur128,aspectralstats,astats`) plus aubio tempo detection in-process via
cgo, and stores the result (loudness, dynamics, spectral features, tempo) in
`track_features`. This is the sole data source for [Volume Normalization](../normalization/README.md);
mood-derived smart-mix is reserved-but-unbuilt on the same rows.

The pipeline is **opt-in** (`AppSettings.LibraryAnalysisEnabled`, default `false`):
the worker pool exists only while enabled. Disabling it also force-disables
Normalization, since Normalization has no other source of loudness data.

## Files

| File | Purpose |
| ---- | ------- |
| `internal/app/analysis/analysis_service.go` | Worker pool, queue, enable/disable lifecycle |
| `internal/app/analysis/module.go` | FX module |
| `internal/infra/wails/analysis_service.go` | Wails binding (`SetLibraryAnalysisEnabled`) |
| `internal/infra/audio/analyzer.go` + `ffmpeg_analyzer.h` | cgo adapter implementing `domain.LoudnessAnalyzer` |
| `internal/infra/sqlite/analysis_repository.go` | `track_features` CRUD, pending-count/list |
| `internal/domain/audio.go` | `LoudnessAnalyzer` interface |
| `internal/domain/models.go` | `TrackFeatures`, `AppSettings.LibraryAnalysisEnabled`, `AnalysisProgress` |
| `internal/infra/sqlite/settings_repository.go` | Persistence for `library_analysis_enabled` |
| `internal/app/module.go` | Central wiring: import → enqueue, playback → throttle, on-play → boost |

## Settings (`AppSettings`)

| Field | Type | Default | Meaning |
| ----- | ---- | ------- | ------- |
| `LibraryAnalysisEnabled` | bool | `false` | Master on/off for the worker pool |

## Enable/Disable Lifecycle (`AnalysisService`)

- `Start(ctx)` (fx `OnStart`, called once): reads `LibraryAnalysisEnabled` from
  `domain.SettingsRepository` and calls `startPool()` if true. Otherwise the pool
  stays off until `SetEnabled(ctx, true)`.
- `SetEnabled(ctx, enabled bool) error`: persists the toggle, and **if disabling,
  also force-disables `NormalizationEnabled`** in the same settings write (cross-
  toggle — Normalization depends entirely on data this pipeline produces). Then
  starts or stops the pool to match.
- `startPool()`: spawns `max(NumCPU()/2, 1)` worker goroutines on a fresh
  `context.WithCancel`, then kicks off a one-time backfill (`ListPending` →
  `Enqueue` every pending track ID). Idempotent — no-op if already running.
- `stopPool()`: cancels the pool's context (workers exit `cond.Wait()` loops on
  next wake — cancel happens *before* the broadcast to avoid a race where a
  worker re-enters `Wait()` before observing cancellation), waits for in-flight
  analysis to finish (`wg.Wait()`), then **drops everything still queued**
  (not yet started) — disabling means disabled, not "finish the backlog". Dropped
  tracks are simply rediscovered by the next `startPool()`'s backfill.
- `Enqueue`/`BoostPriority`: no-op while the pool is disabled, so imports don't
  build an unbounded in-memory queue with nothing consuming it.
- `Stop(ctx)` (fx `OnStop`): unconditionally `stopPool()`.

## Queue / Worker Mechanics

Two queues (`boostQueue`, `normalQueue`) guarded by one `sync.Mutex` + `sync.Cond`.
Workers drain `boostQueue` first. `SetThrottled(true)` (driven by
`PlayerService.AddStatusListener` — playback active) pauses dequeue without
cancelling in-flight work, protecting the audio thread from CPU contention.
Dedup via `queued`/`inFlight` maps: re-enqueuing an in-flight track is a no-op;
re-enqueuing a queued track with `priority=true` promotes it to `boostQueue`.

## Central Wiring (`internal/app/module.go`)

To avoid a `library↔analysis` or `player↔analysis` import cycle, all cross-package
listeners are wired in one `fx.Invoke` block:

```go
lib.AddAnalysisListener(func(trackID string) { analysisSvc.Enqueue(trackID, false) })
playerSvc.AddStatusListener(func(status domain.PlayerStatus) {
    analysisSvc.SetThrottled(status.PlaybackState == domain.PlaybackStatePlaying)
})
playerSvc.AddTrackLoadListener(func(track *domain.TrackDTO) {
    analysisSvc.Enqueue(track.ID, true) // on-play priority boost
})
```

The on-play boost fires on **every** track load, whether or not that track was
analyzed already — `Enqueue`'s dedup only tracks in-flight/queued state, not
whether analysis is up to date. So `analyzeOne` (the thing a worker actually
runs) re-checks `AnalysisRepository.GetFeatures` first and returns early if
`existing.AnalyzerVersion >= analyzerVersion`, before paying for the ffmpeg pass.
Without this guard, playing any track — even a fully-analyzed library — would
re-run the whole analysis pipeline on it every time.

## Wails-Exposed Methods (`AnalysisService`)

```go
SetLibraryAnalysisEnabled(enabled bool) error
```

Calls `AnalysisService.SetEnabled`, then `PlayerService.ReapplyNormalization()` —
needed either way: enabling doesn't change current playback, but disabling may
have just force-disabled Normalization, so the currently-playing track's preamp
gain must be cleared immediately.

## Events Emitted

| Event | Payload | When |
| ----- | ------- | ---- |
| `analysis:progress` | `{ done, total, state }`, `state ∈ analyzing\|paused\|done` | On enable/disable, throttle change, and after each track finishes |

## Frontend

Settings → Playback tab, "Library Analysis" section directly above "Volume
Normalization" (`frontend/src/components/settings/PlaybackSettings.vue`): a single
enable switch with description text, plus the "Analyzing N/M (x%)" progress line
(only shown while `libraryAnalysisEnabled` and `analysisState === 'analyzing'`).
State lives in `frontend/src/stores/app.ts` (`libraryAnalysisEnabled`); the
`updateLibraryAnalysisEnabled` action calls `AnalysisService.SetLibraryAnalysisEnabled`
and optimistically clears local `normalizationEnabled` when disabling, mirroring the
backend cross-toggle so the Normalization switch visually locks immediately rather
than waiting on a refetch. See `catalog/normalization/README.md` for the dependent UI.
