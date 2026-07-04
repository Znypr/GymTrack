# Creator OS Integration

**Status:** Proposed integration contract  
**Owner:** Znypr  
**Related issues:** #135, #136

## Purpose

Creator OS needs enough training data to close the day, review the cut, detect training/recovery issues, and create useful tickets. It does not need a copy of every GymTrack workout set.

GymTrack remains the canonical workout system. Creator OS receives a compact, versioned training summary.

## Non-negotiable boundaries

- GymTrack Room data is the source of truth for detailed workout history.
- Google Sheets is not a live workout database.
- Creator OS may store summary rows only.
- Full backup/restore data remains separate from summary export/sync data.
- Google Sheets API access must not be required inside GymTrack v1.
- Missing optional fields remain blank or null; they must not be inferred.
- Autosave must not trigger export or network sync.

## Recommended data flow

```text
GymTrack Room DB
  -> canonical workout queries
  -> TrainingSummarySnapshot
  -> local summary file or sync queue
  -> optional external bridge
  -> Creator OS / Google Sheets upsert by workout_id
```

## Near-term automation target

The first automated integration should not require manual export. After a workout is completed or explicitly saved as final, GymTrack updates a compact local summary snapshot.

Suggested local files:

```text
Documents/GymTrack/training_summary.json
Documents/GymTrack/training_summary.csv
```

The app may regenerate these files from canonical data. They are integration artifacts, not the source of truth.

## Later sync targets

### Option A: Local file bridge

GymTrack writes summary snapshots locally. A separate process, Drive folder sync, or Creator OS helper ingests the file and updates Google Sheets.

Use this first because it avoids backend scope and preserves offline-first logging.

### Option B: Google Drive app data or user-selected Drive file

GymTrack writes a summary snapshot to Google Drive after the user explicitly enables Google account integration.

This should be optional and must not be required for logging.

### Option C: Backend bridge

A future backend such as Supabase, Firebase, or a Creator OS service can receive summary snapshots and update Google Sheets.

This is not required for GymTrack v1. It becomes useful only if multi-device sync, web dashboards, or productized distribution justify the extra security and maintenance burden.

## Summary schema

One object or row per workout.

| Field | Required | Notes |
|---|---:|---|
| schema_version | yes | Starts at `1`. |
| workout_id | yes | Stable ID. Used for upsert. |
| date | yes | Local training date. |
| started_at | no | ISO timestamp when known. |
| ended_at | no | ISO timestamp when known. |
| focus | no | Push, Pull, Legs, Upper, Lower, Rest, custom. |
| status | yes | completed, skipped, partial, draft. |
| duration_min | no | Null when unknown. |
| exercise_count | yes | Count from canonical workout exercises. |
| set_count | yes | Count from canonical performed sets. |
| top_lifts | no | Compact semicolon-separated list or JSON array. |
| performance_signal | no | up, stable, down, mixed, unknown. |
| energy | no | Optional 1-10 user entry. |
| recovery_note | no | Compact note only. |
| source | yes | `GymTrack`. |
| source_updated_at | yes | ISO timestamp of the summary snapshot. |

## Example JSON

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

The external bridge should upsert by `workout_id`:

```text
if workout_id exists: update that row
else: append new row
```

Date alone must not be used as a unique key because two sessions can happen on one day and a workout can be edited later.

## What Creator OS should use

Creator OS may use summaries to answer:

- Was training completed?
- What was the workout focus?
- How much volume was done?
- Did performance obviously drop?
- Was energy or recovery abnormal?
- Should tomorrow's training, calories, or recovery plan change?

Creator OS should not use summary rows as a replacement for GymTrack history, set-level progression, or backup/restore.

## Implementation sequencing

1. Finish or confirm canonical workout identity and typed data.
2. Add `TrainingSummary` as a domain/export model.
3. Add a summary builder that reads canonical data only.
4. Add explicit final-save or completed-workout summary snapshot generation.
5. Add CSV/JSON summary writer.
6. Add tests for stable IDs, null handling, row/object generation, and no autosave export.
7. Add optional external bridge outside GymTrack or behind an explicit opt-in sync module.

## Validation rules

- Editing a past workout updates the same `workout_id` summary.
- Autosave does not write integration snapshots.
- Export/snapshot failures are visible but do not corrupt workout storage.
- Summary files can be deleted and regenerated from Room data.
- Full backups include enough detail to restore workouts; summaries do not.
