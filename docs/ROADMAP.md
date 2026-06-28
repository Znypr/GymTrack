# GymTrack Roadmap

**Last updated:** 28 June 2026  
**Planning assumption:** One primary developer working part-time. Dates are targets, not commitments.

## How to read this roadmap

This document defines sequencing and outcomes. GitHub Issues, existing labels, linked pull requests, and CI are the live source of truth for ticket status. See [`docs/TICKET_BOARD.md`](TICKET_BOARD.md) for deterministic board-column rules.

Priorities:

- **P0:** data loss, crashes, invalid releases, broken build or test pipeline;
- **P1:** core logging reliability, architecture blockers, incorrect statistics;
- **P2:** meaningful usability and performance improvements;
- **P3:** optional improvements and experiments.

## Phase 0 — Governance baseline

**Target:** 29 June–5 July 2026

Deliverables:

- project charter;
- current and target architecture documentation;
- issue forms and pull-request template;
- GitHub labels and label-driven ticket-board rules;
- Android CI;
- issue-to-branch-to-draft-PR automation;
- contribution and testing documentation;
- stale PR review and cleanup.

Exit condition:

Every new change can follow a documented issue-to-merge path.

## Phase 1 — Safety and stabilization

**Target:** 6–19 July 2026

Deliverables:

- remove destructive database migration behavior;
- add Room migration tests;
- define backup before risky migration;
- ensure tests run in CI;
- remove release debug signing;
- wire or remove incomplete actions;
- separate explicit export from autosave;
- provide user-visible import/export failures;
- remove confirmed dead code and duplicate dependencies.

Exit condition:

A build can be validated automatically and schema changes cannot silently erase data.

## Phase 2 — Canonical data architecture

**Target:** 20 July–16 August 2026

Deliverables:

- ADR for the canonical workout model;
- normalized workout schema;
- stable workout and category IDs;
- repository interfaces and `AppContainer`;
- pure parser returning immutable domain values;
- legacy note migration and backfill;
- statistics reading one source of truth;
- removal of full statistics rebuild at startup.

Exit condition:

History, statistics, and export use the same canonical data.

## Phase 3 — Editor and timer reliability

**Target:** 17 August–6 September 2026

Deliverables:

- typed `WorkoutDraft`;
- removal of hidden separator persistence;
- debounced, transactional autosave;
- timer restoration after process recreation;
- editor-state recovery tests;
- clear save, error, and unsaved states;
- performance validation for long sessions.

Exit condition:

The primary logging workflow is fast, recoverable, and independent of encoded text internals.

## Phase 4 — Statistics and product quality

**Target:** 7–20 September 2026

Deliverables:

- documented metric definitions;
- accurate time-range filtering;
- exercise progression from typed data;
- rolling averages and sample-size handling;
- accessible dark/light chart styling;
- parser and statistics regression datasets;
- UI polish after correctness.

Exit condition:

Every displayed statistic can be explained and reproduced from stored data.

## Phase 5 — Beta and release readiness

**Target:** 21 September–4 October 2026

Deliverables:

- permanent application ID;
- release signing and versioning;
- tested backup and restore;
- privacy statement;
- release checklist;
- closed beta build;
- crash and feedback process;
- version 1.0 milestone review.

Exit condition:

GymTrack is safe to distribute to beta testers without exposing their data to destructive updates.

## Initial prioritized backlog

### P0

1. Establish Android CI and required checks.
2. Replace `fallbackToDestructiveMigration()`.
3. Add database migration coverage from the current schema.
4. Define backup and restore behavior.
5. Remove debug signing from release builds.
6. Decide and document the canonical workout source of truth.
7. Close or supersede stale pull requests.
8. Configure issue forms, pull-request template, labels, label-driven ticket board, and branch protection.

### P1

9. Normalize workout, exercise, set, and category identity.
10. Stop rebuilding statistics on every launch.
11. Separate autosave from parser, export, and public file writing.
12. Replace hidden separator persistence with typed editor state.
13. Make import/export round-trip safe and versioned.
14. Restore timer state after process death.
15. Add user-visible error handling.
16. Remove dead components, legacy models, and duplicate dependencies.
17. Replace placeholder export and navigation callbacks.
18. Add parser regression cases based on real workout logs.

### P2

19. Improve exercise alias management.
20. Add workout templates.
21. Show previous performance while logging.
22. Add configurable rest-timer behavior.
23. Improve progression charts and filters.
24. Add optional RPE or RIR.
25. Improve accessibility and tablet layouts.
26. Optimize startup and large-history performance.

### P3 / Later

27. Encrypted cloud backup or synchronization.
28. Multi-device conflict handling.
29. Wear OS companion.
30. Coach export and reporting.
31. Intelligent parsing suggestions.
32. Exercise-library metadata.
33. Bodyweight and measurement tracking.
34. Training-plan support.

## Candidate first tickets after governance

1. **Build:** Make CI pass with `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
2. **Data safety:** Remove destructive migration fallback and add migration tests.
3. **Architecture decision:** Select the canonical workout data model.
4. **Cleanup:** Remove compiler-confirmed dead code and duplicate dependencies without behavior changes.
5. **Save pipeline:** Separate autosave, export, parsing, and statistics synchronization.
6. **Schema:** Add normalized workout tables and a migration path.

## Milestone rules

- A milestone contains an outcome, not an arbitrary list of features.
- P0 defects can interrupt a milestone.
- Work larger than one reviewable pull request must be split.
- Dates move when safety or correctness requires it; acceptance criteria do not silently weaken.
- New feature work does not bypass architecture or migration prerequisites.
