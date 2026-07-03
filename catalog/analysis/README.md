# Library Analysis

## Summary

Background pipeline that decodes each track once, runs an ffmpeg `libavfilter`
graph (`ebur128,aspectralstats,astats`) plus aubio tempo detection in-process via
cgo, and stores the result (loudness, dynamics, spectral features, tempo) in
`track_features`. This is the sole data source for [Volume Normalization](../normalization/README.md).
`energy`/`danceability` are now derived from these raw features (rule-based, no ML) and consumed by
[Smart Playlists](../smart-playlists/README.md); `valence` remains reserved-but-unbuilt pending Essentia ML work.

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
| `internal/infra/sqlite/analysis_repository.go` | `track_features` CRUD, pending-count/list, `MarkFailed` |
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
Workers drain `boostQueue` first. Concurrency is governed by an `active`/`activeLimit`
counter (not a bool): `startPool()` sets `workersTotal = activeLimit = max(NumCPU()/2, 1)`.
`SetThrottled(true)` (driven by `PlayerService.AddStatusListener` — playback active)
lowers `activeLimit` via `throttledLimit(workersTotal)` instead of stopping dequeue
outright:
- `numCPU() <= 4` (weak machines): `activeLimit = 0` — full pause, the original
  protective behavior, in-flight work still finishes.
- `numCPU() > 4`: `activeLimit = max(workersTotal/2, 1)` — a reduced pool keeps
  analyzing in the background alongside playback; only fully paused if a track
  finishes and no slot is free.

`numCPU` is a package var aliasing `runtime.NumCPU`, overridable in tests.
`emitProgress` reports `state = paused` only when the resulting `activeLimit == 0`;
otherwise it stays `analyzing` even while throttled, since work is still progressing.

`emitProgress` delegates the `(total, state)` computation to the pure `resolveProgress(pending, done, state)`.
`state` only resolves to `done` when `pending == 0` — **not** when `total == 0` (a prior
bug): `total = pending + done`, and `done` stays nonzero forever once any track has
finished, so a `total == 0` check could never fire again after the pool's first
completed track. That bug meant the pool never naturally reported `done` once fully
caught up (short of the explicit `stopPool()` call) — the next `emitProgress` call
from any source (e.g. `SetThrottled` on the next play/pause) re-asserted `analyzing`,
which combined with the frontend's `analysisState !== 'done'` bar condition made the
finished-analysis progress line reappear on pressing Play after it had correctly
disappeared. `pending < 0` is the sentinel for "CountPending failed" — `total` falls
back to `done` alone and `state` is left as the caller passed it, since completion
can't be confirmed without a real pending count.

Fixing `pending == 0` to be reachable surfaced a second, previously-invisible gap: a
track whose `Analyze()` call errors permanently (corrupt file, unsupported codec)
was never marked in any way — `tracks.analyzed_version` only gets bumped inside
`UpsertFeatures`, which the failure path never reaches. That track counts as
pending forever, so `pending` can never reach 0 and the UI would get stuck at e.g.
"Analyzing 12/13 (92%)" permanently. Fixed via `AnalysisRepository.MarkFailed(ctx,
trackID, currentVersion)` — bumps `analyzed_version` alone, no `track_features`
row, so `GetFeatures` still returns nil for it (Normalization safely treats it as
unanalyzed, gain 0) while `CountPending`/`ListPending` correctly stop counting it as
pending. `analyzeOne` calls this from the `Analyze()` error branch (not the
`ctx.Err() != nil` shutdown branch, and not `s.done++` — a failed track is neither
pending nor counted in `done`, so `total` undercounts by the failed-track count and
100% is reached once every *attemptable* track is resolved).
Dedup via `queued`/`inFlight` maps: re-enqueuing an in-flight track is a no-op;
re-enqueuing a queued track with `priority=true` promotes it to `boostQueue`.

`Dequeue(trackIDs []string)` drops IDs from both `boostQueue` and `normalQueue`
(and the `queued` dedup map) without touching `inFlight` — an in-flight track's
`Analyze()` call is left to finish rather than cancelled mid-decode. Wired to
library track deletion (see Central Wiring below) so a folder/track removal
that races a running analysis pass — most visibly right after a large import,
while the pool is still backfilling hundreds of tracks — doesn't keep
analyzing (and writing `UpsertFeatures` for) tracks that are being deleted out
from under it, which otherwise piles unnecessary DB writes onto the same
SQLite writer the deletion itself needs.

## Central Wiring (`internal/app/module.go`)

To avoid a `library↔analysis` or `player↔analysis` import cycle, all cross-package
listeners are wired in one `fx.Invoke` block:

```go
lib.AddAnalysisListener(func(trackID string) { analysisSvc.Enqueue(trackID, false) })
lib.AddTrackDeletedListener(func(trackIDs []string) { analysisSvc.Dequeue(trackIDs) })
playerSvc.AddStatusListener(func(status domain.PlayerStatus) {
    analysisSvc.SetThrottled(status.PlaybackState == domain.PlaybackStatePlaying)
})
playerSvc.AddTrackLoadListener(func(track *domain.TrackDTO) {
    analysisSvc.Enqueue(track.ID, true) // on-play priority boost
})
```

`AddTrackDeletedListener` (on `LibraryService`) fires with the IDs of tracks
just removed — from `RemoveWatchedFolder` (before the search-index/DB delete
loop, so the pool stops competing for the DB write as early as possible) and
from the fsnotify Remove/Rename handler's single-file and directory-removal
branches (after `deletedIDs` is finalized, alongside the existing
`library:track-deleted` event emission).

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
(shown while `libraryAnalysisEnabled` and `analysisState !== 'done'`, i.e. for both
`analyzing` and `paused` — kept visible across play/pause on capable machines instead
of flickering out every throttle change).
State lives in `frontend/src/stores/app.ts` (`libraryAnalysisEnabled`); the
`updateLibraryAnalysisEnabled` action calls `AnalysisService.SetLibraryAnalysisEnabled`
and optimistically clears local `normalizationEnabled` when disabling, mirroring the
backend cross-toggle so the Normalization switch visually locks immediately rather
than waiting on a refetch. See `catalog/normalization/README.md` for the dependent UI.
