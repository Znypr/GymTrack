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

Every future typed/canonical weighted set should store the unit that was actually used for that set. Changing the global default must not reinterpret existing history.

Current compatibility note: the legacy `sets` table stores numeric weights but does not yet have a unit column. This first implementation adds the setting, visible editor default, parser defaulting, explicit override recognition, and parser DTO unit output. Persisting source units for compatibility rows requires a schema-backed follow-up or the typed editor-state work tracked separately.

## Display conversion

Display conversion is separate from input defaults and source-unit storage. It should be explicit and reversible. The app must not silently convert or rewrite historical values because the global default changed.
