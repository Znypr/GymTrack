# GymTrack roadmap

**Last updated:** 5 July 2026  
**Planning assumption:** one primary developer working part-time. Dates are targets, not commitments.

## Source of truth

This document defines sequencing and outcomes. GitHub Issues, labels, linked pull requests, and required checks define live ticket status. The dated [project status](PROJECT_STATUS.md) provides a compact orientation snapshot.

Priorities:

- **P0:** data loss, crashes, invalid releases, or broken delivery pipeline;
- **P1:** core logging reliability, architecture blockers, or incorrect statistics;
- **P2:** meaningful usability and performance improvements;
- **P3:** optional improvements and experiments.

## Progress at 5 July 2026

Completed earlier than the original roadmap sequence:

- Android CI and repository governance baseline;
- explicit Room migration chain and migration tests;
- canonical workout-model ADR;
- canonical Room v9 schema;
- deterministic and idempotent legacy backfill;
- canonical repository boundary and dual-read verification;
- versioned `TrainingSummary` projection.

Still incomplete:

- reliable issue-to-branch automation on current branch protection;
- Android 14+ manual validation and merge of the timer replacement;
- explicit separation of autosave, finalization, statistics synchronization, and export;
- typed editor state without hidden Unicode persistence;
- canonical production read/write cutover;
- removal of full statistics rebuild at startup;
- dead-code and dependency cleanup;
- permanent application identity and production release signing.

## Phase 0 — Governance baseline

**Target:** 29 June–5 July 2026  
**Status:** nearly complete.

Completed:

- project charter and architecture documentation;
- issue forms, pull-request template, labels, and deterministic ticket-board rules;
- Android CI and required checks;
- contribution and testing documentation;
- stale pull-request cleanup.

Remaining:

- **#129 / PR #156:** validate the rebuilt work-item automation on current `master` and confirm one controlled `status:ready` issue creates exactly one branch and draft pull request.

Exit condition:

Every new change can follow a documented issue-to-merge path without bypassing branch protection.

## Phase 1 — Safety and stabilization

**Target:** 6–19 July 2026  
**Status:** partially complete.

Completed:

- destructive migration fallback removed;
- explicit Room migrations and migration coverage added;
- CI validates unit tests, lint, and debug assembly;
- migration-sensitive changes have emulator-backed validation paths.

Current sequence:

1. **#127 / PR #128:** complete Android 14+ timer restoration validation and merge the timestamp-derived timer implementation.
2. **#122:** separate autosave from finalization, statistics synchronization, and export.
3. **#121:** remove only compiler-confirmed dead code and duplicate dependencies.
4. Add user-visible save/import/export failure handling where still missing.

Release-specific work:

- **#124:** permanent application identity, release signing, and versioning remain P0 before external distribution, but do not block internal architecture work.

Exit condition:

Schema changes cannot silently erase data, critical logging paths are deterministic, and the repository workflow is reproducible.

## Phase 2 — Canonical data architecture

**Original target:** 20 July–16 August 2026  
**Status:** foundation completed early; production cutover remains.

Completed:

- **#120 / PR #138:** canonical workout-model decision;
- **#139 / PR #149:** canonical v9 schema beside legacy tables;
- **#140 / PR #150:** deterministic legacy backfill;
- **#141 / PR #154:** domain repository boundary, mappings, aggregate persistence, and dual-read verification;
- **#123:** staged canonical-schema parent completed.

Remaining:

- switch active editor writes and normal history/statistics reads incrementally to canonical repositories;
- keep legacy raw data during the documented compatibility period;
- measure and resolve dual-read mismatches before removing compatibility paths;
- ensure every mutation path keeps canonical statistics data consistent.

Exit condition:

History, statistics, export, and external summaries use the same canonical workout representation during normal operation.

## Phase 3 — Editor, autosave, timer, and summary reliability

**Target:** 17 August–6 September 2026  
**Status:** queued, with some foundation already merged.

Work:

- **#153:** durable local `TrainingSummary` snapshot or outbox after explicit workout completion;
- **#122:** debounced and serialized draft autosave with independent finalization/export responsibilities;
- **#125:** typed `WorkoutDraft`, explicit ordering and exercise modes, and removal of hidden separator persistence;
- complete process-restoration coverage for timer and editor state;
- expose clear saved, unsaved, and failure states;
- validate long-session performance.

Creator OS boundary:

GymTrack produces compact, versioned summaries keyed by stable `workout_id`. The external bridge and Google Sheets upsert are not part of GymTrack runtime storage.

Exit condition:

The primary logging workflow is fast, recoverable, and independent of encoded text internals or network availability.

## Phase 4 — Statistics and product quality

**Target:** 7–20 September 2026  
**Status:** blocked on reliable canonical mutation paths.

Work:

- **#126:** remove the unconditional full statistics rebuild from startup;
- document metric definitions and time-range behavior;
- calculate progression from typed canonical data;
- add rolling averages and sample-size handling;
- improve accessible chart styling only after correctness;
- maintain parser and statistics regression datasets.

Exit condition:

Every displayed statistic can be explained and reproduced from canonical stored data without reparsing the full history at launch.

## Phase 5 — Beta and release readiness

**Target:** 21 September–4 October 2026  
**Status:** planned.

Work:

- **#124:** permanent application ID, release signing, and versioning;
- tested backup and restore;
- privacy statement;
- release checklist and release CI validation;
- closed beta build;
- crash and feedback process;
- version 1.0 milestone review.

Exit condition:

GymTrack is safe to distribute to beta testers without destructive upgrades, debug signing, or ambiguous application identity.

## Current prioritized queue

| Order | Issue | Outcome | Current state |
|---:|---|---|---|
| 1 | #129 / PR #156 | Reliable work-item automation | In review / CI |
| 2 | #127 / PR #128 | Safe long-running workout timer | Manual Android 14+ validation |
| 3 | #153 | Completion-triggered summary outbox | Next implementation after prerequisite cleanup |
| 4 | #122 | Separate autosave and side effects | Ready for detailed implementation planning |
| 5 | #125 | Typed editor state | Sequence after #122 |
| 6 | #126 | Remove startup statistics rebuild | Sequence after mutation-path cutover |
| 7 | #121 | Dead-code and dependency cleanup | Independent maintenance backlog |
| 8 | #124 | Production identity and signing | Required before external distribution |

## Later product backlog

### P2

- exercise alias management;
- workout templates;
- previous-performance display while logging;
- configurable rest-timer behavior;
- progression-chart and filter improvements;
- optional RPE or RIR;
- accessibility and tablet layouts;
- startup and large-history optimization after correctness.

### P3 / later

- encrypted cloud backup or synchronization;
- multi-device conflict handling;
- Wear OS companion;
- coach export and reporting;
- intelligent parsing suggestions;
- exercise-library metadata;
- bodyweight and measurement tracking;
- training-plan support.

## Milestone rules

- A milestone contains an outcome, not an arbitrary list of features.
- P0 defects can interrupt a milestone.
- Work larger than one reviewable pull request must be split.
- Dates move when safety or correctness requires it; acceptance criteria do not silently weaken.
- New feature work does not bypass architecture or migration prerequisites.
- Update this roadmap after phase-defining merges or dependency changes, with an absolute date.
