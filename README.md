# GymTrack

GymTrack is a local-first Android workout logger built with Kotlin and Jetpack Compose. It combines a fast, note-like logging workflow with structured workout history and progression statistics.

> **Project status:** active development. The architecture audit identifies database migration safety, reproducible CI, and a canonical workout data model as prerequisites for a stable release.

## Current capabilities

- Room-backed local workout storage
- Full-screen workout editor
- Relative and elapsed timestamps
- Bilateral, unilateral, and superset flags
- Configurable workout categories and colors
- Workout learnings or notes
- Foreground workout timer
- CSV import and export
- Workout history, statistics, and progression charts
- Dark and light themes

## Product direction

GymTrack is intended to become the fastest reliable way for a strength trainee to record a workout in a flexible interface while still producing accurate structured history and useful progression data.

Every major feature should improve at least one of:

1. logging speed;
2. data safety;
3. workout-history clarity;
4. training decision quality.

Read the [project charter](docs/PROJECT_CHARTER.md) for goals, principles, ambitions, and non-goals.

## Documentation

- [Project charter](docs/PROJECT_CHARTER.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Ticket board](docs/TICKET_BOARD.md)
- [Testing strategy](docs/TESTING.md)
- [Repository settings](docs/REPOSITORY_SETTINGS.md)
- [Contribution workflow](CONTRIBUTING.md)

GitHub Issues, existing labels, linked pull requests, and CI checks are the live source of truth for current work. No separate GitHub Project board is required or maintained.

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Room
- Preferences DataStore
- Navigation Compose
- Coroutines and Flow
- Gradle Kotlin DSL

## Build

Requirements:

- JDK 17
- Android SDK 35
- Android Studio or the Gradle wrapper

From the repository root:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Validate

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The same checks run in GitHub Actions for pull requests targeting `master`.

## Contributing

Start with an issue. An issue that satisfies the Definition of Ready receives `status:ready`, which creates or authorizes a dedicated branch and draft pull request. Once implementation starts, pull-request state replaces `status:ready` as the live board state. See [CONTRIBUTING.md](CONTRIBUTING.md) for the complete workflow.

## Architecture direction

The project remains a single Gradle application module at its current scale. The target is a layered, feature-oriented package structure with:

- typed domain models;
- one canonical persisted workout model;
- explicit Room migrations;
- pure testable parsing and statistics logic;
- import/export separated from autosave;
- UI and ViewModels isolated from Room entities.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for current and target pipelines.
