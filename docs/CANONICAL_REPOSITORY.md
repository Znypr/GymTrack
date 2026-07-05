# Canonical repository boundary

The canonical Room tables are exposed through pure domain models.

`CanonicalWorkoutRepository` loads and saves a complete `WorkoutDetails` aggregate:

- workout metadata;
- ordered workout-exercise occurrences;
- ordered sets;
- referenced exercise definitions and aliases;
- optional category definition.

A save is transactional. Exercise definitions, aliases, category, workout, occurrences, and sets either all persist or none do.

Historical weights without an explicit unit map to `WeightUnit.UNKNOWN` in the domain and back to a null database value. They are never treated as kilograms automatically.

`CanonicalDualReadVerifier` temporarily rebuilds the legacy projection and compares it with the canonical aggregate. It reports count, order, and value mismatches without changing either source.

Production features can migrate to this repository one at a time. Legacy tables remain available until all read and write paths have been validated.
