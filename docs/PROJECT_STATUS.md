# GymTrack project status

**Last reviewed:** 5 July 2026  
**Authoritative live state:** GitHub Issues, pull requests, labels, and required checks.

This document is a dated orientation snapshot. It must not replace live GitHub state.

## Current baseline

`master` now contains:

- documented project governance, issue templates, pull-request rules, and Android CI;
- explicit Room migrations with destructive fallback removed;
- an accepted canonical workout-model ADR;
- the canonical Room v9 schema alongside the legacy tables;
- deterministic and idempotent legacy-data backfill;
- canonical repository boundaries, aggregate mappings, and dual-read verification;
- a pure, versioned `TrainingSummary` projection from canonical `WorkoutDetails`.

The canonical foundation is present, but the application is still in a transition period. The editor, normal history/statistics reads, autosave pipeline, and release configuration have not all moved to the final architecture.

## Active work

### Repository workflow

- **#129 / PR #156:** rebuild the `status:ready` work-item automation on current `master` and validate it under current branch protection.

### Runtime validation

- **#127 / PR #128:** replace the Android `shortService` workout timer with persisted timestamp-derived state. Automated checks pass; Android 14+ manual restoration validation remains before merge.

### Next implementation

- **#153:** write or enqueue one durable `TrainingSummary` snapshot after explicit workout completion. Autosave must never enqueue summaries, and transport failures must not affect canonical workout storage.
- **#135:** parent integration ticket. GymTrack owns summary production; the external Creator OS bridge and Google Sheets upsert remain outside this repository.

## Sequenced backlog

1. **#122 — Separate autosave, finalization, statistics synchronization, and export.**
2. **#125 — Replace hidden Unicode persistence with typed editor state.** Sequence after #122 so the new draft model is not coupled to the current save side effects.
3. **#126 — Remove full statistics rebuild from startup.** Sequence after mutation paths maintain canonical consistency.
4. **#121 — Remove compiler-confirmed dead code and duplicate dependencies.** Keep behavior changes out of this cleanup.
5. **#124 — Configure permanent application identity and production release signing.** Required before external distribution, not before internal architecture work.

## Recently completed

- **#119 / PR #146:** explicit migration chain and migration tests.
- **#120 / PR #138:** canonical workout-model decision.
- **#139 / PR #149:** canonical Room v9 schema.
- **#140 / PR #150:** deterministic legacy backfill.
- **#141 / PR #154:** canonical repository boundary and dual-read verification.
- **#152 / PR #155:** versioned `TrainingSummary` projection.
- **#123:** completed parent ticket for the staged canonical-schema transition.

## Current technical risks

- The legacy note representation is still involved in active editor and compatibility paths.
- Hidden separator metadata remains until #125.
- Autosave still has responsibilities that belong to finalization, statistics updates, or export until #122.
- Full statistics repair work still occurs at startup until #126.
- The foreground timer implementation remains on `master` until PR #128 is manually validated and merged.
- The package identity remains `com.example.gymtrack`, and release signing is not production-ready until #124.
- Confirmed cleanup candidates remain tracked by #121.

## Maintenance rules

Update this file when one of the following occurs:

- a phase-defining issue or pull request merges;
- an issue dependency changes;
- the next active implementation ticket changes;
- a release blocker is added or removed.

Each update must include an absolute review date. Static status text must always defer to live issues, pull requests, labels, and checks.
