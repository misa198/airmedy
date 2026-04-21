# Agent Wails v3 (Alpha) Mandates

This document serves as the internal instruction set for the agent to ensure correct usage of the Wails v3 Alpha framework.

## 1. Application Lifecycle & Initialization
- **V3 Entry Point:** Use `application.NewApp()` and `app.NewWindow()` for initialization.
- **System Tray:** Utilize the native v3 tray implementation for "close-to-tray" behavior.
- **Window Management:** Support macOS-specific features like `Mac.TitleBarHidden` and `Mac.Appearance` for the glassmorphic look.

## 2. Bindings & Communication
- **Method Exposure:** Use the v3 binding syntax for exposing Go structs and methods to the frontend.
- **Event System:** Leverage the v3 event bus for real-time data streaming (e.g., audio levels, playback time).
- **Type Generation:** Ensure `wails generate bindings` is run after Go changes to keep the frontend TypeScript types in sync.

## 3. Frontend & Dev Workflow
- **Vite Integration:** Maintain compatibility with the v3 Vite-based frontend structure.
- **Asset Handling:** Use the built-in v3 asset server for serving local media or artwork if necessary.

## 4. Alpha-Specific Safety
- **API Stability:** Be aware that the v3 API is in alpha. If a common v2 pattern fails, research the v3 equivalent using `grep_search` on the project's Go files or documentation.
- **Error Diagnostics:** In case of build or runtime failures, check the Wails v3 debug logs and ensure the local environment meets the v3 requirements (e.g., Go version, macOS SDK).

## 5. Implementation Checklist
- [ ] **Initialization:** Is the app using the `application` package correctly?
- [ ] **Bindings:** Are the methods exposed and types generated?
- [ ] **OS Integration:** Are macOS-specific v3 flags applied?
- [ ] **Stability:** Does the implementation follow current v3 alpha patterns?

Refer to the root `PLAN.md` for specific implementation phases.
