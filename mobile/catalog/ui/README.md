# Android Compose UI Catalog

This catalog documents the Android UI owned by `mobile/androidApp`. It does not
describe the desktop Vue UI or future iOS UI.

## App shell and navigation

- `App.kt` is the app shell: it owns the theme, shared Haze/list state, header,
  and placement of the floating navigation. `ui/navigation/AppDestinationContent.kt`
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
  the title without animation; push/pop inside a destination keeps the slide.
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
  desktop name, a green Online/red Offline MQTT session badge, and a one-desktop pairing explanation. The badge is driven by the long-lived Android MQTT session, which reconnects while the Sync ViewModel is alive and is the transport foundation for later sync messages. Its separate destructive
  Revoke button opens the shared mobile dialog. LAN host and port are used only
  for the temporary pairing transport and are never saved or displayed.
  The action opens `SettingsSyncScanner`, with a centred rounded QR viewfinder,
  descriptive scan guidance, and an image-picker fallback decoded by ML Kit for
  devices whose camera is unavailable.
  Its session identifies itself to desktop as `airmedy-sync-<desktop-id>-<mobile-id>`;
  this is an internal transport detail, never shown in UI. Revoke is deliberately local-only: it clears the mobile binding and permits a
  new desktop scan, while the old desktop remains trusted until revoked there.
- Home content is supplied by `HomeDemoContent`. A forward action calls the
  callback provided by the app shell, which pushes `HomeSampleDetail`; Android
  Back pops that destination stack while the floating navigation remains shown.
- Tapping the selected navigation destination restores that destination stack
  to `Root`; on Home it also animates the root `LazyColumn` back to its first item.
  Tapping another destination continues to switch stacks without resetting it.
- Shared page chrome is in `ui/components/StackPageLayout.kt`; it owns safe-area
  content padding and the optional glass Back button.

## Shared components

| Component | Contract |
| --- | --- |
| `Card` | Standard 28dp, borderless, opaque themed card surface. It accepts slot content and optional padding; its title/description overload remains a tappable primary-action card. |
| `HeroCard` | A non-interactive informational card with a 40dp decorative icon, bold `titleLarge` title, optional content directly below its title, and muted description. Sync uses this slot for its MQTT Online/Offline badge, aligned with the desktop name. |
| `ActionList` | Displays 56dp `ActionListItem` rows with optional leading Lucide drawable, resource-backed label, optional trailing composable slot, and a chevron only for clickable rows without a supplied trailing slot. `FullWidth` and `InsetForLeadingIcon` divider styles are available. `Card` uses the shared `Card` surface; `Plain` has no enclosing surface. A row is clickable only when its item has `onClick`. |
| `Selection` | Renders an iOS-style dropdown row and custom elevated menu with a 28dp radius for mutually exclusive `SelectionOption` values. Its selected value uses the standard row typography with the muted action colour; each menu option is at least 44dp tall. Only the right-side action slot opens and anchors the right-aligned menu; the opaque, rounded menu expands and collapses vertically while fading and scaling over 220ms. The caller owns selected state and receives the selected value through `onValueSelected`. |
| `StackPageLayout` | Places content below the status/header region and above the persistent navigation; screens must use its supplied padding. Its page-header title uses bold `headlineLarge` typography. |
| `AirmedyGlassIconButton` | A 48dp circular blurred glass icon button with border and button semantics. Back and header actions use this shared primitive. |
| `AirmedyPillButton` | A borderless 52dp minimum-height capsule action. `Primary` and `Destructive` use the primary background with `onPrimary` text; `Secondary` uses the stronger `buttonSecondary` theme surface with normal foreground text. Its label supplies button semantics. |
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
