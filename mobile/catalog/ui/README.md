# Android Compose UI Catalog

This catalog documents the Android UI owned by `mobile/androidApp`. It does not
describe the desktop Vue UI or future iOS UI.

## App shell and navigation

- `App.kt` owns the floating Home, Library, Search, and Settings navigation and
  each destination's independent `AppStackPage` stack.
- Home content is supplied by `HomeDemoContent`. A forward action calls the
  callback provided by the app shell, which pushes `HomeSampleDetail`; Android
  Back pops that destination stack while the floating navigation remains shown.
- Shared page chrome is in `ui/components/StackPageLayout.kt`; it owns safe-area
  content padding and the optional glass Back button.

## Shared components

| Component | Contract |
| --- | --- |
| `Card` | Standard 12dp, borderless, opaque themed card surface. It accepts slot content and optional padding; its title/description overload remains a tappable primary-action card. |
| `ActionList` | Displays `ActionListItem` rows with a leading Lucide drawable, label, trailing chevron, and inset divider between rows. `Card` uses the shared `Card` surface; `Plain` has no enclosing surface. A row is clickable only when its item has `onClick`. |
| `StackPageLayout` | Places content below the status/header region and above the persistent navigation; screens must use its supplied padding. |

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
