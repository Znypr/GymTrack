# Restore interruption validation

Issue: #196

## Scope

This note records the restore interruption points that matter for GymTrack v1 replace-all restore and how each one is validated.

Restore spans three storage surfaces:

1. the selected backup archive, read through Android's document picker;
2. the Room workout database, replaced inside one transaction;
3. DataStore-backed settings and active timer state, updated after the database replacement succeeds.

Room gives transaction-level rollback for database rows. DataStore changes are not part of the same transaction, so process-death checks focus on preserving a recoverable state and on making manual recovery explicit through the safety-backup path.

## Interruption points

| Point | Expected result | Validation |
| --- | --- | --- |
| Before archive read completes | No local data is changed. | Existing malformed/invalid archive tests reject before restore. |
| After archive validation, before database replacement | No local data is changed. | Covered by restore confirmation flow and repository rollback setup. |
| After existing database rows are deleted inside the Room transaction | Room rolls back the uncommitted delete; previous data remains available. | Automated through `RestoreInterruptionTest`. |
| After replacement canonical rows are inserted inside the Room transaction | Room rolls back the uncommitted partial replacement; previous data remains available. | Automated through `RestoreInterruptionTest`. |
| After replacement legacy rows are inserted inside the Room transaction | Room rolls back the uncommitted full row replacement if the transaction fails before commit. | Covered by the same test hook; available for targeted future regression tests. |
| After database replacement commits, before settings save | Database replacement has completed, but settings may still reflect the previous install. This cannot be made atomic with DataStore without a persistent restore journal. Use a user-created safety backup for manual recovery if process death happens here. | Manual process-death validation only. |
| After settings save, before timer cleanup | Database and settings are restored, but active timer cleanup may not have run. Successful in-process restore stops the timer. | Automated through `RestoreRoundTripTest.successfulRestoreStopsActiveTimerAndRestoresSettingsAndHistory`; process death remains manual validation. |
| After timer cleanup | Restore is complete. | Existing round-trip and safety-backup tests. |

## Automated coverage added for #196

`RestoreInterruptionTest.midRestoreInterruptionPreservesPreviousDatabaseAndSettings` injects a simulated failure after canonical replacement rows have been inserted but before the Room transaction commits.

The test verifies:

- the failure is surfaced;
- the old database snapshot is still present;
- previous settings remain present;
- the restore rollback path can run without losing the original state.

`RestoreRoundTripTest.successfulRestoreStopsActiveTimerAndRestoresSettingsAndHistory` verifies that a successful restore leaves these surfaces coherent:

- restored database history matches the backup payload;
- restored settings match the backup payload;
- active timer state is cleared.

## Manual process-death validation

Use this only on a debug build with disposable data or a known-good safety backup.

1. Install the debug app on an emulator or test device.
2. Create a representative local dataset with at least one workout, exercise, set, changed settings, and an active timer.
3. Create `GymTrack-safety-backup-before-restore-<date>.gymtrack-backup` from Settings and copy it off-device if the data matters.
4. Select a different valid backup and proceed through the restore confirmation dialog.
5. During restore, force-stop or kill the app process from Android Studio, `adb shell am force-stop app.znypr.gymtrack.debug`, or the emulator/device UI.
6. Reopen the app and check one of these states:
   - old data is still present because interruption happened before the Room transaction committed;
   - restored data is present because interruption happened after the Room transaction committed;
   - if the state is mixed or not trusted, restore the safety backup created in step 3.
7. Confirm the app still opens history, editor, stats, settings, selected-workout export, and restore flow without crashing.
8. Record device/API level, app version, source backup file name, safety-backup file name, interruption method, observed state, and recovery result in the PR validation section.

## Known limit

GymTrack v1 restore does not yet have a persistent restore journal that can automatically complete or roll back a cross-store restore after process death. The database portion is transactional. Settings and timer state are validated for ordinary in-process success/failure, while process-death recovery outside the database transaction depends on the explicit safety-backup workflow.
