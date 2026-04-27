# Agent Wails v3 (Alpha) Mandates

This document serves as the internal instruction set for the agent to ensure correct usage of the Wails v3 Alpha framework.

## 1. Application Lifecycle & Initialization

- **V3 Entry Point:** Use `application.NewApp()` and `app.NewWindow()` in `main.go` for initialization.
- **System Tray:** Utilize the native v3 tray implementation for "close-to-tray" behavior.
- **Window Management:** Support macOS-specific features like `Mac.TitleBarHidden` and `Mac.Appearance` for the glassmorphic look.
- **DI Wiring:** All Wails services are provided via Uber FX modules registered in `main.go`.

## 2. Bindings & Communication

- **Method Exposure:** Use the v3 binding syntax for exposing Go structs and methods to the frontend.
- **Event System:** Leverage the v3 event bus for real-time data streaming (e.g., audio levels, playback time updates).
- **Type Generation:** Run `wails3 generate bindings` after Go binding changes to keep `frontend/src/bindings/` TypeScript types in sync. Never edit `bindings/` manually.

## 3. Asset Handling

- Local media files and album artwork are served via a custom asset handler in `internal/infra/wails/assets.go`.
- When adding new local resource types, follow the existing pattern in `assets.go` — register a handler with the Wails asset server rather than embedding files or using filesystem paths directly in the frontend.

## 4. Frontend & Dev Workflow

- **Vite Integration:** Maintain compatibility with the v3 Vite-based frontend structure (`frontend/vite.config.ts`).
- **Dev mode:** Run `task dev` (wraps `wails3 dev`) for HMR development.
- **Package manager:** Use `pnpm` for all frontend package operations.

## 5. Alpha-Specific Safety

- **API Stability:** v3 API is in alpha. If a common v2 pattern fails, search the project's Go files for the v3 equivalent before assuming it works the same way.
- **Error Diagnostics:** On build or runtime failures, check Wails v3 debug logs and verify Go version and macOS SDK requirements.

## 6. Implementation Checklist

- [ ] **Initialization:** Is the app using the `application` package correctly?
- [ ] **Bindings:** Are methods exposed and `wails3 generate bindings` run?
- [ ] **Assets:** Are local resources served via the asset handler in `assets.go`?
- [ ] **OS Integration:** Are macOS-specific v3 flags applied?
- [ ] **Stability:** Does the implementation follow current v3 alpha patterns (not v2)?
