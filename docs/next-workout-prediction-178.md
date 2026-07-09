# Next workout prediction baseline

This is the first implementation slice for #178.

## Scope

The baseline is intentionally deterministic and backend-only.

It predicts a likely next workout label from confirmed completed workout history. It does not prefill the editor, suggest exercises, suggest weights, or make coaching claims.

## Inputs

The prediction service accepts canonical `WorkoutDetails` records and uses only completed workouts with a usable label.

Label selection:

1. category name, when present;
2. workout title, when category is absent;
3. ignored when both are blank.

Draft and partial workouts are ignored so transient autosave state does not train suggestions.

## Prediction rules

1. If all completed labeled workouts share one label, suggest that label with low or medium confidence depending on sample size.
2. Otherwise, use historical transitions from the latest completed workout label to the next label.
3. If the latest label has no repeated transition history, fall back to the least recently trained recurring workout.
4. Return no suggestion when there is no completed labeled history or no defensible fallback.

## Output

A suggestion contains:

- workout label;
- confidence: low, medium, or high;
- plain reason text;
- evidence including recent labels, basis, transition counts, previous label, and days since the suggested label.

## Product boundary

This is a logging accelerator, not a coach.

The wording must stay in the form of observed behavior:

- good: "You usually do Pull after Push."
- bad: "You should train Pull today."

No suggestion modifies history or creates a workout without user confirmation.

## Next slices

- Surface the suggestion in Home or the start-workout flow.
- Record accept/reject/replace feedback separately from workout history.
- Prefill editable exercise rows only after explicit user acceptance.
- Defer load/rep targets until progression policy, units, and safety rules are defined.
