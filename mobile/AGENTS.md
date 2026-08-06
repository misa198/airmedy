# Airmedy Mobile Agent Guide

## Scope and current phase

`mobile/` is Airmedy's Kotlin Multiplatform mobile application. It is an
independent application: do not assume or add an integration with the desktop
Wails app, its SQLite database, or its Remote/WebSocket server unless a task
explicitly requests it.

The project is **Android-first**. Work only on Android unless the task explicitly
authorizes iOS work. The existing `iosApp` directory and iOS KMP targets are
frozen scaffolding: do not edit, build, test, or extend them.

Read `README.md` in this directory before changing mobile code. From the
repository root, use `./mobile/gradlew`; from this directory, use `./gradlew`.

## Architecture

The mobile app shares business logic but never shares UI:

```text
androidApp (Android Compose UI, ViewModels, navigation, Android adapters)
    -> sharedLogic (common models, use cases, ports, validation)
```

- `sharedLogic/src/commonMain` contains platform-neutral domain models, use
  cases, repository/service interfaces (ports), validation, and reusable
  business rules. It must not import Android, iOS, Compose, navigation, or UI
  state APIs.
- `androidApp` owns every Android screen, composable, ViewModel, navigation
  graph, Android resource, lifecycle binding, and implementation of shared
  ports. Android code calls shared use cases; it must not duplicate their
  business rules.
- A future iOS app will use native SwiftUI and implement the same `sharedLogic`
  ports. Do not introduce Compose Multiplatform UI, common composables, shared
  navigation, shared ViewModels, or shared UI resources to prepare for it.
- Keep dependency direction inward: UI/adapters depend on `sharedLogic`;
  `sharedLogic` depends only on its own contracts and multiplatform libraries.
  Construct dependencies at the Android composition root. Prefer constructor
  injection; do not add a DI framework without a task-level reason.

## Kotlin and Android conventions

- Use the Gradle version catalog in `gradle/libs.versions.toml`; do not hardcode
  dependency or plugin versions in module build files.
- Keep the existing Android SDK policy unless a task explicitly changes it:
  `minSdk 30`, `compileSdk 36`, and `targetSdk 36`.
- Expose asynchronous shared work as `suspend` functions and `Flow` where state
  updates are needed. Use structured concurrency and propagate cancellation;
  common code must not rely on `Dispatchers.Main`.
- Keep mutable UI state in Android ViewModels. Collect flows with lifecycle-aware
  Android APIs, and release listeners, jobs, and platform resources when their
  lifecycle ends.
- Put strings, icons, themes, and accessibility semantics in Android resources
  or Android UI code. Do not copy desktop Vue/Tailwind conventions into Compose.
- Add a test with each feature or fix: common business rules in `sharedLogic`
  common tests, Android adapter/ViewModel behaviour in Android host tests, and
  Compose UI behaviour in Android UI tests when UI is introduced.

## Workflow and verification

1. Inspect the relevant module and this guide before editing. Keep iOS untouched.
2. Add or update tests with the change, using `kotlin.test` for shared logic.
3. Run the narrowest relevant Gradle task first. The baseline Android checks are:

   ```bash
   ./gradlew :sharedLogic:testAndroidHostTest
   ./gradlew :androidApp:assembleDebug
   ```

4. Run any Android-specific test task added by the feature. `task verify` at the
   repository root currently validates the desktop app only and does not replace
   these mobile checks.
5. Update this README and this guide whenever the mobile module graph, shared
   contracts, platform boundary, commands, or supported targets change.

## Out of scope until explicitly requested

- iOS implementation, Xcode project changes, and iOS test/build commands.
- Shared UI code or a cross-platform design system.
- Desktop backend, database, remote-control protocol, and Wails bindings.
- Production signing, publishing, CI, and release automation.
