# GymTrack project status

**Last reviewed:** 5 July 2026

GitHub Issues, pull requests, labels, and required checks are the authoritative live state. This file is a dated orientation snapshot.

## Current baseline

`master` includes repository governance and Android CI, explicit Room migrations, the canonical workout-model decision, the canonical Room v9 schema, deterministic legacy backfill, canonical repository boundaries, dual-read verification, and versioned `TrainingSummary` projection.

The application remains in transition. The active editor, normal history and statistics paths, autosave responsibilities, timer replacement, cleanup, and release configuration are not all complete.

## Active work

- **#129:** work-item automation code merged in PR #156, but the controlled `status:ready` test on #153 still produced no branch, draft PR, or issue comment. The issue is reopened for Actions event and permission validation.
- **#127 / PR #128:** persisted timestamp-derived workout timer; automated checks pass, Android 14+ manual restoration validation remains.
- **#153:** local `TrainingSummary` snapshot or durable outbox after explicit workout completion. A manual work branch exists because automation did not create the work item.
- **#135:** parent Creator OS integration ticket. GymTrack owns summary production; downstream transport remains outside this repository.

## Sequenced backlog

1. **#122:** separate autosave, finalization, statistics synchronization, and export.
2. **#125:** replace hidden Unicode persistence with typed editor state after #122.
3. **#126:** remove full statistics rebuild from startup after mutation paths maintain canonical consistency.
4. **#121:** remove confirmed dead code and duplicate dependencies without behavior changes.
5. **#124:** permanent application identity and production release signing before external distribution.

## Recently completed

- **#119 / PR #146:** explicit migration chain and migration tests.
- **#120 / PR #138:** canonical workout-model decision.
- **#139 / PR #149:** canonical Room v9 schema.
- **#140 / PR #150:** deterministic legacy backfill.
- **#141 / PR #154:** canonical repository boundary and dual-read verification.
- **#152 / PR #155:** versioned `TrainingSummary` projection.
- **#123:** completed canonical-schema parent ticket.

## Current technical risks

- Legacy note representation remains in active editor and compatibility paths.
- Hidden separator metadata remains until #125.
- Autosave remains coupled to unrelated work until #122.
- Startup statistics repair remains until #126.
- The foreground timer remains on `master` until PR #128 is validated and merged.
- Package identity and release signing remain unfinished until #124.
- Automatic issue-to-work-item creation is not yet proven reliable until #129 passes its end-to-end acceptance criteria.

## Maintenance

Update this file after a phase-defining merge, dependency change, change to the next active implementation ticket, or release-blocker change. Always include an absolute review date and defer to live GitHub state.
