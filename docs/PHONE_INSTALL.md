# Phone install runbook

Use the repository-level `install.ps1` script when installing GymTrack to a real Android phone.

This is the canonical local install path for the Samsung phone because the device has multiple Android users/profiles:

- `0` = main phone user
- `95` = Dual App profile
- `150` = Secure Folder

Gradle's direct `installDebug` task can install through device detection without making the Android user target explicit. On this phone, prefer building with Gradle and installing with ADB using `--user 0` so only the main phone user is updated.

## Canonical commands

From the repository root:

```powershell
.\install.ps1 debug
```

```powershell
.\install.ps1 release
```

```powershell
.\install.ps1 both
```

The default is `both`, so this is also valid:

```powershell
.\install.ps1
```

## What the script does

Debug install:

```powershell
.\gradlew.bat assembleDebug
adb install --user 0 -r .\app\build\outputs\apk\debug\app-debug.apk
```

Release install:

```powershell
.\gradlew.bat assembleRelease
adb install --user 0 -r .\app\build\outputs\apk\release\app-release.apk
```

Then it verifies main-user installs:

```powershell
adb shell pm list packages --user 0 | findstr gymtrack
```

Expected output when both variants are installed in the main user:

```text
package:app.znypr.gymtrack
package:app.znypr.gymtrack.debug
```

## Why not `installDebug` on the Samsung phone

Avoid this for the Samsung phone:

```powershell
.\gradlew.bat installDebug
```

Use this instead:

```powershell
.\install.ps1 debug
```

Reason: the Samsung phone has Dual App and Secure Folder profiles. The explicit `adb install --user 0 -r ...` step prevents accidental clone-profile installs and keeps installs scoped to the main phone user.

## Cleanup duplicate clone-profile installs

List Android users:

```powershell
adb shell pm list users
```

Check where GymTrack is installed:

```powershell
adb shell pm list packages --user 0 | findstr gymtrack
adb shell pm list packages --user 95 | findstr gymtrack
adb shell pm list packages --user 150 | findstr gymtrack
```

Remove only clone-profile copies if they exist:

```powershell
adb shell pm uninstall --user 95 app.znypr.gymtrack.debug
adb shell pm uninstall --user 95 app.znypr.gymtrack

adb shell pm uninstall --user 150 app.znypr.gymtrack.debug
adb shell pm uninstall --user 150 app.znypr.gymtrack
```

Do not run plain release uninstall unless a fresh `.gymtrack-backup` exists:

```powershell
adb uninstall app.znypr.gymtrack
```

Plain uninstall removes the main release app and can wipe main-profile release data.

## Package IDs

- Debug: `app.znypr.gymtrack.debug`
- Release: `app.znypr.gymtrack`
