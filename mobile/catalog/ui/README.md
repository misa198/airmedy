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
to persistent navigation, never each row or card.

Icons use `MaterialSymbol` and the Material Symbols Rounded font. Display text
and content descriptions come from Android resources. Interactions retain a
48dp minimum target, selected semantics, and state labels for Compose tests and
assistive technology. Preserve window and system insets through the shell rather
than hard-coded bottom padding.

## Screen families

| Area | Entry point | Preserve |
| --- | --- | --- |
| Home | `HomeContent` | State comes from the Home destination model; track actions use playback callbacks. |
| Insight | `InsightContent`, `InsightViewModel` | Derived listening/library analytics; filters and periods stay in the ViewModel. |
| Library | `LibraryContent` and `Library*Content/ViewModel` | Virtualized lists; sort, filter, and page state belong to the ViewModel. |
| Details | `Album/Artist/Genre/Composer/PlaylistDetailsContent` | ID selection is in `AppUiState`; playback builds its queue through the ViewModel/controller. |
| Settings | `SettingsContent`, `AppearanceContent`, playback/sync/integration screens | Preferences and Android adapters are in the host/ViewModel, not shared UI. |
| Player | `MiniPlayer`, `FullScreenPlayer`, Queue/Lyrics panels | Render `PlaybackModel`; all mutations use playback actions. |

Playlist, favorite, and sync UI must not infer data from a desktop local database.
They read the Android mirror/state exposed by adapters. Context menus receive
action callbacks; they do not mutate Room or the queue themselves.

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

Extract a component only when it has no domain state and already has at least
three call sites. Keep screen-specific composables beside their screen. A new
component accepts explicit state and callbacks; it does not accept a ViewModel
or Service for convenience.

`LibraryTextFilter` keeps its active edit buffer locally so delayed list
filtering cannot replay an older query into `BasicTextField` and disturb the
cursor. It still reports every edit to the owning ViewModel. The IME Done action
and taps outside the active field clear focus so Android dismisses the keyboard.
Library list and search empty states render only after their ViewModel receives
the first Room-backed snapshot; initial loading stays visually neutral.
When a page with a library search/filter input is popped from its destination
stack, the shell resets that page's query before it can be opened again.

## Player UI contracts

`PlaybackState.showsMiniPlayer()` determines whether chrome shows the mini
player. The mini player and fullscreen player render the state published by the
service, so neither has local shadow state for transport or queue. Their swipe
handlers read the latest queue availability and transport callbacks after recomposition.
Fullscreen seek has an optimistic pending position but must reconcile confirmed state using
`hasConfirmedSeekPosition`. The queue renders active order; a tap dispatches
select and drag commits the entire ordered-ID list through `reorderQueue`, never
by editing the snapshot.

Lyrics parsing and display helpers live in `FullScreenPlayerLyricsPanel.kt`.
Playback position is authoritative for the active line; browsing or dragging
only pauses auto-follow and changes playback only when a valid lyric tap
dispatches seek.

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
