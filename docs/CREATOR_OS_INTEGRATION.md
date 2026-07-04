# Creator OS Integration

**Status:** Active design  
**Owner:** Znypr  
**Related issues:** #135, #136

## Purpose

Creator OS needs enough training data to close the day, review the cut, detect training or recovery issues, and create useful tickets. It does not need a copy of every GymTrack workout set.

GymTrack remains the canonical workout system. Creator OS receives a compact, versioned training summary derived from canonical workout data.

## Boundaries

- GymTrack Room data is the source of truth for detailed workout history.
- Google Sheets is not a live workout database.
- Creator OS stores summary rows only.
- Full backup and restore data remains separate from summary sync data.
- Google Sheets API access is not required inside GymTrack v1.
- Missing optional fields remain blank or null; they are not inferred.
- Autosave does not trigger export or network sync.

## Data flow

```text
GymTrack Room DB
  -> canonical workout query
  -> TrainingSummary
  -> local snapshot or durable sync queue
  -> optional external bridge
  -> Creator OS / Google Sheets upsert by workout_id
```

## Near-term automation

Manual export is not the target workflow. After a workout is completed or explicitly saved as final, GymTrack should update a compact local summary snapshot or enqueue a summary-sync item.

Possible local artifacts:

```text
Documents/GymTrack/training_summary.json
Documents/GymTrack/training_summary.csv
```

These files are integration artifacts and can be regenerated from Room. They are not the source of truth and are not full backups.

## Later sync targets

### Local file bridge

A separate Creator OS helper or Drive-synced folder ingests the summary artifact and updates Google Sheets. This is the preferred first bridge because it preserves offline-first logging and avoids backend scope.

### Google Drive

An optional user-enabled integration may write a summary snapshot to a user-selected Drive file or app data. Logging must continue to work when authentication or network access fails.

### Backend bridge

A future Creator OS service, Supabase, or Firebase backend may receive summaries when multi-device sync or web dashboards justify the additional security and maintenance burden.

## Summary schema

One object or row per workout.

| Field | Required | Notes |
|---|---:|---|
| `schema_version` | yes | Starts at `1`. |
| `workout_id` | yes | Stable canonical ID used for upsert. |
| `date` | yes | Local training date. |
| `started_at` | no | ISO timestamp when known. |
| `ended_at` | no | ISO timestamp when known. |
| `focus` | no | Category/focus display value. |
| `status` | yes | completed, partial, draft, or skipped when supplied externally. |
| `duration_min` | no | Null when unknown. |
| `exercise_count` | yes | Canonical workout-exercise count. |
| `set_count` | yes | Canonical performed-set count. |
| `top_lifts` | no | Compact list; not full set history. |
| `performance_signal` | no | up, stable, down, mixed, or unknown. |
| `energy` | no | Explicit 1–10 user input only. |
| `recovery_note` | no | Compact explicit note. |
| `source` | yes | `GymTrack`. |
| `source_updated_at` | yes | Summary generation timestamp. |

## Example

```json
{
  "schema_version": 1,
  "workout_id": "3f29a8e6-6f9b-4e2e-9f79-6c046d07d2a1",
  "date": "2026-07-05",
  "started_at": "2026-07-05T15:04:00+02:00",
  "ended_at": "2026-07-05T16:31:00+02:00",
  "focus": "Pull",
  "status": "completed",
  "duration_min": 87,
  "exercise_count": 7,
  "set_count": 24,
  "top_lifts": ["Pull-up +10kg x 6", "Row 70kg x 10"],
  "performance_signal": "stable",
  "energy": 6,
  "recovery_note": "Low sleep, performance maintained.",
  "source": "GymTrack",
  "source_updated_at": "2026-07-05T16:32:20+02:00"
}
```

## Google Sheets behavior

The external bridge upserts by `workout_id`:

```text
if workout_id exists: update that row
else: append a new row
```

Date alone is not unique because multiple sessions may happen on one day and past workouts may be edited.

## Creator OS usage

Creator OS may use summaries to answer:

- Was training completed?
- What was the focus?
- How much work was completed?
- Did performance clearly change?
- Was energy or recovery abnormal?
- Should tomorrow's training or recovery plan change?

Creator OS does not use summary rows as a replacement for GymTrack history, set-level progression, or backup and restore.

## Implementation sequence

1. Canonical workout identity and typed domain model — completed by #120 / PR #138.
2. Safe canonical Room schema and migration — #139 and #140.
3. Canonical repository reads and writes — #141.
4. `TrainingSummary` builder from canonical data — #135.
5. Snapshot writer or durable queue triggered only by final save/completion.
6. External bridge that upserts the Health & Fitness Google Sheet.
7. Optional Drive or backend transport after the local bridge is validated.

## Validation

- Editing a past workout updates the same `workout_id` summary.
- Autosave does not write or enqueue integration summaries.
- Summary failures are observable but do not corrupt or block workout storage.
- Summary artifacts can be deleted and regenerated.
- Full backups retain enough detail to restore workouts; summaries do not.
