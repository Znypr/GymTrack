# ADR 0001: Creator OS Training Summary Sync

**Status:** Proposed  
**Date:** 2026-07-05  
**Related issues:** #135, #136  

## Context

Creator OS needs workout information for evening close, weekly health review, cut adjustments, recovery tracking, and planning. GymTrack needs to remain fast, local-first, and safe during active training.

The current architecture direction already requires one canonical workout model, Room as the source of truth, typed statistics, explicit export, and no autosave side effects.

A direct Google Sheets mirror would create another source of truth and would store too much low-value set-level detail outside GymTrack.

## Decision

GymTrack will support Creator OS through a compact, versioned `TrainingSummary` snapshot instead of mirroring detailed workout data.

The preferred near-term automation is:

```text
Completed workout or explicit final save
  -> build TrainingSummary from canonical Room data
  -> update local JSON/CSV summary snapshot
  -> external bridge upserts Google Sheets by workout_id
```

GymTrack v1 will not include Google Sheets as a live persistence target.

## Consequences

### Positive

- GymTrack remains the source of truth for detailed workouts.
- Creator OS gets the information needed for daily and weekly decisions.
- No manual export is needed once local snapshot generation or an external bridge exists.
- Google Sheets stays small and useful.
- Summary files can be regenerated from Room data.
- The app remains usable offline.

### Negative

- A separate bridge is needed to update Google Sheets automatically.
- Full cloud sync is deferred.
- Summary schema versioning must be maintained.
- Optional fields such as energy and recovery notes need explicit user input or remain null.

## Rejected options

### Mirror every workout set into Google Sheets

Rejected because it creates redundancy, increases conflict risk, and turns Sheets into a second workout database.

### Use Google Sheets as GymTrack storage

Rejected because it breaks the local-first product model and makes active training dependent on network and API behavior.

### Add Firebase or Supabase immediately

Rejected for now because GymTrack still needs canonical data, migration safety, and export separation before distributed sync is worth maintaining.

### Keep only manual export

Rejected as the long-term UX because Creator OS should eventually update without manual export friction. Manual export can remain a fallback.

## Rules

- `workout_id` is the stable upsert key.
- Summary sync is not triggered by autosave.
- Summary sync may happen after explicit final save, workout completion, or user-enabled scheduled sync.
- Missing optional values are null or blank, not inferred.
- Summary files are integration artifacts, not backup files.
- Full backup/restore remains separate and more detailed.

## Implementation notes

The implementation should wait until canonical workout identity and typed data are available. The first implementation should add a summary builder and writer, not a network dependency.

Possible future bridges:

1. local file watched by Creator OS tooling;
2. user-selected Google Drive file;
3. Google Drive app data folder;
4. backend service such as Supabase, Firebase, or Creator OS API.

The bridge owns Google Sheets API writes. GymTrack owns workout data and summary generation.
