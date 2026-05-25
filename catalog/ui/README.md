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
| `/recently-added` | RecentlyAddedView    | Sorted by import date                          |
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
| `useGlassBlur`          | WebGL 2-pass Gaussian blur for mini player background   |
| `useKeyboardShortcut`   | Global key binding registration                         |
| `useRestoreScroll`      | Scroll position restore on keep-alive activation        |
| `useLibraryUpdates`     | Reactive array sync on library:track-updated events     |

## WebGL Blur (`useGlassBlur.ts`)

Used in the mini player for the artwork background.

1. Load image into WebGL texture (max 256px to limit VRAM).
2. **Pass 1:** Horizontal Gaussian blur via fragment shader.
3. **Pass 2:** Vertical Gaussian blur + brightness adjustment + gradient alpha fade (transparent at top, opaque at bottom).
4. Render to canvas element behind the controls.

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
- `shallowRef` used for large reactive arrays (queue, tracks, albums).
- Column widths cached in localStorage to avoid recalculation.
- WebGL blur texture capped at 256px to limit GPU memory.
