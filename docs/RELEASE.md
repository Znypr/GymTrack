# Release identity and build checklist

## Permanent Android identity

The permanent release application ID is:

```text
app.znypr.gymtrack
```

This ID is the installed Android app identity for release builds. Treat it as permanent after external distribution starts because changing it later creates a separate Android app with separate local data.

The Kotlin/Android namespace currently remains `com.example.gymtrack` as an internal source namespace to avoid a broad package refactor. It is not the public installed app identity.

## Debug identity

Debug builds use this installed application ID:

```text
app.znypr.gymtrack.debug
```

The `.debug` suffix keeps debug and release installs, launch icons, and app data separate. A debug build must not overwrite release app data.

## Versioning rule

Version codes use a monotonic date-plus-sequence format:

```text
YYYYMMDDNN
```

Example: `2026070701` is the first permanent-ID release baseline.

Rules:

1. Never reuse a version code.
2. Never decrease a version code.
3. Increment the trailing sequence when more than one release candidate is produced on the same day.
4. Update `versionName` for user-facing release labels.

`validateReleaseConfig` enforces the current permanent-ID baseline so accidental rollback is caught locally and in CI when enabled.

## Release signing policy

Release builds must never use the debug signing configuration.

The repository must not contain local release credentials or signing files. If local release signing is needed, keep the local properties file and keystore outside Git. A complete local release signing configuration may be detected by Gradle, but missing local signing material still allows unsigned release artifact validation.

Before opening a release PR, check:

```powershell
git status --short
```

Do not commit local signing files.

## Target API level

`compileSdk` and `targetSdk` are 36 (Android 16), the Google Play minimum for new apps and updates since 2026-08-31 (#336). AGP 8.11.1 supports API 36, so no toolchain bump was needed. Android 16 behaviour reviewed for this app:

- Edge-to-edge: already enforced since target 35; the app never used `android:windowOptOutEdgeToEdgeEnforcement`, so nothing changes.
- Predictive back: `android:enableOnBackInvokedCallback="true"` was already set and the editor uses Compose `BackHandler` (no `onBackPressed()` override, no `KEYCODE_BACK` handling), so back keeps working with the system animation.
- Large screens (sw600dp+): Android 16 ignores orientation, resizability and aspect-ratio limits. The app declares none, so tablets and foldables behave as before. `configChanges` stays, so rotation and resizing do not recreate the activity.

Each future target bump needs the same review plus a smoke test on an emulator of the new API level.

## Android Auto Backup

Auto Backup is on with explicit include-only rules (#339): Room database and settings only. Rules and reasons are in `docs/BACKUP_FORMAT.md`. Keep `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml` in sync when adding stored files.

## Validation commands

From the repository root on Windows:

```powershell
.\gradlew.bat validateReleaseConfig
.\gradlew.bat assembleRelease
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Optional emulator/device validation:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Minification and shrinking

Release minification and resource shrinking remain disabled for the first permanent-ID setup. Enable them only in a separate compatibility PR with focused runtime validation.
