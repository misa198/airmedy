# Android Player

This catalog maps Android playback ownership, command flow, and the invariants
that must remain true. It does not describe the desktop or iOS player.

## Architecture boundary

```text
Compose / ViewModel
        │  PlaybackRequest + commands; observes StateFlow
        ▼
PlaybackController
        │  explicit foreground-service intents
        ▼
PlaybackService ───── Android: audio focus, MediaSession, notification, DataStore
        │
        ├──── sharedLogic: PlaybackQueue, normalization, listening rules
        └──── AndroidPlaybackRuntime → JNI FfmpegDecoder → FFmpeg + AAudio
```

`sharedLogic` contains only platform-neutral queue and tracking rules. Android
resources, lifecycle, track-ID resolution, persistence, and native audio belong
to `androidApp`. UI must neither access the decoder nor mutate the queue itself.

| Component | Responsibility |
| --- | --- |
| `PlaybackController` | Android-only UI API; converts commands into service intents and exposes read-only state/queue flows. |
| `PlaybackService` | Sole owner of playback lifecycle, serialized queue mutation, audio focus, media session, notification, and session restoration. |
| `PlaybackQueue` | Shared queue state machine that operates only on unique track IDs. |
| `PlaybackItemResolver` | Resolves a synced track ID to its local file immediately before native loading. |
| `AndroidPlaybackRuntime` | Kotlin JNI adapter; contains no queue policy. |
| `FfmpegDecoder` / `ffmpeg_player.cpp` | Native decoding and mixing: FFmpeg demuxes/decodes and AAudio renders float PCM. |
| `PlaybackSessionStore` | Private DataStore persistence for the queue snapshot and position. |
| `AndroidPlaybackSession` | `MediaSession` token registry for the system Output Switcher. |

Do not add a Media3, ExoPlayer, or MediaCodec fallback without a policy change.
The only native path is FFmpeg → stereo float PCM → AAudio.

## State and command contract

UI observes `PlaybackService.state` (`PlaybackState`) and `queueState`
(`PlaybackQueueSnapshot`) through the controller. `PlaybackState` is the UI
source of truth for the current item, transport state, position, and duration;
the queue snapshot is the source of truth for playback order, selection, shuffle,
and repeat.

The controller accepts these commands:

```kotlin
play(request); shuffle(request)
pause(); resume(); stop(); clearQueue()
next(); previous(); seekTo(positionMs)
setShuffle(enabled); setRepeatMode(mode)
playNext(trackIds); append(trackIds)
selectQueueTrack(trackId); removeFromQueue(trackId); reorderQueue(trackIds)
```

`play` and `shuffle` replace the current queue; the other commands operate on
the active queue. UI settings write `PlaybackPreferences` directly for crossfade
and artwork blending. Editing a setting must not start the foreground service
when no track is loaded; a running service observes the setting and resynchronizes
its preload itself.

Every native or `MediaSession` callback returns to `PlaybackService`, never to
the UI. The service serializes commands and publishes state only after the queue
and native states agree.

## Queue contract (`sharedLogic/player/PlaybackQueue.kt`)

The queue maintains two orders of the same IDs and one cursor:

| Field | Meaning |
| --- | --- |
| `originalTrackIds` | Source order supplied by an album, playlist, or list. |
| `activeTrackIds` | Order used by Next, Previous, and the queue UI. |
| `currentIndex` | Index in `activeTrackIds`; `-1` means no selected item. |
| `shuffle`, `repeatMode` | Current playback modes. |

Invariants:

- Queue IDs are unique; both orders always contain the same set of IDs.
- The queue is capped at `MaxPlaybackQueueSize` (1,000). On overflow it retains
  the current ID, then trims history before the most distant future entries.
- Enabling shuffle randomizes only future items after the current one. Disabling
  it restores source order and relocates the current item by ID. Repeat-all
  never reshuffles at the loop boundary.
- `reorderQueue` accepts only a complete permutation of active IDs; invalid
  input is a no-op.
- `playNext` inserts a batch after the current item and `append` adds it at the
  end. Both move existing IDs rather than duplicate them. Reordering active
  order does not change original order.
- Removing the current item selects its logical successor. Only Repeat All wraps
  at the end; other modes stop. `clear` resets the queue, shuffle, and repeat.

`next` and natural end share the same `QueueTransition`. Repeat Off ends at the
last item while preserving the queue; the UI retains the selected item and Play
starts from the first active item. In the service, Previous restarts the current
item when position exceeds three seconds; otherwise it uses the queue rule.

Any semantic change here must update
`sharedLogic/src/commonTest/.../PlaybackQueueTest.kt`; do not duplicate queue
rules in a ViewModel or Composable.

## Native playback, gapless transitions, and crossfade

The native engine has two source slots (active/preloaded) and one AAudio stream.
Each source has its own FFmpeg, resampler, and worker; the audio callback uses
only atomic state and preallocated PCM. It must not lock, allocate, call JNI, or
perform I/O.

The service uses `PlaybackQueue.peekNext()` to preload the immediate successor
after a load or queue mutation. A natural end promotes the preloaded source; the
service consumes that transition outside the callback, advances the queue,
updates `MediaSession`, and preloads the new successor. Crossfade uses an
equal-power mix and occupies both slots, so the next preload waits until the
outgoing slot is free. Pause and seek finish a fade; Stop and manual navigation
snap it. The duration is capped by outgoing remaining time.

Displayed position comes from rendered PCM frames, not decoded timestamps: the
ring buffer can decode ahead of audible audio. Seek clamps to duration; native
seek starts at a preceding keyframe, resets the resampler, then discards PCM up
to the target.

`crossfade_seconds` is clamped to `0..12` (`0` disables it); the UI default when
enabled is 4. `blend_artwork_during_crossfade` affects only the fullscreen
`ArtworkCrossfadeTransition`, never audio. Normalization applies separate gain
to active and preloaded sources before mixing; see `catalog/normalization` for
the formula and data contract. Global DSP is currently pass-through and is not
where business policy belongs.

## Android lifecycle and system integration

`PlaybackService` owns:

- Audio focus. Focus loss and `ACTION_AUDIO_BECOMING_NOISY` use the same pause
  path; the receiver is unregistered when the service is destroyed.
- `MediaSession` metadata, position, and transport callbacks. The lock screen
  and Quick Settings use the session; the foreground notification is its service
  companion.
- Output-route recovery. If native audio reports a disconnected stream during
  playback, the service recreates the decoder at the rendered position. Physical
  unplug still pauses through the noisy broadcast.
- The `AndroidPlaybackSession` token, so Output Switcher selects the right media
  session.
- `ListeningTracker` and Last.fm. The rendered-position ticker is their input;
  Last.fm networking is best-effort and never blocks playback.

The service releases the native decoder, audio focus, media session, receiver,
coroutines, and notification with its lifecycle. UI must not retain any of these
resources.

## Session restoration

The private DataStore session contains a `PlaybackQueueSnapshot` and position.
It is never synchronized with desktop. When the service starts without a
queue-replacing command it:

1. Reads the session, drops track IDs no longer available locally, and validates
   the snapshot.
2. Resolves and loads the selected item, clamps position, and publishes paused
   state.
3. Never auto-plays.

A new Play or Shuffle always wins over restoration: cancel restore first so the
old DataStore read cannot delay `Preparing`. Corrupt JSON, an empty queue, or an
unloadable selected asset clears the session and returns to Idle; a stale session
must not break every later launch.

Persist after queue mutations and meaningful transport changes (track, pause,
stop, seek, shuffle, repeat), and flush during service shutdown.

## Change and verification map

| Change | Read and update |
| --- | --- |
| Queue behaviour | `PlaybackQueue.kt`, `PlaybackQueueTest.kt`, and this catalog. |
| Service/native lifecycle | `PlaybackService.kt`, runtime/decoder tests, and this catalog. |
| Fullscreen or mini-player control | The UI catalog and relevant navigation/UI tests. |
| Loudness or preamp | `catalog/normalization/README.md`. |

Run the narrowest applicable test, then `./gradlew :sharedLogic:testAndroidHostTest`
and `./gradlew :androidApp:assembleDebug` from `mobile/`. Before an Android build,
generate FFmpeg libraries with `bash ../scripts/build-ffmpeg-android.sh arm64-v8a`.
