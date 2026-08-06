# Airmedy Mobile

Android-first Kotlin Multiplatform mobile app for Airmedy. The application is
currently independent from the desktop app; it does not share the desktop
database, Wails bindings, or Remote API.

## Status

- Android development is active and uses native Jetpack Compose UI.
- `sharedLogic` is the future cross-platform business-logic module.
- iOS is intentionally frozen. Do not modify `iosApp` or iOS targets unless a
  task explicitly enables iOS work.

See [AGENTS.md](AGENTS.md) for the mandatory engineering rules and cleanup
sequence.

## Module boundaries

```text
androidApp
  Android-only Compose screens, ViewModels, navigation, resources, and adapters
      |
      v
sharedLogic
  Common models, use cases, ports, validation, and business rules
```

`sharedLogic` must remain UI- and platform-neutral. Android implements its ports
inside `androidApp`. When iOS work is authorized, it will have native SwiftUI and
its own adapters while using the same shared business contracts.

## Prerequisites

- JDK 11, as configured by the Gradle modules.
- Android SDK Platform 36 and an Android device or emulator for runtime testing.
- Android Studio is recommended for running and inspecting the Android app.

## Commands

Run from `mobile/`:

```bash
./gradlew :androidApp:assembleDebug
./gradlew :sharedLogic:testAndroidHostTest
```

Or, from the repository root:

```bash
./mobile/gradlew :androidApp:assembleDebug
./mobile/gradlew :sharedLogic:testAndroidHostTest
```

The repository-root `task verify` command covers desktop Go/Vue code only.
Mobile changes must run the relevant Gradle build and tests above, plus any
feature-specific Android host or UI test task.
