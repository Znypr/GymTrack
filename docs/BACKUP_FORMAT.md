# GymTrack backup format v1

GymTrack backups are lossless, local files for disaster recovery, upgrades, reinstalls, and device migration. They are separate from selected-workout export and from compact `TrainingSummary` export.

## User-visible file

A backup is created through the Android system document picker with a name like:

```text
GymTrack-backup-2026-07-05.gymtrack-backup
```

The file extension is GymTrack-specific. Internally, the file is a ZIP archive containing JSON entries.

## Archive entries

Version 1 archives contain exactly these supported entries:

```text
manifest.json
data.json
```

The reader ignores unsupported extra entries, but restore only depends on the two entries above. A duplicate supported entry is rejected.

Each supported entry is limited to 50 MiB while reading.

## `manifest.json`

`manifest.json` is metadata used to validate and describe the backup before restore.

Fields:

- `formatVersion`: backup contract version. Current value: `1`.
- `createdAtEpochMillis`: backup creation time in Unix epoch milliseconds.
- `appVersion`: GymTrack app version that wrote the backup.
- `databaseSchemaVersion`: Room schema version used when the backup was written.
- `payloadSha256`: SHA-256 digest of the raw UTF-8 `data.json` bytes.
- `counts`: record counts for each backed-up collection.

`counts` contains:

- `legacyNotes`
- `legacyExercises`
- `legacySets`
- `canonicalCategories`
- `canonicalExercises`
- `canonicalExerciseAliases`
- `canonicalWorkouts`
- `canonicalWorkoutExercises`
- `canonicalWorkoutSets`

The total user-facing restore count is the sum of those count fields.

Example shape:

```json
{
  "formatVersion": 1,
  "createdAtEpochMillis": 1783286400000,
  "appVersion": "1.8",
  "databaseSchemaVersion": 10,
  "payloadSha256": "...",
  "counts": {
    "legacyNotes": 12,
    "legacyExercises": 30,
    "legacySets": 120,
    "canonicalCategories": 6,
    "canonicalExercises": 30,
    "canonicalExerciseAliases": 4,
    "canonicalWorkouts": 12,
    "canonicalWorkoutExercises": 72,
    "canonicalWorkoutSets": 120
  }
}
```

## `data.json`

`data.json` is the durable backup payload. It stores settings plus the legacy and canonical workout tables required for a lossless restore during the canonical-data transition.

Top-level fields:

- `settings`
- `legacyNotes`
- `legacyExercises`
- `legacySets`
- `canonicalCategories`
- `canonicalExercises`
- `canonicalExerciseAliases`
- `canonicalWorkouts`
- `canonicalWorkoutExercises`
- `canonicalWorkoutSets`

Legacy set records include `weightUnit` when available. Older v1 backup files that do not contain this field are still readable and default missing legacy set units to `KG` during decode because previous legacy rows had no source-unit column.

The canonical records are the long-term source for workout history. Legacy records remain in the backup while the app still needs them for compatibility with existing note-based storage and migration paths.

## Validation rules

Before restore changes local data, GymTrack must read and validate the complete archive.

A backup is rejected when:

- `manifest.json` is missing or malformed;
- `data.json` is missing or malformed;
- `formatVersion` is not supported by the running app;
- `payloadSha256` does not match the raw `data.json` bytes;
- supported entries are duplicated;
- a supported entry exceeds the reader size limit;
- manifest record counts do not match the decoded payload;
- IDs that must be unique are duplicated;
- legacy sets reference missing legacy workouts or exercises;
- canonical aliases reference missing exercises;
- canonical workouts reference missing categories;
- canonical workout exercises reference missing workouts or exercises;
- canonical workout sets reference missing workout exercises;
- canonical exercise parent references are missing, self-referential, or cyclic.

Unsupported backup versions fail safely with an explicit unsupported-version error. The app does not guess or partially import unknown formats.

## Restore semantics

The first backup release supports **replace all local data** only.

Restore behavior:

1. Inspect and validate the selected backup.
2. Show a confirmation dialog with the validated record count.
3. If the current install has restorable local records, offer to create a safety backup before restore.
4. If the user chooses a safety backup, write the current app state to a user-selected `.gymtrack-backup` destination before replacement starts.
5. If confirmed, snapshot the current database and settings in memory for ordinary rollback.
6. Replace all backed-up database tables inside a transaction.
7. Save backed-up settings.
8. Stop any active timer state after successful restore.
9. If ordinary restore work fails, restore the previous database and settings snapshot.

The safety backup is a separate user-selected file. It protects against user regret, later manual recovery needs, or failures outside the ordinary in-process rollback path. If safety backup creation fails, restore does not start.

Before risky schema-changing test builds or releases, users and testers should create a `.gymtrack-backup` from the currently installed build before installing or opening the risky build. See `docs/PRE_MIGRATION_BACKUP_POLICY.md`.

Merge restore, selective restore, encryption, cloud upload, and automatic backups are intentionally out of scope for v1.

## Privacy and storage

Backups may contain private training history, settings, notes, exercise names, timestamps, weight units, and performance data. GymTrack writes backups only to a user-selected destination through the Android system document picker and does not upload them automatically.

Users are responsible for storing backup files securely. The v1 format is not encrypted.

## Compatibility policy

`formatVersion` is independent from the Room database schema version. Future app versions may support multiple backup format versions, but each supported version must have an explicit reader or migration path.

Changing JSON field names, required fields, archive entry names, checksum rules, or restore semantics requires a new backup format version or an explicit compatibility reader. Optional additive fields may stay within format v1 when old backups can be decoded deterministically.

Committed fixture resources live under `app/src/androidTest/assets/backup-fixtures/` and are documented in `docs/backup-restore-fixtures.md`. These fixtures protect cross-version compatibility separately from archives generated by the current writer.

## Testing requirements

Changes to the backup format or restore logic require:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Database or restore behavior changes also require emulator-backed validation, including:

- round-trip backup and restore from a populated database;
- replace-all restore into an empty install;
- malformed archive rejection;
- unsupported format-version rejection;
- checksum mismatch rejection;
- failed restore rollback preserving previous database and settings;
- cross-version fixture restore into the current schema;
- safety backup creation before replace-all restore;
- verification that restored history, settings, statistics, source weight units, and export-visible data remain coherent.

## API 36 manual validation checklist

Use this checklist before marking PR #182 ready for review:

1. Install the PR build on an API 36 emulator or device.
2. Create a representative local dataset with settings, categories, at least one completed workout, exercises, sets, notes, and canonical records.
3. Create a `.gymtrack-backup` file through Settings.
4. Restore the backup into the same populated install and confirm the replace-all warning appears before data changes.
5. Restore again while choosing **Create safety backup first** and confirm the current data is written before replacement starts.
6. Restore the same backup into a fresh empty install.
7. Confirm history, editor reopen, statistics, settings, selected-workout export, source weight units, and timer state are coherent after restore.
8. Select a malformed or unsupported backup file and confirm restore is rejected before local data changes.
9. Interrupt or force a restore failure where possible and confirm previous database and settings are preserved.
10. Record device/API level, app version, backup file name, and result in the PR validation section.
