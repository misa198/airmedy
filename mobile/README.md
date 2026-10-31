# Airmedy Mobile

Android-first Kotlin Multiplatform mobile app for Airmedy. The application is
currently independent from the desktop app; it does not share the desktop
database, Wails bindings, or Remote API.

## Status

- Android development is active and uses native Jetpack Compose UI.
- Android supports API 31 (Android 12) and newer; this baseline allows the
  floating navigation to use true backdrop blur.
- Android compiles and targets API 37 (Android 17).
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

On Android 17 and newer, open Sync Settings and allow local network access
before pairing or connecting to a desktop. If permission is permanently denied,
the Sync screen offers app settings. Permission is checked again when the app
resumes; granting it reconnects the saved QR route, while revoking it stops
discovery, MQTT, and active sync work. Pairing and downloaded music are retained,
so offline playback remains available. Android 12–16 need no new permission.

## Fullscreen player layouts

Playback Settings offers Solid and Living artwork (default) backgrounds.
Living artwork animates colors extracted from the playing cover, slows while
paused, and blends with song transitions when artwork blending is enabled.
It applies to Phone, Wide and Square Lyrics/Queue; Square Main always keeps its
cropped artwork background. The selection persists locally on Android.

The Android fullscreen player adapts to its available window size in dp after
system-bar and display-cutout insets. Safe widths below 360dp always use Square,
including tall, narrow cover-screen windows. It has three layouts: Square for small
near-square windows and landscape windows without room for two columns,
Phone for large square and portrait windows, and Wide for landscape windows at least
840dp wide and 400dp high. Wide requires width greater than height; rotating a
large portrait window can therefore switch Phone to Wide. Windows at least
600dp wide and 480dp high use Phone when square or portrait. Wide uses equal columns: artwork,
metadata and controls on the left; Lyrics or Queue on the right. Lyrics is the
default and the active panel cannot be closed. Its drag handle is centered above
both columns. Phone and Wide always show the volume slider and output switcher.
When width/height reaches 0.60 (using half the width in Wide), control gaps shrink
to free space for artwork instead of hiding volume.
At safe heights of at least 480dp, Wide centers both columns in a group capped at
1280dp. Artwork grows to the metadata/control group width or the remaining
height, whichever is smaller; it has no separate fixed size cap.
The column gap grows linearly from 0dp at 840dp safe width to 48dp at 1280dp,
then stays fixed. The background continues to fill the window.
Wide with at least 1280×720dp safe space also tops up each vertical system inset
to a minimum of 32dp before the existing content spacing; larger insets are
preserved. This provides breathing room on large windows such as DeX without
assuming a particular device or display density.
Below 480dp safe height, Wide keeps its full-width columns, reduces spacing and places artwork beside metadata
above the controls.

Wide supports swipe left for Next and right for Previous in the left column's
artwork/metadata region, using Phone's thresholds, haptics, and metadata drag
feedback. This applies to both the stacked and compact side-by-side arrangements
and remains available with either Lyrics or Queue open. The controls below and
the right panel do not receive track-swipe gestures. Resize, layout changes, and
fullscreen dismissal cancel active swipes; swipe-down still dismisses the player.

Wide at least 480dp high uses slightly larger lyrics: 28sp synced lyrics and
20sp secondary/plain lyrics. At 1280×720dp safe space or larger, both lyric font
sizes and line heights scale by 1.6 (44.8sp synced and 32sp secondary/plain).
Resizing below either threshold restores regular Wide typography.
Compact Wide, Phone, and Square keep their sizes.
Smaller near-square windows show one of Main, Lyrics, or Queue, with a persistent
top drag handle and bottom Lyrics/Queue buttons. Main places metadata near the
top and seek/playback controls near the bottom over cropped, darkened artwork;
Lyrics and Queue retain the colored player backdrop. Tapping the selected panel
button returns to Main. Square has no volume slider or output switcher.

Square Main supports swipe left for Next and right for Previous using Phone's
thresholds and haptic feedback. The entire content, including margins and gaps
between controls, is swipeable except the progress slider and button touch
targets (including disabled buttons and the interactive quality badge). Exclusion
is determined at pointer-down and remains in effect until release. Lyrics and
Queue do not support track swipes in Square. Panel/layout changes and dismissal
cancel transient swipes; vertical swipe-down still dismisses the player.

Phone retains volume in Main, Lyrics and Queue at every window size.
Its horizontal content padding stays at 20dp on narrow or tall windows. At safe
widths of at least 600dp and width/height of at least 0.60, padding becomes 10%
of safe width per side across Main, Lyrics and Queue.
Large square windows use
the same Phone artwork and metadata placement. Landscape windows below the
Wide minimum size use Square, avoiding a tiny Phone cover above the controls.
Detection does not depend on device brand, physical inches, or hinge angle.
Fold posture does not split the player into hinge-aware panes yet.
The Android app stays portrait below 600dp smallest width. At 600dp and above
(tablets and large foldable screens), it follows the system auto-rotate setting.
This policy applies throughout the app, including fullscreen playback. Back and swipe-down close every player layout.

## Prerequisites

- JDK 11, as configured by the Gradle modules.
- Android SDK Platform 37 stable and an Android device or emulator for runtime testing.
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

### Android update notices

The `prod` flavor checks the repository's release manifest at startup, independently
of the landing-page domain. A newer Android version opens a dismissible dialog
with its version and rendered Markdown release notes, including images; Update
opens that release in the browser.
Each version is persisted before presentation and is never announced twice while
that app data remains. The `dev` flavor does not check. Errors are logged without
blocking startup and can be retried at the next app session.

The shared `CheckForAppUpdate` use case owns version comparisons and eligibility;
Android owns HTTP, Preferences DataStore, ViewModel state and Compose UI.
See [release discovery](../releases/README.md) for the manifest contract and the
**Bump Landing Download Version** workflow that publishes its pointers.

### FFmpeg player build

Android playback uses FFmpeg directly for local synced audio: FFmpeg demuxes and
decodes every enabled music format, then the native player sends float PCM to
AAudio. It does not use a Media3 or platform-decoder fallback. All Android app
variants require `../scripts/build-ffmpeg-android.sh arm64-v8a` to have run
first. It downloads the pinned FFmpeg 8.1.2 tarball to a temporary cache and writes generated
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

## Offline lyric romanization

Lyrics Settings also has a Lyrics glow switch. It defaults off and persists on
Android; turning it off removes the enhanced LRC word glow while keeping the
timed word highlight.

Lyrics Settings keeps Chinese and Korean romanization disabled by default;
enabling it reveals a floating show/hide button in the fullscreen lyrics panel.
It works offline with synced or plain lyrics and preserves
the original text and timing. Converted text replaces the bilingual secondary
line; disabling it restores that line. The choice lasts for the app process and
defaults off at the next launch. Han-only lyrics use Mandarin; Japanese/kana
lines are unsupported. Names can have different readings.

Shared logic owns validation, conversion contracts and Korean rules; Android
owns dictionary loading, Unicode normalization and presentation. The build
packages pinned desktop phrase/OpenCC data without requiring a desktop
connection. Provenance and the optional regeneration command are in
[`tools/romanization/ATTRIBUTION.md`](tools/romanization/ATTRIBUTION.md).

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
