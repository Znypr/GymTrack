# Workout timer

GymTrack does not require a continuously running Android service to keep workout time accurate.

## State model

The active timer stores four values in Preferences DataStore:

- active workout note timestamp;
- elapsed seconds accumulated before the current running interval;
- wall-clock start time of the current running interval;
- whether the timer is running.

Elapsed time is calculated when the UI needs it:

```text
accumulated seconds + max(0, current time - running interval start)
```

No database write, notification update, or background loop runs every second.

## Lifecycle behavior

- Opening the latest workout starts a timer when no timer exists for that workout.
- Backgrounding the app does not stop or reset the timer.
- Process recreation restores the persisted state and derives the current elapsed time.
- A paused timer remains paused after recreation.
- Resuming starts a new running interval while preserving accumulated time.
- Leaving the active workout through the editor's normal exit path clears its timer.
- After a device restart, the timer is restored when GymTrack is opened again. No boot receiver is used.

## Android service and notification behavior

The timer uses no foreground service. The manifest therefore declares neither `shortService` nor foreground-service or notification permissions for the timer.

This avoids Android foreground-service timeouts and does not show a persistent notification. Lock-screen controls and persistent notifications are outside the current scope.

## Validation

Automated tests cover:

- elapsed time beyond three minutes;
- pause and resume accumulation;
- switching to another workout;
- protection against negative elapsed time after a backward clock adjustment.

Manual Android 14+ validation should cover:

1. Run the timer for more than three minutes.
2. Background and reopen the app.
3. Force-stop the process, reopen the latest workout, and verify elapsed time.
4. Pause, recreate the process, and confirm the timer remains paused.
5. Resume and verify accumulated time is retained.
