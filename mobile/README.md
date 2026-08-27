# Airmedy Mobile

Android-first Kotlin Multiplatform mobile app for Airmedy. The application is
currently independent from the desktop app; it does not share the desktop
database, Wails bindings, or Remote API.

## Status

- Android development is active and uses native Jetpack Compose UI.
- Android supports API 31 (Android 12) and newer; this baseline allows the
  floating navigation to use true backdrop blur.
- `sharedLogic` is the future cross-platform business-logic module.
- iOS is intentionally frozen. Do not modify `iosApp` or iOS targets unless a
  task explicitly enables iOS work.

See [AGENTS.md](AGENTS.md) for the mandatory engineering rules and cleanup
sequence.

Android Compose UI architecture and reusable components are documented in the
[mobile UI catalog](catalog/ui/README.md).

## Module boundaries

```text
androidApp
  Android-only Compose screens, ViewModels, navigation, resources, and adapters
      |
      v
sharedLogic
  Common models, use cases, ports, validation, and business rules
```

## Navigation

The floating Home, Insight, Library, and Settings destinations each own an
independent Android UI stack. Switching destinations preserves the currently
open page in every other stack. Stack changes within a destination use the
standard page transition; switching destination stacks changes the title and
content together while the floating navigation remains in place.

`sharedLogic` must remain UI- and platform-neutral. Android implements its ports
inside `androidApp`. When iOS work is authorized, it will have native SwiftUI and
its own adapters while using the same shared business contracts.

The mobile pairing and library-sync protocols are implemented in `sharedLogic`:
QR parsing, MQTT topic construction, signed request/response validation, sync
request/receipt validation, and transfer ordering. Android supplies Room mirror
storage, authenticated HTTP asset pulling, MQTT, notifications, and the
`dataSync` foreground service. The service temporarily borrows the app-owned
MQTT session during an active transfer, then returns it connected, so the
desktop starts a plan only after Android is already online.
Android supplies camera, encrypted identity storage, and Compose UI adapters.
If the active playback track is absent from an activated library snapshot,
Android stops playback and clears its queue.
Android makes one opportunistic MQTT connection using
the QR-verified route when the app starts, without background retries. Its
connection state drives the desktop Online/Offline badge and provides the
transport boundary for library sync messages. When an already trusted desktop is
Offline, Android browses for its short-lived mDNS broadcast only while Sync
Settings is visible; a discovered route is transient and never replaces the
QR-verified saved endpoint. See the repository pairing catalog for the wire
contract.

Each activated library snapshot also carries `library_analysis_enabled`. It is
the Android source of truth for Mood Radio: existing analysis documents never
enable radio by themselves, and changing the desktop setting takes effect on
the next completed sync.

## Prerequisites

- JDK 11, as configured by the Gradle modules.
- Android SDK Platform 36 and an Android device or emulator for runtime testing.
- Android Studio is recommended for running and inspecting the Android app.

## Commands

Run from `mobile/`:

```bash
./gradlew :androidApp:assembleDevDebug
./gradlew :androidApp:assembleProdDebug
./gradlew :sharedLogic:testAndroidHostTest
```

The Android app has two installable variants: `dev` uses application ID
`me.misa198.airmedy.dev`; `prod` uses `me.misa198.airmedy`. This allows both
variants to be installed on the same device.

### Android versioning

Android releases use a three-part `versionName` and a monotonically increasing
`versionCode`. To bump both values, run this from the repository root:

```bash
task bump-mobile-version VERSION=0.0.2
```

The command accepts only `X.Y.Z` versions and increments `versionCode` by one.

### FFmpeg player build

Android playback uses FFmpeg directly for local synced audio: FFmpeg demuxes and
decodes every enabled music format, then the native player sends float PCM to
AAudio. It does not use a Media3 or platform-decoder fallback. All Android app
variants require `../scripts/build-ffmpeg-android.sh arm64-v8a` to have run
first. It downloads the pinned FFmpeg 8.1 tarball to a temporary cache and writes generated
headers and `.so` files below `androidApp/build/` and `androidApp/src/main/jniLibs/`.
Those artifacts are ignored by Git. The build requires Android NDK 30.0.15729638.

For a manual rebuild, run from the repository root:

```bash
bash scripts/build-ffmpeg-android.sh arm64-v8a
```

The FFmpeg build enables the complete upstream decoder/demuxer/parser registry,
so music never falls back to Android decoding. It stays smaller than a full
distribution by excluding programs, encoders, muxers, filters, devices and
network protocols. It is LGPL-only: it does not enable GPL or nonfree components.
Release attribution must provide the matching FFmpeg source archive, configure
line, and any patches.

Or, from the repository root:

```bash
./mobile/gradlew :androidApp:assembleDevDebug
./mobile/gradlew :androidApp:assembleProdDebug
./mobile/gradlew :sharedLogic:testAndroidHostTest
```

The repository-root `task verify` command covers desktop Go/Vue code only.
Mobile changes must run the relevant Gradle build and tests above, plus any
feature-specific Android host or UI test task.

## Last.fm

Android authenticates independently from desktop through the browser and the
`airmedy://lastfm/auth` callback. Supply its dedicated API credentials in the
Git-ignored `mobile/local.properties` file (environment variables remain the CI
fallback):

```properties
LASTFM_API_KEY=your_key
LASTFM_API_SECRET=your_secret
```

Builds without them remain usable, but the Last.fm Connect action is disabled.

The session key is encrypted with Android Keystore before DataStore persistence.
Now Playing, scrobbling, and individual favorite love/unlove requests are sent
directly from Android; they never depend on a paired desktop.

## Listening tracking

Android independently records actual playing time, qualified plays, and
completed/skipped/stopped attempts in Room. Raw records are retained for 180
days and daily origin-tagged aggregates are retained for all-time totals. A
manual Library Sync exchanges this data in both directions: Android uploads
only rows owned by its mobile identity, while desktop returns the union from
all synchronized devices. Repeated syncs are idempotent.

## Playlist reconciliation

Playlist reconciliation arrives on a separate MQTT stream from Library Sync
asset downloads. Android persists playlist deltas in Room and retains them
until a terminal desktop result (`applied`, `duplicate`, `stale`, `rejected`,
or `scope-conflict`) acknowledges the mutation.
When switching away from a playlist scope, a local-only playlist that receives
`scope-conflict` is removed with its queued mutations.
When a playlist deletion is `applied` or `duplicate`, its local projection
remains in effect until the replacement library snapshot activates, so a
deleted item cannot briefly reappear while assets download. Rejected or stale
deletions remain available to the next authoritative snapshot.

Artwork staging is stored in Room with its SHA-256, MIME type, byte size, and
app-private relative path. It is verified and uploaded before `SET_ARTWORK`,
and mutations are acknowledged only after publishing the signed MQTT result.

Favorites use the same reconciliation request: the fullscreen player queues an
optimistic `SET_FAVORITE` mutation and the next desktop Sync applies it before
returning the new scoped library snapshot.
