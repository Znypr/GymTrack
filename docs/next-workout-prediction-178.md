# Next workout prediction baseline

This is the first implementation stream for #178.

## Branching model

#178 is a larger feature and should stay isolated on `feature/178-workout-prediction` until the full user-facing flow is manually validated.

Small implementation PRs should target `feature/178-workout-prediction`, not `master`.

## Slice 1: deterministic prediction service

The baseline is intentionally deterministic and backend-only.

It predicts a likely next workout label from confirmed completed workout history. It does not prefill the editor, suggest exercises, suggest weights, or make coaching claims.

### Inputs

The prediction service accepts canonical `WorkoutDetails` records and uses only completed workouts with a usable label.

Label selection:

1. category name, when present;
2. workout title, when category is absent;
3. ignored when both are blank.

Draft and partial workouts are ignored so transient autosave state does not train suggestions.

### Prediction rules

1. If all completed labeled workouts share one label, suggest that label with low or medium confidence depending on sample size.
2. Otherwise, use historical transitions from the latest completed workout label to the next label.
3. If the latest label has no repeated transition history, fall back to the least recently trained recurring workout.
4. Return no suggestion when there is no completed labeled history or no defensible fallback.

### Output

A suggestion contains:

- workout label;
- confidence: low, medium, or high;
- plain reason text;
- evidence including recent labels, basis, transition counts, previous label, and days since the suggested label.

## Slice 2: recent completed history source

`CanonicalWorkoutRepository.getRecentCompleted(limit)` exposes a bounded, newest-first list of completed canonical workouts.

`NextWorkoutPredictionProvider` is the bridge from stored history to the deterministic prediction service:

1. load recent completed canonical workouts;
2. pass them into `NextWorkoutPredictionService`;
3. return the suggestion without persisting learned state or changing workout history.

The default history window is 24 completed workouts. This is intentionally small and deterministic for the initial baseline.

## Slice 3: Home suggestion surface

The first UI surface is intentionally minimal:

- Home loads the current prediction into `HomeViewModel` state.
- `NotesScreen` shows a dismissible "Likely next workout" card when a suggestion exists.
- The card shows the predicted label, reason, and confidence.
- The action is `Start blank`, which opens the existing blank editor flow.

This slice does not create a prefilled workout, does not save history, and does not silently apply the predicted label.

## Product boundary

This is a logging accelerator, not a coach.

The wording must stay in the form of observed behavior:

- good: "You usually do Pull after Push."
- bad: "You should train Pull today."

No suggestion modifies history or creates a workout without user confirmation.

## Next slices

- Add explicit accept/reject/replace feedback separately from workout history.
- Create a confirmed suggested workout only after explicit user acceptance.
- Prefill editable exercise rows only after explicit user acceptance.
- Defer load/rep targets until progression policy, units, and safety rules are defined.
