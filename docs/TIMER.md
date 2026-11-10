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
- Navigating to notes or workout history preserves the active timer so prior exercises, weights, and reps can be checked during a session.
- The explicit Finish action saves the workout, clears timer state, and returns to the tab it was opened from.
- The timer Stop control clears timer state without leaving the editor.
- After a restart, state is available when the app is opened again.

## Running workout and new starts (#293)

- Back (not Finish) leaves the workout running. Every tab (Today, History, Progress) then shows the active-workout bar above the bottom nav: "● Push · 24:10 · Resume ›". Tapping it opens that workout with its timer and Finish.
- Only one workout owns the timer. Every Start (Start <Category>, Other workout, Start blank) first checks the running one (`decideWorkoutStart`, `feature/home/ActiveWorkout.kt`):
  - nothing running: start;
  - the timer points at a finished, deleted or empty workout: clear it silently (an empty saved workout is removed);
  - a real unfinished workout: the start sheet asks "Finish Pull from Tue first?"
    - **Finish it**: the old workout is finished now (Finish time = now, docs/WORKOUT_DURATION.md);
    - **Discard timer**: the old workout is finished without the timer's running time; it ends at its last logged row;
    - **Keep both**: the old workout stays unfinished. History marks it "Unfinished" and long-press offers **Mark finished** (ends at its last logged row).
- No workout can become un-finishable: an unfinished workout with logged rows always offers Mark finished in History.
- Deleting or marking finished the running workout from History also stops its timer.

## Android behavior

The manifest declares no timer service, foreground-service permission, or notification permission. Persistent notification controls are outside this change.

## Validation

Automated tests cover long duration, pause/resume, workout switching, backward clock movement, and navigation-versus-finish exit behavior.

Manual validation on Android 14 or newer:

1. Run a new workout timer beyond three minutes.
2. Background and reopen the app.
3. Close the app process, reopen the newest workout, and verify elapsed time.
4. Repeat while paused, then resume.
5. Open older workouts and confirm they have no timer while the active timer continues.
6. Return to the active workout and confirm the same timer state is restored.
7. Use Finish and confirm reopening does not restore an active timer.
