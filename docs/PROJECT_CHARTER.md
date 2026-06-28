# GymTrack Project Charter

**Status:** Proposed living standard  
**Last updated:** 28 June 2026

## Vision

GymTrack should be the fastest reliable way for a strength trainee to record a workout in a flexible, note-like interface while still producing accurate structured training history and useful progression data.

The application should combine:

- the speed and freedom of writing a note;
- the reliability of structured workout data;
- an offline-first experience;
- statistics that are understandable and reproducible;
- minimal friction during an active gym session.

GymTrack should not become a collection of loosely related fitness features. Every major feature must improve at least one of these outcomes:

1. faster logging;
2. safer data;
3. clearer workout history;
4. more useful training decisions.

## Product promise

A user should be able to:

1. open the application and start logging immediately;
2. add an exercise or set with minimal interaction;
3. leave and return without losing data;
4. understand exactly what was recorded;
5. inspect prior performance without manually cleaning notes;
6. export or restore their data;
7. trust that an update will not erase workout history.

## Primary user

The initial target user is a strength-training or bodybuilding-focused athlete who:

- logs several exercises and sets per session;
- values speed over filling out long forms;
- may use shorthand, aliases, unilateral work, or supersets;
- wants progression history without maintaining a spreadsheet;
- expects the application to work without an account or internet connection.

The first stable release optimizes for one user and one device. Multi-user, coaching, social, and cloud systems are later-stage concerns.

## Product principles

### Logging speed over form complexity

The editor should feel close to writing a note. Structure should help rather than force the user through unnecessary screens and fields.

### Structured storage behind a flexible interface

The interface may be text-like, but saved workouts should use typed, explicit data. Invisible characters and repeated reparsing are not a suitable long-term data model.

### Local-first and offline-first

Core logging, history, editing, statistics, import, and export must work without a network connection.

### Data safety is a feature

Production builds must not use destructive database fallback. Every schema change requires an explicit migration and migration test. Backup and restore must be testable.

### Statistics must be honest

Metrics must state what they measure and their limitations. Missing or ambiguous data must not silently become valid data.

### Small architecture before distributed architecture

GymTrack should remain one Gradle application module until build time, reuse, team ownership, or platform expansion provides a measurable reason to split it. Clear package boundaries are sufficient at the current scale.

### Every change must be traceable

Every non-trivial change should have:

- an issue;
- acceptance criteria;
- a dedicated branch;
- a pull request;
- automated checks;
- testing evidence;
- relevant documentation;
- a clear relation to this charter.

## Current product scope

GymTrack currently provides:

- local workout storage with Room;
- a note-like workout editor;
- relative and elapsed timestamps;
- bilateral, unilateral, and superset flags;
- categories and colors;
- workout learnings or notes;
- a foreground workout timer;
- CSV import and export;
- workout history;
- statistics and progression charts;
- exercise parsing and normalization.

## First stable release

Version 1.0 should provide a dependable offline workout logger with:

- excellent active-session logging;
- safe database upgrades;
- structured workouts;
- reliable history and search;
- clear progression statistics;
- tested import, export, backup, and restore;
- a documented release process.

## Non-goals for version 1.0

Unless this charter is revised, version 1.0 does not include:

- social feeds, follower systems, or public profiles;
- nutrition or calorie tracking;
- a marketplace;
- coach-client management;
- mandatory accounts;
- cloud infrastructure before local data integrity is proven;
- AI features without a clear advantage over deterministic behavior;
- medical or rehabilitation recommendations;
- speculative multi-module architecture.

## Near-term goals

### Goal 1 — Establish a reliable delivery system

- issues are the unit of planned work;
- each ready issue receives a dedicated branch and draft pull request;
- all pull requests run automated validation;
- `master` remains releasable and protected;
- decisions and changes remain documented.

### Goal 2 — Protect user data

- replace destructive Room fallback with explicit migrations;
- add migration tests;
- define backup and restore behavior;
- separate release and debug signing;
- surface import and export failures.

### Goal 3 — Establish one source of truth

- define a canonical workout model;
- move parsing to input, import, and migration boundaries;
- stop rebuilding all statistics at startup;
- make history, statistics, and export read the same typed data.

### Goal 4 — Stabilize the core logging workflow

- replace hidden separator persistence with typed editor state;
- make autosave transactional and recoverable;
- restore timer state after process recreation;
- test real workout-entry scenarios.

## Ambitions

### Version 1.x

After version 1.0 stability:

- workout templates;
- previous-session suggestions;
- exercise alias editing;
- improved progression views;
- optional RPE or RIR;
- richer backup formats;
- accessibility and layout improvements.

### Long term

GymTrack can become a personal training data system that remains fast and understandable rather than a generic fitness platform. Possible later capabilities include encrypted optional synchronization, wearable entry, program tracking, coach-readable reports, and explainable recommendations based on the user's own history.

Long-term features must not weaken offline operation, data ownership, or logging speed.

## Priority model

- **P0:** data loss, crashes, invalid releases, broken build or test pipeline;
- **P1:** core logging reliability, architecture blockers, incorrect statistics;
- **P2:** meaningful usability and performance improvements;
- **P3:** optional improvements and experiments.

P0 work interrupts other work. P3 work does not begin while unresolved P0 items remain.

## Idea filter

Before prioritizing an idea, answer:

1. Which primary-user problem does it solve?
2. Does it improve speed, reliability, history, or decision quality?
3. Can the outcome be measured?
4. Does it introduce permanent complexity?
5. Is the required data already trustworthy?
6. Can it remain offline-first?
7. What simpler alternative exists?
8. What is explicitly outside its scope?

Ideas that cannot answer these questions remain in the backlog rather than becoming active work.

## Success measures

### Engineering

- CI pass rate;
- migration-test pass rate;
- parser regression pass count;
- crash-free beta sessions;
- failed import/export rate;
- startup time with a large local dataset;
- unresolved P0 and P1 issues;
- stale pull requests;
- direct commits to the protected branch.

### Product

- time to start a workout;
- time and interactions required to log a set;
- sessions recovered after interruption;
- workouts requiring parser correction;
- successful backup and restore rate;
- use of previous-performance views;
- qualitative user trust in recorded data.

The goal is not to maximize time spent in the application. A workout logger that completes its task quickly is succeeding.

## Governance

- This charter defines stable direction.
- GitHub Issues, labels, linked pull requests, and CI define live work status.
- `docs/TICKET_BOARD.md` defines deterministic ticket-board columns.
- Pull requests define implementation and validation evidence.
- Architecture Decision Records define consequential technical decisions.
- Changes that conflict with this charter must explicitly propose a charter revision.
