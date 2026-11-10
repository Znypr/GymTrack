# GymTrack project charter

## Vision

GymTrack should be the fastest reliable way for a strength trainee to record a workout in a flexible, note-like interface while still producing accurate structured history and useful progression data.

Every major capability must improve at least one of:

1. faster logging;
2. safer data;
3. clearer workout history;
4. better-supported training decisions;
5. less time and attention spent on the phone during a workout, without losing functionality.

The overall direction: save as much time as possible during the workout, make tracking and planning easy, notice the user's mistakes and help correct them, stay lightweight and responsive, and guide visually rather than through text.

## Product promise

A user should be able to start logging immediately, record exercises and sets with minimal interaction, leave and return without losing data, understand what was recorded, inspect prior performance, back up or restore their data, and trust application updates.

The user should also be able to start from what they usually do instead of a blank page, see at a glance which muscles a workout or week has trained, and get explainable help while logging: likely mistakes flagged with a one-tap fix, and optional suggestions drawn from their own history and documented training evidence.

## Primary user

The initial user is a strength-training or bodybuilding-focused athlete who values speed, uses shorthand or exercise aliases, may perform unilateral work or supersets, wants progression history, and expects the application to work without an account or internet connection.

Version 1.0 optimizes for one user and one device.

## Product principles

### Fast, flexible logging

The editor should feel close to writing a note. Structure should help without forcing unnecessary screens or fields.

### Typed storage

The interface may be text-like, but saved workouts should use explicit typed data. Invisible characters and repeated reparsing are not a suitable long-term persistence model.

### Local-first operation

Logging, history, editing, statistics, import, export, backup, and restore must work without a network connection.

### Data safety

Schema changes require explicit migrations and migration tests. User data must not be silently discarded or guessed.

### Honest statistics

Metrics must state what they measure and their limitations. Missing or ambiguous data must remain visible.

### Guidance, not autopilot

Suggestions, corrections, and plans are optional, explainable, and never saved without a user action. Each suggestion states whether it comes from the user's own history or from a documented training policy, and training policies cite their evidence. Mistake checks suggest a fix; they never change data on their own.

### Lightweight and responsive

The logging screen has an explicit performance budget: no parsing or history queries on the keystroke path, no added cold-start work, and derived data computed once when a workout is saved. Features that add taps or latency during a set must justify themselves against that budget.

### Visual guidance

State should be readable at a glance through shape, icon, and pattern as well as colour. Colour alone never carries meaning, and each colour keeps a single meaning across the app.

### Proportionate architecture

GymTrack remains one Gradle application module until a measurable need justifies additional modules.

### Traceable changes

Every non-trivial change should have a GitHub Issue, acceptance criteria, a dedicated branch, a pull request, automated checks, and testing evidence.

## Version 1.0 outcome

The first stable release should provide:

- dependable offline workout logging;
- safe database upgrades;
- structured workouts;
- reliable history and search;
- clear progression statistics;
- tested import, export, backup, and restore;
- a documented production release process.

## Version 1.0 non-goals

- social feeds or public profiles;
- nutrition tracking;
- marketplace features;
- coach-client management;
- mandatory accounts;
- cloud infrastructure before local data integrity is proven;
- speculative multi-module architecture;
- medical, injury, or rehabilitation advice;
- AI-generated training programs.

## Long-term direction

The next named phase after 1.0 is guided logging and planning (see `docs/ROADMAP.md`, Phase 6): input guardrails, start-from-last prefill, history-based set suggestions, inferred warm-up and rest data, a muscle coverage map, and opt-in, evidence-based training goals.

Later capabilities may include full templates and week planning, measurements, program tracking, encrypted synchronization, wearable entry, and coach-readable reporting. They must not weaken offline operation, data ownership, explainability, or logging speed.

## Priority model

- **P0:** data loss, crashes, invalid releases, or broken delivery pipeline;
- **P1:** core logging reliability, architecture blockers, or incorrect statistics;
- **P2:** meaningful usability and performance improvements;
- **P3:** optional improvements and experiments.

P0 work interrupts other work.

## Idea filter

Before prioritizing an idea, determine the user problem, measurable outcome, permanent complexity, required data quality, offline behavior, simpler alternative, and explicit non-scope.

Ideas that cannot answer those questions remain in the Issue backlog.

## Success measures

Engineering measures include CI reliability, migration coverage, regression coverage, crash-free sessions, data-operation failure rates, startup performance, unresolved high-priority Issues, stale pull requests, and protected-branch compliance.

Product measures include time to begin a workout, interactions required to log a set, recovery after interruption, correction rate, backup and restore success, usefulness of history, and user trust in recorded data.

## Governance

- This charter defines stable product direction and principles.
- `docs/ROADMAP.md` defines long-term sequence and outcomes.
- GitHub Issues define current work contracts, priority, dependencies, and acceptance criteria.
- The GitHub Project visualizes those Issues without duplicating them.
- Pull requests define implementation and validation evidence.
- Architecture Decision Records define consequential technical decisions.
