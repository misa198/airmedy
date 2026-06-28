# Audio Player

## Summary

The player feature handles audio playback, queue management, shuffle/repeat modes, state persistence across app restarts, and OS-level media integration (macOS Now Playing, Windows System Media Transport Controls + taskbar thumbnail toolbar, Linux MPRIS). It is split between an application-layer service and platform-specific audio adapters.

## Files

| File                                       | Purpose                                  |
| ------------------------------------------ | ---------------------------------------- |
| `internal/app/player/player_service.go`    | Orchestration: load, play, state, events |
| `internal/app/player/queue_service.go`     | Queue data structure and navigation      |
| `internal/infra/audio/player_darwin.go`    | macOS SFBAudioEngine player (cgo)        |
| `internal/infra/audio/native_player_darwin.m` | Obj-C SFBAudioEngine implementation   |
| `internal/infra/audio/player_miniaudio.go` | Windows/Linux miniaudio player           |
| `internal/infra/audio/nowplaying.go`       | `nowPlayingBackend` interface + `MiniAudioPlayer` delegation (`//go:build windows \|\| linux`) |
| `internal/infra/audio/nowplaying_windows.go` | Windows backend factory → `smtcBackend` |
| `internal/infra/audio/nowplaying_linux.go` | Linux MPRIS (D-Bus) backend (`mprisBackend`) |
| `internal/infra/audio/player_nowplaying_windows.go` | Windows `smtcBackend`: cgo bridge to SMTC |
| `internal/infra/audio/smtc_windows.cpp`    | WinRT SMTC implementation (C-ABI, mingw)  |
| `internal/infra/audio/smtc_windows.h`      | C-ABI bridge for the SMTC backend         |
| `internal/infra/wails/player_service.go`   | Wails binding wrapper                    |
| `internal/infra/wails/thumbbar_manager_windows.go` | `ThumbBarManager`: wires taskbar thumbnail buttons → player (`//go:build windows`) |
| `internal/infra/wails/thumbbar_manager_other.go` | No-op `ThumbBarManager` stub (non-Windows) |
| `internal/infra/wails/thumbbar_windows.cpp` | `ITaskbarList3` thumbnail-toolbar impl (GDI icons, subclass WndProc) |
| `internal/infra/wails/thumbbar_windows.h`  | C-ABI bridge for the thumbnail toolbar    |
| `internal/infra/power/inhibitor_darwin.go` | macOS IOPMAssertion sleep inhibitor (cgo) |
| `internal/infra/power/inhibitor_windows.go` | Windows SetThreadExecutionState sleep inhibitor |
| `internal/infra/power/inhibitor_linux.go`  | Linux no-op sleep inhibitor              |
| `internal/infra/power/module.go`           | FX module for `SleepInhibitor` binding   |

## AudioPlayer Interface

```go
type AudioPlayer interface {
    Play() error
    Pause() error
    Stop() error
    Seek(position float64) error
    SetVolume(volume float64) error
    SetMuted(muted bool) error
    Load(track *TrackDTO) error
    Unload() error
    GetStatus() PlayerStatus
    OnTrackEnd(callback func())
}
```

## GaplessPlayer Interface (Optional)

Implemented by audio adapters that support gapless or near-gapless transitions. Detected via type assertion in `PlayerService`.

```go
type GaplessPlayer interface {
    // Pre-load / pre-queue the next track while the current one plays.
    EnqueueNext(track *TrackDTO) error
    // Promote the pre-loaded track to active. For auto-transition players (SFBAudioEngine)
    // this updates Go-side status tracking only. For miniaudio this calls ma_player_start_preloaded.
    StartPreloaded(track *TrackDTO) error
    // Returns true when the engine transitions automatically (SFBAudioEngine).
    // HandleTrackEnd must NOT call Load/Play when this returns true.
    AutoTransitions() bool
    // ClearEnqueued discards the pending pre-queued track from the engine without
    // affecting the currently playing track. Called by SetRepeatMode to re-sync
    // the pre-queue when the repeat mode changes during playback.
    ClearEnqueued()
}
```

Both `DarwinPlayer` (macOS) and `MiniAudioPlayer` (Win/Linux) implement `GaplessPlayer`.

## NowPlayingController Interface (Optional)

Implemented by audio adapters that provide OS-level Now Playing info and media-key
remote commands. Detected via type assertion in `PlayerService` (constructor); when present,
the service wires remote callbacks and calls the update methods on track load, the 500ms
ticker, and stop.

```go
type NowPlayingController interface {
    SetupRemoteCommands()
    SetRemoteCallbacks(play, pause, next, previous func(), seek func(float64))
    UpdateNowPlaying(track *TrackDTO, position float64, artworkPath string)
    UpdateNowPlayingPosition(position float64)
    ClearNowPlaying()
}
```

Implemented by `DarwinPlayer` (macOS, `MPNowPlayingInfoCenter`) directly, and by
`MiniAudioPlayer` (Windows + Linux). On Win/Linux `MiniAudioPlayer` does not talk to the OS
itself — it delegates to a `nowPlayingBackend` (`nowplaying.go`) selected per platform:
`smtcBackend` (SMTC) on Windows, `mprisBackend` (MPRIS/D-Bus) on Linux. The factory
`newNowPlayingBackend` may return nil when the OS integration is unavailable (e.g. no D-Bus
session bus), in which case every delegated call is a no-op.

### Playback state (play/pause glyph)

The `domain.NowPlayingPlaybackState` interface (`SetNowPlayingPlaybackState(playing bool)`) and
`PlayerService.setNowPlayingPlaybackState` still exist but currently have **no implementer** — the
type assertion always fails, so the service hook is a no-op on every platform. Play/pause state
is instead pushed where the engine state actually flips:

- **Win/Linux:** `MiniAudioPlayer.Play/Pause/Stop` call `np.setPlaybackState(domain.PlaybackState)`
  on the backend, which drives the OS glyph (SMTC `PlaybackStatus` / MPRIS `PlaybackStatus`).
- **macOS:** `DarwinPlayer` derives the glyph from its own engine's `isPlaying`.

## SleepInhibitor Interface

Prevents the OS from sleeping while music plays. Defined in `internal/domain/audio.go`, implemented in `internal/infra/power/`.

```go
type SleepInhibitor interface {
    Inhibit() error  // acquire OS sleep prevention
    Release() error  // release it
}
```

| Platform | Implementation | Mechanism |
| -------- | -------------- | --------- |
| macOS    | `inhibitor_darwin.go` | `IOPMAssertion` (cgo) |
| Windows  | `inhibitor_windows.go` | `SetThreadExecutionState(ES_CONTINUOUS \| ES_SYSTEM_REQUIRED)` |
| Linux    | `inhibitor_linux.go` | no-op (returns nil) |

## Platform Adapters

### macOS — SFBAudioEngine (`player_darwin.go`)

- Implemented via **cgo** calling Objective-C bridging code (`native_player_darwin.m`).
- Audio engine: **SFBAudioEngine** (v0.12.1) — replaces AVAudioEngine + FFmpeg.
- Framework deps: `SFBAudioEngine`, `AVFoundation`, `CoreMedia`, `MediaPlayer`, `AppKit`, `CoreFoundation`, `Security`, `AudioToolbox`, `opus`, `sndfile`, `lame`, `FLAC`, `tta-cpp`, `vorbis`, `wavpack`, `mpg123`, `mpc`, `ogg`.
- SFBAudioEngine and its dependencies are dynamic xcframeworks built/downloaded by `task build:sfbaudioengine` and stored at `internal/infra/audio/sfb_libs/` (not committed; add to `.gitignore`). At runtime, the frameworks are embedded in `Contents/Frameworks/`.
- **Format support:** All formats natively — MP3, FLAC, AAC, WAV, AIFF, Opus, Vorbis, WavPack, APE, DSD, and more. No FFmpeg required on darwin.
- **EQ:** `AVAudioUnitEQ` (10-band parametric, ISO frequencies) injected into SFBAudioEngine's graph via `modifyProcessingGraph:` on init and reconnected on format changes via the `reconfigureProcessingGraph:withFormat:` delegate. Returns the EQ node so SFBAudioEngine connects `sourceNode → EQ → mainMixerNode`.
- **Track end:** `SFBAudioPlayerDelegate audioPlayer:renderingComplete:` fires when last sample is rendered (not when decoding finishes). When a next track was pre-queued gaplessly, SFBAudioEngine is still playing; `renderingComplete:` fires for each track in the queue, allowing the Go layer to advance state without stopping audio.
- **Gapless:** `EnqueueNext` calls `[sfbPlayer enqueueURL:url forImmediatePlayback:NO]`. SFBAudioEngine transitions seamlessly if sample rate and channel count match. `AutoTransitions()` returns `true`.
- Provides `NowPlayingController` for OS-level media info (lock screen, menu bar).
- Remote command callbacks: Play, Pause, Next, Previous, Seek (media keys + AirPods).
- `UpdateNowPlaying(track, position, artworkPath)` — populates the macOS Now Playing widget.

### Windows/Linux — miniaudio (`player_miniaudio.go`)

- C library (`miniaudio`) integrated via cgo as the playback and output engine.
- **Decoding Backend:** Leverages FFmpeg for **all** audio formats to ensure maximum compatibility and robustness.
- Functions: `ma_player_create()`, `ma_player_play()`, `ma_player_pause()`, `ma_player_stop()`, `ma_player_seek()`, `ma_player_set_volume()`.
- Track end detected via `goMiniAudioTrackEnd()` Go callback.
- **EQ:** Implemented via a chain of 10 `ma_peak_node` filters. Enabled state routes audio through the chain before output. Support for live band updates.
- **Gapless (near-gapless):** Uses a ping-pong slot design (`slot_a`/`slot_b`). `ma_player_preload_next` initializes the next track into the idle slot. On `HandleTrackEnd`, Go calls `ma_player_start_preloaded` which uninits the current slot and starts the pre-loaded slot — gap is only goroutine scheduling latency (~1–5 ms). `AutoTransitions()` returns `false`.

### Windows — System Media Transport Controls (Now Playing)

On Windows the `nowPlayingBackend` is `smtcBackend` (`player_nowplaying_windows.go`,
`//go:build windows`), which bridges via cgo to `smtc_windows.cpp`.

- **Toolchain:** built with mingw-w64 GCC (not MSVC), so SMTC is reached through the WinRT
  **C-ABI** projection headers (`ABI::Windows::Media::*` vtable COM), compiled as C++. Extra
  link libs (`-lruntimeobject -lshlwapi -lshell32`) live in `cgoflags_smtc_windows.go`.
- **Static C++ runtime:** because this WinRT code is C++, the binary would otherwise depend on
  `libstdc++-6.dll` / `libgcc_s_seh-1.dll` / `libwinpthread-1.dll` (absent on a clean Windows
  machine → "libstdc++-6.dll was not found" at launch). `cgoflags_windows_amd64.go` appends
  `-Wl,-Bstatic -lstdc++ -lpthread -Wl,-Bdynamic -static-libgcc` to link these statically while
  keeping system libs dynamic. `-static-libstdc++` alone does **not** work: Go links via g++ and
  the earlier `-Wl,-Bdynamic` (for `-lmfplat` etc.) leaves the linker in dynamic mode for the
  implicit `-lstdc++`. The NSIS installer therefore bundles no runtime DLLs.
- **Threading:** a dedicated **STA thread** owns a hidden **top-level** window (0×0, never shown;
  _not_ a message-only `HWND_MESSAGE` window, which `GetForWindow` accepts but the shell never
  registers a media session for), obtains SMTC via
  `ISystemMediaTransportControlsInterop::GetForWindow`, registers the `ButtonPressed`
  and `PlaybackPositionChangeRequested` handlers, and runs a message pump. Public `Smtc*`
  functions marshal work onto that thread via `PostMessage` (heap payloads), so all COM calls
  stay on the owning apartment. Self-contained — does not use the Wails window HWND.
- **Identity:** calls `SetCurrentProcessExplicitAppUserModelID("me.misa198.airmedy")` so the OS
  attributes the media session to Airmedy.
- **Mapping:** metadata + album-art thumbnail (file:// URI) via `DisplayUpdater`; play/pause
  glyph via `PlaybackStatus` (driven by the backend's `setPlaybackState`, called from
  `MiniAudioPlayer.Play/Pause/Stop`); seek scrubber via
  `ISystemMediaTransportControls2::UpdateTimelineProperties`; media-button/seek events forwarded
  to `PlayerService` through the `goWinNowPlaying*` cgo exports.
- **Teardown:** `MiniAudioPlayer.Close()` (called by `PlayerService` OnStop) posts quit, drains
  queued payloads, releases interfaces, and joins the thread. Idempotent.

### Windows — Taskbar Thumbnail Toolbar (`internal/infra/wails/`)

Separate from SMTC: adds **Prev / Play-Pause / Next** buttons to the taskbar
thumbnail popup (hover preview) via `ITaskbarList3::ThumbBarAddButtons`. Lives in the
**wails adapter layer** (not `infra/audio`), wired in `main.go`.

- **Files:** `thumbbar_windows.cpp` / `.h` (C++ COM impl), `thumbbar_manager_windows.go`
  (`ThumbBarManager`, `//go:build windows`), `thumbbar_manager_other.go` (no-op stub for
  non-Windows), `cgoflags_thumbbar_windows*.go` (link libs).
- **Init timing:** `Setup()` registers a `WindowFocus` hook (`once.Do`). The hook fires on a
  goroutine, so actual init is dispatched via `application.InvokeAsync` onto the Win32 **message
  thread** — the only thread valid for `SetWindowSubclass` and COM. Uses the Wails main-window
  HWND (`mainWindow.NativeWindow()`), unlike SMTC which owns its own hidden window.
- **COM:** `CoInitializeEx(APARTMENTTHREADED)` then `CoCreateInstance(CLSID_TaskbarList)` →
  `ITaskbarList3`. `SetWindowSubclass` intercepts `WM_COMMAND` (`THBN_CLICKED`) for button clicks
  and `WM_TASKBARBUTTONCREATED` to re-add buttons after an Explorer restart.
- **Icons:** white 16×16 top-down 32bpp DIBs on transparent background (Prev/Play/Pause/Next),
  drawn with GDI `Polygon`/`Rectangle`.
- **Wiring:** buttons → `PlayerService.Previous` / `TogglePause` / `Next` (each in a goroutine to
  avoid blocking the message thread). A status listener calls `ThumbBarSetPlaying` to swap the
  play/pause icon. Clicks routed back to Go through `goThumbBar*` cgo exports.
- **Teardown:** `Stop()` (called before `stopFX()` in `main.go`) → `ThumbBarStop` removes the
  subclass and releases COM resources.

### Linux — MPRIS (Now Playing)

On Linux the `nowPlayingBackend` is `mprisBackend` (`nowplaying_linux.go`, `//go:build linux`),
pure Go over D-Bus (`github.com/godbus/dbus/v5`). No cgo.

- **Bus:** connects to the session bus and exports `org.mpris.MediaPlayer2.airmedy` at
  `/org/mpris/MediaPlayer2`, implementing the `org.mpris.MediaPlayer2` (root) and
  `org.mpris.MediaPlayer2.Player` interfaces so GNOME/KDE shells, media keys and `playerctl` can
  see and control playback. `newNowPlayingBackend` returns nil if the session bus is unavailable.
- **Mapping:** track metadata → `Metadata` property (`xesam:*` + `mpris:artUrl` file:// URI);
  play/pause → `PlaybackStatus` (driven by `setPlaybackState`); position via `mpris:length` +
  `Position`. Remote Play/Pause/Next/Previous/Seek method calls are forwarded to `PlayerService`
  via the registered callbacks; property changes are emitted as D-Bus `PropertiesChanged` signals.
- **Teardown:** `close()` releases the bus name and connection.

## PlayerService (Application Layer)

### Responsibilities

- Loads tracks into the audio adapter.
- Manages playback state transitions.
- Runs a **500ms ticker** for internal logic:
  - Increments play counts and scrobbling thresholds via `checkThreshold()`.
  - Updates OS-level Now Playing position via `UpdateNowPlayingPosition()`.
  - **Note:** This ticker no longer emits `player:status` every 500ms; status is only emitted on meaningful state changes (Play, Pause, Seek, Stop, Track End) to reduce IPC overhead.
- Acquires OS sleep inhibition (`domain.SleepInhibitor`) when ticker starts (playback begins); releases on ticker stop (pause/stop). Controlled by `PreventSleepWhilePlaying` setting.
- Persists and restores state via `PlayerStateRepository`.
- Increments play counts via `TrackRepository.IncrementPlayCount()`.
- Syncs artwork theme colors on track load.
- Fetches/delivers lyrics on track load.
- Resets playback position to 0 on track change to ensure clean UI transitions.
- Handles track-end → advance queue → load next.
- **Gapless playback (always on):** `loadAndPlay` pre-enqueues the next track via `GaplessPlayer.EnqueueNext`. On `HandleTrackEnd`, the service calls `GaplessPlayer.StartPreloaded` (for miniaudio) or just updates status (SFBAudioEngine auto-transitions), then calls `transitionToTrack` to update currentTrack, Now Playing, palette, and lyrics without interrupting audio.

### Key Methods

```go
Play() error
Pause() error
Stop() error
Next() error
Previous() error
Seek(position float64) error
SetVolume(volume float64) error
SetMuted(muted bool) error
SetShuffle(enabled bool) error
SetRepeatMode(mode RepeatMode) error
PlayTracks(tracks []*TrackDTO, startIndex int) error
PlayTrackIDs(ids []string, startIndex int) error
ShuffleTracks(tracks []*TrackDTO) error
ShuffleTrackIDs(ids []string) error
PlayNext(track *TrackDTO)
PlayNextTracks(tracks []*TrackDTO)
RemoveFromQueue(trackID string)
GetStatus() PlayerStatus
GetQueue() []*TrackDTO
```

### PlayerStatus

```go
type PlayerStatus struct {
    TrackID       string
    PlaybackState PlaybackState  // "playing", "paused", "stopped"
    Position      float64        // seconds
    Duration      float64        // seconds
    Volume        float64        // 0.0–1.0
    Muted         bool
    RepeatMode    RepeatMode     // "off", "one", "all"
    Shuffle       bool
    Theme         *ThemeColors
}
```

## Queue Service

```go
type QueueService struct {
    originalList []*TrackDTO  // unshuffled order
    shuffledList []*TrackDTO  // shuffled order (Fisher-Yates)
    currentIndex int
    repeatMode   RepeatMode
    shuffle      bool
}
```

### Navigation

| Mode          | Next behavior              | Previous behavior          |
| ------------- | -------------------------- | -------------------------- |
| RepeatModeOff | Advance index; stop at end | Retreat index; stop at 0   |
| RepeatModeAll | Advance; wrap to 0 at end  | Retreat; wrap to last at 0 |
| RepeatModeOne | Return current track again | Return current track again |

### Shuffle

Fisher-Yates shuffle. When entering shuffle mode with a playing track, the current track retains focus (its new shuffled index is tracked) but is not pinned at any fixed position.

**Shuffle state invariant:** `SetQueue` (called by `PlayTracks`/`PlayTrackIDs`) always resets shuffle to false. `ShuffleTracks`/`ShuffleTrackIDs` always sets shuffle to true. UI components must not call `SetShuffle(false)` after `playTracks` — the invariant is enforced at the queue layer.

### Insert After Current

`PlayNext(track)` / `PlayNextTracks(tracks)` inserts after the current index in both `originalList` and `shuffledList`.

### Other QueueService Methods

| Method | Description |
| --- | --- |
| `SetCurrentIndex(index)` | Moves the queue pointer without modifying queue contents |
| `PeekNext() / PeekPrevious()` | Read-only lookahead — returns next/previous track without advancing the index |
| `ReorderQueue(trackIDs []string)` | Reorders the active list by ID slice; maintains current track index |
| `GetOriginalQueue()` | Returns the unshuffled `originalList` |
| `Restore(original, shuffled, currentIndex, shuffle, repeatMode)` | Bulk-sets all queue state; used on app startup to restore persisted queue |
| `UpdateTrack(track)` | Updates `IsFavorite` in-place for matching entries in both lists |

## State Persistence

On every state change, `PlayerStateRepository.Save()` writes:

```go
type PlayerState struct {
    QueueTrackIDs  []string    // JSON array
    CurrentTrackID string
    Position       float64
    Volume         float64
    Muted          bool
    Shuffle        bool
    RepeatMode     RepeatMode
}
```

On app startup, `Load()` restores queue, seeks to saved position, but does not auto-play (playback state is paused on restore).

## Events Emitted

| Event                  | When                                                   |
| ---------------------- | ------------------------------------------------------ |
| `player:status`        | On any state change (Play, Pause, Seek, Stop, Track Change) |
| `player:queue-updated` | Queue modified (insert, remove, reorder, new playlist) |
| `player:theme`         | New track loaded — artwork color palette               |
| `player:lyrics`        | New track loaded — lyrics object (may be null)         |

## Frontend Store (`stores/player.ts`)

**State:** `status`, `queue`, `currentTrack`, `theme`, `lyrics`, `playerMode` (`sticky | mini | fullscreen`), drawer visibility flags.

**Playback Interpolation:**
To ensure smooth 60fps progress updates and reduce IPC overhead, the store uses a **Sync-and-Drift** mechanism:
- **Sync:** Listens for `player:status` from the backend to get the authoritative position (`lastSyncPosition`) and records the arrival time (`lastSyncTime` via `performance.now()`).
- **Drift (Interpolation):** Runs a `requestAnimationFrame` loop that calculates the current position as: `lastSyncPosition + (performance.now() - lastSyncTime) / 1000`.
- The `position` computed property returns this interpolated value, providing silky smooth progress bar movement without constant backend ticking.

**Computed:** `isPlaying`, `isPaused`, `progressPercent`, `artworkUrl`, `artworkUrlMd`, `artworkUrlSm`.

**Artwork URLs:** Constructed from `artworkKey` using variant naming: `{key}_sm.jpg` (64px), `{key}_md.jpg` (500px), `{key}.jpg` (original).

**Player modes:**

- `sticky` — Full player footer pinned at bottom.
- `mini` — Floating mini player window (separate Wails window, always-on-top).
- `fullscreen` — Full-screen overlay in the main window.

## Wails-Exposed Methods

```typescript
Play(), Pause(), Stop()
Next(), Previous()
Seek(position: number)
SetVolume(volume: number)
SetMuted(muted: boolean)
SetShuffle(enabled: boolean)
SetRepeatMode(mode: string)
PlayTracks(tracks: TrackDTO[], startIndex: number)
PlayTrackIDs(trackIDs: string[], startIndex: number)
ShuffleTracks(tracks: TrackDTO[])
ShuffleTrackIDs(trackIDs: string[])
PlayNext(track: TrackDTO)
PlayNextTracks(tracks: TrackDTO[])
RemoveFromQueue(trackID: string)
GetStatus(): PlayerStatus
GetQueue(): TrackDTO[]
```

## Mini Player Window

Separate Wails window (default 300×300, min 280×180, max 500×500). Route: `/mini-player`. Uses `useGlassBlur()` composable for WebGL Gaussian blur of artwork as background. Has always-on-top toggle and volume slider with auto-fade timer.

### Geometry & Pin Persistence

`WindowService` persists the mini player's position, size, and pin (always-on-top) state to the single-row `mini_player_state` table (`MiniPlayerStateRepository`), so they survive close/reopen and app restarts. The window is recreated on each open (see `catalog/ui`), so restore happens in the factory:

- **Restore** — `WindowService.ApplyMiniState(w)` runs in the mini window factory before show. If `has_position` is set, it applies the saved bounds (clamped, see below) and re-applies `always_on_top`.
- **Capture** — `WindowDidMove`/`WindowDidResize` hooks call `WindowService.SaveMiniGeometry()`, which reads `w.Bounds()` and persists it debounced (~400ms) to coalesce drag/resize streams. `WindowClosing` flushes a final save.
- **Pin** — frontend calls `WindowService.SetMiniAlwaysOnTop(b)` (not `Window.SetAlwaysOnTop` directly) so the toggle is persisted immediately. On mount the component reads `WindowService.GetMiniState()` to render the correct pin icon.
- **Screen-aware clamp** — `clampToScreen` clamps width/height into `[280..500]`×`[140..500]`, then positions the window and reads its screen's `WorkArea` (via `w.GetScreen()`); the pure helper `clampRectToWorkArea` shrinks/moves the rect fully inside the work area. This keeps the window reachable after a layout change (lower resolution, disconnected monitor, different screen).
