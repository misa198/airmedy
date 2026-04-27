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

### 2. UI Design System

#### Token Usage

The project uses a **custom glass-morphism dark theme**. Only use tokens defined in `frontend/tailwind.config.js`. Never invent ShadCN-standard tokens that are not in the config.

Defined tokens: `background`, `foreground`, `glass`, `glass-elevated`, `border-glass`, `border`, `primary`, `card`, `accent`, `muted`, `sidebar.*`, `dynamic.*`

#### Apple-Style Design Principles

- **Borders**: Use `border-white/[0.06]` for separators, `ring-1 ring-white/8` for card outlines. Never use a plain `border` class without an explicit low-opacity color.
- **Backgrounds**: Player bars and overlays use `bg-background/80 backdrop-blur-2xl`. Cards and surfaces use `bg-card` (`#1A1A1A`).
- **Row hover states**: `hover:bg-white/[0.04]` — never use `hover:bg-accent/50` on list rows.
- **Button resting states**: Use opacity variation (`text-white/40 → text-white/70`) rather than color switches for inactive/active toggle states.
- **Play button (footer/mini)**: White circle with black icon (`bg-white`, icon `text-black`). Play button in full-screen: white circle, larger.
- **Typography**: Secondary / metadata text uses `text-white/40` or `text-white/30`. Never use `text-muted-foreground` on elements that are overlaid on a dark translucent background — prefer explicit opacity variants.

#### ShadCN Component Rules

- **Sliders / progress bars**: Always use `@/components/ui/slider/Slider.vue`. Never use `<input type="range">` directly in templates.
- **Text inputs**: Always use `@/components/ui/input/Input.vue`. Never use raw `<input type="text">` in templates.
- New ShadCN components go in `frontend/src/components/ui/<name>/`.

#### Package Manager

Use **pnpm** for all commands (`pnpm build`, `pnpm test`, `pnpm dev`). Never use npm.

### 2.1 Internationalization (i18n)

- **Frontend Localization:** Use `vue-i18n`. All user-facing strings MUST be localized via `frontend/src/locales/en.json`.
- **No Hardcoding:** Never hardcode strings in templates or scripts. Use `$t()` or `t()` from `useI18n()`.
- **No Inline Fallbacks:** Localized keys must exist in the locale files; do not provide inline fallback strings in the code.
- **Workflow:** Add keys to `en.json` first, then use them in components.

### 2.5 Frontend Component Architecture

#### Component Classification

- **UI Primitives (`frontend/src/components/ui/`)**: Low-level, generic, stateless components (buttons, inputs, sliders). They MUST NOT import from `stores/` or `bindings/`. They are strictly for layout, styling, and basic interactivity.
- **Feature Components (`frontend/src/components/`)**: Domain-aware, application-specific components (e.g., `AlbumCard.vue`, `TrackTable.vue`, `MiniPlayer.vue`). They MAY import from `stores/` and `bindings/` and are designed to be reused across multiple views.
- **Views/Pages (`frontend/src/views/`)**: Top-level route-level components. They orchestrate feature components and handle page-specific fetching and logic.

#### Separation and Reusability

- **The Rule of Three**: If a UI pattern or piece of logic is used in more than two places, it MUST be extracted into a reusable component or composable.
- **Composition over Props**: Favor `<slot />` and component composition over adding an excessive number of props to a single component.
- **Single Responsibility**: Components should do one thing well. If a component's `<template>` or `<script>` grows beyond 300 lines, it should be split into smaller, more focused sub-components.
- **Logic Extraction**: Shared logic (formatting, fallbacks) should reside in `frontend/src/lib/utils.ts` or in a dedicated composable within `frontend/src/composables/`.

#### Type Safety & Testing

- **TypeScript**: All components MUST use `<script setup lang="ts">`. Props and Emits MUST be explicitly typed using `defineProps<{ ... }>()` and `defineEmits<{ ... }>()`.
- **Unit Testing**: Every non-trivial feature component MUST have a corresponding `.spec.ts` file in the same directory using Vitest and `@vue/test-utils`.
- **Validation**: A component is not complete until it has been tested for accessibility and responsiveness.

### 3. Architecture & Patterns

- **Hexagonal / Ports & Adapters**: The Go backend MUST follow a hexagonal architecture.
  - Business logic resides in `internal/domain` and `internal/app`.
  - External integrations (Wails, SQLite, Bleve) reside in `internal/infra`.
  - Dependencies must always point inwards towards the domain.
- **Dependency Injection**: Use `uber-go/fx` for all dependency management. Define components as `fx.Module` and use `fx.Provide` for constructors. Avoid global state and manual instantiation.
- **Logging**: Use `log/slog` for structured logging. Logs are automatically rotated daily and kept for 7 days via `lumberjack`. Inject `*slog.Logger` where needed.
- **Surgical Backend**: Keep Wails bindings thin in `internal/infra/wails`. They should only translate frontend requests to application service calls.
- **Reactive State**: Use Pinia for all global UI states (playback queue, current track, user settings). Avoid prop drilling.
- **Component Design**: Adhere strictly to the **Frontend Component Architecture** (see section 2.5). Prioritize modularity, clear separation of concerns, and testability.

### 4. Git & Commits

- **Convention**: Use Conventional Commits (`type(scope): description`).
  - Types: `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`.
  - Scopes: `core`, `app`, `infra`, `domain`, `ui`, `meta`.
- **Hooks**: Ensure git hooks are installed via `task setup:hooks`. The `commit-msg` hook enforces the convention.

### 5. Data Integrity & Safety

- **Schema Migrations:** Use a structured migration tool for SQLite. Never perform destructive schema changes without a migration path.
- **Metadata:** Always treat the user's original music files as read-only unless the user explicitly triggers a "Save Metadata" action.
- **Error Handling:** Implement robust error handling in Go and propagate meaningful errors to the frontend via Wails.

### 6. Testing & Verification

- **Mandatory Tests:** ALL bug fixes and new features MUST be accompanied by relevant unit tests (Go) or component tests (Vue).
- **Regression Testing:** Before completing any directive, run all existing tests to ensure no regressions were introduced.
- **Verification:** A task is only complete when behavioral correctness has been verified through both automated tests and manual verification of the UI/UX.

### 7. macOS Integration

- **Now Playing:** Ensure seamless integration with the macOS "Now Playing" widget and media keys.
- **AirPlay 2:** Prioritize native AirPlay 2 support for audio output.
- **Window Management:** Support "close to tray" behavior where music continues playing after the main window is closed.

## Implementation Workflow

1. **Research:** Analyze existing Go/Vue patterns and identify all requirements. **ALWAYS reproduce bugs with a test case first.**
2. **Execution (The Implementation Loop):**
   - **Implement:** Apply surgical changes. **New code MUST have accompanying unit or component tests.**
   - **Verify:** Execute `task verify` (or equivalent) to run all tests and linters.
   - **Review & Recheck:** Perform a critical self-review of the code and re-verify against the original requirements and project mandates.
   - **Fix or Continue:** Address any discrepancies, bugs, or missing features identified during review before moving on.
3. **Validation:** Verify performance with large datasets and OS-level integrations.

## Files Reference:

Read agent/FILES.md to know about file structure and agent guidelines.
