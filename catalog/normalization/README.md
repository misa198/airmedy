# Volume Normalization

## Summary

Applies a per-track pre-amp gain at playback so all tracks (or all tracks on an
album) hit the user's target loudness, computed from the one-time loudness/dynamics
analysis the analysis pipeline (`internal/app/analysis`, see `catalog/analysis/README.md`)
writes to `track_features`. No re-encoding — gain is applied live by the audio
adapter, the same way EQ is (see `catalog/equalizer/README.md`).

## Files

| File                                              | Purpose                                  |
| -------------------------------------------------- | ---------------------------------------- |
| `internal/app/normalization/service.go`            | Gain compute + settings CRUD             |
| `internal/app/normalization/module.go`             | FX module                                |
| `internal/infra/wails/normalization_service.go`    | Wails binding (settings get/set)         |
| `internal/domain/audio.go`                         | `NormalizationController` interface      |
| `internal/domain/models.go`                        | `AppSettings` normalization_* fields, `TrackFeatures`, `DefaultTargetLUFS` |
| `internal/infra/sqlite/settings_repository.go`     | Persistence for normalization settings   |
| `internal/infra/audio/player_darwin.go` + `native_player_darwin.m` | macOS `SetPreampGain` via `AVAudioUnitEQ.globalGain` |
| `internal/infra/audio/player_miniaudio.go` + `miniaudio_wrapper_{linux,windows}.c` | Windows/Linux `SetPreampGain` via engine-endpoint output-bus volume |

## Settings (`AppSettings`)

| Field | Type | Default | Meaning |
| ----- | ---- | ------- | ------- |
| `NormalizationEnabled` | bool | `false` | Master on/off. Cannot be set `true` while `AppSettings.LibraryAnalysisEnabled` is `false` — enforced in `NormalizationService.SetEnabled`, not just the UI. See `catalog/analysis/README.md`. |
| `NormalizationMode` | string | `"track"` | `off` \| `track` \| `album`. The mode `Select` in the UI only ever offers `track`/`album` — `"off"` is purely an unset-default, never a user choice. Both default paths must agree: the migration column default (`ALTER TABLE ... DEFAULT 'track'`, `migrations/000034_track_features.up.sql`) and the Go fallback in `SettingsRepository.Load` for a brand-new `app_settings` row (`sql.ErrNoRows` branch, `internal/infra/sqlite/settings_repository.go`) — the latter used to hardcode `"off"`, diverging from the migration and silently persisting `"off"` on first save for new installs. `SetEnabled(true)` also auto-promotes a lingering `"off"` to `"track"` (see below) as a safety net for installs that already persisted `"off"` before this fix. |
| `NormalizationTargetLUFS` | float64 | `-14.0` (`domain.DefaultTargetLUFS`) | Target integrated loudness |
| `NormalizationPreventClip` | bool | `true` | Clamp gain so `gain + TruePeak <= 0` dBFS |

## Enable Lifecycle (`NormalizationService.SetEnabled`)

`SetEnabled(true)` also defaults `NormalizationMode` from `"off"` to `"track"` if it's
still at the DB default. Without this, enabling normalization for the first time
(right after analysis finishes) applies gain `0` — indistinguishable from "not
analyzed yet" — until the user separately picks Track/Album mode, which was a real
reported bug (`enabled changed enabled=true` → `gain_db=0`, only fixed once `mode
changed` fired). An already-explicit mode (`track` or `album`) is left untouched.

## Gain Formula (`NormalizationService.ComputeGain`)

1. If `!LibraryAnalysisEnabled || !Enabled || Mode == "off"` → gain `0`. The
   `LibraryAnalysisEnabled` check is the actual enforcement point — it must be
   re-checked here, not just in `SetEnabled`'s validation, so a DB row that
   somehow has `NormalizationEnabled=true` with `LibraryAnalysisEnabled=false`
   (pre-existing data from before this gate existed, manual edits) can't still
   apply gain just because the UI happens to lock the switch.
2. Look up `TrackFeatures` via `AnalysisRepository.GetFeatures`. If not analyzed yet → gain `0`, `hasFeatures=false` (plays at normal volume; the on-play boost listener — see `catalog/player/README.md` — pushes the track to the front of the analysis queue).
3. **Track mode:** `gain = target - track.LoudnessLUFS`.
4. **Album mode (look-ahead):** `ComputeGain(ctx, track, next *domain.TrackDTO)` takes
   the track that will play immediately after `track` (nil at the end of the queue).
   `sameAlbumChain(track, next)` decides whether to use the album average:
   - `track.AlbumID == ""` (no album metadata) → always `false`, never grouped —
     otherwise two unrelated untagged files would match on the shared empty string.
   - `next == nil` (last track in the queue) → `false`.
   - Otherwise → `next.AlbumID == track.AlbumID`.

   When `true`: `gain = target - albumLUFS`, where `albumLUFS` is the arithmetic
   mean of `LoudnessLUFS` across every analyzed sibling on the album
   (`TrackRepository.GetByAlbumID`), falling back to the track's own LUFS if no
   sibling has been analyzed. All tracks in a continuous same-album run share this
   one gain, preserving the original relative loudness between them (a quiet intro
   stays quieter than the chorus).

   When `false` (album mode but chain broken — end of queue, or the next track is
   from a different album/untagged): falls back to the **track-mode formula**
   (`target - track.LoudnessLUFS`) for this one track. This protects the listener
   from a jarring level jump at the seam where a mixed playlist or a lone inserted
   track breaks the album run.
5. If `PreventClip`: clamp `gain` so `gain + TruePeak <= 0` dBFS (`gain = min(gain, -TruePeak)`).

## Apply Flow

`ApplyToPlayer(ctx, track, next *domain.TrackDTO)` computes gain (passing `next`
through to `ComputeGain`) and pushes it via `NormalizationController.SetPreampGain`.
No-op if the player doesn't implement `NormalizationController`.

Called from every place `PlayerService` changes the current track, always with
`s.queue.PeekNext()` as `next` (queue's `currentIndex` is advanced *before* each of
these runs, so `PeekNext()` already reflects the new current track):

- `loadAndPlay` — right after `player.Load()` and before `player.Play()`, so gain is
  set before audio starts (no audible jump).
- `transitionToTrack` — the gapless-preload path, when the native engine has already
  switched to the pre-queued next track on its own (track-end during gapless
  playback). Missing this call was a real bug: the previous track's gain used to
  carry over onto the new track until something else re-triggered `ApplyToPlayer`.
- `restoreState` — on app boot, right after reloading the last-played track and
  seeking to its saved position. Missing this call was also a real bug: gain
  silently defaulted to the native player's built-in value (effectively 0) until
  the user touched a normalization setting.
- `ReapplyNormalization()` — re-runs `ApplyToPlayer` for the currently-loaded track
  when normalization settings change via the Wails binding (`SetEnabled`/`SetMode`/
  `SetTarget`/`SetPreventClip`), so the change takes effect immediately during
  playback, not just on the next track load.

## NormalizationController Interface (Optional, per-adapter)

```go
type NormalizationController interface {
    SetPreampGain(db float64) error
}
```

| Platform | Mechanism |
| -------- | --------- |
| macOS | `AVAudioUnitEQ.globalGain` (dB) on the persistent EQ node. `setEQEnabled` bypasses **each band individually** (`AVAudioUnitEQFilterParameters.bypass`), not the whole `AVAudioUnitEQ` unit — the unit-level `bypass` property silences the *entire* Audio Unit including `globalGain`, so a naive whole-unit bypass would silently break normalization whenever the user's EQ is off. See `catalog/equalizer/README.md`. |
| Windows/Linux | `ma_node_set_output_bus_volume()` on the engine endpoint (`ma_engine_get_endpoint`), converted from dB to linear (`10^(dB/20)`). Both the EQ-enabled and EQ-bypassed sound-routing paths already converge there, so no extra node or topology change was needed. Independent of `ma_player_set_volume` (per-sound user volume). |

## Wails-Exposed Methods (`NormalizationService`)

```go
GetSettings() (*domain.AppSettings, error)
SetEnabled(enabled bool) error      // errors if LibraryAnalysisEnabled is false
SetMode(mode string) error          // "off" | "track" | "album"
SetTarget(targetLUFS float64) error
SetPreventClip(enabled bool) error
```

Each setter persists via `domain.SettingsRepository` then calls
`PlayerService.ReapplyNormalization()` for immediate effect.

## Frontend

Settings → Playback tab, in a "Volume Normalization" section directly below a
"Library Analysis" section (`frontend/src/components/settings/PlaybackSettings.vue`,
see `catalog/analysis/README.md`): enable toggle, mode select (Track/Album), target
LUFS input (default `-14`), prevent-clip toggle. A small "% of library analyzed"
readiness label sits in the section header; the inline "Analyzing N/M (x%)" line
(driven by the `analysis:progress` Wails event) moved to the Library Analysis card
above it. Every control in this section is dimmed/disabled (`opacity-40
pointer-events-none`) while `libraryAnalysisEnabled` is false, with a short hint
text explaining the dependency.

State lives in `frontend/src/stores/app.ts` (`normalizationEnabled`,
`normalizationMode`, `normalizationTargetLufs`, `normalizationPreventClip`); each
`update*` action calls the dedicated `NormalizationService.Set*` binding directly
(not the generic `SettingsService.SaveSettings`) so `ReapplyNormalization()` fires
and a currently-playing track's gain updates without a restart. The 4 fields are
still included in the generic `saveSettings()` payload so they round-trip correctly
when other settings are saved. `NormalizationService` is registered as a Wails
service in `main.go` (was previously only constructed via fx, never bound).
