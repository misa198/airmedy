# Android Compose UI Catalog

This catalog documents the Android UI owned by `mobile/androidApp`. It does not
describe the desktop Vue UI or future iOS UI.

## App shell and navigation

- `App.kt` owns the floating Home, Library, Search, and Settings navigation and
  each destination's independent `AppStackPage` stack. Its shared selection
  pill is also an active-foreground mask: a primary-coloured duplicate of the
  icons and labels is clipped to the pill, so only the exact covered portions
  of an icon or label become active while it moves.
- Header title animation keys include both destination and current stack page.
  Stack changes retain the title slide while disabling size interpolation; the
  header always reserves one action slot, preventing a title-width reflow when
  controls disappear. Switching bottom-navigation destination stacks changes
  the title without animation; push/pop inside a destination keeps the slide.
- The Settings root shows one card-contained action list for Appearance, Sync,
  Playback, Integration, and About. Appearance
  opens `SettingsAppearance` in the Settings stack; the remaining rows are
  presentational until their destination screens are introduced.
- Appearance contains vertically arranged sections, each in its own `Card`.
  Its Theme section uses `Selection`, the reusable iOS-style dropdown row, to
  persist the System, Light, or Dark theme choice.
- Sync opens `SettingsSync` in the Settings stack. Its temporary Android UI
  model is nullable: no device shows one connection placeholder card and a
  glass Add-device header action; a connected device shows one device card
  (name, type, Connected status, and presentational Revoke action) and hides
  the header action. Connection and revoke behavior are not implemented yet.
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
| `HeroCard` | A non-interactive informational card with a 36dp decorative icon, bold `titleLarge` title, and muted description. Sync uses it for its empty device state. |
| `ActionList` | Displays `ActionListItem` rows with a leading Lucide drawable, label, trailing chevron, and inset divider between rows. `Card` uses the shared `Card` surface; `Plain` has no enclosing surface. A row is clickable only when its item has `onClick`. |
| `LabeledActionRow` | A reusable 56dp row for settings or metadata sections, matching an `ActionList` row. It provides a resource-backed label on the left and a caller-supplied action slot on the right. |
| `Selection` | Renders an iOS-style dropdown row and custom elevated menu with a 28dp radius for mutually exclusive `SelectionOption` values. Its selected value uses the standard row typography with the muted action colour; each menu option is at least 44dp tall. Only the right-side action slot opens and anchors the right-aligned menu; the opaque, rounded menu expands and collapses vertically while fading and scaling over 220ms. The caller owns selected state and receives the selected value through `onValueSelected`. |
| `StackPageLayout` | Places content below the status/header region and above the persistent navigation; screens must use its supplied padding. Its page-header title uses bold `headlineLarge` typography. |
| `AirmedyGlassIconButton` | A 48dp circular blurred glass icon button with border and button semantics. Back and header actions use this shared primitive. |

## UI rules

- Use `AirmedyTheme` and `LocalAirmedyColors`; feature composables do not add raw colours.
- Use Lucide Android drawables and resource-backed strings. Decorative icons have no content description; actionable rows expose their label through Compose semantics.
- Keep interactive targets at least 48dp. Rows and cards delegate click behavior through callbacks rather than storing navigation state.
- Card surfaces are borderless unless a feature explicitly requires a border.

## Testing

- Android UI changes require Compose instrumentation coverage in
  `androidApp/src/androidTest` plus `:androidApp:assembleDebug`.
- Test component interaction and screen-level navigation separately. `ActionListTest`
  covers optional callbacks; `AppNavigationTest` covers Home-to-detail navigation.
