# GymTrack release milestone roadmap

This document defines how GymTrack tickets are grouped into finite release milestones. Milestones are release checkpoints, not labels. Labels describe status, type, priority, area, energy, confidence, risk, automation, and CI. Milestones describe the release that owns the work.

## Operating rules

1. Every implementation ticket that belongs to a finite release checkpoint should have exactly one milestone.
2. Parent or vision issues may be assigned to a milestone only when their acceptance criteria are finite enough to close inside that release.
3. Broad product differentiators should be split into concrete child issues before they become release blockers.
4. A release may ship only when all `must close` tickets in its milestone are closed or explicitly moved out with a written reason.
5. A `should close` ticket can move to a later milestone if the deferral is documented in the issue or release notes.
6. Labels remain the source of truth for priority and work type. Do not create duplicate milestones for `P0`, `P1`, `feature`, `bug`, `refactor`, or app areas.
7. Release tags should follow the milestone sequence, for example `v0.1-safe-foundations` for `M1 Safe Foundations`.

## Milestone definitions

| Milestone | Release tag | Purpose | Release gate |
| --- | --- | --- | --- |
| M0 Governance Baseline | `v0.0-governance` | Project-management infrastructure exists and works. | Labels, project fields, issue metadata, milestone rules, and automation conventions are defined. |
| M1 Safe Foundations | `v0.1-safe-foundations` | GymTrack can safely store, migrate, restore, and derive data without fragile hidden assumptions. | Typed persistence, safe startup behavior, one-file backup/restore, restore failure safety, and enough cleanup to make the active architecture clear. |
| M2 First Production Loop | `v0.2-production-loop` | GymTrack has one complete useful loop from workout completion to durable output. | Completed workout produces a durable versioned summary/outbox entry without coupling active logging to network, Google Sheets, or a backend. |
| M3 Automation Hardening | `v0.3-automation-hardening` | Development execution becomes less manual without hiding risk. | Automatic and manual work-item recovery paths are validated and do not create duplicate draft PRs. |
| M4 Beta Readiness | `v0.4-beta-readiness` | GymTrack is safe enough for broader external testing. | Release identity, signing separation, version rules, backup privacy decisions, backup warning copy, and release validation are documented. |
| M5 Migration & Import Expansion | `v0.5-migration-import` | Switching into GymTrack becomes easier for users with existing history. | Import and restore expansion features have explicit duplicate, conflict, and review semantics. |
| M6 Intelligent Logging | `v0.6-intelligent-logging` | GymTrack uses confirmed history to reduce manual workout entry. | Suggestions are optional, editable, explainable, and derived from trustworthy canonical history. |
| M7 Public Polish | `v0.7-public-polish` | Public-facing release polish is ready. | Store-facing assets, onboarding, support, privacy review, analytics/crash policy, and user documentation are complete. |

## Ticket assignment

### M1 Safe Foundations

Must close:

| Issue | Title | Reason |
| --- | --- | --- |
| #121 | Remove confirmed dead code and duplicate dependencies | Reduces repository ambiguity before larger data and release work. |
| #125 | Replace hidden separator persistence with typed editor state | Removes fragile hidden Unicode metadata from new persisted workouts. |
| #126 | Remove full statistics rebuild from application startup | Prevents startup from reparsing every workout and concealing consistency defects. |
| #179 | Add one-file full backup and restore for all GymTrack data | Establishes the baseline data-safety mechanism for development builds, migrations, reinstalls, and recovery. |
| #196 | Validate restore interruption and process-death recovery | Proves restore failure and interruption paths do not corrupt existing local data. |

Release gate:

- newly saved workouts use typed state rather than zero-width separator persistence;
- existing encoded notes remain compatible;
- normal startup does not parse every workout;
- save, edit, delete, and import operations keep derived data consistent;
- backup/restore works transactionally;
- at least one interrupted or mid-restore failure path is validated;
- unit tests, lint, and debug assembly pass for the affected areas.

### M2 First Production Loop

Must close:

| Issue | Title | Reason |
| --- | --- | --- |
| #135 | Export compact training summaries for Creator OS / Google Sheets | Defines the first durable external-output loop while keeping GymTrack canonical. |
| #153 | Write TrainingSummary snapshot/outbox after explicit workout completion | Implements completion-triggered summary persistence without draft-autosave coupling. |

Release gate:

- explicit workout completion produces one current summary per workout;
- autosave produces no summary;
- editing a completed workout updates the same stable summary key;
- pending summaries survive restart;
- external transport can be added without changing workout storage;
- transport failures cannot roll back or block workout storage.

### M3 Automation Hardening

Must close:

| Issue | Title | Reason |
| --- | --- | --- |
| #171 | Validate manual work-item workflow dispatch | Validates the manual Actions recovery path for work-item automation. |

Release gate:

- manual workflow dispatch runs successfully with an eligible `status:ready` issue;
- the workflow creates missing work or reports an existing draft PR without duplication;
- the workflow run link and result are recorded.

### M4 Beta Readiness

Must close:

| Issue | Title | Reason |
| --- | --- | --- |
| #124 | Configure permanent application identity and production release build | Required before external distribution. |
| #199 | Decide backup encryption and private metadata policy | Records whether unencrypted backups are acceptable, whether encryption is deferred, and what metadata is allowed. |
| #200 | Define backup policy for future non-core data | Prevents backup-format drift once generated summaries, notebook images, drafts, timer state, deleted records, or archives exist. |

Should close:

| Issue | Title | Reason |
| --- | --- | --- |
| #197 | Add preview-only backup inspection mode | Improves restore trust before broader testers handle backup files. |

Release gate:

- permanent application ID and namespace decision is documented;
- release signing does not reference debug signing;
- version-code and version-name rules are documented;
- no signing material is committed;
- backup privacy/security decision is recorded;
- allowed and disallowed backup metadata fields are documented;
- user-facing backup warning copy is defined;
- preview-only backup inspection is implemented or explicitly deferred with a reason.

### M5 Migration & Import Expansion

Candidate tickets:

| Issue | Title | Handling |
| --- | --- | --- |
| #177 | Import handwritten workout history from notebook photos | Split into design, extraction prototype, review UI, duplicate detection, and resumability children before implementation. |
| #198 | Design merge restore without duplicates | Keep design-first. Implementation should not start until duplicate identity, conflict handling, partial import behavior, and review UI are documented. |

Release gate:

- imports are reviewable before commit;
- duplicate detection is deterministic enough for user trust;
- uncertain values are visible rather than silently accepted;
- interrupted large imports can resume safely;
- merge restore semantics are documented before coding.

### M6 Intelligent Logging

Candidate tickets:

| Issue | Title | Handling |
| --- | --- | --- |
| #178 | Learn from workout history to predict and prefill future training | Start only after typed data, stable units, completion boundaries, and trustworthy history are in place. |

Release gate:

- suggestions are optional and editable;
- suggestions explain the local history signal used;
- no suggestion silently modifies completed history;
- deterministic rules are preferred before opaque machine-learning behavior;
- user-configured progression goals are respected.

### M7 Public Polish

No current mapped tickets.

Candidate future work:

- onboarding and first-run empty-state flow;
- store listing metadata and screenshots;
- support and feedback channel;
- crash-reporting and analytics policy decision;
- permissions review;
- user documentation and tester handoff notes.

## Dependency policy

M2 depends on the relevant typed/canonical data work from M1. Training summaries should be generated from canonical typed data rather than reparsed note text.

M4 release work can begin before M1 and M2 are fully closed, but M4 should not ship externally until M1 data-safety gates and M2 production-loop gates are satisfied or explicitly deferred.

M5 and M6 should not block beta readiness. They are product expansion milestones after the first safe beta path exists.

## GitHub milestone assignment table

Use this table when assigning GitHub issue milestones.

| Issue | Milestone |
| --- | --- |
| #121 | M1 Safe Foundations |
| #125 | M1 Safe Foundations |
| #126 | M1 Safe Foundations |
| #179 | M1 Safe Foundations |
| #196 | M1 Safe Foundations |
| #135 | M2 First Production Loop |
| #153 | M2 First Production Loop |
| #171 | M3 Automation Hardening |
| #124 | M4 Beta Readiness |
| #197 | M4 Beta Readiness |
| #199 | M4 Beta Readiness |
| #200 | M4 Beta Readiness |
| #177 | M5 Migration & Import Expansion |
| #198 | M5 Migration & Import Expansion |
| #178 | M6 Intelligent Logging |

## Review checklist for changing this roadmap

- Does the ticket have finite acceptance criteria?
- Is this a release gate or only an area/category label?
- Does the ticket belong to a parent initiative instead of becoming a release blocker?
- Is the dependency relationship explicit?
- Can the milestone close without carrying vague vision work?
- Are release blockers separated from optional polish?
