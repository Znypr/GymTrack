# TrainingSummary v1

`TrainingSummary` is a compact projection of canonical `WorkoutDetails`. It is not another workout database.

Rules:

- Read typed data through `CanonicalWorkoutRepository`.
- Use stable `workout_id` for external upsert.
- Keep unavailable values null.
- Never infer weight units, energy, recovery notes, or completion state.
- Use an explicitly supplied time zone for date and ISO timestamps.
- Keep `performance_signal` as `unknown` until a separate comparison rule has enough data.

JSON fields:

- `schema_version`
- `workout_id`
- `date`
- `started_at`
- `ended_at`
- `focus`
- `status`
- `duration_min`
- `exercise_count`
- `set_count`
- `top_lifts`
- `performance_signal`
- `energy`
- `recovery_note`
- `source`
- `source_updated_at`

`top_lifts` contains at most three representatives in workout-exercise order. Per occurrence, selection prefers highest weight, then highest reps; otherwise highest reps, duration, or distance. Ties use the earliest set. Unknown units are rendered as `[unit unknown]`.

Serialization uses fixed snake-case keys, explicit nulls, deterministic ordering, and standard JSON escaping.
