# Audio Player

## Summary

The player feature handles audio playback, queue management, shuffle/repeat modes, state persistence across app restarts, and OS-level media integration (macOS Now Playing). It is split between an application-layer service and platform-specific audio adapters.

## Files

| File                                       | Purpose                                  |
| ------------------------------------------ | ---------------------------------------- |
| `internal/app/player/player_service.go`    | Orchestration: load, play, state, events |
| `internal/app/player/queue_service.go`     | Queue data structure and navigation      |
| `internal/infra/audio/player_darwin.go`    | macOS SFBAudioEngine player (cgo)        |
| `internal/infra/audio/native_player_darwin.m` | Obj-C SFBAudioEngine implementation   |
| `internal/infra/audio/player_miniaudio.go` | Windows/Linux miniaudio player           |
| `internal/infra/wails/player_service.go`   | Wails binding wrapper                    |

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

## PlayerService (Application Layer)

### Responsibilities

- Loads tracks into the audio adapter.
- Manages playback state transitions.
- Runs a **500ms ticker** that emits `player:status` events while playing.
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
| `player:status`        | Every 500ms during playback, and on any state change   |
| `player:queue-updated` | Queue modified (insert, remove, reorder, new playlist) |
| `player:theme`         | New track loaded — artwork color palette               |
| `player:lyrics`        | New track loaded — lyrics object (may be null)         |

## Frontend Store (`stores/player.ts`)

**State:** `status`, `queue`, `currentTrack`, `theme`, `lyrics`, `playerMode` (`sticky | mini | fullscreen`), drawer visibility flags.

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

Separate Wails window (300×300, always-on-top). Route: `/mini-player`. Uses `useGlassBlur()` composable for WebGL Gaussian blur of artwork as background. Has always-on-top toggle and volume slider with auto-fade timer.
