# Android Player and Queue

## Scope and Status

Android playback is independent from the desktop player. `PlaybackController`
is the Android-only command boundary; Compose UI supplies a synced track-ID
queue and observes its `StateFlow<PlaybackState>`.

`PlaybackService` owns audio focus, the platform `MediaSession`, notification,
and the JNI `FfmpegDecoder`. The native decoder opens the private synced file,
uses FFmpeg for demuxing and decoding, converts samples through
`libswresample` to stereo float PCM, and sends them to AAudio through a bounded
ring buffer. There is no Media3, ExoPlayer, or `MediaCodec` decoder fallback.

`PlaybackService` publishes the active item's title, artist, artwork, duration,
position, and transport state through the platform `MediaSession`. Android
System Now Playing (lock screen and Quick Settings) consumes that session; the
foreground notification is only the service companion.

Seek requests clamp to the loaded duration and publish the target position to
the media session immediately. While playing, Android refreshes the published
position from the decoder so System Now Playing converges on the actual seek
position.

Opening the Android System Now Playing card uses a `CLEAR_TOP | SINGLE_TOP`
activity intent. It brings the existing `MainActivity` task forward rather than
creating a second app UI session.

The queue contract below is implemented by the platform-neutral shared logic.
It intentionally matches desktop queue semantics where they are user-visible, while excluding
desktop-only gapless playback, crossfade, listening analytics, Wails events,
and desktop-database integration.

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
the queue intact so Play can reload the selected track.

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
