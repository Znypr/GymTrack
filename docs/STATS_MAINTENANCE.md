# Statistics maintenance

## Startup behavior

Normal application startup must not reparse every saved workout.

Startup may run a lightweight compatibility check through `WorkoutRepository.checkAndMigrate()`. That check reads counts only and runs the full legacy rebuild only for old or inconsistent databases where notes exist but no derived set rows exist.

## Explicit mutation boundaries

Derived statistics are maintained at the same boundaries that change workout data:

- Draft autosave writes the compatibility note only and does not parse workout text.
- Explicit workout completion writes the note, replaces derived set rows, updates the canonical workout projection, and refreshes the local training-summary outbox.
- Import routes through explicit workout completion.
- Delete routes through `WorkoutRepository.deleteWorkout(note)` so the note, legacy derived set rows, canonical workout row, and pending summary outbox entry are removed together.

## Manual repair path

`WorkoutRepository.forceUpdateStats()` remains a manual compatibility repair. It reparses every saved workout and should not be called unconditionally from application startup.

## Local validation

Use local validation while GitHub Actions quota is exhausted:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Android migration and repository instrumentation tests should be run locally when an emulator/device is available.
