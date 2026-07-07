# Pre-migration backup reminder policy

GymTrack stores private workout history locally. Schema upgrades must preserve data without requiring a backup, but risky upgrade work should still prompt the user to create a full `.gymtrack-backup` file before installing or opening a build that may change durable data in a hard-to-reverse way.

## Risk levels

### Safe schema change

A schema change is considered safe when all of these are true:

- every supported source schema has an explicit Room migration;
- existing user rows are not deleted, overwritten, or merged;
- existing identifiers and timestamps remain stable;
- new required columns receive deterministic defaults;
- new tables start empty or are filled by an idempotent post-migration backfill;
- migration tests cover representative source versions;
- no backup format compatibility change is required.

Safe schema changes do not need a dedicated user-facing backup reminder beyond normal release notes and existing backup access in Settings.

### Risky schema change

A schema change is risky when any of these are true:

- tables, columns, identifiers, timestamps, or relationships are deleted, renamed, merged, split, or rewritten;
- legacy note text is parsed into canonical rows as part of an upgrade or backfill;
- data is deduplicated, normalized, converted between units, or re-keyed;
- migration behavior depends on ambiguous user-entered workout text;
- restore, backup format, or cross-version fixture behavior changes;
- unsupported-source handling changes;
- rollback safety depends on app process lifetime or post-migration jobs;
- the release is intended for external testers or broader distribution and includes any durable-data migration.

Risky schema changes require explicit backup-reminder handling.

## Reminder behavior

GymTrack cannot reliably show an in-app dialog before Room opens and runs a migration, so the primary reminder must happen before the risky build is installed or launched:

1. The pull request and release notes must mark the migration as risky.
2. The validation section must instruct testers to create a `.gymtrack-backup` from the currently installed build before updating.
3. The release or test-build handoff must include the same instruction.
4. After installing the risky build, the app must still preserve the existing no-destructive-migration policy and migration tests must prove old data is not silently erased.

If an in-app reminder is needed for a future release, it should be implemented as a post-launch safety banner for the next risky operation, not as a replacement for pre-install instructions. A post-launch banner can guide users to Settings -> Back up all data before they run optional backfill, merge, cleanup, or restore actions.

## Required PR checklist for risky schema work

Risky migration pull requests must include:

- the source schema versions affected;
- why the migration is risky;
- exact user-facing backup reminder text for release notes or tester handoff;
- migration tests for every supported source schema affected;
- backup/restore fixture coverage when backup format or restored durable data changes;
- confirmation that `fallbackToDestructiveMigration()` is still not used;
- confirmation that no backup data is uploaded automatically;
- manual validation notes from an emulator or device with representative pre-upgrade data.

## User-facing reminder text

Use this baseline text for risky pre-release builds:

> This build changes GymTrack's local workout database. Before installing or opening it, open the current GymTrack build, go to Settings -> Back up all data, and save a `.gymtrack-backup` file somewhere you control. GymTrack does not upload this backup automatically.

## Internal snapshots

A separate internal pre-migration snapshot is not required for the current schema state. The current policy is:

- user-visible backups protect against user regret, device issues, and manual recovery needs;
- Room migration tests protect against known schema-upgrade data loss;
- restore rollback protects ordinary restore failures;
- destructive migration fallback remains prohibited.

Add an internal snapshot design only if a future migration performs irreversible post-open data rewriting that cannot be fully protected by pre-install user backup instructions and migration tests.
