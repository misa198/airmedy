# Android Compose UI

This catalog records the Android UI boundaries and contracts in `androidApp`.
Mobile does not share Vue, Tailwind, or UI code with desktop; iOS remains frozen
scaffolding.

## Ownership and data flow

```text
MainActivity (Android composition root)
        │ collects state, Android integrations, one-off effects
        ▼
App / AppUiState / AppDestinationModels / PlaybackModel
        │ immutable state + callbacks
        ▼
AppDestinationContent ── screens / shared composables
        │
        └── AppIntent → MainActivity / ViewModel → sharedLogic or Android adapter
```

`MainActivity` constructs dependencies, collects ViewModel and
`PlaybackController` flows lifecycle-aware, and handles Android-only effects:
system music volume, the media output switcher, Last.fm callback, and system
bars. It does not contain rendering logic.

`App.kt` is the Compose shell: theme, Haze host, stack/page state, chrome, popup
and bottom-sheet hosts, and the mini/fullscreen player. It receives immutable
models and emits `AppIntent`; do not put an Android service, Room, or network
dependency in a composable.

| Boundary | Contract |
| --- | --- |
| `AppUiState` | Selected destination, independent navigation stacks, and page/detail selection. |
| `AppDestinationModels` | State and callbacks per domain: Home, Insight, Library, and Settings. Avoid one app-wide callback bag. |
| `PlaybackModel` | Playback state, queue, and actions; player UI does not access the service directly. |
| `AppIntent` | Navigation, theme/accessibility preferences, and external navigation from the shell to the host. |
| Screen ViewModels | State transforms, query/sort, and app use-case calls. Android UI state belongs here; business rules belong in `sharedLogic`. |

## Navigation and shell

The four root destinations are Home, Insight, Library, and Settings. Each has
its own stack; switching tabs preserves the page in every other tab.
`AppDestinationContent` is the common router and owns the transition host and
nested-scroll signal. Page metadata (title and direction) belongs in
`ui/navigation/AppNavigationMetadata.kt`, not in individual screens.
During a stack transition, the outgoing page renders from its last `StateFlow`
snapshot until its animation completes; inactive flows remain unsubscribed.

`StackPageLayout` provides the screen frame, header/back affordance, and inset
contract. Screen content must use its `contentPadding`, never estimate floating
navigation or mini-player dimensions. Long lists use `LazyColumn` or
`LibraryVirtualList`, never an eager column.

`NavigationChrome` coordinates the floating navigation and mini player. The
shell owns their geometry, compact state, and fullscreen-player visibility.
`NavigationChromeScrollAccumulator` receives user scroll deltas to collapse or
expand chrome; a screen emits its delta through `AppDestinationContent` and
never controls chrome directly.

The fullscreen player is a shell overlay, not a destination. It consumes
`PlaybackModel`; its queue reorder and lyrics panels dispatch through playback
callbacks. Opening the Media Output Switcher is an Android host action, not a
platform API call from a composable.

## Theme, glass, and accessibility

`AirmedyTheme.kt` and `AirmedyColors` are the single source of truth for colour.
Composables use `LocalAirmedyColors.current` or `MaterialTheme`; feature code
must not introduce raw `Color(...)` values. Supported modes are System, Light,
and Dark. Primary remains rose; inactive controls use themed foreground rather
than an unrelated accent.

Glass uses `liquidGlassBackground` or existing glass primitives and applies Haze
only with a `hazeSource`. With `reduceTransparency`, the shell creates no Haze
state; a new feature must remain legible on that path. Strong blur belongs only
to persistent navigation, never each row or card. Backdrop sampling stays at
full resolution (`HazeInputScale.None`) in the shared helper, including header
and Back controls; per-screen downsampling causes visible low-resolution
artifacts. `LiquidGlassTest` guards the configured input scale on the host;
visual quality and GPU cost require an authorized device check.

Icons use `MaterialSymbol` and the Material Symbols Rounded font. Display text
and content descriptions come from Android resources. Interactions retain a
48dp minimum target, selected semantics, and state labels for Compose tests and
assistive technology. Preserve window and system insets through the shell rather
than hard-coded bottom padding.

## Screen families

| Area | Entry point | Preserve |
| --- | --- | --- |
| Home | `HomeContent` | State comes from the Home destination model; track actions use playback callbacks. Empty sections are omitted, and a one-track section uses one grid row. |
| Insight | `InsightContent`, `InsightViewModel` | Derived listening/library analytics; filters and periods stay in the ViewModel. |
| Library | `LibraryContent` and `Library*Content/ViewModel` | Virtualized lists; sort, filter, and page state belong to the ViewModel. |
| Details | `Album/Artist/Genre/Composer/PlaylistDetailsContent` | ID selection is in `AppUiState`; playback builds its queue through the ViewModel/controller. |
| Settings | `SettingsContent`, `AppearanceContent`, playback/sync/integration screens | Preferences and Android adapters are in the host/ViewModel, not shared UI. Integration persists LRCLIB/KuGou switches and a lyrics-source preference: desktop sync is the default; auto-fetch prefers Android provider cache and falls back to desktop. |
| Player | `MiniPlayer`, `FullScreenPlayer`, Queue/Lyrics panels | Render `PlaybackModel`; all mutations use playback actions. |

Mood Radio is enabled only by the active sync manifest's
`library_analysis_enabled` flag. A disabled replacement snapshot ends the
in-memory radio session and stops refills without changing the current queue.
The fullscreen track menu exposes Start Mood Radio only for tracks whose active
analysis includes energy, danceability, brightness, and tempo.

Playlist, favorite, and sync UI must not infer data from a desktop local database.
They read the Android mirror/state exposed by adapters. Context menus receive
action callbacks; they do not mutate Room or the queue themselves.
Favorites use the rounded star glyph throughout the player, playlist artwork
fallbacks, and track/album menus; the player star is filled only when selected. The track menu uses a star with
a minus for Remove from favorites and a plain star for Add. `MaterialSymbol`
composes the minus with the bundled star because the font has no star-minus glyph.

Normal playlist detail menus expose a reorder mode. Its grips emit one
`MOVE_TRACK` mutation when a drag ends; the Android optimistic projection
renders the resulting order while desktop reconciliation confirms it. Favorites
and smart playlists do not expose manual reordering.

While a replacement sync plan is staging, playlist details continue to render
the local playlist projection. That projection is removed only after the new
plan activates, when its synced replacement is already available.

The Sync settings page provides a single ActionList FAQ row and, when the active snapshot enables library analysis, its library-analysis percentage. It emits
`AppIntent.OpenExternalUrl`; the Android host owns opening the browser.

## Shared composables

`ui/components` is a reusable Android Compose layer, not a new mini design
system. Reuse the existing primitive before making another component:

| Need | Reuse |
| --- | --- |
| Page frame/header | `StackPageLayout`, `StackPageHeader`, `AirmedyBackButton` |
| Floating glass/buttons | `liquidGlassBackground`, `AirmedyIconButton`, `AirmedyPillButton` |
| Text input / slider / selection | `AirmedyTextField`, `AirmedyTrackSlider`, `Selection` |
| Rows/lists | `TrackRow`, collection rows, `LibraryVirtualList`, `InsetListDivider` |
| Modal/menu | `AirmedyBottomSheet`, `AirmedyDialog`, `AnchoredPopupMenu`, context menus |
| Artwork and status | `DetailHero`, `DiscCard`, `AirmedyPlayingIndicator`, `MaterialSymbol` |

`AirmedyDialog` supports both confirmations and one-action alerts. The app
shell renders the transient insufficient-storage sync failure above every
destination from `AndroidSyncState.Failed`; its Close callback resets the
runtime state to `Idle`, and the failure is never persisted.

Settings card groups use a consistent 12dp gap between adjacent cards.

Extract a component only when it has no domain state and already has at least
three call sites. Keep screen-specific composables beside their screen. A new
component accepts explicit state and callbacks; it does not accept a ViewModel
or Service for convenience.

The Albums Grid and Library Recently added section share `discGridItems` inside
virtualized `LazyColumn` rows. Each screen measures its available width and
subtracts its horizontal content padding before calling `discGridColumnCount`:
minimum card width 150dp, horizontal gap 12dp, and at least one column. Cards
share row width equally; empty slots keep the last row from stretching. Width
changes recompute the columns without resetting list state. Albums passes the
same column count to the alphabetical index so letter jumps target the correct
lazy row. Home/Search horizontal carousels retain their existing layouts.
`DiscGridTest` covers column thresholds and alphabetical row mapping; the two
Library screen Compose tests cover resize and incomplete rows.

`LibraryTextFilter` keeps its active edit buffer locally so delayed list
filtering cannot replay an older query into `BasicTextField` and disturb the
cursor. It still reports every edit to the owning ViewModel. The IME Done action
and taps outside the active field clear focus so Android dismisses the keyboard.
Library list and search empty states render only after their ViewModel receives
the first Room-backed snapshot; initial loading stays visually neutral.
When a page with a library search/filter input is popped from its destination
stack, the shell resets that page's query before it can be opened again.

`AirmedyMarqueeText` is shared by mini-player and fullscreen title/artist labels.
It uses Compose `basicMarquee`: overflowing text loops in one direction with a
32dp trailing gap, 20dp/s motion, and a three-second pause before each cycle.
Text that fits stays still, and system-disabled animations disable scrolling.
Changing text restarts the initial pause. Compose owns measurement, repeated
drawing, and animation cleanup. Only the original text is exposed to accessibility.
Mini-player and fullscreen title/artist labels enable its optional trailing alpha
mask when text overflows, softening the edge beside playback controls and
Favorite/More in expanded and compact modes.
The mask reveals the existing background and leaves fitting text unchanged.
`AirmedyMarqueeTextTest` covers overflow motion, its initial pause, fitting text,
and the trailing fade in light and dark themes.

## Player UI contracts

### Adaptive fullscreen layout

The `dev` product flavor overlays the current `PlayerLayoutMode` enum name in
an inset-safe top-right badge. It follows resize without reserving content space
or adding a tap handler. The `prod` flavor never renders this diagnostic badge,
including debug builds.

`FullScreenPlayer` owns the background, open/close animation, dismiss handling,
selected panel, and pending seek. Layout components arrange artwork, metadata,
panels and controls supplied as slots. Classification uses the current window's
available dp after `WindowInsets.safeDrawing` (including display cutouts), before
content padding; animation transforms do not affect detection.

| Selection order | Layout |
| --- | --- |
| Invalid/unmeasured dimensions | Phone |
| Width below 360dp | Square (narrow-window fallback) |
| Width at least 840dp, height at least 400dp, and width greater than height | Wide (two columns) |
| Remaining width greater than height | Square (compact fallback) |
| Remaining width at least 600dp and height at least 480dp | Phone |
| Remaining width/height from 0.8 through 1.25 inclusive | Square |
| Remaining windows | Phone |

`AdaptivePlayerContentTest` locks the 360dp minimum Phone width and the narrow
portrait fallback; height alone cannot select Phone below this width.

There are only three layout modes: Phone, Square and Wide. Small Flip covers
use Square; large square and portrait windows, including Fold inner screens,
use the same Phone layout. Wide requires landscape orientation and enough
space for both columns; landscape windows without that space use Square, never
Phone. The compact fallback uses Square's artwork backdrop and single-panel UI
instead of shrinking a separate cover above the Phone controls. Rotation
from 900×1200dp to 1200×900dp switches Phone to Wide, while 1000×1000dp stays
Phone. Breakpoints use actual available space, not inches, PPI, or model names.

Phone and Wide always show the volume row (slider and both icons), including
Main, Lyrics and Queue and after resize. The output switcher stays available.
The previous volume-hiding thresholds now compact spacing instead: Phone uses
`safeWidth / safeHeight >= 0.60`; Wide uses `(safeWidth / 2) / safeHeight >= 0.60`.
The gaps between seek, playback and the volume/footer group shrink from 16dp to
8dp, and the volume-to-footer gap shrinks from 4dp to 0dp. Controls retain their
touch targets and volume stays visible, releasing 20dp for artwork. Invalid
sizes keep normal spacing. The phone control stack aligns to the bottom above
safe-area padding; extra height must not spread these controls apart.

Phone keeps 20dp horizontal content padding on narrow or tall windows. When
safe width is at least 600dp and safe width/height is at least 0.60, it uses 10%
of safe width on each side in Main, Lyrics and Queue. Queue retains its own
20dp row/header insets inside that adaptive frame; compact metadata may extend
through the base 20dp right inset but not the extra adaptive margin. Layout
selection and compact-control spacing still use the original safe window size.
`AdaptivePlayerContentTest` guards padding thresholds; `AdaptivePlayerTest`
guards content margins across panels, resize and both themes.

Phone computes artwork size inside safe drawing insets and bottom padding,
reserving height for metadata and the measured control stack, including volume,
before choosing the cover's square size. Wider covers must not grow into the
volume or footer controls.
In Phone mode, expanded metadata sits immediately above the seek controls with
bottom spacing, and artwork is centered horizontally and vertically in the
remaining area above metadata with a minimum 24dp gap. Opening Lyrics/Queue keeps the compact top header
and panel layout. The same geometry applies to every window using Phone.
The short-screen artwork regression in `FullScreenPlayerTest` also covers the
475×751dp Fold cover profile. `AdaptivePlayerContentTest` covers landscape
selection and minimum dimensions; `AdaptivePlayerTest` covers compact Wide
artwork/control bounds in both themes and Square fallback. Its fixture sizes
represent safe space and add system insets to the outer player dimensions.

Wide places its drag handle above both columns, centered across the safe window.
Artwork and metadata have a minimum 32dp vertical gap, or 16dp horizontal gap
in the compact side-by-side arrangement.
The left column contains a square artwork sized to remaining height, metadata,
seek, playback buttons, volume, and Lyrics/Output/Queue buttons. Volume
is always visible, along with Output.
Below 480dp safe height, Wide places artwork beside metadata above the controls
and reduces outer spacing to 8dp. This preserves artwork height without reducing
control touch targets. Volume remains visible in this compact arrangement;
its height is reserved before sizing artwork. The 400dp minimum safe height
reserves room for this compact arrangement.
The right column always shows Lyrics or Queue. A null shared selection displays
Lyrics only in Wide without changing Phone/Square's Main selection. Tapping an
active panel button does nothing. Queue reordering stays in the right column,
leaving left controls visible; lyrics keep-awake and romanization work normally.

Wide supports swipe left for Next and right for Previous in the left column's
artwork/metadata region, using Phone's thresholds, haptics, and metadata drag
feedback. This applies to both the stacked and compact side-by-side arrangements
and remains available with either Lyrics or Queue open. The controls below and
the right panel do not receive track-swipe gestures. Resize, layout changes, and
fullscreen dismissal cancel active swipes; swipe-down still dismisses the player.

Square shows exactly one mode: Main (null selected panel), Lyrics, or Queue.
Only Main uses cropped artwork with a 60% themed dark overlay; Lyrics and Queue
keep the dominant-color backdrop. Main places metadata near the top and groups
seek/quality badge with three playback controls near the bottom, above the fixed
footer. Both groups are capped at 480dp with at least 24dp side insets. Square has
no volume/output or metadata More button. The top drag handle and bottom panel
buttons remain visible in every mode, including queue reordering. Tapping an
active button returns to Main; tapping the other switches directly. Content is
replaced before a 220ms fade-in, without overlapping modes.

Square Main supports swipe left for Next and right for Previous using Phone's
thresholds and haptic feedback. The entire content, including margins and gaps
between controls, is swipeable except the progress slider and button touch
targets (including disabled buttons and the interactive quality badge). Exclusion
is determined at pointer-down and remains in effect until release. Lyrics and
Queue do not support track swipes in Square. Panel/layout changes and dismissal
cancel transient swipes; vertical swipe-down still dismisses the player.


Back and swipe-down dismiss every layout. Layout changes do not dispatch playback
actions. The host's `SaveableStateHolder` retains the rendered Phone/Square/Wide queue/lyrics
viewport and browsing across layout visits and Activity recreation. Selected
panel is shared across layouts. Queue scroll is independent of the current track;
lyrics state is scoped to track and text. Initial queue positioning runs once per
panel opening, not on layout restoration. Pending seek confirmation runs in the
host even when controls are absent. Layout/size changes reset transient gestures
and context menus; queue commits after closing the panel/fullscreen are ignored. Lyrics keep-awake and romanization run only while their
UI is actually present.

`MainActivity` collects Jetpack WindowManager folding features with lifecycle
awareness, passing pixel bounds, orientation, half-open state, separation and
occlusion through the shell. Layout selection uses window size. Hinge-aware pane placement is not implemented. Never divide an OEM-reduced window a second time.

The Android host allows system-user rotation only while fullscreen is open or
its dismissal animation is running, then restores portrait. The shell restores
this policy and fullscreen system bars after Activity recreation; playback and
its service do not depend on layout or orientation. Android/OEM window policy can
override orientation requests, so layouts always use actual constraints.

Host tests: `AdaptivePlayerContentTest`, `PlayerOrientationTest` and existing
player helpers. Compose coverage: `AdaptivePlayerTest`, `FullScreenPlayerTest`,
`WidePlayerSwipeTest` (left-only swipes, independent right-panel interactions,
controls, and gesture cancellation), and fullscreen shell cases in `AppNavigationTest`. UI tests must only be run on
an emulator/device when explicitly authorized; building their APK is host-only.


`PlaybackState.showsMiniPlayer()` determines whether chrome shows the mini
player. The mini player and fullscreen player render the state published by the
service, so neither has local shadow state for transport or queue. Their swipe
handlers read the latest queue availability and transport callbacks after recomposition.
Long-pressing the mini player opens the current track's shared context menu;
it must not trigger the tap-to-open-fullscreen action or a swipe action.
Previous and next remain interactive at queue boundaries: previous rewinds the
current track while preserving play/pause state, and next retains it paused at
the beginning. Buttons and artwork/metadata swipes share this behavior.
Fullscreen seek has an optimistic pending position but must reconcile confirmed state using
`hasConfirmedSeekPosition`. The queue renders active order and scrolls to the
current track only when the panel opens; afterward it preserves the listener's
viewport. A tap dispatches select and drag commits the entire ordered-ID list
through `reorderQueue`, never by editing the snapshot.
Its centered seek-row quality badge uses the current `LibraryTrack` metadata and
appears only for Lossless, Hi-Res, or DSD playback when enabled in Playback
settings; the preference defaults to enabled and persists in DataStore.
Tapping it opens a centered metadata dialog with the quality icon and only the
available sample-rate, bit-depth, and codec values. The dialog is capped at
420dp including its 20dp side margins, so Wide keeps phone-sized content rather
than stretching across the window. `FullScreenPlayerTest` covers this width cap,
the theme variants, details and dismissal.
Playback settings also persists an off-by-default keep-screen-on preference.
The fullscreen player keeps the Android view awake only while its lyrics panel
is open and that preference is enabled; changing panels, closing fullscreen, or
disabling the preference releases it immediately, including when lyrics are absent.

Lyrics parsing and display helpers live in `FullScreenPlayerLyricsPanel.kt`.

Wide with at least 480dp safe height uses 28sp synced lyrics and 20sp secondary
or plain lyrics, with 38sp and 30sp line heights respectively. Karaoke text and glow use the
same typography. Compact Wide, Phone, and Square retain their existing sizes;
loading and empty states are unchanged. `AdaptivePlayerTest` covers typography
across resize, bilingual/plain lyrics, and karaoke layers.
Playback position is authoritative for the active line; browsing or dragging
only pauses auto-follow and changes playback only when a valid lyric tap
dispatches seek.
Enhanced LRC inline `<mm:ss.xxx>` timestamps are parsed into primary-word
segments for the active follow-mode row only. A shared lyrics clock advances
between playback samples and drives both the word sweep and active line, so
the last word completes at the next line boundary. Pauses freeze it and seeks
update it immediately;
ordinary or malformed timing retains normal line highlighting. Timing tags are
removed before display and romanization, while translations remain line-level.
Enhanced rows animate sung and unsung opacity independently over 320ms. While
following, the sung region has a short glow that fades after the word ends.
Lyrics Settings persists a glow switch in Android DataStore; it defaults off
and changes only the glow layer, leaving the timed word fill visible. The glow
follows the displayed playback position, stops in browse mode, and is disabled
when Android's animation scale is zero; the karaoke fill remains visible.
Leaving the active state holds its last displayed fill while both regions fade
to the inactive opacity; never replace that fill
with a fully highlighted line at the old active opacity. The two text regions
use complementary clips so translucent glyphs do not overlap and brighten.
Entering follow mode starts at the current playback position; browsing fades
both regions to full opacity. Blur remains animated. `FullScreenPlayerTest`
samples the presentation through outgoing/incoming fades and interrupted
browse transitions.

Chinese/Korean romanization is gated by the persisted Lyrics Settings switch,
which defaults off. Disabling it cancels conversion, restores the original
secondary lyrics, and hides the fullscreen toggle. While enabled, it runs
offline while fullscreen lyrics are visible and the lifecycle is started.
`RomanizationViewModel` owns inspection, cancellable conversion and current
results; its process-local show/hide preference defaults off and survives
Activity/player recreation, not process restart. The Android engine implements
the shared `RomanizationEngine` port; `RomanizeLyrics`
validates input and caches only the last successful result. Dictionaries load
lazily and are released after two idle minutes.

The panel sends only displayed primary lines and passes romanization separately
to its rows. Converted lines replace bilingual secondary text; unsupported or
failed lines retain it. Primary identity, timestamps and browse/seek state are
preserved. Secondary height changes reposition only auto-follow. The show/hide
button stays at the viewport's bottom-right, outside the scroll list. Active
styling matches the bottom lyrics control; inactive styling matches track More.
Loading does not disable toggling; after errors, toggling off/on retries. Han
defaults to Mandarin; kana-containing lines are unsupported. Host fixtures live
in shared/Android lyrics tests; the fullscreen Compose test covers replacement
and restoration of secondary text.

Bottom sheets may use `AirmedyBottomSheetStack`: only its top entry renders;
popping removes that entry while the entry below retains its data. Find Lyrics
uses it for a full-height, prefilled search sheet with leading input icons and
placeholders (not separate labels), and a lyric-content-only preview sheet.
Selecting
a candidate saves the Android provider cache. If that track is playing, the
shell uses the selection for the current playback session only; later plays
continue to follow the configured Desktop sync or Auto fetch preference.

## Tests and safe changes

Tests follow ownership:

| Change | Nearest test location |
| --- | --- |
| Pure navigation/player helper | `androidApp/src/test/.../ui/navigation` |
| ViewModel/state transform | `androidApp/src/test/.../ui/screens` |
| Reusable composable semantics | `androidApp/src/androidTest/.../ui/components` |
| Rendered screen behaviour | `androidApp/src/androidTest/.../ui/screens` |
| Shared business rule | `sharedLogic/src/commonTest` |

Add or update the test in the layer that owns the contract, not only a test
through `App`. Run the narrowest test first; the mobile baseline is
`./gradlew :sharedLogic:testAndroidHostTest` and `./gradlew :androidApp:assembleDebug`
from `mobile/`. Do not run an emulator or device unless explicitly authorized.

When navigation, theme, a screen/component contract, or a UI test pattern
changes, update this file in the same change. Pixel-level animation detail does
not belong here unless it is an accessibility, lifecycle, or data contract.
