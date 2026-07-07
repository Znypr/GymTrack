# GymTrack Testing Strategy

## Purpose

Tests protect the active logging workflow and user data. Coverage percentage is secondary to dependable tests around high-risk behavior.

## Required pull-request checks

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

These commands must run in GitHub Actions for every pull request targeting `master`.

## Test layers

### Unit tests

Use local JVM tests for:

- workout parsing;
- note and domain mappings;
- duration and time calculations;
- statistics calculations;
- validation;
- import/export parsing and formatting;
- alias and normalization rules.

Pure logic should not require Android `Context`, Room, or Compose.

### Database and migration tests

Every Room schema change requires migration tests.

At minimum, tests must:

1. create a database at the oldest supported version;
2. insert representative workouts, categories, exercises, and sets;
3. run all migrations to the current version;
4. verify row counts and important values;
5. confirm relationships and ordering;
6. confirm no user data was silently dropped.

Destructive migration fallback is not acceptable in production.

Risky schema changes must also follow `docs/PRE_MIGRATION_BACKUP_POLICY.md`. The PR must document whether the migration is safe or risky, the backup-reminder text for testers or release notes when risky, and why ordinary migration tests are enough when a reminder is not required.

### Compose UI tests

Prioritize:

- creating and editing a workout;
- adding exercise and set lines;
- flag changes;
- autosave and restoration;
- navigation back to history;
- selection and deletion;
- import result messages;
- time-range selection in statistics.

### Instrumented smoke tests

A managed device or emulator smoke suite should eventually validate:

- application launch;
- database creation and upgrade;
- one complete workout flow;
- process recreation;
- foreground timer interaction;
- export through Android storage APIs.

## Regression fixtures

Maintain synthetic fixtures representing real entry patterns:

- explicit weight and repetitions;
- weight carry-over;
- bilateral and unilateral sets;
- supersets;
- bodyweight exercises;
- cardio entries;
- aliases and spelling mistakes;
- midnight rollover;
- long workouts;
- old exported CSV formats;
- malformed and partial imports.

Do not use private user exports as committed fixtures.

## Import/export requirements

Round-trip tests should verify:

```text
canonical workout -> export -> import -> equivalent canonical workout
```

Cover:

- commas and quotation marks;
- multiline learnings;
- Unicode;
- empty optional values;
- duplicate IDs or timestamps;
- unsupported versions;
- partial failures;
- deterministic ordering.

## Manual validation

Runtime pull requests should record:

- device or emulator model;
- Android version;
- application build type;
- steps performed;
- expected and observed result;
- screenshots or video when UI changes.

Manual validation does not replace automated tests.

Risky migration pull requests should additionally record that a `.gymtrack-backup` was created from the pre-upgrade build before installing or opening the risky build.

## Failure policy

- A failing required check blocks merge.
- A check that did not run blocks merge.
- Flaky tests must be fixed or removed from the required set with a documented issue; repeated reruns are not a solution.
- Known unrelated failures require an explicit tracked issue and should not become a permanent exception.

## Future checks

Add when the baseline is stable:

- parser regression corpus;
- Room migration test job;
- import/export round-trip job;
- managed virtual-device smoke test;
- dependency review;
- focused static analysis;
- release bundle validation.
