# Airmedy File Structure

This document provides an overview of the project's directory structure and the purpose of key files.

## Root Directory

- `main.go`: Entry point for the Wails application. Initializes the backend and starts the UI.
- `PLAN.md`: Development roadmap and current status of project phases.
- `GEMINI.md`: Project mandates and technical standards for AI interactions.
- `Taskfile.yml`: Task runner configuration for common development operations.
- `THEME.md`: Documentation for the application's design system and theming.
- `go.mod`, `go.sum`: Go module definition and dependencies.

## Backend (`/internal`)

The backend follows a Hexagonal Architecture (Ports & Adapters).

### `/internal/domain` (Core)
Contains the business logic, entity models, and interface definitions (ports). This layer has no external dependencies.
- `models.go`: Core data structures (Track, Album, Artist, Playlist, etc.).
- `repositories.go`: Interface definitions for database operations.
- `metadata.go`: Interface for metadata extraction.
- `search.go`: Interface for search indexing and querying.
- `artwork.go`: Interface for artwork caching and retrieval.

### `/internal/app` (Application)
Application services that coordinate domain logic and infrastructure.
- `module.go`: Fx module definition for application services.
- `config/`: Configuration management.

### `/internal/infra` (Infrastructure)
Concrete implementations (adapters) for the domain interfaces.
- `sqlite/`: Database implementation using SQLite.
  - `migrations/`: SQL migration files for schema versioning.
  - `*_repository.go`: Repository implementations for various entities (Tracks, Albums, Artists, Playlists, Genres, Composers, Lyrics, Watched Folders).
  - `columns.go`: Shared column definitions for SQL queries.
  - `sqlite.go`: Database connection and initialization logic.
- `bleve/`: Search indexing implementation using Bleve.
- `metadata/`: Metadata extraction using TagLib.
- `artwork/`: Artwork caching and storage.
- `logging/`: Structured logging setup using `slog`.
- `wails/`: Wails bindings for exposing backend services to the frontend.
  - `assets.go`: Asset handler for serving album artwork.
  - `library_service.go`: Main Wails bindings for library management.

## Frontend (`/frontend`)

Vue.js 3 based user interface.

- `package.json`, `pnpm-lock.yaml`: Node.js dependencies and scripts.
- `tailwind.config.js`, `postcss.config.js`: Tailwind CSS configuration.
- `vite.config.ts`: Vite build configuration.
- `tsconfig.json`: TypeScript configuration.

### `/frontend/src`
- `main.ts`: Frontend entry point.
- `App.vue`: Root Vue component.
- `components/`: Reusable Vue components (Sidebar, PlayerFooter, etc.).
- `views/`: Main page components (Home, Albums, Artists, Tracks, etc.).
- `stores/`: Pinia state management modules.
- `locales/`: I18n localization files.
- `lib/`: Utility functions and shared libraries.
- `assets/`: Global styles and static assets.
- `bindings/`: Automatically generated Wails bindings for backend-frontend communication.

## Support & Build (`/build`, `/scripts`)

- `build/`: Icons, manifests, and platform-specific build configurations (macOS, Windows, Linux, Android, iOS).
- `scripts/`: Helper scripts and Git hooks (e.g., `commit-msg`).
