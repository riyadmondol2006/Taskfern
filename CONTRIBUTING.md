# Contributing to Taskfern

Thanks for helping. Taskfern aims to stay fast, private, offline and simple; contributions that keep it that way are
very welcome.

## Before you start

- For bugs, open an issue with steps to reproduce, your phone model and Android version, and the Taskfern version.
- For features, open an issue first so we can agree that it fits (no accounts, no network, no trackers, no ads).

## Development setup

1. Install JDK 21 and the Android SDK (platform 37). Android Studio works, but is not required.
2. Clone the repository and build:

   ```bash
   ./gradlew :app:assembleDebug          # debug APK in app/build/outputs/apk/debug/
   ./gradlew :app:testDebugUnitTest      # JVM unit tests
   ./gradlew :app:lintDebug              # Android Lint
   ./gradlew :app:connectedDebugAndroidTest   # instrumented tests, needs a device or emulator
   ```

   Debug builds install as **Taskfern Debug** (`com.riyadmondol2006.taskfern.debug`) next to the release app.

## Code guidelines

- Kotlin, Jetpack Compose and Material 3; follow the existing structure described in
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- Business rules go in plain Kotlin (`domain/`) or repositories and get unit tests. UI never writes to the database
  directly.
- Every database change needs a Room migration, an exported schema in `app/schemas/` and a migration test. Never use
  destructive migrations.
- Do not add the `INTERNET` permission, analytics, ads or proprietary SDKs. The build fails if a network permission
  appears in the merged manifest.
- User-visible text goes in string resources; keep it short, friendly and specific.
- Keep accessibility in mind: content descriptions, 48 dp touch targets, and layouts that work with large fonts.

## Pull requests

- Keep each pull request focused, describe what changed and how you tested it, and include screenshots for UI changes.
- CI runs unit tests, Lint and builds on every pull request; please make sure they pass.
- By contributing you agree that your contribution is licensed under the MIT License of this project.
