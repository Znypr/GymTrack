# ADR 0001: Creator OS Training Summary Sync

**Status:** Accepted  
**Date:** 2026-07-05  
**Related issues:** #135, #136  

## Context

Creator OS needs workout information for evening close, weekly health review, cut adjustments, recovery tracking, and planning. GymTrack must remain fast, local-first, and safe during active training.

The canonical workout decision in ADR 0002 makes GymTrack Room data the source of truth. A direct Google Sheets mirror would create another source of truth and store excessive low-value detail outside GymTrack.

## Decision

GymTrack will support Creator OS through a compact, versioned `TrainingSummary` projection instead of mirroring detailed workout data.

The near-term automated path is:

```text
Completed workout or explicit final save
  -> build TrainingSummary from canonical Room data
  -> write local snapshot or durable sync queue item
  -> external bridge upserts Google Sheets by workout_id
```

GymTrack v1 will not use Google Sheets as live persistence and will not require a backend for active logging.

## Rules

- `workout_id` is the stable upsert key.
- Autosave does not trigger summary generation or network sync.
- Summary generation may run after explicit final save or workout completion.
- Missing optional values remain null or blank and are not inferred.
- Summary artifacts are integration outputs, not backup files.
- Full backup and restore remains separate and more detailed.
- The external bridge owns Google Sheets writes.
- GymTrack owns detailed workout data and summary generation.

## Consequences

### Positive

- GymTrack remains the single detailed source of truth.
- Creator OS receives the fields needed for daily and weekly decisions.
- No manual export is required in the final workflow.
- Google Sheets remains compact and understandable.
- Summaries can be regenerated from canonical Room data.
- Logging remains available offline.

### Negative

- A separate bridge is required for automatic Google Sheets updates.
- Full cloud synchronization is deferred.
- The summary schema must be versioned.
- Energy and recovery fields require explicit user input or remain null.

## Rejected options

### Mirror every set into Google Sheets

Rejected because it creates redundancy, increases conflict risk, and turns Sheets into a second workout database.

### Use Google Sheets as GymTrack storage

Rejected because it breaks the local-first product model and makes active training dependent on network and API behavior.

### Add Firebase or Supabase immediately

Rejected for the initial implementation because canonical persistence and migration safety must be established before distributed synchronization is worth maintaining.

### Keep manual export as the primary workflow

Rejected because Creator OS should update without repeated export friction. Manual export may remain a fallback and recovery tool.

## Implementation order

1. Canonical workout model — completed by ADR 0002 / PR #138.
2. Safe canonical Room schema and legacy backfill — #139 and #140.
3. Canonical repository layer — #141.
4. Summary builder and snapshot/queue implementation — #135.
5. External Creator OS bridge.
6. Optional Drive or backend transport after validation.
