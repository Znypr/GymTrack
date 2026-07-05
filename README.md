# GymTrack

GymTrack is a local-first Android workout logger built with Kotlin and Jetpack Compose. It combines a fast, note-like logging workflow with structured workout history and progression statistics.

> **Project status:** active architecture transition. Explicit Room migrations, the canonical v9 schema, deterministic legacy backfill, canonical repository boundaries, dual-read verification, and a versioned `TrainingSummary` projection are merged. The editor/save pipeline, statistics cutover, timer replacement, cleanup, and release configuration remain active work. See the [dated project status](docs/PROJECT_STATUS.md).

## Current capabilities

- Room-backed local workout storage
- Full-screen workout editor
- Relative and elapsed timestamps
- Bilateral, unilateral, and superset flags
- Configurable workout categories and colors
- Workout learnings or notes
- Foreground workout timer on `master`; persisted timer replacement awaiting Android 14+ validation in PR #128
- CSV import and export
- Workout history, statistics, and progression charts
- Dark and light themes
- Explicit database migrations and canonical v9 transition tables
- Deterministic legacy-data backfill and dual-read verification
- Versioned compact training-summary projection for external integrations

## Product direction

GymTrack is intended to become the fastest reliable way for a strength trainee to record a workout in a flexible interface while still producing accurate structured history and useful progression data.

Every major feature should improve at least one of:

1. logging speed;
2. data safety;
3. workout-history clarity;
4. training decision quality.

Read the [project charter](docs/PROJECT_CHARTER.md) for goals, principles, ambitions, and non-goals.

## Documentation

- [Current project status](docs/PROJECT_STATUS.md)
- [Project charter](docs/PROJECT_CHARTER.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Canonical-data transition](docs/CANONICAL_DATA_TRANSITION.md)
- [Training summary contract](docs/TRAINING_SUMMARY.md)
- [Roadmap](docs/ROADMAP.md)
- [Ticket-board rules](docs/TICKET_BOARD.md)
- [Testing strategy](docs/TESTING.md)
- [Repository settings](docs/REPOSITORY_SETTINGS.md)
- [Work-item automation](docs/WORK_ITEM_AUTOMATION.md)
- [Contribution workflow](CONTRIBUTING.md)

GitHub Issues, labels, linked pull requests, and required checks are the live source of truth for current work. Static documentation is orientation only and must carry an explicit review date when it describes status.

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

The same checks run in GitHub Actions for pull requests targeting `master`. Database changes also require the relevant Room migration and emulator-backed validation described in [docs/TESTING.md](docs/TESTING.md).

## Contributing

Start with an issue. An issue that satisfies the Definition of Ready receives `status:ready`, which creates or authorizes a dedicated branch and draft pull request. Once implementation starts, pull-request state replaces `status:ready` as the live board state. See [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/WORK_ITEM_AUTOMATION.md](docs/WORK_ITEM_AUTOMATION.md).

## Architecture direction

GymTrack remains a single Gradle application module at its current scale. The canonical data foundation now exists beside the legacy compatibility path. The remaining transition is incremental:

- separate autosave from finalization, statistics synchronization, and export;
- move the editor to typed draft state;
- move normal history and statistics reads to canonical data;
- remove unconditional startup repair work;
- retain legacy compatibility only for a documented verification period;
- keep Room entities and Android details behind domain repository boundaries.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the current transition architecture and target pipeline.
