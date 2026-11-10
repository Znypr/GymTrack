# Workout duration

One rule decides how long a workout took. The Home card, the canonical `workouts.ended_at`, the
training summary and the Stats duration chart all use it. Code: `workoutDuration` and
`noteWorkoutDuration` in `core/util/DateUtils.kt`.

## The rule

1. **Start:** the workout start (`notes.timestamp`, the tap on +).
2. **End:**
   - **Finish**, when the workout was finished (#323). Before this fix the duration ended at the
     last set, so the card showed less than the timer did.
   - **The last logged row** (exercise or set, grey elapsed column) when there was no Finish.
3. **Session gap:** a pause of more than **3 h** (`WORKOUT_SESSION_GAP_SECONDS`) between two
   consecutive logged rows ends the session at the row before the pause. A Finish more than 3 h
   after the last row before it does not count either (#321).
   - Example: sets until 19:00, Back, one forgotten set at 08:00 the next morning, then Finish.
     The duration is the evening session (for example 60 min), not 804 min.
   - Rows after the pause stay in the workout. They are saved, parsed and counted as sets. Only
     the duration stops before the pause.
4. **Start to first row** is never cut, because there is no earlier row to end at. With no logged
   row at all, a Finish counts only if it is within 3 h of the start. Otherwise the duration is
   unknown (for example a CSV import, which has no real Finish).

`WorkoutDuration.endedByGap` says when rule 3 applied.

## Where it is stored

- **Finish** (`WorkoutRepository.saveCompletedWorkout`) writes `ended_at = start + duration`,
  with the Finish time as input. A CSV import passes no Finish time.
- **Editing a past workout** (`saveEditedWorkout`) and the **startup import** keep the `ended_at`
  of a `COMPLETED` row (`CanonicalImportSource.merge`). The note text cannot reproduce the
  Finish time, so re-deriving the row must not move it.
- **Unfinished workouts:** the projector writes `ended_at` from the logged rows with the same gap
  rule.

No schema change: `ended_at` already existed, and the note storage format is unchanged.

## Where it is shown

- **Home card:** `workoutCardMinutes(note, finishedAt)`. `finishedAt` is the `ended_at` of
  completed workouts (`WorkoutRepository.observeFinishTimes`).
- **Stats duration chart** (`StatsOutlierPolicy`): uses the gap rule on the elapsed column. An
  overnight-resumed workout is charted with its real session instead of being dropped as a 13 h
  outlier. Durations still over 5 h after the rule are excluded, and the chart says so ("Ignoring
  N duration outliers over 5h").
- **Not migrated yet:** the per-category average in `StatsViewModel` still takes the largest
  elapsed value. It belongs to the Stats owner and is a follow-up.
