# Android Player and Queue

## Scope and Status

Android playback is independent from the desktop player. `PlaybackController`
is the Android-only command boundary; Compose UI supplies a synced track-ID
queue and observes its `StateFlow<PlaybackState>`.

`PlaybackService` owns audio focus, the platform `MediaSession`, notification,
and the JNI `FfmpegDecoder`. The native decoder opens the private synced file,
uses FFmpeg for demuxing and decoding, converts samples through
`libswresample` to stereo float PCM, and sends them to AAudio through a bounded
ring buffer. `PlaybackEngine` owns two independent source slots (active and
preloaded) and one shared power-saving AAudio stream. Each slot has its own
FFmpeg state, resampler and decode worker; the callback only uses atomic state
and preallocated PCM to apply per-source normalization, equal-power mix and a
neutral global DSP stage. It never locks, allocates, calls JNI, or performs I/O.
Pausing also pauses the AAudio stream, so it does not keep rendering silent
callbacks. There is no Media3, ExoPlayer, or `MediaCodec` decoder fallback.

When the service is started by a new Play or Shuffle command, that new queue
takes precedence over saved-session restoration. The service cancels restoration
before handling the command, so DataStore reads and validation of an old queue
cannot delay the initial `Preparing` state or the mini-player. Restoration still
occurs when the service starts without a queue-replacing command.

While playback is active, `PlaybackService` also listens for Android's
`ACTION_AUDIO_BECOMING_NOISY` broadcast. This occurs when a wired or Bluetooth
audio route disconnects and dispatches the same pause action as the player
controls; the receiver is unregistered when the service is destroyed.

AAudio can also report `AAUDIO_ERROR_DISCONNECTED` without that broadcast (for
example while changing output routes). The native decoder marks that stream as
terminal; while still playing, the service closes it and creates a fresh decoder
for the new route at the rendered position, so manual output switching continues
without interruption. A physical device removal also sends
`ACTION_AUDIO_BECOMING_NOISY`; that queued action pauses playback instead, and a
later Play recreates the invalid decoder from the retained position.

`PlaybackService` publishes the active item's title, artist, artwork, duration,
position, and transport state through the platform `MediaSession`. Android
System Now Playing (lock screen and Quick Settings) consumes that session; the
foreground notification is only the service companion.

The service also publishes its live `MediaSession.Token` through the Android-only
`AndroidPlaybackSession` registry. The fullscreen Output Switcher uses that token
on Android versions exposing the session-bound platform API, so Android displays
the Airmedy route rather than inferring another active session; it falls back to
the Android 14 generic switcher on earlier platform versions.

Seek requests clamp to the loaded duration and publish the target position to
the media session immediately. While playing, Android refreshes the published
position from PCM frames consumed by the AAudio callback, rather than decoded
frame timestamps: the decoder can fill the two-second ring buffer ahead of
audible sound, so using decoded PTS would make Now Playing and synced lyrics
advance early.

Native FFmpeg seeks use a preceding keyframe for decoder safety, then reset the
resampler and discard decoded PCM before the requested timestamp. This keeps
the audible position aligned with the seek bar after a crossfade promotion as
well as during normal playback.

Opening the Android System Now Playing card uses a `CLEAR_TOP | SINGLE_TOP`
activity intent. It brings the existing `MainActivity` task forward rather than
creating a second app UI session.

The queue contract below is implemented by the platform-neutral shared logic.
It intentionally matches desktop queue semantics where they are user-visible,
while excluding desktop analytics, Wails events, and desktop-database integration.

## Native Transitions and DSP

Android persists `crossfade_seconds` separately from the resumable queue
session (`0` = disabled; values are clamped to `0..12`). The current default is
off; `4` is the UI default when the user enables it. It also persists
`blend_artwork_during_crossfade`, which defaults on and changes only fullscreen
visuals, never audio. `PlaybackService` keeps a
read-only `PlaybackQueue.peekNext()` look-ahead and asks its native decoder to
preload that immediate next item after a hard load and after mutations which
can change the next item (insert-next, remove, reorder, shuffle, repeat).

The JNI boundary exposes load/preload status, idle-slot clearing, crossfade
begin/finish/snap, per-source gains, active/preloaded timing, output disconnect,
and a one-shot native transition event. At a natural end, the callback promotes
the preloaded source on the next audio frame. At crossfade start, it promotes
the incoming source immediately and uses equal-power `cos/sin` gains based on
the AAudio frame clock; the outgoing worker is reclaimed after the fade.
`PlaybackService` consumes that event outside the callback, advances the queue,
and publishes the incoming MediaSession metadata. A gapless promotion can
preload the new next item immediately. During a crossfade, both native source
slots are occupied by the incoming and fading-out items, so it defers that
preload until the callback retires the outgoing slot; this prevents the source
being faded out from being overwritten by the queue item after next.

Crossfade duration is captured when it begins and is capped by the outgoing
track's remaining rendered time, so its gain reaches zero before that source
ends; setting changes only resync the preload. Pause and seek snap/finish a
fade, while Stop and manual navigation snap it. `normalizationGainDb` is applied before mix. A thread-safe immutable
global DSP snapshot reserves preamp/EQ/stereo controls after mix; its initial
implementation is neutral/pass-through.

On every successful automatic crossfade start, `PlaybackService` publishes an
Android-only `ArtworkCrossfadeTransition` containing a monotonic ID, source and
destination artwork paths, and that effective duration. It clears the state at
fade completion or when playback is snapped. This separates visual lifecycle
from normal track-state changes so manual navigation never blends artwork.

## Ownership and Public Contract

Queue business rules belong in `sharedLogic`; they must be platform-neutral and
must not depend on Android services, Compose, or decoder APIs. Android owns the
adapter layer:

```text
Compose / ViewModel
        |
        v
PlaybackController ----> sharedLogic queue rules
        |
        v
PlaybackService ----> MediaSession, audio focus, local persistence, FFmpeg/AAudio
```

The queue operates on synced track IDs. `PlaybackService` resolves a selected
ID to a locally available `PlaybackItem` immediately before decoding it. A
queue snapshot exposes the active playback order, current track ID/index,
shuffle state, repeat mode, and the normal `PlaybackState` (including position
and duration when a track is loaded).

The target command surface is:

```kotlin
play(request)                 // replace queue in source order and start at startIndex
shuffle(request)              // replace queue, Fisher-Yates shuffle, and start at index 0
pause(); resume(); stop()
next(); previous(); seekTo(positionMs)
setShuffle(enabled)
setRepeatMode(mode)
clear()                       // stop playback and remove the entire queue
playNext(trackId); playNext(trackIds)
append(trackIds)
removeFromQueue(trackId)
reorderQueue(trackIds)
```

## Tracks-list playback integration

Tapping a row in Android's Library > Tracks screen replaces the queue with the
currently visible sorted list and starts the tapped track at its visible index.
The screen does not own queue state: it forwards the selected ID to its Android
ViewModel, which builds the shared `PlaybackRequest` and delegates to
`PlaybackController`. The player defaults to shuffle off and repeat off; its
queue command/state surface is available for a future player UI.

`PlaybackQueue`, `PlaybackRequest`, `PlaybackQueueSnapshot`, and `RepeatMode`
live in `sharedLogic` so native Android and future iOS playback adapters share
the exact same queue behavior. Android's service persists the queue snapshot
in private DataStore and restores available synced IDs without auto-playing.

`RepeatMode` has exactly three values: `off`, `one`, and `all`. A playback
request must be non-empty and its start index must be in range. A reorder list
must contain every active queue ID exactly once; otherwise the queue is not
changed.

## Queue Model and Invariants

The queue keeps two ordered ID lists and one cursor:

| Field | Meaning |
| --- | --- |
| `originalTrackIds` | Source order supplied by an album, playlist, or track list. |
| `activeTrackIds` | The order used for navigation: source order when shuffle is off, shuffled order when it is on. |
| `currentIndex` | Index in `activeTrackIds`; `-1` means no current track. |
| `shuffle` | Whether `activeTrackIds` is the shuffled order. |
| `repeatMode` | `off`, `one`, or `all`. |

Track IDs are unique within a queue. Every mutation keeps both lists as the
same set of IDs, and it preserves the current track by ID whenever that track
remains in the queue. The active list is the only order exposed to playback
controls and the queue UI.

The maximum queue size is 1000. Replacing a queue keeps the first 1000 source
items and clamps the start index. Starting shuffled playback first performs the
shuffle and then keeps its first 1000 items, so the cap acts as random
sampling; `originalTrackIds` retains those selected IDs in source order.

When an append or play-next operation would exceed the cap, trim existing
tracks before adding the incoming batch: remove the oldest history first, then
the farthest future entries. The selected current track is retained whenever
the cap is at least one. An incoming batch is itself limited to the available
capacity, reserving one slot for a current track.

## Navigation, Repeat, and Shuffle

Natural completion and the Next command use the same navigation rule:

| Mode | Next | Previous at queue boundary |
| --- | --- | --- |
| `off` | Advance; after the final track, stop playback. | At the first track, remain on that track. |
| `one` | Select and replay the current track. | Select and replay the current track. |
| `all` | Advance and wrap from the final track to the first. | Move back and wrap from the first track to the final. |

At the player-control level, Previous first restarts the current item when its
position is greater than three seconds. At three seconds or less it performs
the queue Previous action above. Stopping after repeat-off exhaustion leaves
the queue intact and retains the final item as a stopped/paused player state,
so its controls remain visible and Play reloads that selected track. This is
distinct from Clear Queue, which removes every entry and returns the player to
an idle state.

Shuffle uses Fisher-Yates:

- `play(request)` always replaces the queue in source order and turns shuffle off.
- `shuffle(request)` always replaces the queue, turns shuffle on, shuffles the
  complete supplied set, and starts the first shuffled item.
- Enabling shuffle during playback preserves already played history and the
  current item; only entries after the current index are shuffled.
- Enabling shuffle without a current item shuffles the entire queue and selects
  index zero.
- Disabling shuffle restores `originalTrackIds` and relocates the cursor to the
  same current ID.
- Repeat-all repeats the resulting shuffled order; it never reshuffles at the
  loop boundary.

## Queue Mutations

`playNext(trackId)` inserts an item immediately after the current item. Calling
it for the current item is a no-op; calling it for another ID already in the
queue moves that ID to the next position rather than duplicating it.
`playNext(trackIds)` preserves the supplied batch order. Both operations update
the source and active orders so later unshuffle remains deterministic.

`append(trackIds)` adds IDs at the tail without changing the current item or
the immediate next item. `removeFromQueue(trackId)` removes the ID from both
orders. Removing the active item starts its logical successor; if none exists,
only repeat-all wraps and every other repeat mode stops. Removing any other
item preserves the current ID and adjusts only the cursor as needed.

`reorderQueue(trackIds)` accepts a complete ordering of the active queue's
unique IDs, changes only the active order, and relocates the cursor to the
same current ID. While shuffled, this does not overwrite source order, so
turning shuffle off still restores the original order.

`select(trackId)` selects an existing active entry and returns a Play transition
without changing the active order, shuffle flag, or repeat mode. Android exposes
it as the fullscreen Queue row-tap action.

## Local Session Persistence

Player session state is private Android app data and is never synchronized to
the desktop application. Persist it after queue mutations and meaningful
transport changes (track change, pause, stop, seek, repeat, and shuffle
changes), and flush it during service shutdown. Fullscreen volume controls use
Android's system music stream, so they are not stored in this session.

The persisted snapshot contains both queue orders, the current track ID,
position, shuffle state, and repeat mode. On restore:

1. Resolve IDs only from the current synced local library and drop unavailable
   tracks while retaining the stored order.
2. If no source-order IDs remain, restore an empty idle queue; otherwise apply
   the 1000-item cap and restore the active order when valid, falling back to
   source order when it is not.
3. Restore the cursor by current ID. If it is unavailable, select the first
   remaining active item without loading it.
4. When the current item is available, load it, clamp the saved position to its
   duration, and expose a paused state. Restoration never auto-plays.

## Non-goals

This contract does not require gapless preload, crossfade, desktop analytics, or
desktop queue synchronization. Android's fullscreen player may locally derive a
blurred gradient from loaded artwork; it falls back to theme colours and does not
alter playback metadata or synchronize a palette. FFmpeg remains
the only mobile decoder/demuxer path; adding a Media3, ExoPlayer, or MediaCodec
fallback requires an explicit policy change.

## FFmpeg Build

`scripts/build-ffmpeg-android.sh` mirrors the desktop build scripts: it fetches
the FFmpeg 8.1 source tarball to a temporary cache, creates Android shared
libraries for `arm64-v8a` and `x86_64`, and leaves generated artifacts ignored.
The configuration enables FFmpeg's complete decoder/demuxer/parser registry,
but excludes programs, encoders, muxers, filters, devices, and network
protocols. It is LGPL-only; do not enable GPL/nonfree components without a
licensing review.
