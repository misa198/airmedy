# UI System

## Summary

The frontend is a Vue 3 SPA built with Vite 5, TailwindCSS v4, and Pinia. It uses a glass-morphism design system with dynamic artwork-based color theming. All views are lazy-loaded except Home. Track lists use virtual scrolling for performance.

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
| `/`               | HomeView             | Recently played, most/least listened carousels |
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

## Track Table (`TrackTable.vue`)

Virtualized list of tracks supporting reordering, sorting, and horizontal scrolling with sticky columns.

### Architecture

- **Virtualization**: Uses `vue-virtual-sortable`. Root `VirtualList` handles both vertical virtualization and horizontal scrolling.
- **Absolute Rows**: `TrackTableRow` uses `absolute inset-x-0` positioning within each virtual item container. This allows rows to span the full width of the scrollable area while maintaining high performance.
- **Scroll Sync**: Header horizontal scroll is programmatically synced to the `VirtualList` scroll position via the `handleScroll` event.
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
| `disc_number`  | Disc         | No                 | Yes      | No     |
| `track_number` | Track        | No                 | Yes      | No     |
| `album_artist` | Album Artist | No                 | No       | No     |
| `context_menu` | ⋮            | Yes                | No       | Right  |

Row height: 56px (default) or 36px (compact mode), header height: 40px. Column visibility, order, and widths persisted to `localStorage`:

- `airmedy:track-table-visible`
- `airmedy:track-table-order`
- `airmedy:track-table-widths`
- `airmedy:track-table-collapsed`

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
| Add to Playlist     | Submenu with playlist list                   |
| Go to Album         | Router navigate to `/albums/:id`             |
| Go to Artist(s)     | Submenu or direct navigate to `/artists/:id` |
| Edit Metadata       | Open `MetadataEditDialog`                    |
| Show in Explorer    | `LibraryService.ShowInExplorer()`            |

`ContextMenu.vue` is rendered via `<Teleport to="body">`. Handles viewport edge detection and keyboard navigation.

## Modal & Dialog System

Common dialogs are consolidated under the **`Modal.vue`** primitive. It provides synchronized transitions, standard backdrop behavior, and consistent header styling.

| Dialog                | Purpose                                      |
| --------------------- | -------------------------------------------- |
| `FindLyricsDialog`    | Manual lyrics search and selection           |
| `SyncProgressDialog`  | Library sync status and progress             |
| `MetadataEditDialog`  | Manual tag and artwork editing               |
| `ConfirmDialog`       | Generic confirmation for destructive actions |

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

## Interactive Polish

- **Auto-scroll to Active**: `TrackTable.vue` and `QueueDrawer.vue` automatically scroll to the currently playing track when opened or when the track changes. Uses a 100ms delay to ensure layout stability.
- **Path Morphing**: Play/Pause buttons in `PlayerFooter`, `PlayerPlaybackControls`, and `MiniPlayer` use SVG path morphing for Apple Music-style fluid transitions.
- **Tactile Feedback**: Interactive buttons use a `scale-95` active state for a "pressed" feel.
- **Glass-Morphism**: Surfaces use `var(--bg-glass)` with `backdrop-filter: blur(30px)`.

## Track Table (`TrackTable.vue`)


| Composable              | Purpose                                                 |
| ----------------------- | ------------------------------------------------------- |
| `useContextMenu`        | Generic context menu state manager                      |
| `useTrackContextMenu`   | Track-specific menu item builder                        |
| `useGroupContextMenu`   | Multi-track selection menu (Play Next, Add to Playlist) |
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
- Artwork requests use variants (`_sm`, `_md`) sized appropriately for each context.
- Fullscreen `PlayerArtwork` can stack outgoing and incoming covers for an automatic audio crossfade; `requestAnimationFrame` applies equal-power `cos(t*pi/2)`/`sin(t*pi/2)` opacity weights over the backend-provided effective fade duration, using `plus-lighter` compositing. Its maximum size is explicitly set by `FullScreenPlayer`: `22rem` without a right column and `20rem` when queue or lyrics is open. Player bars and mini-player artwork do not blend.
- `shallowRef` used for large reactive arrays (queue, tracks, albums).
- Column widths cached in localStorage to avoid recalculation.
- Mini player glass panel uses `backdrop-filter` on a GPU-composited layer (no rendered artwork copy).
