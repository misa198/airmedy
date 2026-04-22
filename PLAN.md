# Airmedy Music Player - Development Plan

## 1. Background & Motivation
The goal is to build a robust, cross-platform music player application, initially targeting macOS. The technology stack consists of Wails v3 for the application framework (Go backend, Web frontend), Vue.js for the UI, ShadCN-vue for UI components, Pinia for state management, and i18n for localization. The application will provide a modern user experience with comprehensive library management, advanced search, and a feature-rich playback engine supporting diverse audio formats and AirPlay 2.

## 2. Scope & Impact
This is a greenfield project establishing the entire architecture:
- **Backend (Go):** File system interactions, metadata extraction, database management (SQLite), search indexing (Bleve), audio playback, OS integration (Now Playing, AirPlay 2).
- **Frontend (Vue.js):** Highly interactive, responsive user interface with virtualized lists/grids to handle large music libraries smoothly. State management for playback queues, UI configurations, and real-time updates.

## 3. Proposed Solution
- **Storage:** SQLite for structured relational data (Tracks, Albums, Artists, Playlists, custom metadata, and Lyrics).
- **Music Import:** A robust file system watcher (like `fsnotify` in Go) paired with a manual sync mechanism to immediately detect and process changes in user-selected music directories.
- **Search Engine:** An embedded Go search engine (Bleve) for fast, typo-tolerant full-text search across the entire library.
- **Lyrics:** Stored directly within the SQLite database to avoid cluttering user music folders.
- **Suggestions Algorithm:** The 'Suggested' section will utilize heuristics based on play history, frequently played artists/genres, and highly rated tracks.
- **UI Performance:** Implementation of virtual scrolling/pagination (e.g., `vue-virtual-scroller`) for all long lists and grids.

## 4. Alternatives Considered
- **Search:** SQLite FTS5 was considered for its simplicity but rejected in favor of an embedded search engine (Bleve) to support more advanced ranking and search capabilities.
- **Import Sync:** Periodic background scanning was considered but rejected in favor of an OS file watcher to provide real-time updates to the library.
- **Lyrics Storage:** Saving to sidecar `.lrc` files was considered for portability but rejected to prioritize keeping the user's file system pristine.
- **Suggestions:** Complex ML-based metadata similarity algorithms were considered but deferred; a heuristics-based approach provides immediate value with lower complexity.

## 5. Phased Implementation Plan & To-Do List

### Phase 1: Foundation & Project Setup
- [x] Initialize Wails v3 project with Vue + Vite template.
- [x] Set up Tailwind CSS and ShadCN-vue.
- [x] Configure Pinia for state management and Vue I18n for localization.
- [x] Define project structure (frontend/src, backend/internal).

### Phase 2: Data Layer & Core Backend
- [x] Initialize SQLite database and define schemas (Tracks, Albums, Artists, Playlists, Genres, Composers, Lyrics).
- [x] Implement robust Many-to-Many junction tables (e.g., track_artists, track_genres) to support multiple tags per file.
- [x] Integrate an embedded search engine (Bleve) and configure indexing rules using DTOs for multi-table coverage.
- [x] Implement a metadata extraction module and resolve entities into a fully normalized relational graph.

### Phase 3: Music Import & Library Management
- [x] Build Wails bindings for opening folder selection dialogs.
- [x] Implement recursive directory scanning for initial import.
- [x] Integrate `fsnotify` for real-time folder watching and database updates.
- [x] Implement manual sync trigger from the UI.

### Phase 4: Frontend Architecture & UI Shell
- [x] Create main layout: Sidebar (Navigation, Playlists), Main Content Area, and Player Footer.
- [x] Set up Vue Router with main routes: Home, Recently Added, Artists, Albums, Tracks, Genres, Composers, Search, Settings.
- [x] Implement the 'Home' view layout with placeholder sections (Recently Listened, Most Listened, Suggested).

### Phase 5: Core UX Views & Virtualization
- [x] Implement `vue-virtual-scroller` (or similar) for robust performance on large datasets.
- [x] **Tracks View:** Table format with customizable, reorderable, and sortable columns. Support large/small row options.
- [x] **Artist View:** Standardized 2-column layout (list on left, detail view on right).
- [x] **Album/Recently Added Views:** Grid layout with covers, titles, and artists.
- [x] **Detail Views:** Album details (artwork, tracklist) and Artist details (header, albums grid, tracks).
- [x] **Deep Linking:** Full parameterized routing for Albums and Artists.
- [ ] **Genre/Composer Views:** Implement 2-column layout with track listing for selected entity.
- [x] **Reusable Components:** `TrackTable` and `EntityExplorerLayout`.


### Phase 6: Audio Playback Engine & Player UI
- [ ] Develop the Go audio playback engine supporting all requested formats and gapless playback if possible.
- [ ] Implement 4 Player UI modes: Sticky (bottom bar), Miniplayer, Full Window, and Full Screen.
- [ ] **Dynamic Theming:** Implement palette extraction from artwork (Vibrant/Muted) and real-time CSS variable updates with contrast validation.
- [ ] Build the queue management system (persisting state to settings/database).
- [ ] Integrate with macOS "Now Playing" and implement AirPlay 2 support.

### Phase 7: Advanced Features
- [ ] **Search:** Implement the unified search UI with horizontal scrolling carousels for different entity types.
- [ ] **Playlists:** Build CRUD operations for playlists and the playlist detail table view.
- [ ] **EQ:** Implement the graphic equalizer backend and UI with multiple profiles.
- [ ] **Lyrics:** Build the scrolling lyrics UI, automatic search integration, and manual entry/fallback logic.

### Phase 8: Polish & Release
- [ ] Implement context menus globally (add to playlist, play next, favorite, open album, delete).
- [ ] Build the metadata editing dialog and save functionality.
- [ ] Ensure seamless window closing while music continues playing in the background (system tray integration).
- [ ] Final optimization, UI polish, and cross-platform build configurations.

## 6. Verification & Testing
- Write unit tests for the Go metadata extraction, search indexing, and database layers.
- Implement component tests for critical Vue components (Player, Virtual Lists).
- Perform manual end-to-end UX testing with a large mock library (10k+ tracks) to ensure UI responsiveness.
- Verify AirPlay 2 and OS Now Playing integration on a physical macOS device.

## 7. Migration & Rollback Strategies
- Use a robust schema migration tool for SQLite (e.g., `golang-migrate`) to allow safe upgrades of the database schema as the app evolves.
- Backup the SQLite database before applying any major metadata syncing operations or schema migrations.
