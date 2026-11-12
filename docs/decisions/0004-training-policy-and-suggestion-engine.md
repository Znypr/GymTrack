# ADR 0004: Training policy, opt-in goals and a pure suggestion engine

- Status: Proposed
- Date: 2026-11-11
- Related issues: #312, #308 (parent #178)

## Context

Users want to type less during a workout and get evidence-based guidance. The intake (2026-11-10) set three rules:

- every suggestion needs a tap;
- observed history and coaching policy are labelled separately;
- history prefill is on by default, coaching is opt-in.

The editor's Enter path has a budget of 16 ms or less on the main thread, so no query may run on Enter.

## Decision drivers

- No silent changes to logged data, and no advice dressed up as fact.
- Defaults that match the literature (science review), with expert practice labelled as such.
- A platform-neutral engine that can be unit-tested and reused (a future iOS or web client).
- Old backups must still restore.

## Options considered

### A. Rules inside the editor composables
Fast to write. Not testable without the UI, mixes policy and history, and is easy to run on Enter by mistake.

### B. A pure engine fed by the derived `exercise_recent` index (chosen)
`feature/suggest/` holds `TrainingGoal`, `GoalPolicy` (the literature table as data) and `SuggestionEngine` (pure functions: history + goal → next set, warm-up ramp, set count, rest). The editor maps `ExerciseRecentEntity` rows to engine input once, when it opens.

### C. A server or an LLM coach
Out of scope: no budget, offline-first, and "AI-generated programs" is a non-goal.

## Decision

Option B.

- **The goal** is an optional setting (`Settings.trainingGoal`, default `NONE`), stored in DataStore under `training_goal` and in backups as the optional field `settings.trainingGoal`. When the field is missing, it reads as `NONE`.
- **Each engine output carries its source.** History outputs are labelled `HISTORY`; outputs from the goal table are labelled `POLICY`. Policy is never applied without a goal.
- **Load safety.** The step is inferred from history and capped at 5 kg or 10 lb. A suggested load is never more than one step above the matched set. Low-confidence input gives no suggestion.
- **Documentation.** `docs/TRAINING_POLICY.md` holds the table, citations and caveats.

## Consequences

### Positive
- The engine is unit-tested, including a months-long simulation for load jumps.
- The editor reads an in-memory snapshot, so Enter stays cheap.
- Backups remain compatible in both directions: older app versions ignore the extra key.

### Negative
- Compound or isolation is a name heuristic until the muscle map (#309) can provide it.
- History is loaded once per editor open, so an exercise that gets its first finished session mid-workout only shows a pill after the editor is reopened.

### Follow-up
- Accept/ignore counts are kept for the editor session only. Persisting them for the taps-per-set measurement is a follow-up.
- Use the muscle taxonomy (#309) for the exercise kind and for per-muscle weekly targets.
