# #178 Workout prediction QA strategy

This plan validates the isolated #178 feature branch before it is allowed to merge to `master`.

## Branch rules

All #178 implementation slices must target:

```text
feature/178-workout-prediction
```

Do not merge #178 into `master` until the full user-facing flow has passed automated tests and manual smoke tests.

## Test layers

### 1. Compile and local JVM checks

Run on every #178 slice branch:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Required result: all pass.

For the Home suggestion slice, `testDebugUnitTest` includes `WorkoutPredictionSeedFixtureTest`, which parses the checked-in seed CSVs and verifies they predict `Legs` after the seeded `Push -> Pull -> Legs -> Push -> Pull` cycle.

### 2. Instrumented persistence checks

Run when a slice touches Room, DAO, backup/restore, import, or canonical repository code:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Required result: all pass.

The #178 Home suggestion slice includes `CanonicalPredictionHistoryRepositoryTest`, which verifies prediction history includes canonical `COMPLETED` records plus migrated legacy-backed records while excluding drafts and pending partials.

### 3. Manual prediction smoke test

Run when a slice affects prediction data loading, Home UI, editor entry, or user-visible behavior.

#### Prepare a clean debug app

```powershell
adb shell pm clear app.znypr.gymtrack.debug
.\gradlew.bat installDebug
adb shell monkey -p app.znypr.gymtrack.debug 1
```

#### Push seed CSVs to the phone/emulator

```powershell
adb shell mkdir -p /sdcard/Download/gymtrack-178-prediction-seed
adb push testdata/178-workout-prediction/seed-cycle/. /sdcard/Download/gymtrack-178-prediction-seed/
```

#### Import seed data

In the debug app:

1. Open Home.
2. Use the debug-only legacy CSV import button.
3. Select all five files in `Download/gymtrack-178-prediction-seed`.
4. Wait for the import completion toast.
5. Restart the app if the suggestion card does not refresh immediately.

Expected result after importing all five files:

```text
Likely next workout: Legs
Confidence: Medium confidence
Reason: mentions saved history most often follows Pull with Legs
```

Rationale: the seeded completed history is:

```text
Push -> Pull -> Legs -> Push -> Pull
```

The latest completed workout is `Pull`, and the observed transition from `Pull` is `Legs`.

### 4. Manual real-backup smoke test

On a local debug app with the supplied 224-workout backup restored, expected current prediction after the latest saved workout should be:

```text
Likely next workout: Push
Reason: saved history most often follows Legs with Push
Confidence: High confidence
```

Rationale: the latest saved workout in the analyzed backup is `Legs`, and the aggregate transition history is strongly `Legs -> Push`.

Do not commit the private real backup file or raw workout exports to the repository.

## Manual behavior checks

### Home card

- Home opens without crash.
- Suggestion card appears above the note grid when enough saved history exists.
- Card displays label, confidence, and reason.
- Text stays observational, not prescriptive.
  - Allowed: `Likely next workout`, `Saved history most often follows...`
  - Not allowed: `You should train...`

### Dismiss

- Tap dismiss.
- Card disappears.
- Card stays hidden while the current `HomeViewModel` session is alive.
- Force-stopping/reopening may show the card again; this is acceptable until persisted feedback exists.

### Start blank

- Tap `Start blank`.
- App opens the existing blank editor route.
- No workout is saved until the user explicitly saves.
- Suggested label is not silently applied to title/category.
- Cancel returns safely to Home.
- Save returns safely to Home and updates the note grid.

### Regression checks

Verify existing Home behavior still works:

- Existing notes open normally.
- Long-press selection still works.
- Delete selected notes still works.
- Export selected notes still works.
- Category filter still works.
- Sort toggle still works.
- Settings opens.
- Stats opens.
- Debug-only CSV import is still hidden in release builds.

## Edge cases to verify before final #178 merge

These do not all need to block the first Home-card slice, but they should be covered before merging #178 to `master`.

- No prediction-eligible canonical history: no suggestion card.
- One saved label only: low/medium confidence same-label suggestion.
- Deleted latest workout: suggestion recalculates.
- Imported duplicate seed files: no duplicate history explosion.
- Restored backup with history: suggestion loads after restore.
- Release build: no legacy CSV import surface.
- Dark mode/light mode: card remains readable.

## Final #178 merge gate

Before merging `feature/178-workout-prediction` into `master`, require:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Plus manual smoke testing on the real debug app using the seed data above.
