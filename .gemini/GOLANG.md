# Agent Go Backend Mandates

This document serves as the internal instruction set for the agent to ensure high-quality, performant, and idiomatic Go development for the Airmedy backend, following **Package-Oriented + Hexagonal (Ports & Adapters)** architecture.

## 1. Architectural Integrity: Hexagonal / Ports & Adapters
The codebase MUST be organized to isolate the core business logic from external concerns (UI, DB, Search).

### Core Layers:
- **`internal/domain`**: 
    - **Entities**: Plain Go structs representing the core business objects (e.g., `Track`, `Playlist`, `User`).
    - **Ports (Interfaces)**: Definition of Repository and Service interfaces. Core logic depends on these, NOT on implementations.
- **`internal/app`**:
    - **Application Services / Use Cases**: Orchestrates domain entities and ports to fulfill specific business requirements. Logic here should be framework-agnostic.

### Infrastructure Layer (Adapters):
- **`internal/infra`**:
    - **`internal/infra/sqlite`**: Implementation of domain repositories using SQLite.
    - **`internal/infra/bleve`**: Implementation of search ports using Bleve.
    - **`internal/infra/wails`**: Wails-specific bindings. These "adapters" translate between Wails requests and `internal/app` services. Keep these thin.
    - **`internal/infra/fsnotify`**: Library watching implementation.

### Entry Point:
- **`cmd/airmedy`**: The main entry point for the application. It handles dependency injection, wiring adapters to ports, and starting the Wails application.

## 2. Dependency Rule
- **Dependencies MUST point inwards**: `infra` -> `app` -> `domain`.
- `domain` MUST NOT import anything from `app` or `infra`.
- `app` MUST NOT import anything from `infra`.
- Interfaces defined in `domain` or `app` are implemented in `infra`.

## 3. Performance & Concurrency
- **Goroutines**: Use them for long-running tasks like directory scanning and search indexing. Always use `context.Context` for cancellation.
- **SQLite Optimization**: Use the implementation in `internal/infra/sqlite`. Ensure thread-safety and use transactions for bulk operations.

## 4. Error Handling & Logging
- **Structured Errors**: Wrap errors with context. Domain errors should be defined in the `domain` package and translated to meaningful frontend errors in the `infra/wails` layer.

## 5. Implementation Checklist
- [ ] Is the business logic isolated in `domain` or `app`?
- [ ] Does the `infra` layer implement a defined Port?
- [ ] Is dependency injection used in `cmd/` to wire everything up?
- [ ] Are Wails bindings kept thin (strictly for translation)?

