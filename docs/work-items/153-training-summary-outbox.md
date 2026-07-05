# Work item #153 — TrainingSummary outbox

Status: implementation draft.

The implementation must produce or enqueue one versioned `TrainingSummary` after explicit workout completion, never from autosave. Updates use stable `workout_id`, pending entries survive restart when durable storage is selected, and failures remain isolated from canonical workout persistence.

Validation must cover autosave, completion, completed-workout edits, restart recovery, idempotency, and failure isolation.
