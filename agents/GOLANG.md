# Agent Go Backend Mandates

This document serves as the internal instruction set for the agent to ensure high-quality, performant, and idiomatic Go development for the Airmedy backend, following **Package-Oriented + Hexagonal (Ports & Adapters)** architecture.

## 1. Architectural Integrity: Hexagonal / Ports & Adapters

The codebase MUST isolate core business logic from external concerns (UI, DB, Search, Audio).

### Core Layers:
- **`internal/domain`**:
    - **Entities**: Plain Go structs representing core business objects (e.g., `Track`, `Playlist`, `Artist`).
    - **Ports (Interfaces)**: Repository and Service interfaces. Core logic depends on these, NOT on implementations.
- **`internal/app`**:
    - **Application Services / Use Cases**: Orchestrates domain entities and ports. Framework-agnostic.

### Infrastructure Layer (Adapters):
- **`internal/infra`**:
    - **`internal/infra/sqlite`**: Domain repository implementations using SQLite + sqlx + golang-migrate.
    - **`internal/infra/bleve`**: Search port implementation using Bleve v2.
    - **`internal/infra/audio`**: Audio playback adapters — `player_darwin.go` (AVFoundation, macOS) and `player_miniaudio.go` (Windows/Linux).
    - **`internal/infra/metadata`**: Metadata extraction via TagLib; FFmpeg fallback for unsupported formats.
    - **`internal/infra/artwork`**: Artwork caching, resizing, and color palette extraction.
    - **`internal/infra/logging`**: Structured logging via `zap` + `lumberjack` (file rotation).
    - **`internal/infra/wails`**: Wails v3 bindings. Translate between Wails requests and `internal/app` services. Keep thin.

### Entry Point:
- **`main.go`** (root): Wires all Uber FX modules, registers infra adapters against domain ports, and starts the Wails application. There is no `cmd/` directory.

## 2. Dependency Rule
- **Dependencies MUST point inwards**: `infra` → `app` → `domain`.
- `domain` MUST NOT import anything from `app` or `infra`.
- `app` MUST NOT import anything from `infra`.
- Interfaces defined in `domain` or `app` are implemented in `infra`.

## 3. Dependency Injection (Uber FX)
- Use `fx.Module` in each package to declare providers.
- Wire everything in `main.go` via `fx.New(...)`.
- Never instantiate infrastructure directly in `app` or `domain`.

## 4. Performance & Concurrency
- **Goroutines**: Use for long-running tasks (directory scanning, search indexing). Always accept `context.Context` for cancellation.
- **SQLite**: Use transactions for bulk operations. Ensure write serialization.

## 5. Error Handling & Logging
- **Structured Errors**: Wrap errors with context using `fmt.Errorf("...: %w", err)`.
- Domain errors defined in `domain`; translated to user-facing messages in `infra/wails`.
- **Logging**: Use `zap.Logger` (injected via FX). Never use `log.Print*` or `fmt.Print*` in production paths.

## 6. Implementation Checklist
- [ ] Is the business logic isolated in `domain` or `app`?
- [ ] Does the `infra` layer implement a defined Port?
- [ ] Is Uber FX used for dependency injection wiring in `main.go`?
- [ ] Are Wails bindings kept thin (translation only, no business logic)?
- [ ] Are goroutines cancellable via `context.Context`?
- [ ] Is `zap.Logger` used for all structured logging?
