# Next workout prediction baseline

This is the first implementation stream for #178.

## Branching model

#178 is a larger feature and should stay isolated on `feature/178-workout-prediction` until the full user-facing flow is manually validated.

Small implementation PRs should target `feature/178-workout-prediction`, not `master`.

## Slice 1: deterministic prediction service

The baseline is intentionally deterministic and backend-only.

It predicts a likely next workout label from saved workout history. It does not suggest weights or make coaching claims.

### Inputs

The prediction service accepts canonical `WorkoutDetails` records and uses only prediction-eligible workouts with a usable label.

Prediction-eligible means:

1. canonical `COMPLETED`; or
2. legacy-backed canonical history with migration status `MIGRATED` or `NEEDS_REVIEW`.

Label selection:

1. category name, when present;
2. workout title, when category is absent;
3. ignored when both are blank.

Draft workouts, pending legacy migrations, and blank-label records are ignored so transient or unverified state does not train suggestions.

### Prediction rules

1. If all saved labeled workouts share one label, suggest that label with low or medium confidence depending on sample size.
2. Otherwise, use historical transitions from the latest saved workout label to the next label.
3. If the latest label has no repeated transition history, fall back to the least recently trained recurring workout.
4. Return no suggestion when there is no prediction-eligible labeled history or no defensible fallback.

### Output

A suggestion contains:

- workout label;
- confidence: low, medium, or high;
- plain reason text;
- evidence including recent labels, basis, transition counts, previous label, and days since the suggested label.

## Slice 2: recent prediction-history source

`CanonicalWorkoutRepository.getRecentPredictionHistory(limit)` exposes a bounded, newest-first list of prediction-eligible canonical workouts.

`NextWorkoutPredictionProvider` is the bridge from stored history to the deterministic prediction service:

1. load recent prediction-eligible canonical workouts;
2. pass them into `NextWorkoutPredictionService`;
3. return the suggestion without persisting learned state or changing workout history.

The default history window is 24 saved workouts. This is intentionally small and deterministic for the initial baseline.

## Slice 3: Home suggestion surface

The first UI surface shows the next workout and optional exercise-order preview:

- Home loads the current prediction into `HomeViewModel` state.
- `NotesScreen` shows a dismissible "Likely next workout" card when a suggestion exists.
- The card shows the predicted label, reason, confidence, and suggested exercise order when stable enough.
- The action opens an editable suggested draft only after explicit user tap.

This slice does not save history automatically and does not suggest weights/reps.

## Slice 4: Exercise-order suggestion

`ExerciseOrderSuggestionService` derives editable exercise rows from matching saved workouts for the predicted label.

Rules:

1. Use category/title label matching.
2. Require at least two matching workouts.
3. Include exercises that appear repeatedly or in at least half of matching workouts.
4. Order exercises by observed median position.
5. Limit to six exercises for the initial draft.

The editor draft contains exercise names only, separated as editable rows. Sets, weights, reps, RPE, and progression targets remain out of scope.

## Product boundary

This is a logging accelerator, not a coach.

The wording must stay in the form of observed behavior:

- good: "You usually do Pull after Push."
- bad: "You should train Pull today."

No suggestion modifies history or creates a workout without user confirmation.

## Next slices

- Add explicit accept/reject/replace feedback separately from workout history.
- Add better accept/edit tracking for suggested exercise drafts.
- Defer load/rep targets until progression policy, units, and safety rules are defined.
