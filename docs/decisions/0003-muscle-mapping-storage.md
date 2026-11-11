# ADR 0003: Muscle mapping storage (seed in code, user overrides in the database)

**Status:** Proposed
**Date:** 2026-11-11
**Issues:** #309, #310 (depends on #306, #307; read by #311 body map)

## Context

The body map and weekly muscle stats need to know which of the 26 regions (`core/muscle/Muscle.kt`)
each exercise trains. The mapping must be scientifically sourced (`docs/design/ANATOMY_MAPPING.md`),
correctable by app updates, and editable by the user. Until now the only data was the free-text
`exercises.muscleGroup`, which is mostly empty and not a region id.

## Options considered

1. **Seed rows in the database** (inserted by a migration). Corrections need another migration and
   must not overwrite user edits, so the app could no longer tell seed rows from user rows reliably.
2. **Seed in code, user overrides in a table** (chosen). The seed ships with the app and is fixed by
   an update; the table holds user edits only.
3. **Everything in code, no user edits.** Rejected: brands and personal technique change the split
   (doc §3, caveats 3–4) and the user must be able to correct it.

## Decision

- `MuscleMappingSeed` (code) holds every row of the reviewed doc with its source and citation status
  (V/K/C). It is keyed by the `ExerciseIdentity` base key plus the WorkoutParser keywords in the doc,
  and refined by the set row's modifier (incline/decline bench, seated calf raise, free vs supported
  row). Exercises the doc does not cover are **unmapped**, never guessed.
- `exercise_muscles(exercise_id → exercises.exerciseId ON DELETE CASCADE, muscle, role PRIMARY|SECONDARY,
  source USER)` stores user edits only. An exercise with a PRIMARY row uses exactly its rows; deleting
  them resets it to the seed. Keyed by the legacy `exercises` id because `sets` and `muscle_week` use it.
- Resolver order: user override → seed → parent walk (`exercises.parentId`, cycle-safe) → unmapped.
- `MuscleLoadCalculator`: per WORKING set, primary 1.0, secondary 0.5 (Pelland et al. 2024). Warm-ups
  excluded; unilateral rows count once; timed sets count one each; units are irrelevant.
- `muscle_week` (derived, #307) stores fractional sets per workout, muscle and exercise, plus an
  `unmapped` row per unmapped exercise. Schema v15; `DerivedIndexWriter.VERSION` 2 triggers the lazy rebuild.
- The free-text `muscleGroup` is deprecated for reads; it is kept in backups so no data is lost.

## History rule

Editing or resetting a mapping **never rewrites logged sets**. History **re-renders**: the
`muscle_week` rows of every workout containing the exercise are rebuilt in the same transaction, so
past weeks show the new mapping. A seed correction in an app update re-renders history the same way
(bump `DerivedIndexWriter.VERSION`).

## Level rule

Weekly bands: none 0 / low under 5 / moderate 5 to under 10 / high 10+ fractional sets (display bands,
not targets). A region with 0 mapped sets is `NONE` only when every trained exercise that week is
mapped; otherwise it is `UNMAPPED`, because an unmapped exercise may have trained it.

## Consequences

- Positive: seed fixes need no migration; user edits survive updates and backups (optional
  `exerciseMuscles` field, default empty; old backups restore unchanged).
- Negative: common exercises outside the doc (chest press machine, rear delt row, pullover, cardio)
  stay unmapped until the science reviewer adds rows.
- Follow-up: a body-map picker in the Edit-muscles sheet; science review of the gaps above.
