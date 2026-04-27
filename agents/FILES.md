# Airmedy File Structure

This document provides an overview of the project's directory structure and the purpose of key files.

## Root Directory

- `Taskfile.yml`: Task runner configuration for common development operations.
- `main.go`: Entry point for the Wails application. Wires Uber FX dependency injection, initializes backend services, and starts the UI.
- `go.mod`, `go.sum`: Go module definition and dependencies.
- `agents/THEME.md`: Documentation for the application's design system and theming.
- `agents/GOLANG.md`: Go backend architecture and guidelines.
- `agents/WAILS_V3.md`: Wails v3 usage guidelines.
- `agents/I18N.md`: i18n standards and workflow.
- `agents/INTERACTION.md`: Agent interaction preferences.
- `agents/FILES.md`: This file.

## Backend (`/internal`)

The backend follows a Hexagonal Architecture (Ports & Adapters).

### `/internal/domain` (Core)

Contains business logic, entity models, and interface definitions (ports). No external dependencies.

- `models.go`: Core data structures (Track, Album, Artist, Playlist, etc.) and DTOs.
- `repositories.go`: Interface definitions for database operations.
- `metadata.go`: Interface for metadata extraction.
- `metadata_processing.go`: Business logic for string splitting, deduplication, and tag normalization.
- `search.go`: Interface for search indexing and querying.
- `artwork.go`: Interface for artwork caching and retrieval.
- `audio.go`: Interface for audio playback.

### `/internal/app` (Application)

Application services that coordinate domain logic and infrastructure.

- `module.go`: Uber FX module definition for application services.
- `player_service.go`: Playback control logic.
- `library_service.go`: Library management.
- `playlist_service.go`: Playlist operations.
- `eq_service.go`: 10-band equalizer.
- `lyrics_service.go`: Synced lyrics.
- `queue_service.go`: Queue management.
- `config/`: Configuration management.

### `/internal/infra` (Infrastructure)

Concrete implementations (adapters) for domain interfaces.

- `sqlite/`: Database implementation using SQLite + sqlx + golang-migrate.
  - `migrations/`: SQL migration files for schema versioning.
  - `*_repository.go`: Repository implementations (Tracks, Albums, Artists, Playlists, Genres, Composers, Lyrics, Watched Folders).
  - `columns.go`: Shared column definitions for SQL queries.
  - `sqlite.go`: Database connection and initialization logic.
- `bleve/`: Full-text search indexing using Bleve v2.
- `audio/`: Platform-specific audio playback adapters.
  - `player_darwin.go`: Native macOS AVFoundation player.
  - `player_miniaudio.go`: miniaudio player for Windows/Linux.
- `metadata/`: Metadata extraction using TagLib and FFmpeg fallback.
- `artwork/`: Artwork caching, resizing, and color palette extraction.
  - `cache.go`: Artwork cache management.
  - `resize.go`: Image resizing for optimized display.
  - `palette.go`: Dominant color extraction for dynamic theming.
- `logging/`: Structured logging setup using `zap` + `lumberjack` (file rotation).
- `wails/`: Wails v3 bindings for exposing backend services to the frontend.
  - `assets.go`: Asset handler for serving album artwork via HTTP to the frontend.
  - `library_service.go`: Main Wails bindings for library management.

## Frontend (`/frontend`)

Vue 3 based user interface.

- `package.json`, `pnpm-lock.yaml`: Node.js dependencies (use `pnpm`).
- `tailwind.config.js`, `postcss.config.js`: TailwindCSS v4 configuration.
- `vite.config.ts`: Vite 5 build configuration.
- `tsconfig.json`: TypeScript configuration (strict mode, `@/*` alias → `src/*`).

### `/frontend/src`

- `main.ts`: Frontend entry point.
- `App.vue`: Root Vue component.
- `components/`: Reusable Vue components (Sidebar, PlayerFooter, EQ panel, context menus, grids, dialogs, etc.).
- `views/`: Page-level components (Home, Albums, Artists, Tracks, Playlists, Search, Settings, etc.).
- `stores/`: Pinia state management modules (player, app, device, search, playlists, favorites).
- `composables/`: Vue Composition API hooks (useContextMenu, useGlassBlur, useKeyboardShortcut, useLibraryUpdates, etc.).
- `router/`: Vue Router configuration (`index.ts`).
- `locales/`: vue-i18n translation files — 12 languages: `de`, `en`, `es`, `fr`, `it`, `ja`, `ko`, `pt`, `ru`, `th`, `vi`, `zh`.
- `lib/`: Utility functions and shared libraries.
- `assets/`: Global styles and static assets.
- `bindings/`: Auto-generated Wails TypeScript bindings for backend-frontend communication. Do not edit manually.

## Support & Build (`/build`, `/scripts`)

- `build/`: Icons, manifests, and platform-specific build configurations (macOS, Windows, Linux, Android, iOS).
- `scripts/`: Helper scripts and Git hooks (e.g., `commit-msg`, FFmpeg build scripts).
