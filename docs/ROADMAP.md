# GymTrack roadmap

This roadmap defines product sequence and outcomes. It is not a backlog or status report.

Use GitHub Issues for the current work contract and the GitHub Project for the live visual plan.

## Phase 1 — Safety and reliability foundation

Outcomes:

- reproducible builds and required automated checks;
- explicit, tested database migrations;
- protected local workout data;
- reliable issue-to-branch-to-pull-request workflow;
- observable failures instead of silent data loss or repair loops.

Exit condition:

Core development and schema changes can be validated without risking existing workout history.

## Phase 2 — Canonical workout data

Outcomes:

- one typed canonical workout representation;
- stable workout, exercise, set, and category identity;
- deterministic migration from legacy data;
- repository boundaries between UI, domain logic, and Room;
- history, statistics, export, and integrations reading consistent data.

Exit condition:

Normal application behavior no longer depends on multiple conflicting workout representations.

## Phase 3 — Logging workflow reliability

Outcomes:

- typed workout-draft state;
- fast, serialized, transactional autosave;
- explicit separation of draft saving, finalization, statistics updates, and export;
- timer restoration after interruption or process recreation;
- clear saved, unsaved, and failure states;
- stable behavior during long sessions and rapid input.

Exit condition:

A workout can be logged quickly and recovered safely without hidden persistence metadata or background timing loops.

## Phase 4 — Statistics and product quality

Outcomes:

- statistics derived from canonical typed data;
- no unconditional full-history rebuild at startup;
- documented and reproducible metric definitions;
- correct time-range filtering and progression calculations;
- accessible chart and interaction behavior;
- regression datasets for parsing and statistics.

Exit condition:

Every displayed statistic can be explained and reproduced from stored workout data.

## Phase 5 — Beta and release readiness

Outcomes:

- permanent application identity;
- production signing and monotonic versioning;
- tested backup and restore;
- privacy documentation;
- release checklist and release validation;
- closed beta feedback and crash process.

Exit condition:

GymTrack can be distributed to testers without destructive upgrades, debug signing, or ambiguous application identity.

## Phase 6 — Guided logging and planning

Outcomes:

- likely input mistakes flagged at entry with a one-tap fix, never changed silently;
- one-tap start from the last workout of a rotation;
- warm-up sets and rest times inferred from loads and timestamps, with user override;
- history-based next-set suggestions accepted with one tap;
- derived per-exercise and per-muscle indexes so guidance adds no logging latency;
- an anatomically accurate muscle coverage map after each workout and per week;
- opt-in training goals whose defaults are documented with citations.

Exit condition:

A repeated set takes one tap, likely mistakes are caught before they reach statistics, the user can see which muscles a workout or week trained, and the logging screen stays within its documented performance budget.

## Later ambitions

- full workout templates and week planning;
- configurable rest timers;
- optional explicit RPE or RIR entry;
- bodyweight and measurement tracking;
- training-plan support;
- coach-oriented reporting;
- encrypted backup or synchronization;
- multi-device conflict handling;
- Wear OS support.

## Planning rules

- Safety and data-integrity defects can interrupt the planned sequence.
- Each implementation issue must be reviewable in one pull request or split into child issues.
- Acceptance criteria do not weaken silently.
- The roadmap changes only when product sequence or intended outcomes change, not when individual tickets move.
