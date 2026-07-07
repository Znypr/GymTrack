# Weight units

GymTrack separates weight-unit behavior into three concepts.

## Input default

`Settings.defaultWeightUnit` controls what the editor and parser assume when a new set line contains a number but no explicit unit.

Examples when the default is pounds:

```text
Bench Press
    5x 100
```

The parser treats `100` as `100 lb` for that new parsed set.

## Explicit set override

Typed unit text still wins over the global default.

Examples:

```text
Bench Press
    5x 100 kg
    8x 220 lb
```

This allows rare mixed-unit workouts without changing the global setting.

## Stored source unit

Every finalized weighted set stores the unit that was actually used for that set.

- Legacy compatibility rows store this in `sets.weightUnit`.
- Canonical rows store this in `workout_sets.weight_unit`.
- Parser DTOs carry the source unit from input into persistence.
- Existing legacy rows from schema 8 or 9 migrate to explicit `KG` because old compatibility rows had no source-unit column and GymTrack must not guess silently.

Changing the global default must not reinterpret existing history. It only affects new set input that omits an explicit unit.

## Display conversion

Display conversion is separate from input defaults and source-unit storage. It should be explicit and reversible. The app must not silently convert or rewrite historical values because the global default changed.
