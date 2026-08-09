# Android Compose UI Catalog

This catalog documents the Android UI owned by `mobile/androidApp`. It does not
describe the desktop Vue UI or future iOS UI.

## App shell and navigation

- `App.kt` is the app shell: it owns the theme, shared Haze/list state, header,
  and placement of the navigation chrome. `ui/navigation/AppDestinationContent.kt`
  routes each destination's independent `AppStackPage` stack, and
  `ui/navigation/FloatingNavigationBar.kt` owns its visual and gesture behavior.
  Individual page content lives in `ui/screens/`. The navigation's shared
  selection pill is also an active-foreground mask: a primary-coloured duplicate
  of the icons and labels is clipped to the pill, so only the exact covered
  portions of an icon or label become active while it moves.
- The Android app shell follows unidirectional data flow: `MainViewModel`
  exposes immutable `AppUiState`, `App.kt` emits the sealed `AppIntent` through
  one callback, and the ViewModel reduces navigation/theme inputs. One-time
  host work is emitted as `AppEffect`; `MainActivity` collects it and performs
  Android-only actions such as opening an external URL. Feature state remains
  in Android ViewModels; `sharedLogic` does not contain UI state or effects.
- `ThemeModeStore` is the Android-local settings port consumed by the app
  shell. `ThemePreferences` implements it with DataStore, while tests use an
  in-memory implementation.
- Header title animation keys include both destination and current stack page.
  Stack changes retain the title slide while disabling size interpolation; the
  header always reserves one action slot, preventing a title-width reflow when
  controls disappear. Switching bottom-navigation destination stacks changes
  the title and content without animation; push/pop inside a destination stack
  animates content and header title in horizontal sync using directional slides,
  opaque page surfaces, and Z-index ordering to prevent ghosting/overlap.
- The Settings root shows one card-contained action list for Appearance, Sync,
  Playback, Integration, and About. Appearance opens `SettingsAppearance`, Sync
  opens `SettingsSync`, and About opens `SettingsAbout` in the Settings stack;
  Playback and Integration remain presentational.
- About has an informational hero card using the desktop-derived
  `airmedy_about_app_icon` drawable, app name and description, followed by an
  iconless, card-contained action list. Its version is static build metadata;
  GitHub and GPL-3.0 license rows delegate opening their URLs to the Android host.
- Appearance contains vertically arranged sections, each in its own `Card`.
  Its Theme section uses `Selection`, the reusable iOS-style dropdown row, to
  persist the System, Light, or Dark theme choice.
- Sync opens `SettingsSync` in the Settings stack. `SyncViewModel` owns pairing
  state from the shared pairing use case: no device shows the empty hero and a
  glass Add-device header action; a pending request shows an approval hero; a
  paired device uses a HeroCard with a Lucide computer icon, bold QR-provided
  desktop name, and a green Online/red Offline MQTT session badge. Offline
  guidance says the desktop is ready to connect and instructs the user to start
  Broadcast on desktop; the screen connects automatically. While this screen is
  visible, and only while a trusted desktop is Offline, Android browses its
  mDNS broadcast; entering the scanner or leaving the screen stops browsing and
  releases the multicast lock. A saved QR route gets one connection attempt at
  app start, without a background retry loop; foreground discovery is the only
  source of reconnect attempts. The MQTT session remains connected after leaving
  the screen, but its transient discovery endpoint is never persisted or reused
  for discovery outside this lifecycle. Its separate destructive Revoke button
  opens the shared mobile dialog. LAN host and port are used only for the
  temporary pairing transport and are never displayed.
  The action opens `SettingsSyncScanner`, with a centred rounded QR viewfinder,
  descriptive scan guidance, and an image-picker fallback decoded by ML Kit for
  devices whose camera is unavailable.
  Its session identifies itself to desktop as `airmedy-sync-<desktop-id>-<mobile-id>`;
  this is an internal transport detail, never shown in UI. Revoke is deliberately local-only: it clears the mobile binding and permits a
  new desktop scan, while the old desktop remains trusted until revoked there.
- A valid desktop library-sync request starts Android's foreground transfer
  service. Sync Settings renders its preparing/progress/completed/failed state inside a dedicated `Card` featuring a top gap, primary `LinearProgressIndicator` progress bar, status text, and percentage indicator;
  the system notification remains the background control surface and provides
  Cancel. Revoke stops the transfer before deleting all mirrored library data.
- Library root page displays a plain `ActionList` (`ActionListContainerStyle.Plain`) with actions for Artists,
  Albums, Tracks, Genres, and Composers. Tapping Tracks opens `LibraryTracks` on the Library stack.
- `LibraryTracks` renders a virtualized `LazyColumn` of tracks with sorting controls (Name, Artist, Play count, Date added; ASC/DESC). Tapping a track delegates its ID to `LibraryTracksViewModel`, which starts playback from a queue built from the visible sorted order; the overflow action remains independent.
- Home content is supplied by `HomeDemoContent`. A forward action calls the
  callback provided by the app shell, which pushes `HomeSampleDetail`; Android
  Back pops that destination stack while the floating navigation remains shown.
- Tapping the selected navigation destination restores that destination stack
  to `Root`; on Home it also animates the root `LazyColumn` back to its first item.
  Tapping another destination continues to switch stacks without resetting it.
- `NavigationChrome` owns the floating navigation plus its optional mini player,
  so later animations can treat them as one persistent navigation unit. The mini
  player is a 56dp glass pill positioned 8dp above the navigation and uses the
  same max width, border, blur, and safe-area placement. It appears for
  Preparing, Playing, and Paused playback, reserving matching page-bottom space;
  it is absent for Idle and Failed states. It presents 48dp square artwork with
  a 10dp radius with an 8dp left inset (or the Lucide music fallback), marquee title/artist labels,
  and larger, sharp-cornered filled Lucide Previous, Play/Pause, and Next controls with
  visually overlapping 48dp touch targets. Preparing disables its play/pause control. Its
  title/artist area also accepts horizontal transport
  gestures: a left swipe advances to Next and a right swipe invokes Previous. The metadata
  follows the drag inside a clipped metadata viewport, so it cannot overlap artwork or
  controls; it snaps back after release and emits one confirmation haptic only when the swipe
  reaches the distance or velocity threshold and dispatches a transport command. Horizontal
  gestures are scoped to metadata so they do not conflict with the pill's vertical
  fullscreen/dismiss gestures or its control buttons. Tapping the mini player has no ripple.
  Tapping its non-control surface opens the temporary `FullScreenPlayerPlaceholder`.
  Pulling upward moves the mini player up by its full 56dp height while fading it out.
  The edge-to-edge overlay follows that pull continuously from the bottom and
  reaches full opacity while the mini player fades away. Releasing after any
  upward drag completes the transition to the fullscreen player. Once
  released, it uses the slower 520ms slide/fade only to complete the current
  partial transition. Pulling the mini player down by 36dp keeps it sliding
  off the bottom of the screen without rebounding while playback stops and clears the
  persisted queue. Tap and vertical drag are separate consumed gesture paths,
  so a slow downward dismiss or a cancelled drag can never be reinterpreted as
  an open-fullscreen tap. Every pointer-down clears any interrupted fullscreen
  pull state before classifying the new gesture. Once a vertical gesture crosses
  touch-slop, its initial direction is locked: a downward drag can only dismiss
  or settle the mini player, never transition to fullscreen.
  The pill remains visually below navigation while dismissing. Its drag receiver
  stays anchored at the initial mini-player position, so the same downward drag
  continues even after the pill passes behind the navigation.
  The placeholder has no player controls yet; pulling it down by 96dp or pressing
  system Back slides it down and returns to the unchanged navigation/page state.
  Every new open clears any residual downward-dismiss offset before expanding,
  so consecutive opens always finish flush with the top of the screen.
- While the mini player is visible, the app shell observes user-driven nested
  scrolling from any page content. A content-upward delta switches the chrome
  to its 56dp compact row: a left-side circular button for the active destination
  and the mini player to its right. The compact player keeps artwork, title/artist,
  and Play/Pause plus Next; Previous fades out, then its vacated slot is reclaimed
  by the title/artist while Play/Pause and Next remain trailing controls. A downward delta, tapping
  the active-destination button, changing pages, or dismissing the mini player
  restores the standard 72dp four-tab navigation with the full mini player above
  it. This is one shared chrome layout: navigation width/height, mini-player
  width, and its X/Y position animate together over 280ms, so the player shrinks
  and slides into the compact row rather than a replacement bar appearing. Its
  16dp horizontal content inset and 38dp artwork remain fixed while moving. Page
  bottom padding animates with these chrome layouts. During expansion, the compact
  active icon remains left-aligned until the navigation surface reaches 85% of its
  full width; its opacity cross-fades with the four icon/label targets over 160ms,
  while the full targets rise 8dp and scale from 96% to 100%. All chrome geometry
  uses one shared 420ms eased transition, preventing labels and icons from being
  compressed or appearing abruptly on slower GPUs. The compact target fills the
  navigation's padded content bounds rather than requesting its own fixed width,
  preventing end-of-transition clipping as the 56dp capsule settles. Its Haze background keeps a
  stable full-width render surface while the visible capsule is clipped horizontally,
  avoiding blur re-render work on every compact-transition frame; the mini player
  uses the same stable-surface approach while it narrows.
  Direction changes have a 24dp hysteresis threshold: a short reversal or a few
  pixels of incidental scroll never toggles the compact chrome.
- Shared page chrome is in `ui/components/StackPageLayout.kt`; it owns safe-area
  content padding and the optional glass Back button.

## Shared components

| Component | Contract |
| --- | --- |
| `Card` | Standard 28dp, borderless, opaque themed card surface. It accepts slot content and optional padding; its title/description overload remains a tappable primary-action card. |
| `HeroCard` | A non-interactive informational card with a 40dp decorative icon, bold `titleLarge` title, optional content directly below its title, and muted description. Sync uses this slot for its MQTT Online/Offline badge, aligned with the desktop name. |
| `TrackRow` | Displays a track row with 48dp rounded artwork (or fallback glass icon), 2-line title/artist text display, and a trailing `...` overflow button. |
| `ActionList` | Displays 56dp `ActionListItem` rows with optional leading Lucide drawable, resource-backed label, optional trailing composable slot, and a chevron only for clickable rows without a supplied trailing slot. `FullWidth` and `InsetForLeadingIcon` divider styles are available. `Card` uses the shared `Card` surface; `Plain` has no enclosing surface. A row is clickable only when its item has `onClick`. |
| `Selection` | Renders an iOS-style dropdown row and custom elevated menu with a 28dp radius for mutually exclusive `SelectionOption` values. Its selected value uses the standard row typography with the muted action colour; each menu option is at least 44dp tall. Only the right-side action slot opens and anchors the right-aligned menu; the opaque, rounded menu expands and collapses vertically while fading and scaling over 220ms. The caller owns selected state and receives the selected value through `onValueSelected`. |
| `StackPageLayout` | Places content below the status/header region and above the persistent navigation; screens must use its supplied padding. Its page-header title uses bold `headlineLarge` typography. |
| `AirmedyGlassIconButton` | A 48dp circular blurred glass icon button with border and button semantics. Back and header actions use this shared primitive. |
| `AirmedyPillButton` | A borderless 52dp minimum-height capsule action. `Primary` and `Destructive` use the primary background with the explicit white `onPrimary` token in both light and dark themes; `Secondary` uses the stronger `buttonSecondary` theme surface with normal foreground text. Its label supplies button semantics. |
| `AirmedyDialog` | A 36dp-radius, two-action mobile dialog. Its text content has 20dp horizontal inset; the button area has a thinner 16dp horizontal/bottom inset. It supports `Horizontal` and `Vertical` action layouts; the left/top action always dismisses with `Secondary`. |

## UI rules

- Use `AirmedyTheme` and `LocalAirmedyColors`; feature composables do not add raw colours.
- Use Lucide Android drawables and resource-backed strings. Decorative icons have no content description; actionable rows expose their label through Compose semantics.
- Keep interactive targets at least 48dp. Rows and cards delegate click behavior through callbacks rather than storing navigation state.
- Card surfaces are borderless unless a feature explicitly requires a border.

## Testing

- Android UI changes require Compose instrumentation coverage in
  `androidApp/src/androidTest` plus `:androidApp:assembleDebug`.
- Test component interaction and screen-level navigation separately. `ActionListTest`
  covers optional callbacks and iconless trailing slots; `AppNavigationTest` covers
  Home-to-detail and Settings-to-About navigation.
- `MiniPlayerTest` covers playback visibility, transport controls, metadata left/right swipe
  transport dispatch, and the upward fullscreen-opening gesture in both full and compact
  navigation-chrome layouts.
  `AppNavigationTest` covers the fullscreen overlay's tap-open, downward-dismiss,
  and system-Back behavior.
