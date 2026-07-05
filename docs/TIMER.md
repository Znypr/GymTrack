# Workout timer

GymTrack derives workout duration from persisted timestamps instead of a continuously running Android service.

## Persisted state

Preferences DataStore stores the active workout timestamp, accumulated seconds, running interval start, and paused/running state. Elapsed time is calculated when displayed, so no database, notification, or background-service update is required each second.

## Lifecycle

- A new workout starts automatically.
- The newest saved workout restores an existing timer. Without persisted state, it waits for Play.
- Older workout edits do not display a timer.
- Backgrounding and process recreation preserve running or paused state.
- Resume retains accumulated time.
- Normal editor exit clears timer state before navigation.
- After a restart, state is available when GymTrack is opened again.

## Android behavior

The manifest declares no timer service, foreground-service permission, or notification permission. Persistent notification controls are outside this change.

## Validation

Automated tests cover long duration, pause/resume, workout switching, and backward clock movement.

Manual validation on Android 14 or newer:

1. Run a new workout timer beyond three minutes.
2. Background and reopen the app.
3. Close the app process, reopen the newest workout, and verify elapsed time.
4. Repeat while paused, then resume.
5. Confirm older workouts have no timer.
6. Exit the active workout and confirm reopening does not auto-start.
