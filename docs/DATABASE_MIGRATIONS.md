# Database migrations

GymTrack stores workout history locally. Production database upgrades must never delete the database to recover from a missing migration.

## Current schema

The current Room schema is version 8 and contains:

- `notes` for the original note-like workout record;
- `exercises` for normalized exercise names and aliases;
- `sets` for structured set records linked to exercises.

## Supported historical versions

Repository history provides complete schemas for these versions:

| Version | Schema change |
|---|---|
| 1 | `notes` contains timestamp and text |
| 2 | Adds required workout title |
| 3 | Adds nullable category name and color |
| 4 | Adds nullable learnings |
| 8 | Adds exercises, sets, timing columns, foreign key, and indices |

The supported upgrade chain is:

```text
1 -> 2 -> 3 -> 4 -> 8
```

Versions 5, 6, and 7 do not exist in committed schema history. GymTrack does not guess their structure. Opening one of those databases fails without deleting or replacing the database file.

## Migration policy

- Every known schema transition has an explicit `Migration` object.
- New required columns need a deterministic value for existing rows.
- New nullable columns preserve existing values and default to null.
- Tables, foreign keys, and indices must match Room's generated current schema.
- `fallbackToDestructiveMigration()` is prohibited in production.
- Unsupported or corrupt databases remain on disk for diagnosis or future recovery.

## Automated validation

Pull requests that touch database or migration code run instrumented tests on an Android emulator.

The suite creates real SQLite databases at versions 1, 2, 3, 4, and an unsupported version, then verifies:

- workout text, title, category, color, and learnings;
- ordering by workout timestamp;
- creation of normalized tables and indices;
- exercise-to-set cascade behavior;
- unsupported upgrades fail without erasing sentinel data.

Local command with a running emulator:

```bash
./gradlew connectedDebugAndroidTest
```
