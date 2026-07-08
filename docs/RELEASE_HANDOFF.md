# Release app handoff

This runbook protects the migrated debug-app data while moving to the permanent release application ID.

The debug app installs as:

```text
app.znypr.gymtrack.debug
```

The release app installs as:

```text
app.znypr.gymtrack
```

Android stores those package IDs separately. Installing the release app must not modify or remove the debug app's private data.

## Preconditions

- The debug app still contains the validated migrated dataset.
- A `.gymtrack-backup` created from the debug app is copied somewhere outside the phone.
- The backup is the clean 38-workout migration artifact, or a newer backup created after manually verifying the migrated dataset.
- `release-signing.properties` exists locally and was created from `release-signing.properties.example`.
- The referenced release keystore file exists locally and is not committed.
- GitHub Actions quota is available, or the PR records that local validation was used because Actions quota is exhausted.

## Build validation

Run the normal local checks first:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Then verify the final release handoff configuration:

```powershell
.\gradlew.bat validateReleaseHandoff
.\gradlew.bat assembleRelease
```

On macOS or Linux:

```bash
./gradlew validateReleaseHandoff
./gradlew assembleRelease
```

`validateReleaseHandoff` is intentionally stricter than the normal `check` task. It fails unless local release signing is configured and the release build remains tied to `app.znypr.gymtrack`.

## Install without touching debug data

Install the signed release APK while keeping the debug app installed:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell pm list packages | findstr /i gymtrack
```

Expected package list includes both:

```text
package:app.znypr.gymtrack
package:app.znypr.gymtrack.debug
```

Do not uninstall `app.znypr.gymtrack.debug` until the release app restore and backup round trip are verified.

## Restore migrated backup into release app

1. Open the release app, not the debug app.
2. Go to Settings or Data Management.
3. Choose restore from backup.
4. Select the preserved `.gymtrack-backup` file.
5. Confirm the restore summary before replacement.
6. Complete the restore.

Expected result:

- The release app shows 38 workouts.
- Representative migrated workouts open correctly.
- Statistics opens without crashing.
- Statistics uses the restored migrated dataset.
- Settings and source weight units remain coherent.

## Create a fresh release-app backup

After restore validation, create a new backup from the release app:

```text
GymTrack-release-validated-<date>.gymtrack-backup
```

Copy this backup outside the phone before deleting or ignoring the debug app.

Expected result:

- Backup creation succeeds from `app.znypr.gymtrack`.
- The created backup has nonzero size.
- The app reports counts that match the restored dataset.

## Manual validation evidence

Record this evidence in the pull request or release note before closing issue #237:

| Check | Result |
|---|---|
| Device model and Android version | |
| Release APK version code/name | |
| `validateReleaseHandoff` passed | |
| `assembleRelease` passed | |
| `adb pm list packages` shows debug and release packages | |
| Release app restored the migrated backup | |
| Release app shows 38 workouts | |
| Stats opens after restore | |
| Fresh release-app backup created | |
| Debug app remains installed after release validation | |

## Failure policy

If any restore, statistics, or backup step fails, keep the debug app installed and keep the external debug-app backup. Do not remove, clear, or overwrite debug-app data while investigating.
