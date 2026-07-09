# Issue 121 cleanup slices

This document records small, low-risk cleanup slices for #121.

## Slice 1: generated dump and placeholder tests

Removed confirmed non-product artifacts only.

### Removed

- `FullProjectCode.txt`
  - Generated repository dump.
  - Already ignored by `.gitignore`.
  - Not an application source, resource, test, or build input.

- `app/src/test/java/com/example/gymtrack/ExampleUnitTest.kt`
  - Android Studio placeholder test that only asserted `2 + 2 == 4`.
  - Does not validate GymTrack behavior.

- `app/src/androidTest/java/com/example/gymtrack/ExampleInstrumentedTest.kt`
  - Android Studio placeholder instrumentation test that only checked the package name.
  - Restore, migration, parser, and domain behavior are covered by app-specific tests elsewhere.

## Slice 2: dependency dedupe

Kept one dependency declaration per active dependency source and removed stale duplicate declarations.

### Removed

- duplicate `kapt("androidx.room:room-compiler:2.6.1")`
- raw duplicate `implementation("androidx.core:core-ktx:1.9.0")`
  - kept `implementation(libs.androidx.core.ktx)`
- raw duplicate `implementation("androidx.compose.material3:material3:1.2.1")`
  - kept `implementation(libs.androidx.material3)`
- unused `implementation("co.yml:ycharts:2.1.0")`
  - no active source imports `co.yml.charts`
- older conflicting `implementation("androidx.compose.material:material-icons-extended:1.5.4")`
  - kept `implementation("androidx.compose.material:material-icons-extended:1.6.5")` because the app still uses extended material icons

## Slice 3: color helper cleanup

Removed unused duplicate color helper extensions from `ColorUtils.kt` while keeping the active `presetColors` list used by `ColorDropdown`.

### Removed

- `Color.darken(factor: Float)` from `core.util.ColorUtils`
- `Color.lighten(factor: Float)` from `core.util.ColorUtils`

### Kept

- `presetColors`, because `ColorDropdown` still imports and renders it
- theme color helpers in `core.ui.theme.Color`, because the draft visual PR #252 also touches theme files and this slice avoids that conflict area

## Not touched

To avoid interference with draft visual PR #252, these cleanup slices do not modify:

- theme files
- Home screen files
- editor component files
- settings files
- models or backup serialization

## Validation

Recommended commands:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Manual smoke test remains limited to launch and the main entry points because these slices have no intended user-facing behavior changes.
