# UI System

## Summary

The frontend is a Vue 3 SPA built with Vite 5, TailwindCSS v4, and Pinia. It uses a glass-morphism design system with dynamic artwork-based color theming. All views are lazy-loaded except Home. Track lists use virtual scrolling for performance.

## Mobile library sync

An Online device in Settings → Mobile opens `/settings/mobile-devices/:deviceId/sync`.
The desktop-only view reuses shared Radio, TabSwitcher, Input and Checkbox
primitives and virtualizes its selector. It supports all-library sync or one
active selected source tab: artists, albums, genres, or regular playlists;
Favorites and smart playlists are not selectable. It owns and
disposes subscriptions to pairing and `mobile-library-sync:updated` events. An
active plan also polls its status once per second, so percentage progress
remains current if a desktop runtime event is missed; polling stops when the
plan finishes or the view unmounts. A poll response is ignored if a newer event
has already completed, replaced, or advanced that plan, preventing stale active
state from restarting the spinner or moving progress backward. Status IPC omits
the immutable sync manifest. Its status is rendered inline in the
existing sync-selection panel with a quiet divider, status label, percentage,
and a thin progress bar rather than a separate card or technical asset counts.
The selection panel is a headerless `SettingSection` (`panel` variant), so it
inherits the same Settings card treatment without duplicating the page title.
The view uses the same centered `max-w-3xl p-8` content container as Settings.
Its Sync action and scope choices remain disabled for the full lifetime of an
active plan, the action shows a rotating sync icon, and duplicate sync requests
are prevented while transfer is in progress. When a selected-items table is
shown, a light theme-safe overlay with a centered spinner blocks edits until
that plan completes.

## Tech Stack

| Library              | Version | Purpose                              |
| -------------------- | ------- | ------------------------------------ |
| Vue 3                | 3.x     | Component framework, Composition API |
| Vite                 | 5.x     | Build tool, HMR                      |
| TailwindCSS          | 4.x     | Utility CSS, CSS-first config        |
| Pinia                | 3.x     | State management                     |
| Vue Router           | 4.x     | Hash-based routing                   |
| vue-i18n             | -       | Internationalization (12 locales)    |
| vue-virtual-sortable | 3.x     | Virtual list with DnD support        |
| Radix Vue            | -       | Headless accessible components       |
| Lucide Vue           | -       | Icon library (thin-stroke)           |

## Routing

Hash history mode (`createWebHashHistory`). All views lazy-loaded except HomeView.

| Route             | View                 | Notes                                          |
| ----------------- | -------------------- | ---------------------------------------------- |
| `/`               | HomeView             | Overview carousels and listening analytics tabs |
| `/recently-added` | RecentlyAddedView    | TrackCard grid, tracks sorted by import date   |
| `/albums`         | AlbumsView           | Album grid                                     |
| `/albums/:id`     | AlbumDetailView      | Hero + track table                             |
| `/artists`        | ArtistsView          | Artist grid                                    |
| `/artists/:id`    | ArtistDetailView     | Albums + tracks                                |
| `/tracks`         | TracksView           | Full track table (virtualized)                 |
| `/genres`         | GenresView           | Genre list                                     |
| `/genres/:id`     | GenreDetailView      | Genre tracks                                   |
| `/composers`      | ComposersView        | Composer list                                  |
| `/composers/:id`  | ComposerDetailView   | Composer tracks                                |
| `/search`         | SearchView           | Unified search results                         |
| `/playlists/:id`  | PlaylistDetailView   | Playlist hero + tracks                         |
| `/settings`       | SettingsView         | Tabbed settings (General/Library/EQ/About)     |
| `/mini-player`    | MiniPlayerWindowView | Separate Wails window                          |

The `/mini-player` route bypasses the MainLayout wrapper and renders directly.

## Mobile Pairing UI

`MobileDevicesSettings.vue` is a separate Settings category from browser Remote
Control. It selects one reachable LAN address for the pairing QR, lists trusted
mobiles with broker-session Online/Offline badges, and invokes revocation. MQTT
port selection and recovery are automatic implementation details and are never
shown or configured in the UI. `mobilePairing.ts`
owns the `pairing:request` Wails subscription and its cleanup; the globally mounted
`MobilePairingDialog.vue` accepts or rejects a pending request even when Settings
is not open. `NetworkAddressList.vue` and `useNetworkInterface.ts` are shared by
this page and `RemoteServerSettings.vue`, so address cards, selection, copy state,
interface icons, labels, and tooltips remain identical. The protocol itself is
documented in [mobile pairing](../pairing/README.md).

The same panel has a short-lived **Broadcast pairing signal** action. It explains
that desktop sends the 30-second signal so ready devices can find and connect to
it, rather than exposing the transport term “broadcast”. While mDNS is active it
displays a pulsing status and backend-derived 30-second countdown, can stop early,
refreshes on `pairing:broadcast-changed`, and clears its interval and Wails event
listener on unmount.

Trusted mobile-device status uses the semantic `--status-online` token for the
Online badge (green in every theme); Offline continues to use `--text-muted`.
While a device has an active mobile-library sync plan, its Delete action is
disabled in both the row actions and context menu; the panel refreshes this
state on `mobile-library-sync:updated`.

### Cached Route Data Refresh

`MainLayout` and entity explorer routes use `KeepAlive` to preserve UI state.
`useLibrarySync` therefore treats Wails library data events as invalidations:
the currently visible view reloads silently (with closely spaced events
coalesced), while a deactivated cached view is marked dirty and reloads only
when `onActivated` runs. This preserves scroll/filter state and avoids fetching
data for routes the user cannot see. `library:track-updated`, `library:updated`,
and non-background `library:sync-finished` all use this behavior.

`SearchView` uses the same invalidation flow. Its Pinia store retains a search
response, so `useLibrarySync` calls `searchStore.refresh()` for a non-empty
query. Refresh bypasses typing debounce, keeps the existing results visible,
and supersedes any pending older request.

`HomeOverview` also uses `useLibrarySync` to silently reload its listening
carousels after library mutations. This prevents a cached home track from
linking to an album ID that was removed after a metadata edit.

Album and playlist detail pages use the same invalidation flow so their cached
headers and membership cannot outlive metadata changes. This is essential for
smart playlists, whose rules can add or remove a track after its metadata is
edited. Home Analytics silently refreshes its hydrated top-track DTOs and
summary data, and Playlists refreshes mosaic-preview tracks even when playlist
membership itself did not change.

When `mobile-library-sync:updated` reports a completed plan, the same cached
view invalidation runs. Mobile playlist reconciliation can change the desktop
playlist database before that plan completes, so the playlists store reloads
for the sidebar and list while playlist detail and mosaic data refresh through
`useLibrarySync`.

### Mini Player Window Lifecycle

The mini player window is **destroyed on close and recreated on open** (not just hidden). `WindowService` holds a factory function (`SetMiniWindowFactory`) that creates a fresh `WebviewWindow` each time. Closing the window does not call `e.Cancel()` on the `WindowClosing` hook, so Wails destroys the native window and frees its memory. Reopening calls the factory to create a new window. This resets all Vue/Pinia state in that webview.

Because state is reset on every open, window **position, size, and pin (always-on-top)** are persisted natively (not in Vue) to the `mini_player_state` table and restored by `WindowService.ApplyMiniState(w)` in the factory — clamped to the current screen's work area so the window never restores off-screen. See `catalog/player` → *Geometry & Pin Persistence*.

The tray **"Show Airmedy"** action calls `WindowService.ShowCurrent()`, which reveals only the currently active window — the mini player if it is open, otherwise the main window — instead of forcing both visible at once.

## CSS Variables & Theming

TailwindCSS v4 uses a **CSS-first** `@theme` directive approach. All design tokens are CSS custom properties.

### Static Variables

```css
/* Dark theme (.dark class) */
--bg-main: #18181b
--bg-glass: rgba(35, 35, 38, 0.6)
--bg-glass-elevated: rgba(55, 55, 60, 0.4)
--border-glass: rgba(255, 255, 255, 0.1)
--text-main: #ffffff --text-muted: #a1a1aa
--primary: #e11d48 --accent-favorite: #ef4444

/* Black theme (.dark.black classes — OLED override) */
--bg-main: #0a0a0a
--bg-glass: rgba(25, 25, 25, 0.6)
--bg-glass-elevated: rgba(45, 45, 45, 0.4)

/* Light theme (default) */
--bg-main: #f4f4f5
--bg-glass: rgba(255, 255, 255, 0.7)
--bg-glass-elevated: rgba(255, 255, 255, 0.9)
--border-glass: rgba(0, 0, 0, 0.1)
--text-main: #0a0a0a --text-muted: #52525b;
```

`--primary` and its RGB/tint/foreground companion variables are updated at runtime from the persisted primary-color setting. This is independent of artwork-derived `--dynamic-*` colors.

`--primary-foreground` favors `#FFFFFF` for saturated primary accents and uses
`#18181B` only when the primary's perceived brightness is high (for example,
yellow or pastel). This keeps orange and similar accents visually cohesive
without using pure black.

`@airmedy/ui` exports `ColorPicker`, a stateless popover primitive with hue, saturation/brightness, preview, and validated `#RRGGBB` input. General Settings uses it after the six primary-color preset circles.

The Track Info Drawer keeps its Lossless quality badge on the original rose
`#E11D48`; it deliberately does not follow the user-configured primary color.

### Dynamic Variables (Artwork-Derived)

Updated via JavaScript on each track change. Declared with `@property` for CSS transition support:

```css
@property --dynamic-primary {
  syntax: "<color>";
  inherits: true;
}
@property --dynamic-surface {
  syntax: "<color>";
  inherits: true;
}
```

```javascript
// App.vue on player:theme event
root.style.setProperty("--dynamic-primary", vibrant);
root.style.setProperty("--dynamic-surface", hexToRgba(dominant, 0.15));
root.style.setProperty("--dynamic-glow", hexToRgba(vibrant, 0.4));
```

Transition: `1.5s ease-in-out` for smooth color shifts between tracks.

Detail views override `--dynamic-surface` locally on their own hero element so
the tint reflects the viewed entity, not the playing track: `DetailHero.vue`
(albums/playlists, via a `theme` prop) and `ArtistDetailView.vue` (artist, via
`GetArtistColors`). Each falls back to `var(--bg-glass)` when no colors.

## Glass-Morphism Implementation

```css
/* Sidebar, player bar, lyrics panel */
background: var(--bg-glass);
backdrop-filter: blur(30px);
border-top: 1px solid var(--border-glass);
```

Cards use lower blur with hover scale:

```css
.card:hover {
  transform: scale(1.02);
  filter: brightness(1.1);
}
```

## Main Layout Sidebar

The left sidebar in `MainLayout.vue` can be resized between 180px and 260px.
Its final width is saved when the resize drag ends and restored on the next app
load from `localStorage` key `airmedy:sidebar-width`. Missing, invalid, or
out-of-range saved values fall back to the 250px default.

Sidebar labels use a shrinking text region with `truncate`; adjacent navigation
and action icons are `flex-shrink-0`, so a narrow sidebar shows an ellipsis
without compressing or overlapping icons.

## Track Table (`TrackTable.vue`)

Virtualized list of tracks supporting reordering, sorting, and horizontal scrolling with sticky columns.

### Architecture

- **Virtualization**: Uses `vue-virtual-sortable`. Root `VirtualList` handles both vertical virtualization and horizontal scrolling.
- **Absolute Rows**: `TrackTableRow` uses `absolute inset-x-0` positioning within each virtual item container. This allows rows to span the full width of the scrollable area while maintaining high performance.
- **Scroll Sync**: The header is its own hidden-scrollbar horizontal scroll container. It is bidirectionally synced with the `VirtualList` scroll position, so its sticky `dnd` and `index` cells remain pinned while either the rows or header receives horizontal scrolling.
- **Sticky Columns**:
  - `dnd`: Sticky left (`z-10`).
  - `index`: Sticky left (`z-10`). If `dnd` is active, it offsets by 32px to stay visible next to the handle.
  - `context_menu`: Sticky right (`z-10`).
  - Sticky cells use opaque backgrounds to prevent overlapping content from being visible during scroll.

**Columns (configurable):**

| Key            | Label        | Default visible    | Sortable | Sticky |
| -------------- | ------------ | ------------------ | -------- | ------ |
| `dnd`          | -            | (Conditional)      | No       | Left   |
| `index`        | #            | Yes                | Yes      | Left   |
| `title`        | Title        | Yes                | Yes      | No     |
| `duration`     | Duration     | Yes                | Yes      | No     |
| `artist`       | Artist       | Yes                | Yes      | No     |
| `album`        | Album        | No                 | Yes      | No     |
| `year`         | Year         | No                 | Yes      | No     |
| `genre`        | Genre        | No                 | No       | No     |
| `favorite`     | ♥            | Yes                | No       | No     |
| `play_count`   | Plays        | No                 | Yes      | No     |
| `listened_seconds` | Listening time | Feature-supplied | Yes | No |
| `disc_number`  | Disc         | No                 | Yes      | No     |
| `track_number` | Track        | No                 | Yes      | No     |
| `album_artist` | Album Artist | No                 | No       | No     |
| `context_menu` | ⋮            | Yes                | No       | Right  |

Row height: 56px (default) or 36px (compact mode), header height: 40px. Column visibility, order, and widths persisted to `localStorage`:

- `airmedy:track-table-visible`
- `airmedy:track-table-order`
- `airmedy:track-table-widths`
- `airmedy:track-table-collapsed`

For small, non-virtualized embedded lists, `autoHeight` lets the table expand to
its rows instead of creating a nested vertical scroll region. The Home analytics
"Most played tracks" table uses this mode so normal wheel/trackpad scrolling
continues through the page. It previews five tracks by default and provides an
expand/collapse control for the full 50-track analytics ranking.

## Context Menu System

**`useContextMenu()`** composable: manages position, visibility, and items for a generic `ContextMenu.vue`.

**`useTrackContextMenu()`**: builds the standard track action menu:

| Item                | Action                                       |
| ------------------- | -------------------------------------------- |
| Play Next           | `PlayerService.PlayNext(track)`              |
| Track Info          | Open track info drawer                       |
| Refresh Lyrics      | `LyricsService.FetchLyrics()`                |
| Find Lyrics         | Open `FindLyricsDialog.vue`                  |
| Add/Remove Favorite | `LibraryService.ToggleFavorite()`            |
| Add to Playlist     | Create Playlist action, divider, then playlist list |
| Go to Album         | Router navigate to `/albums/:id`             |
| Go to Artist(s)     | Submenu or direct navigate to `/artists/:id` |
| Edit Metadata       | Open `MetadataEditDialog`                    |
| Show in Explorer    | `LibraryService.ShowInExplorer()`            |

**`useGroupContextMenu()`**: used by artist, genre, and composer detail menus.
It supports Play Next, Add to Queue (skipping tracks already queued), and Add to Playlist.

`ContextMenu.vue` is rendered via `<Teleport to="body">`. It clamps against the layout viewport (`document.documentElement.clientWidth/clientHeight`) so menus stay inside the visible content area even when browser scrollbars reduce usable space. It handles viewport edge detection and keyboard navigation.
It supports one hover-open submenu level, including separators within the submenu.

### Player Footer Quick Settings

`PlayerQuickSettingsMenu.vue` opens from a right click on a non-interactive area of
the sticky player footer or fullscreen player. Artwork, track information, sliders,
buttons, tabs, and queue/lyrics panels retain their own interactions and do not open
the quick settings menu. It provides persisted toggles for prevent-sleep, active
player indicators, and crossfade, plus an EQ-profile submenu (which loads
the current profiles on open, marks the active profile, applies a chosen profile
live, and links to the EQ section at `/settings/playback?section=equalizer`), and
an option at the end to open general playback settings. When either settings
link is selected from fullscreen player, it returns the player to sticky mode
before navigating.

`FullScreenPlayer.vue` listens for `Escape` while the player mode is fullscreen and
returns the player to sticky mode. Its window key listener is removed on unmount.

## Modal & Dialog System

Common dialogs are consolidated under the **`Modal.vue`** primitive. It provides synchronized transitions, standard backdrop behavior, and consistent header styling.

| Dialog                | Purpose                                      |
| --------------------- | -------------------------------------------- |
| `FindLyricsDialog`    | Manual lyrics search and selection           |
| `SyncProgressDialog`  | Library sync status and progress             |
| `MetadataEditDialog`  | Manual tag and artwork editing               |
| `ConfirmDialog`       | Generic confirmation for destructive actions |

`MobileLibrarySyncView` uses `Modal` for a transient insufficient-storage
alert. It accepts the runtime payload from both initial `GetStatus` and
`mobile-library-sync:updated`, stops active polling, hides the superseded
plan's stale progress, and keeps dismissal as UI-only state.

## Chip Input (`components/settings/DelimiterInput.vue`)

Reusable tag/chip editor for the Library tab's tag-delimiter settings. Renders each delimiter as
a removable chip plus an add input. `v-model` is `string[]`; emits `update:modelValue`. Enforces
inline validation (trim, reject empty/duplicate, max 5 chars), allows removing the last chip
(empty list = splitting disabled), and supports Backspace-on-empty to delete the last chip. A
`color` prop (`neutral` default | `primary` | `success` | `warning` | `danger`) themes the chips.

## Settings Panels

### Library Settings (`components/settings/LibrarySettings.vue`)

Owns watched-folder management, tag delimiters, and Library Analysis controls.
The Library Analysis section contains:

- An enable switch.
- A live `analysis:progress` status line.
- A library-readiness percentage (`libraryDone / libraryTotal`).
- A worker-count slider when more than one worker is available.

`LibrarySettings.vue` subscribes to `analysis:progress` on mount, stores the
returned off-function, and also issues an immediate `AnalysisService.GetProgress()`
fetch so the panel starts from the current snapshot instead of waiting for the
next event. A `receivedLiveEvent` guard prevents a slower initial IPC response
from overwriting newer event-driven state.

The worker-count slider uses a "live while dragging, persist on release"
pattern: `workerCountLive` updates during drag, but the store action only fires
on `mouseup` / `touchend`, avoiding backend writes on every drag frame.

### Playback Settings (`components/settings/PlaybackSettings.vue`)

Now contains the EQ panel, prevent-sleep toggle, and Volume Normalization
controls. Library Analysis controls were moved out to `LibrarySettings.vue`; the
playback panel only retains the dependency hint
(`settings.library_analysis.requires_enable`) when normalization is unavailable
because analysis is off.

### About Settings (`components/settings/AboutSettings.vue`)

The application icon uses a rounded wrapper with a regular `box-shadow`, rather
than `filter: drop-shadow()`. This avoids an intermittent gray compositing
surface rendered by the desktop webview when the About panel mounts.

The EQ section is identified by `#equalizer`; `SettingsView` recognizes the
`section=equalizer` query on the Playback route and scrolls that section into view.

## UI Primitives (`@airmedy/ui`)

### Slider (`packages/ui/src/slider/Slider.vue`)

Props: `modelValue`, `min`, `max`, `step`, `class`, `scrollable`.

`scrollable` (default `false`) — enables mouse wheel to increment/decrement by one `step`. Use only on volume sliders; seek bar and other sliders must leave it unset to avoid hijacking page scroll. Currently enabled on: `PlayerVolumeControl.vue`, `PlayerFooter.vue` (volume only), `MiniPlayerFloating.vue` (volume popup), `remote/PlayerControls.vue`.

### Checkbox (`packages/ui/src/checkbox/Checkbox.vue`)

Props: `checked: boolean`, `variant?: 'outlined' | 'contained'`. Emits `update:checked`. Purely
presentational (no internal click handler) — pair with a wrapping `<label>` that toggles state
on click, e.g. `SmartPlaylistDialog.vue` (limit toggle) and `FindLyricsDialog.vue` (save-file
toggle).

### Tooltip (`packages/ui/src/tooltip/Tooltip.vue`)

Props: `text: string`. Wraps a default slot trigger in a `relative inline-flex group` span; a
`absolute`-positioned popup (card-styled, positioned above the trigger) fades in on
`group-hover`. CSS-only, no JS positioning — used where a hover explanation is needed on a small
element (e.g. the info icon next to `FindLyricsDialog.vue`'s save-file checkbox). Native `title`
attributes are the fallback convention elsewhere in the app (e.g. `RemoteServerSettings.vue`)
but do not render reliably inside the Wails webview for icon components, so prefer `Tooltip` for
new hover explanations.

### TabSwitcher (`packages/ui/src/TabSwitcher.vue`)

Props: `options`, `modelValue`, `mandatory?`, `variant?: 'icon' | 'label'`.
The default icon mode uses fixed-size buttons. Label mode measures the active
button and moves/resizes the slider to match it; a `ResizeObserver` keeps that
measurement current and is disconnected on unmount.

## Home Analytics

`HomeView` owns the Overview/Analytics tab selection and delegates to
`components/home/HomeOverview.vue` and `HomeAnalysis.vue`. Analytics is split
into a Desktop Library section, loaded with `AnalyticsService.GetLibraryInsights`
and its own range, and a Listening History section, loaded with
`GetListeningInsights(range, sourceDeviceID)`. Their requests are cancellable
and independent. The device selector defaults to all sources and offers this
desktop plus every currently trusted mobile device; it applies only to Listening
History. Library Growth, library totals, and Audio Quality remain desktop-library data.
It also shows a Playback Outcomes donut for the selected range, splitting
completed, skipped, and stopped attempts; the three slices total 100% and the
card remains unavailable until at least one playback attempt has ended. The
same row includes a one-column Average Session card, calculated from actual
audio-running seconds per finalized playback attempt.
Every donut's adjacent breakdown is sorted by value descending. Genre
breakdowns place the aggregate `Other` entry last regardless of its value.
Library growth is an SVG ECharts area chart with a fading primary-color gradient
inside its chart; the Library Growth and Streak cards themselves intentionally
do not inherit the current track's artwork surface color.
7D and 30D use daily points, while All uses yearly points. The total-time card
shares its row with a one-column current listening-streak card; streak is
independent of the selected range and is based on local calendar days. Its warm
flame treatment uses a decorative glow and watermark with a hover-only halo;
these visuals are hidden from assistive technology and their transitions are
disabled for `prefers-reduced-motion`. Top tracks are hydrated through
`LibraryService.GetTracksByIDs` and shown in the virtualized `TrackTable` with
period-specific `play_count` and the feature-supplied `listened_seconds` column.
The queue preserves the analytics rank (play count descending, then listened
seconds, then title) rather than the library-wide play count returned by track hydration. A null or partial response is normalized
to empty insight fields so the full analytics layout remains visible; each card
renders its own placeholder when its data is unavailable. Charts use
SVG-rendered `vue-echarts` components.

## Interactive Polish

- **Auto-scroll to Active**: `TrackTable.vue` and `QueueDrawer.vue` automatically scroll to the currently playing track when opened or when the track changes. Uses a 100ms delay to ensure layout stability.
- **Path Morphing**: Play/Pause buttons in `PlayerFooter`, `PlayerPlaybackControls`, and `MiniPlayer` use SVG path morphing for Apple Music-style fluid transitions.
- **Tactile Feedback**: Interactive buttons use a `scale-95` active state for a "pressed" feel.
- **Glass-Morphism**: Surfaces use `var(--bg-glass)` with `backdrop-filter: blur(30px)`.
- **Lyrics lifecycle**: `stores/player.ts` tracks `lyrics_request_id` from `player:status` and applies same-track `player:lyrics` updates whose request ID is equal to or newer than the latest observed ID. While loading, it polls the backend's retained `GetCurrentLyrics` lifecycle snapshot every 250 ms, with a 36-second failsafe, so a missed first terminal event cannot leave the UI loading indefinitely. A matching error ends loading without clearing an already shown lyric.

### Development Tools Overlay

`DevToolsOverlay.vue` is mounted by `App.vue` only when `import.meta.env.DEV` is true (and never
in the mini-player window). Its glass button invokes Wails `Window.OpenDevTools()` for the current
webview and supports pointer dragging anywhere within the viewport for the current session. The
overlay is not rendered in production builds.

## Track Table (`TrackTable.vue`)


| Composable              | Purpose                                                 |
| ----------------------- | ------------------------------------------------------- |
| `useContextMenu`        | Generic context menu state manager                      |
| `useTrackContextMenu`   | Track-specific menu item builder                        |
| `useGroupContextMenu`   | Artist/genre/composer menu (Play Next, Add to Queue, Add to Playlist) |
| `useAddToPlaylistMenu`  | Shared Create Playlist submenu prefix                   |
| `useCreatePlaylistWithTracks` | Global create-then-add-tracks dialog state        |
| `useTrackTableSettings` | Column config with localStorage persistence             |
| `useLyrics`             | LRC parser for synced/plain view                        |
| `useKeyboardShortcut`   | Global key binding registration                         |
| `useRestoreScroll`      | Scroll position restore on keep-alive activation        |
| `useLibraryUpdates`     | Reactive array sync on library:track-updated events     |

## Mini Player Glass Panel

The mini player controls sit over a CSS glassmorphism panel (`.glass-panel` in `MiniPlayerFloating.vue`), not a rendered artwork copy:

- Full-width layer anchored to the bottom, 300px tall.
- `backdrop-filter: blur(24px)` blurs the artwork directly behind it; a `linear-gradient` dark tint sits on top.
- `mask-image` (bottom→top) fades the blur out: full strength for the bottom 25%, gone by the top.
- GPU-compositing hints (`transform: translateZ(0)`, `will-change`, `backface-visibility: hidden`, `isolation: isolate`) force a dedicated layer to avoid `backdrop-filter` repaint flicker on macOS.

## Internationalization

- **Frontend**: 12 locale JSON files in `frontend/src/locales/` managed via `vue-i18n`. `i18n.locale` is set dynamically from `appStore.language`. No page reload needed.
- **Backend**: Native application and system tray menus are localized via a dedicated Go `i18n.Service` in `internal/app/i18n`. 
  - Backend locales are stored in `internal/app/i18n/locales/` and embedded via `go:embed`.
  - When the frontend language changes, it emits a `language:changed` Wails event.
  - The backend listens for this event and dynamically rebuilds/updates the native menus on the main thread.

## Performance Notes

- `vue-virtual-sortable` renders only visible rows in track lists (56px or 36px each).
- Views are lazy-loaded (dynamic `import()` in router) — only the home view loads eagerly.
- Search is debounced 300ms in `stores/search.ts`.
- Track and album results in `SearchView` expose their shared context menus on right-click, matching the actions available on other track and album surfaces.
- Artwork requests use variants (`_sm`, `_md`) sized appropriately for each context.
- Fullscreen `PlayerArtwork` can stack outgoing and incoming covers for an automatic audio crossfade; `requestAnimationFrame` applies equal-power `cos(t*pi/2)`/`sin(t*pi/2)` opacity weights over the backend-provided effective fade duration, using `plus-lighter` compositing. Its maximum size is explicitly set by `FullScreenPlayer`: `22rem` without a right column and `20rem` when queue or lyrics is open. Player bars and mini-player artwork do not blend.
- Fullscreen lyrics use `PlayerLyricsPanel` when `appStore.highContrastLyrics` is on (glass surface and header) and `ImmersiveLyricsPanel` when off (transparent, headerless content over the artwork background). Both delegate lyric parsing, loading, seek, and scrolling to `PlayerLyrics`; `LyricsDrawer` is unaffected.
- `SyncedLyricsView` applies the GPU transform hint only to the active lyric and the two lines on either side, limiting composited layers while preserving smooth nearby transitions.
- `shallowRef` used for large reactive arrays (queue, tracks, albums).
- Column widths cached in localStorage to avoid recalculation.
- Mini player glass panel uses `backdrop-filter` on a GPU-composited layer (no rendered artwork copy).
