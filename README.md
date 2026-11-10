# GymTrack

GymTrack is a local-first Android workout logger built with Kotlin and Jetpack Compose. It combines a fast, note-like logging workflow with structured workout history and progression statistics.

## Current capabilities

- Room-backed local workout storage with explicit migrations
- Full-screen workout editor
- Relative and elapsed timestamps
- Bilateral, unilateral, and superset flags
- Configurable workout categories and colors
- Workout learnings or notes
- Persisted workout timer without a continuously running service
- CSV import and export
- One-file backup and restore
- Workout history, statistics, and progression charts
- Dark and light themes
- Canonical workout tables with legacy-data compatibility
- Versioned compact training summaries for external integrations

## Product direction

GymTrack is intended to become the fastest reliable way for a strength trainee to record a workout in a flexible interface while still producing accurate structured history and useful progression data.

Every major feature should improve at least one of:

1. logging speed;
2. data safety;
3. workout-history clarity;
4. training decision quality.

Read the [project charter](docs/PROJECT_CHARTER.md) for goals, principles, ambitions, and non-goals.

## Work tracking

- [GitHub Issues](https://github.com/Znypr/GymTrack/issues) contain the canonical problem statement, scope, acceptance criteria, priority, dependencies, and validation plan.
- The GitHub Project is a visual view over those issues. It must not contain separate duplicate work items.
- Pull requests contain implementation, review, checks, and runtime-validation evidence.
- Static documentation does not maintain the current queue or backlog.

See [work-tracking rules](docs/TICKET_BOARD.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

## Documentation

- [Project charter](docs/PROJECT_CHARTER.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Workout timer](docs/TIMER.md)
- [Editor save pipeline](docs/EDITOR_SAVE_PIPELINE.md)
- [Editor behaviour (rows, keypad grammar, flags, must-keep time savers)](docs/EDITOR_BEHAVIOUR.md)
- [Roadmap](docs/ROADMAP.md)
- [Work-tracking rules](docs/TICKET_BOARD.md)
- [Canonical-data transition](docs/CANONICAL_DATA_TRANSITION.md)
- [Training-summary contract](docs/TRAINING_SUMMARY.md)
- [Backup format](docs/BACKUP_FORMAT.md)
- [Release app handoff](docs/RELEASE_HANDOFF.md)
- [Phone install runbook](docs/PHONE_INSTALL.md)
- [Old CSV consolidation runbook](docs/OLD_CSV_CONSOLIDATION.md)
- [Testing strategy](docs/TESTING.md)
- [Repository settings](docs/REPOSITORY_SETTINGS.md)
- [Work-item automation](docs/WORK_ITEM_AUTOMATION.md)
- [Contribution workflow](CONTRIBUTING.md)
- [Privacy policy (EN)](docs/legal/PRIVACY_POLICY.md), [Datenschutzerklärung (DE)](docs/legal/DATENSCHUTZ.md), [Impressum template](docs/legal/IMPRESSUM.md)

## Website and privacy policy (GitHub Pages)

The landing page, privacy policy and Impressum live in `site/` (plain HTML/CSS, no tracking, no external requests). The workflow `.github/workflows/pages.yml` deploys `site/` to GitHub Pages on every push to `master` that changes `site/**`.

Publish once:

1. Fill in the placeholders first: `[NAME …]` and `[ADDRESS …]` in `docs/legal/*.md`, and `[TESTER_SIGNUP_URL]` in `site/index.html`.
2. Regenerate the legal pages from the markdown: `python docs/legal/build_pages.py`, then commit `site/`.
3. Repo **Settings → Pages → Build and deployment → Source: GitHub Actions**.
4. Run the "Deploy site to GitHub Pages" workflow (Actions tab → Run workflow), or push a change to `site/`.

Resulting URLs:

- Landing page: `https://znypr.github.io/GymTrack/`
- **Privacy policy URL for Play Console:** `https://znypr.github.io/GymTrack/privacy.html` (German: `datenschutz.html`)
- Impressum: `https://znypr.github.io/GymTrack/impressum.html`

Edit the markdown in `docs/legal/`, never the generated `site/privacy.html`, `site/datenschutz.html` or `site/impressum.html`.

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

## Install on phone

For the Samsung phone, use the checked-in install script instead of Gradle's direct `installDebug` task:

```powershell
.\install.ps1 debug
.\install.ps1 release
.\install.ps1 both
```

The script installs with `adb install --user 0 -r ...` so only the main phone user is updated, avoiding Dual App / Secure Folder clone-profile installs. See [docs/PHONE_INSTALL.md](docs/PHONE_INSTALL.md).

## Validate

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Database changes also require the relevant Room migration and emulator-backed validation described in [docs/TESTING.md](docs/TESTING.md).

## Architecture direction

GymTrack remains a single Gradle application module at its current scale. The target is a layered, feature-oriented architecture with:

- typed domain models;
- one canonical persisted workout model;
- explicit Room migrations;
- pure, testable parsing and statistics logic;
- import/export separated from autosave;
- UI and ViewModels isolated from Room entities.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the current and target data flows.
