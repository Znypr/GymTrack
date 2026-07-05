# GymTrack roadmap

**Last updated:** 5 July 2026

Live status is defined by GitHub Issues, pull requests, labels, and required checks. See [PROJECT_STATUS.md](PROJECT_STATUS.md) for the dated current snapshot.

## Completed foundation

- Repository governance, Android CI, and recoverable work-item automation (#129 / PR #156).
- Explicit Room migrations and migration tests (#119 / PR #146).
- Canonical workout-model decision (#120 / PR #138).
- Canonical Room v9 schema (#139 / PR #149).
- Deterministic legacy backfill (#140 / PR #150).
- Canonical repository boundary and dual-read verification (#141 / PR #154).
- Versioned `TrainingSummary` projection (#152 / PR #155).

## Current sequence

1. **#127 / PR #128 — Timer reliability.** Complete Android 14+ manual restoration validation, then merge the timestamp-derived timer replacement.
2. **#153 — Training summary outbox.** Produce one idempotent local summary only after explicit workout completion.
3. **#122 — Save-pipeline separation.** Separate autosave, finalization, statistics synchronization, and export.
4. **#125 — Typed editor state.** Remove hidden Unicode persistence after #122 establishes the final save boundaries.
5. **#126 — Statistics cutover.** Remove the full startup rebuild after mutation paths maintain canonical consistency.
6. **#121 — Cleanup.** Remove only confirmed dead code and duplicate dependencies without behavior changes.
7. **#124 — Release identity.** Configure permanent application identity, signing, and versioning before external distribution.

## Phase outcomes

### Governance — complete

Changes follow an issue, dedicated branch, pull request, required checks, and documented merge path.

### Safety and stabilization — active

Database upgrades are non-destructive and tested. Remaining outcomes are timer validation, save-pipeline isolation, cleanup, and visible failure handling.

### Canonical data architecture — foundation complete

The v9 schema, backfill, repository boundary, verification, and summary projection exist. Production editor, history, and statistics paths still need incremental cutover.

### Editor and integration reliability — queued

Autosave becomes small and serialized, finalization writes canonical data, typed editor state replaces hidden metadata, and completed workouts create local versioned summaries without requiring network access.

### Statistics quality — blocked by mutation cutover

Statistics must read canonical typed data and normal startup must not reparse all workouts.

### Beta and release readiness — planned

Permanent identity, release signing, backup/restore, privacy documentation, release validation, and closed-beta feedback are required before version 1.0.

## Milestone rules

- Safety defects can interrupt planned work.
- Large work is split into reviewable pull requests.
- Acceptance criteria do not weaken silently when dates move.
- New features do not bypass architecture or migration prerequisites.
- Update this file after phase-defining merges or dependency changes.
