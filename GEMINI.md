# Airmedy Project Mandates

This document defines the foundational mandates and technical standards for the Airmedy music player project. All AI agent interactions must adhere to these guidelines.

## Technical Stack
- **Framework:** Wails v3 (Go backend, Web frontend)
- **Frontend:** Vue.js 3 (Composition API), Pinia (State Management), Vue I18n (Localization)
- **UI Components:** ShadCN-vue, Tailwind CSS
- **Database:** SQLite (Relational data & Lyrics storage)
- **Search:** Bleve (Embedded Go search engine)
- **File Watching:** fsnotify (Real-time library updates)

## Core Guidelines

### 1. Performance & Scalability
- **Virtualization:** All lists and grids MUST use virtual scrolling (e.g., `vue-virtual-scroller`) to handle libraries with 10,000+ tracks without UI lag.
- **Concurrency:** Use Go's goroutines for heavy tasks like metadata extraction, directory scanning, and search indexing to keep the UI responsive.
- **Database Optimization:** Use appropriate indexing in SQLite and Bleve to ensure sub-millisecond query times for common operations.

### 2. Architecture & Patterns
- **Hexagonal / Ports & Adapters**: The Go backend MUST follow a hexagonal architecture. 
    - Business logic resides in `internal/domain` and `internal/app`.
    - External integrations (Wails, SQLite, Bleve) reside in `internal/infra`.
    - Dependencies must always point inwards towards the domain.
- **Surgical Backend**: Keep Wails bindings thin in `internal/infra/wails`. They should only translate frontend requests to application service calls.
- **Reactive State**: Use Pinia for all global UI states (playback queue, current track, user settings). Avoid prop drilling.
- **Component Design**: Follow ShadCN-vue patterns for consistent UI/UX. Prioritize reusable, accessible components.


### 3. Data Integrity & Safety
- **Schema Migrations:** Use a structured migration tool for SQLite. Never perform destructive schema changes without a migration path.
- **Metadata:** Always treat the user's original music files as read-only unless the user explicitly triggers a "Save Metadata" action.
- **Error Handling:** Implement robust error handling in Go and propagate meaningful errors to the frontend via Wails.

### 4. macOS Integration
- **Now Playing:** Ensure seamless integration with the macOS "Now Playing" widget and media keys.
- **AirPlay 2:** Prioritize native AirPlay 2 support for audio output.
- **Window Management:** Support "close to tray" behavior where music continues playing after the main window is closed.

## Implementation Workflow
1. **Research:** Analyze existing Go/Vue patterns in the codebase.
2. **Strategy:** Update `PLAN.md` if a task requires architectural changes.
3. **Execution:** Apply surgical changes with accompanying unit or component tests.
4. **Validation:** Verify performance with large datasets and OS-level integrations.
