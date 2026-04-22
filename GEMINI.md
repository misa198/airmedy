# Airmedy Project Mandates

This document defines the foundational mandates and technical standards for the Airmedy music player project. All AI agent interactions must adhere to these guidelines.

## Technical Stack
- **Framework:** Wails v3 (Go backend, Web frontend)
- **Dependency Injection:** uber-go/fx
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
- **Dependency Injection**: Use `uber-go/fx` for all dependency management. Define components as `fx.Module` and use `fx.Provide` for constructors. Avoid global state and manual instantiation.
- **Logging**: Use `log/slog` for structured logging. Logs are automatically rotated daily and kept for 7 days via `lumberjack`. Inject `*slog.Logger` where needed.
- **Surgical Backend**: Keep Wails bindings thin in `internal/infra/wails`. They should only translate frontend requests to application service calls.
- **Reactive State**: Use Pinia for all global UI states (playback queue, current track, user settings). Avoid prop drilling.
- **Component Design**: Follow ShadCN-vue patterns for consistent UI/UX. Prioritize reusable, accessible components.

### 3. Git & Commits
- **Convention**: Use Conventional Commits (`type(scope): description`). 
    - Types: `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`.
    - Scopes: `core`, `app`, `infra`, `domain`, `ui`, `meta`.
- **Hooks**: Ensure git hooks are installed via `task setup:hooks`. The `commit-msg` hook enforces the convention.

### 4. Data Integrity & Safety

- **Schema Migrations:** Use a structured migration tool for SQLite. Never perform destructive schema changes without a migration path.
- **Metadata:** Always treat the user's original music files as read-only unless the user explicitly triggers a "Save Metadata" action.
- **Error Handling:** Implement robust error handling in Go and propagate meaningful errors to the frontend via Wails.

### 5. Testing & Verification
- **Mandatory Tests:** ALL bug fixes and new features MUST be accompanied by relevant unit tests (Go) or component tests (Vue).
- **Regression Testing:** Before completing any directive, run all existing tests to ensure no regressions were introduced.
- **Verification:** A task is only complete when behavioral correctness has been verified through both automated tests and manual verification of the UI/UX.

### 6. macOS Integration
- **Now Playing:** Ensure seamless integration with the macOS "Now Playing" widget and media keys.
- **AirPlay 2:** Prioritize native AirPlay 2 support for audio output.
- **Window Management:** Support "close to tray" behavior where music continues playing after the main window is closed.

## Implementation Workflow
1. **Research:** Analyze existing Go/Vue patterns and identify all requirements. **ALWAYS reproduce bugs with a test case first.**
2. **Strategy:** Update `PLAN.md` if a task requires architectural changes or new testing strategies.
3. **Execution (The Implementation Loop):**
    - **Implement:** Apply surgical changes. **New code MUST have accompanying unit or component tests.**
    - **Verify:** Execute `task verify` (or equivalent) to run all tests and linters.
    - **Review & Recheck:** Perform a critical self-review of the code and re-verify against the original requirements and project mandates.
    - **Fix or Continue:** Address any discrepancies, bugs, or missing features identified during review before moving on.
4. **Validation:** Verify performance with large datasets and OS-level integrations.
