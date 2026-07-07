# Database migrations

GymTrack stores workout history locally. Production database upgrades must never delete the database to recover from a missing migration.

## Current schema

The current Room schema is version 10.

Legacy compatibility tables remain available:

- `notes` for the original note-like workout record;
- `exercises` for the current derived exercise names and aliases;
- `sets` for the current derived set records, including explicit `weightUnit` source-unit storage.

Version 9 adds empty canonical tables beside the legacy tables:

- `categories`;
- `canonical_exercises`;
- `exercise_aliases`;
- `workouts`;
- `workout_exercises`;
- `workout_sets`.

Version 10 adds `sets.weightUnit` to the legacy compatibility set table. Existing rows from schemas 8 and 9 migrate to explicit `KG` because old rows did not store a source unit and GymTrack must not guess silently.

The canonical exercise and set tables use non-conflicting names because v8 already owns `exercises` and `sets`. Version 9 does not rename, delete, backfill, or switch reads away from legacy tables.

## Supported historical versions

Repository history provides complete schemas for these versions:

| Version | Schema change |
|---|---|
| 1 | `notes` contains timestamp and text |
| 2 | Adds required workout title |
| 3 | Adds nullable category name and color |
| 4 | Adds nullable learnings |
| 8 | Adds legacy exercises, sets, timing columns, foreign key, and indices |
| 9 | Adds empty canonical workout tables, relationships, and ordering constraints |
| 10 | Adds explicit source-unit storage to legacy `sets.weightUnit` |

The supported upgrade chain is:

```text
1 -> 2 -> 3 -> 4 -> 8 -> 9 -> 10
```

Versions 5, 6, and 7 do not exist in committed schema history. GymTrack does not guess their structure. Opening one of those databases fails without deleting or replacing the database file.

## Version 8 to 9 boundary

The `8 -> 9` migration creates the canonical schema only. It deliberately does not parse note text or copy workout rows.

This keeps two separately reviewable operations:

1. schema creation and validation;
2. idempotent legacy backfill with ambiguity reporting.

The second operation is tracked separately and must retain every legacy row during compatibility.

## Version 9 to 10 boundary

The `9 -> 10` migration adds source-unit storage to the legacy `sets` table:

```sql
ALTER TABLE sets ADD COLUMN weightUnit TEXT NOT NULL DEFAULT 'KG'
```

This is a preservation migration. It does not convert numeric weights, parse note text, or reinterpret history. Rows that existed before source-unit storage become explicit `KG`, while new completed-workout parsing writes the selected default unit or an explicit typed `kg`/`lb` override.

## Migration policy

- Every known schema transition has an explicit `Migration` object.
- New required columns need a deterministic value for existing rows.
- New nullable columns preserve existing values and default to null.
- Tables, foreign keys, and indices must match Room's generated current schema.
- `fallbackToDestructiveMigration()` is prohibited in production.
- Unsupported or corrupt databases remain on disk for diagnosis or future recovery.
- Data parsing and backfill do not run inside the schema-only `8 -> 9` migration.
- Risky schema upgrades must follow the pre-migration backup reminder policy in `docs/PRE_MIGRATION_BACKUP_POLICY.md`.

## Risky schema upgrades

A migration is risky when it deletes, renames, merges, splits, rewrites, deduplicates, normalizes, re-keys, or converts durable user data, or when it changes backup/restore compatibility behavior.

Risky migration pull requests must define the user-facing reminder text before merge. The baseline requirement is to tell testers or users to create a `.gymtrack-backup` from the currently installed build before installing or opening the risky build. GymTrack must not silently upload backup data.

An in-app reminder is only feasible after the migrated app launches, so it cannot replace pre-install release notes or tester handoff instructions for migrations that run during database open.

## Automated validation

Pull requests that touch database or migration code run instrumented tests on an Android emulator.

The suite creates real SQLite databases at versions 1, 2, 3, 4, 8, and an unsupported version, then verifies:

- workout text, title, category, color, and learnings;
- ordering by workout timestamp;
- creation of legacy and canonical tables and indices;
- legacy row preservation during `8 -> 9`;
- explicit source-unit defaulting during `9 -> 10`;
- canonical foreign-key cascade behavior;
- exercise-to-set cascade behavior in the legacy schema;
- unsupported upgrades fail without erasing sentinel data.

Local command with a running emulator:

```bash
./gradlew connectedDebugAndroidTest
```
