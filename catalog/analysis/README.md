# Library Analysis

## Summary

Background pipeline that decodes each track once, runs an ffmpeg `libavfilter`
graph (`ebur128,aspectralstats,astats`) plus aubio tempo detection in-process
via cgo, and stores the result (loudness, dynamics, spectral features, tempo,
onset variance) in `track_features`. This is the sole data source for
[Volume Normalization](../normalization/README.md).
`energy`/`danceability`/`brightness` are derived from these raw features (rule-based, no ML,
normalized against corpus-wide percentiles — see Mood Derivation below) and
consumed by Mood Radio (see below).

Raw-source freshness is stored per track in `track_analysis_components`: the
FFmpeg filter graph and aubio rhythm detector each have an independent version.
A version bump queues only its stale component; when both are stale they still
share one decode. The current component versions start at `ffmpeg@1` and
`aubio@1`. Migration 000051 adopts only legacy `analyzed_version >= 4` data,
so older results are deliberately re-analyzed.

`tracks.analysis_pending_mask` materializes unresolved source work (FFmpeg =
`1`, aubio = `2`) and has a partial index for non-zero rows. Progress counts
and backfill queries use this mask, so they scale with pending work instead of
scanning the full library. Any future source-version bump must include a
migration that ORs its component bit into affected tracks' masks.
Backfill reads pending tracks in stable `created_at, id` order through a
matching partial index.

The pipeline is **opt-in** (`AppSettings.LibraryAnalysisEnabled`, default `false`):
the worker pool exists only while enabled. Disabling it also force-disables
Normalization, since Normalization has no other source of loudness data.

## Aubio FFT Backend

The tempo/onset stage now vendors **FFTW3F** (`libfftw3f.a`) and builds aubio
with `--enable-fftw3f` via a vendored `pkg-config` manifest under
`internal/infra/audio/fftw3_libs/pkgconfig/<platform>/<arch>/fftw3f.pc`.
This keeps aubio on its single-precision ABI (`smpl_t=float`) while avoiding
the old Ooura fallback that forced process-wide serialization.

`ffmpeg_analyzer.h` no longer wraps aubio `new_/do_/del_` calls in an
application-global mutex. Concurrency is now limited only by the outer analysis
worker pool and aubio's own internal FFTW plan guards.

## Data Flow

```mermaid
flowchart TB
    IMP["Track imported / played"] --> ENQ["Analysis queue<br/>(boost + normal)"]
    ENQ --> POOL["Worker pool<br/>decode once → ffmpeg + aubio"]
    POOL --> RAW[("track_features<br/><i>raw: loudness, spectral, tempo</i>")]

    RAW --> NORM["Volume Normalization<br/>(preamp gain)"]

    RAW --> PCT["Recompute corpus percentiles"]
    PCT --> PCTBL[("feature_percentiles<br/><i>p1/p5/p50/p95/p99 per feature</i>")]

    RAW --> MOOD["Mood derivation<br/>energy / danceability / brightness"]
    PCTBL --> MOOD
    MOOD --> MOODF[("track_features<br/><i>mood: energy, danceability, brightness</i>")]

    MOODF --> SIM["FindSimilar<br/>(weighted-euclidean)"]
    SIM --> RADIO["Mood Radio queue<br/>seed + auto-refill"]
```

Raw features feed two independent consumers: **Normalization** (loudness → gain)
and **Mood** (all features → energy/danceability/brightness, but only relative to the
corpus-wide percentile distribution). Mood then backs **Mood Radio** similarity.

## Files

| File | Purpose |
| ---- | ------- |
| `internal/app/analysis/analysis_service.go` | Worker pool, queue, enable/disable lifecycle, mood-derivation trigger |
| `internal/app/analysis/module.go` | FX module |
| `internal/app/analysis/mood/` | Mood formulas, percentile normalization, corpus percentile recompute |
| `internal/infra/wails/analysis_service.go` | Wails binding (`SetLibraryAnalysisEnabled`, `GetWorkerCountInfo`, `SetWorkerCount`, `GetProgress`) |
| `internal/infra/wails/mood_radio_service.go` | Wails binding (`SeedMoodRadio`) |
| `internal/infra/audio/analyzer.go` + `ffmpeg_analyzer.h` | cgo adapter implementing `domain.LoudnessAnalyzer` |
| `scripts/build-fftw3-*.sh` + `scripts/build-aubio-*.sh` | Vendored FFTW3F/aubio builders for macOS, Linux, Windows; Aubio `0.4.9` source is fetched from its SHA-256-pinned PyPI source distribution because GitHub's generated tag archive omits bundled Waf files |
| `internal/infra/sqlite/analysis_repository.go` | `track_features` CRUD, pending-count/list, `MarkFailed`, percentile table CRUD, mood-pending list |
| `internal/infra/sqlite/track_query_repository.go` | `FindSimilar` — weighted-euclidean nearest-neighbor query over energy/danceability/brightness/tempo, backs Mood Radio |
| `internal/domain/audio.go` | `LoudnessAnalyzer` interface |
| `internal/domain/models.go` | `TrackFeatures`, `FeaturePercentileRow`, `AppSettings.LibraryAnalysisEnabled`/`LibraryAnalysisWorkerCount`/`MoodDerivationVersion`, `AnalysisProgress` |
| `internal/domain/repositories.go` | `AnalysisRepository` (percentile/mood methods), `TrackQueryRepository` |
| `internal/infra/sqlite/settings_repository.go` | Persistence for `library_analysis_enabled`, `library_analysis_worker_count`, `mood_derivation_version` |
| `internal/app/module.go` | Central wiring: import → enqueue, playback → throttle, on-play → boost, delete → mood-change notify |
| `frontend/src/stores/moodRadio.ts` | Mood Radio queue-seeding/auto-refill store |

## Settings (`AppSettings`)

| Field | Type | Default | Meaning |
| ----- | ---- | ------- | ------- |
| `LibraryAnalysisEnabled` | bool | `false` | Master on/off for the worker pool |
| `LibraryAnalysisWorkerCount` | int | `2` | Desired concurrent decode-worker count; `0` falls back to the default and runtime clamps it to `[1, numCPU/2]` |

## Enable/Disable Lifecycle (`AnalysisService`)

- `Start(ctx)` (fx `OnStart`, called once): reads `LibraryAnalysisEnabled` and
  `LibraryAnalysisWorkerCount` from `domain.SettingsRepository`, warms the
  in-memory worker-count setting, and calls `startPool()` if true. Otherwise the
  pool stays off until `SetEnabled(ctx, true)`.
- `SetEnabled(ctx, enabled bool) error`: persists the toggle, and **if disabling,
  also force-disables `NormalizationEnabled`** in the same settings write (cross-
  toggle — Normalization depends entirely on data this pipeline produces). Then
  starts or stops the pool to match.
- `SetWorkerCount(ctx, count) error`: persists the desired concurrent-worker
  count, clamps it, and applies it live. Because the worker goroutine count is
  fixed at pool start, a running pool is stopped and restarted to pick up the
  new size; a stopped pool just uses it next time it starts.
- `GetWorkerCount(ctx) (count, max int)`: returns the configured worker count
  after defaulting/clamping, plus the UI ceiling (`numCPU/2`, minimum 1).
- `startPool()`: spawns `clampWorkerCount(workerCount)` worker goroutines on a
  fresh `context.WithCancel`, then kicks off a bounded backfill: 1,000 pending
  IDs are enqueued at a time and the batch drains before the next is fetched.
  This bounds queue memory for large libraries. Idempotent — no-op if already running.
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
counter (not a bool): `startPool()` sets `workersTotal = activeLimit = clampWorkerCount(workerCount)`.
`SetThrottled(true)` (driven by `PlayerService.AddStatusListener` — playback active)
lowers `activeLimit` via `throttledLimit(workersTotal)` instead of stopping dequeue
outright:
- `numCPU() <= 4`: `activeLimit = 0` — full pause; in-flight work still finishes.
- `numCPU() > 4`: `activeLimit = max(workersTotal/2, 1)` — a reduced pool keeps
  analyzing in the background alongside playback; fully pauses only when a track
  finishes and no slot is free.

`DefaultWorkerCount` is `domain.DefaultLibraryAnalysisWorkerCount` (`2`).
`MaxWorkerCount()` returns `max(numCPU()/2, 1)`, which is both the runtime cap
and the settings slider ceiling. `clampWorkerCount` resolves a requested value:
`<= 0` falls back to the default, then clamps the result to `[1, MaxWorkerCount()]`.

`numCPU` is a package var aliasing `runtime.NumCPU`, overridable in tests.
`emitProgress` reports `state = paused` only when the resulting `activeLimit == 0`;
otherwise it stays `analyzing` even while throttled, since work is still progressing.

### Progress computation

`emitProgress` delegates `(total, state)` to the pure `resolveProgress(pending, done, state)`:

- `total = pending + done`.
- `state = done` iff `pending == 0`. Completion is keyed on the pending count, not
  `total` — `done` stays nonzero once any track has finished.
- `pending < 0` is the "`CountPending` failed" sentinel: `total` falls back to `done`
  alone, and `state` is left as the caller passed it (completion is unconfirmable
  without a real pending count).

### Failed tracks

A track/component whose analysis errors permanently (corrupt file, unsupported
codec, or source-specific setup failure) is recorded as `failed` at that
component's current version in `track_analysis_components`. It is resolved for
progress and does not retry until that component's version increases. A decode
failure marks every requested component failed; a single-source run does not
invalidate the other source's completed data.

- A track with no feature row still makes Normalization no-op (gain 0).
- Component-pending queries stop counting the failed source → progress can reach 0.
- It is counted in neither `pending` nor `done`, so `total` excludes it; 100% is
  reached once every *attemptable* track is resolved.

`analyzeOne` calls `MarkFailed` from the `Analyze()` error branch only — not from the
`ctx.Err() != nil` shutdown branch.

### Dedup and dequeue

Dedup via `queued`/`inFlight` maps: re-enqueuing an in-flight track is a no-op;
re-enqueuing a queued track with `priority=true` promotes it to `boostQueue`.

`Dequeue(trackIDs []string)` drops IDs from `boostQueue`, `normalQueue`, and the
`queued` dedup map, leaving `inFlight` untouched — an in-flight `Analyze()` finishes
rather than being cancelled mid-decode. Wired to library track deletion (see Central
Wiring) so a deletion racing an in-flight backfill does not keep analyzing and
`UpsertFeatures`-ing tracks being removed, which would contend with the deletion for
the single SQLite writer.

## Mood Derivation (`internal/app/analysis/mood/`)

`energy`/`danceability`/`brightness` are computed from raw `track_features` columns
(`rms`, `spectral_centroid`, `spectral_flux`, `tempo`, `crest`,
`onset_variance`, `loudness_range`) via locked formulas in `formulas.go`
(`DeriveEnergy`, `DeriveDanceability`) — weighted sums of each feature run
through `Normalize` (`mood.go`): hard-clamp to `[P1,P99]`, then a sigmoid
(`k=2.5`) of the value's distance from the corpus median in half-`(P95-P5)`
units. Danceability's tempo term instead uses `tempoScore`, a fixed
triangular function on raw BPM (0 at ≤60/≥180bpm, peaks at 1 at ~115bpm) —
not percentile-normalized, since tempo has a musically meaningful absolute
scale rather than a corpus-relative one.

The locked weights (all `Normalize` calls use `k=2.5`; `norm(x)` below is
`Normalize(x, pctl[x], 2.5)`):

```
energy       = 0.32·norm(rms)
             + 0.23·norm(spectral_centroid)
             + 0.17·norm(spectral_flux)
             + 0.18·norm(min(tempo, 180))   // tempo capped at energyTempoCap=180
             + 0.10·(1 − norm(crest))

danceability = 0.45·tempoScore(tempo)        // triangular, NOT percentile-normalized
             + 0.30·(1 − norm(onset_variance))
             + 0.15·(1 − norm(crest))
             + 0.10·(1 − norm(loudness_range))

brightness   = norm(spectral_centroid)        // direct bright ↔ dark similarity axis
```

Each weight set sums to 1.0, so both scores land in `[0,1]`. A `PercentileSet`
entry missing for a feature normalizes to a neutral `0.5` (degenerate-spread
branch), so a partial/empty warm cache still yields a valid score. Bump
`moodVersion` (`analysis_service.go`) on any weight/formula change.

`brightness` is not valence: it is a direct bright↔dark axis derived solely
from `spectral_centroid`. Migration 000054 invalidates cached percentile/mood
rows so the normal startup recompute path backfills brightness for existing
tracks before Mood Radio considers them.

`Normalize` needs a `PercentileSet` (`map[featureName]Percentile{P1,P5,P50,P95,P99}`)
computed across the whole analyzed library — a single track's raw features are
meaningless without a corpus to compare against. That set is:

- **Computed** by `RecomputeCorpusPercentiles` (`percentiles.go`): pulls every
  analyzed track's raw values via `AnalysisRepository.ListRawFeatureValues`,
  sorts and linearly interpolates p1/p5/p50/p95/p99 per feature (numpy's
  "linear" method), and persists via `UpsertFeaturePercentiles` into
  `feature_percentiles` (one row per feature, with `sample_count`/`computed_at`).
- **Cached** in-memory on `AnalysisService.moodPctl` (`loadPercentileCache`
  loads it at startup); hot-swapped by `recomputePercentilesAndBump` whenever
  recomputed, so in-flight derivations pick up the fresh table without
  blocking on a DB read per track.
- **Triggered** to recompute via three independent paths:
  1. **Startup staleness** (`maybeRecomputeOnStartup`): if the cached rows are
     older than `percentileStalenessThreshold` (24h) or don't exist yet,
     recompute once at `Start(ctx)`. This is the app's stand-in for a
     nightly-cron scheduler, since it's an offline desktop app that isn't
     always running.
  2. **Batch threshold**: `notifyMoodChange(n)` (called with `n=1` from
     `driveMoodDerivation` after each successful raw analysis, and
     `n=len(trackIDs)` from `NotifyTracksDeleted`) adds to a single combined
     `moodChangeCounter`; once it reaches `percentileRecomputeBatchSize`
     (100), a recompute fires immediately and the counter resets.
  3. **Debounce timeout**: every call to `notifyMoodChange` also (re)arms a
     `time.AfterFunc(percentileRecomputeDebounce, ...)` (30s). If no further
     add/delete event arrives before it fires, `fireDebouncedRecompute`
     recomputes with whatever's accumulated so far — bounds staleness for
     small batches instead of waiting indefinitely for the count to fill up.
     Adds and deletes share one counter/timer; the debounce timeout bounds
     staleness for sub-threshold batches.

All three trigger paths funnel into `recomputePercentilesAndBump`, which is
serialized: only one recompute runs at a time (`recomputeMu.TryLock`), and a
trigger that arrives mid-run sets a pending flag so exactly one more run
happens afterward against the latest corpus — concurrent triggers (e.g. a
sync-finished firing as the batch threshold trips) can't interleave the
version bump or run overlapping backfills.

```mermaid
flowchart TB
    T1["Startup staleness<br/>(>24h or missing)"] --> R
    T2["Batch threshold<br/>(100 adds+deletes)"] --> R
    T3["Debounce<br/>(30s quiet)"] --> R
    T4["Folder sync finished"] --> R

    R["recomputePercentilesAndBump<br/><i>serialized: 1 at a time, coalesces</i>"]
    R --> RC["Recompute corpus percentiles"]
    RC --> EMPTY{"Corpus empty?"}
    EMPTY -->|yes| STOP["Skip (nothing to bump)"]
    EMPTY -->|no| BUMP["Bump MoodDerivationVersion<br/>→ every track's mood now stale"]
    BUMP --> BF["backfillMood<br/>re-derive stale tracks in batches"]
    BF --> DONE[("track_features<br/>mood updated to new version")]
```

After any recompute that yields a non-empty corpus, `settings.MoodDerivationVersion`
is bumped and every track's `tracks.mood_derived_version` becomes stale
relative to it. `backfillMood` then re-derives mood in batches
(`ListMoodPending` → `Derive` → `UpsertMoodFeatures`) for every stale track —
run as a plain sequential loop, not through the boost/normal worker pool,
since re-deriving from already-decoded features is cheap in-memory
arithmetic plus one `UPDATE`, unlike the expensive ffmpeg decode pass the
pool's concurrency/throttle knobs are tuned for.

`driveMoodDerivation` runs immediately after each track's raw analysis
succeeds: if `moodPctl` is already warm, it derives and persists that
track's mood right away (via `UpsertMoodFeatures`); if the cache is cold
(true cold start, no corpus yet), it's a no-op until the first recompute
backfills it.

## Mood Radio (`frontend/src/stores/moodRadio.ts`, `MoodRadioService`)

"Give me more like this" queue seeding/auto-refill, gated on
`AppSettings.LibraryAnalysisEnabled` (energy/danceability/brightness/tempo are its only
inputs). `MoodRadioService.GenerateMoodRadio(seedTrackID, excludeTrackIDs,
limit)` forwards to `app/moodradio.Service`. The service asks
`TrackQueryRepository.FindSimilar` for the nearest 80 analyzed candidates,
excluding the seed and every supplied queue/history ID. SQLite ranks candidates
by weighted-euclidean distance over `energy`/`danceability`/`brightness`/`tempo` (tempo
scaled `/200` to bring its BPM range in line with the 0-1 normalized features),
computed entirely in SQL via correlated subqueries against the seed's own
feature row. If the seed track itself has no analyzed feature row, `FindSimilar`
returns no results (rather than an arbitrary order from NULL-valued distances).
The exclusion IDs are encoded as one JSON array and filtered with SQLite
`json_each(?)`, so a large playback queue does not consume one SQL bind
parameter per track.

The app service turns those deterministic candidates into a varied batch. Its
first three selections are weighted-random within the top 20; remaining
selections use the full candidate pool with rank weight `1 / sqrt(rank + 1)`.
Selection is without replacement and avoids a primary artist from the previous
three tracks plus a second track from an already selected album whenever an
alternative exists. It relaxes the album rule first, then artist cooldown, so
small libraries still receive a full batch.

The frontend store starts Mood Radio by seeding + prepending the seed track
(`FindSimilar` always excludes it). When that seed is already the active
track, it replaces only the queue through `ReplaceQueueKeepingCurrentTrackIDs`,
so the native player retains its current position rather than replaying the
seed. It then auto-refills the queue as it drains
below `REFILL_THRESHOLD` (3) remaining tracks, appending `SEED_BATCH_SIZE` (15)
more similar tracks at a time. The whole existing queue is supplied as
`excludeTrackIDs` on refill, so already queued/played tracks are removed before
the SQL candidate limit rather than being discarded too late in the frontend.
`MoodRadioService` owns the process-wide active flag and emits
`mood-radio:state`, so the main and mini-player webviews show the same state.
Only the main window runs the refill watcher; the mini player subscribes for
state display and actions without issuing a competing refill.
Turning off Library Analysis mid-session stops the radio immediately (watched
reactively), since its only data source just went away.

## Central Wiring (`internal/app/module.go`)

To avoid a `library↔analysis` or `player↔analysis` import cycle, all cross-package
listeners are wired in one `fx.Invoke` block:

```go
lib.AddAnalysisListener(func(trackID string) { analysisSvc.Enqueue(trackID, false) })
lib.AddTrackDeletedListener(func(trackIDs []string) {
    analysisSvc.Dequeue(trackIDs)
    analysisSvc.NotifyTracksDeleted(trackIDs) // feeds the mood percentile batch/debounce trigger
})
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
from `SyncFolder`'s missing-file cleanup step (after `deletedIDs` is finalized,
alongside the existing `library:track-deleted` event emission). Files removed
from disk are detected on the next periodic sync scan, not instantly — see
[Library catalog](../library/README.md#periodic-sync-scheduler).

The on-play boost fires on **every** track load, whether or not the track is
already analyzed — `Enqueue`'s dedup tracks only in-flight/queued state, not
analysis freshness. `analyzeOne` re-checks the pending component mask before
running; replaying a current track costs only component-version lookups.

## Wails-Exposed Methods (`AnalysisService`)

```go
SetLibraryAnalysisEnabled(enabled bool) error
ListFailedTracks() ([]domain.FailedAnalysisTrack, error)
RetryFailedTracks() error
GetWorkerCountInfo() WorkerCountInfo
SetWorkerCount(count int) error
```

Calls `AnalysisService.SetEnabled`, then `PlayerService.ReapplyNormalization()` —
needed either way: enabling doesn't change current playback, but disabling may
have just force-disabled Normalization, so the currently-playing track's preamp
gain must be cleared immediately.

## Events Emitted

| Event | Payload | When |
| ----- | ------- | ---- |
| `analysis:progress` | `{ done, total, state, libraryDone, libraryTotal, failed }`, `state ∈ analyzing\|paused\|done` | On enable/disable, throttle change, retry, and after each track finishes |

`done`/`total` track only the tracks pending in the current pool run. `libraryDone` is the number of library tracks whose required components all completed successfully; `failed` tracks remain in `libraryTotal` but are excluded from `libraryDone`. Retry atomically restores every failed component to pending, enqueues those tracks on the enabled pool, and immediately emits progress. The library total is served from a short-TTL cache (`libraryTotalCached`, ~3s) so per-track `emitProgress` during a bulk scan doesn't issue a full-table `COUNT(*)` per row.

## Frontend

Settings → Library tab, "Library Analysis" section
(`frontend/src/components/settings/LibrarySettings.vue`): enable switch,
"Analyzing N/M (x%)" progress line, library-readiness percentage, and a
worker-count slider (`libraryAnalysisWorkerCount`) shown when more than one
worker is available. The panel subscribes to `analysis:progress` and also
fetches an initial `GetProgress()` snapshot on mount so the UI starts from the
current backend state instead of waiting for the next event.

State lives in `frontend/src/stores/app.ts` (`libraryAnalysisEnabled`,
`libraryAnalysisWorkerCount`, `libraryAnalysisMaxWorkerCount`). The store loads
worker-count metadata from `AnalysisService.GetWorkerCountInfo()`. The
`updateLibraryAnalysisEnabled` action calls `AnalysisService.SetLibraryAnalysisEnabled`
and optimistically clears local `normalizationEnabled` when disabling, mirroring
the backend cross-toggle so the Normalization switch locks immediately without
waiting on a refetch. `updateLibraryAnalysisWorkerCount` clamps in the frontend
and calls `AnalysisService.SetWorkerCount()` on slider release rather than on
every drag frame. See `catalog/normalization/README.md` for the dependent UI.
