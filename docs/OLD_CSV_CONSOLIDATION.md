# Old CSV consolidation into one GymTrack backup

This runbook covers the migration path for combining old phone CSV exports with the current GymTrack debug app data, then producing one master `.gymtrack-backup` file for release restore.

Related work: #179, #196, #257, #258, #259.

## App identities

GymTrack currently uses separate package IDs for debug and release builds:

| Build | Package ID | Purpose |
|---|---|---|
| Debug | `app.znypr.gymtrack.debug` | Migration workspace and legacy CSV import |
| Release | `app.znypr.gymtrack` | Real app handoff and backup restore |

Legacy CSV import is debug-only. Release builds should restore the final combined `.gymtrack-backup` file instead of importing CSVs.

## Current migration shape

Observed migration target:

- Current debug app state: 38 workouts.
- Old phone history: about 185 legacy CSV workouts.
- Expected combined history: about 224 workouts after dedupe/import.

Counts can differ slightly if duplicate snapshots or already imported timestamps are skipped.

## Copy old CSV folder into emulator Downloads

Source on Windows:

```powershell
D:\Google Drive\old gymtrack backups
```

Destination in emulator:

```text
/sdcard/Download/old gymtrack backups
```

PowerShell:

```powershell
adb shell mkdir -p "/sdcard/Download/old gymtrack backups"
adb push "D:\Google Drive\old gymtrack backups\." "/sdcard/Download/old gymtrack backups/"
adb shell ls -la "/sdcard/Download/old gymtrack backups"
```

If multiple devices are connected:

```powershell
adb devices
adb -s emulator-5554 shell mkdir -p "/sdcard/Download/old gymtrack backups"
adb -s emulator-5554 push "D:\Google Drive\old gymtrack backups\." "/sdcard/Download/old gymtrack backups/"
adb -s emulator-5554 shell ls -la "/sdcard/Download/old gymtrack backups"
```

## Safe consolidation workflow

### 1. Back up current debug app state first

Before importing old CSVs, create a safety backup of the existing debug app data.

Suggested file name:

```text
before-old-csv-import-38-workouts.gymtrack-backup
```

Keep this file even after the combined import works. It is the rollback point if CSV import creates bad data or duplicates.

### 2. Import old CSVs in debug app

Use the debug-only legacy CSV import action.

Expected behavior after #257:

- Import progress appears immediately.
- Progress shows processed/selected files.
- Final summary reports imported/skipped/failed counts.

### 3. Verify combined data

After import, verify:

- Workout count is roughly the expected combined total.
- Old workouts appear in History.
- Dates are correct.
- Exercise names are readable.
- Sets, reps, weights, and units look correct.
- Stats opens without looking frozen after #258.

Spot-check at least:

- A very old workout.
- A recent old-phone workout.
- One Push, Pull, and Legs workout if available.
- A workout with machines/brands.
- A workout with unilateral or modifier notation.

### 4. Create the combined master backup

After the combined data looks correct, create one full backup from the debug app.

Suggested file name:

```text
gymtrack-master-combined-224-workouts.gymtrack-backup
```

This file is the source of truth for moving into the release app.

### 5. Copy emulator Downloads back to PC

Destination on Windows:

```powershell
D:\Google Drive\gymtrack\full backup
```

PowerShell:

```powershell
New-Item -ItemType Directory -Force "D:\Google Drive\gymtrack\full backup"
adb pull "/sdcard/Download" "D:\Google Drive\gymtrack\full backup"
```

If Google Drive path causes `Invalid argument`, pull to a local temp folder first:

```powershell
New-Item -ItemType Directory -Force "C:\Temp\gymtrack-full-backup"
adb pull "/sdcard/Download" "C:\Temp\gymtrack-full-backup"
```

Then move the files with Windows Explorer.

### 6. Restore into release app

Install/open the release app package:

```text
app.znypr.gymtrack
```

Use Restore from backup and select the combined master `.gymtrack-backup` file.

Do not use legacy CSV import in release builds.

## Files to preserve

Keep these separately:

| File | Why |
|---|---|
| Pre-import debug backup | Rollback to the clean 38-workout state |
| Original old CSV folder | Raw source archive from old phone |
| Combined master backup | Final source for release restore |
| Post-release backup | Confirmation backup after release restore succeeds |

## Release validation checklist

After restoring the combined backup into release:

- [ ] Release app opens as `app.znypr.gymtrack`.
- [ ] Debug app can still remain installed separately as `app.znypr.gymtrack.debug`.
- [ ] Restored workout count matches expected combined count.
- [ ] History opens.
- [ ] Several imported old workouts open and edit correctly.
- [ ] Stats opens and uses cached results after initial build.
- [ ] Export/share flow still works.
- [ ] Create a fresh release-app backup after validation.

## Non-goals

- Cloud sync.
- Automatic Google Drive upload.
- Merge restore across multiple live devices.
- CSV import in release builds.
