# Momentum

Android habit and session tracker built with Kotlin and Jetpack Compose. Data stays on device (Room); optional JSON backup import/export for your own copies.

## Features

- **Today:** See scheduled sessions, quick complete/skip, tasks and notes per habit, activity log (long-press on log entries for actions).
- **Habits:** List grouped by valence, create/edit habits, archive, detail screens with navigation.
- **Progress:** Streak summary, weekly grid, charts for scheduled and unscheduled habits.
- **Settings:** Theme (light/dark/system), JSON backup export and replace-database import (`MomentumBackupV1`).

## Requirements

- Android Studio (recommended) or compatible Gradle environment
- JDK **17**
- Android SDK with **compileSdk 34**
- **minSdk 26**

## Setup

1. Clone this repository.
2. Copy `local.properties.example` to `local.properties` and set `sdk.dir` to your Android SDK path (Android Studio usually creates `local.properties` for you when you open the project).

## Build

```bash
./gradlew assembleDebug
```

Install the debug build from `app/build/outputs/apk/debug/` or run from Android Studio.

## Security & privacy notes

- No accounts or cloud sync in-app; treat exported backup files as sensitive if they contain personal notes.
- Do not commit release keystores, `local.properties`, or `google-services.json` (ignored by `.gitignore`).

## License

See [LICENSE](LICENSE).
