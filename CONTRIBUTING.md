# Contributing to GymTrack

GymTrack uses GitHub Issues, short-lived branches, pull requests, automated checks, and documented architecture decisions. Direct feature work on `master` is not part of the normal workflow.

## Sources of truth

| Information | Source |
|---|---|
| Product direction and non-goals | `docs/PROJECT_CHARTER.md` |
| Long-term sequence and outcomes | `docs/ROADMAP.md` |
| Current architecture and boundaries | `docs/ARCHITECTURE.md` |
| Work scope, priority/execution rank, dependencies, acceptance criteria | GitHub Issue |
| Milestone fit and roadmap checkpoint | GitHub milestone, selected from the charter and roadmap |
| Visual workflow status and filtering | GitHub Project view |
| Implementation, review, checks, validation | Pull request |
| Consequential technical decisions | ADRs under `docs/decisions/` |

The Project view displays Issues. It must not contain separate duplicate cards or become a second manually maintained source of truth.

## Standard workflow

1. Create or triage one GitHub Issue.
2. Define the problem, intended outcome, scope, non-scope, acceptance criteria, parent issue, blockers, risks, and validation plan.
3. Assign the current owner.
4. Apply one type, one priority/execution-rank label, one or more areas, and relevant risk or flag labels.
5. Add the issue to the global Project and set a milestone when the work advances a roadmap phase, release checkpoint, or charter-level outcome.
6. Keep new or unclear work in `status:ideas`.
7. Use `status:planned` for valid work that is not executable yet.
8. Use `flag:decision-needed` or `flag:blocked` when the issue cannot begin for a specific reason.
9. Apply `status:ready` only after the Definition of Ready is satisfied.
10. Create a dedicated branch and draft pull request from the latest protected `master`.
11. Let automation move linked draft pull requests to `status:in-progress`.
12. Implement only the linked issue scope.
13. Add or update tests and durable documentation.
14. Mark the pull request ready only after implementation and required checks are complete.
15. Let automation move ready pull requests to `status:needs-manual-review`.
16. Record emulator or device validation for runtime behavior changes.
17. Squash merge with `Closes #N` and delete the branch.

Automation should add Issues to the Project and update its visual status. Full reconciliation includes closed issues so Completed cards are present after migration. Manual board movement is recovery behavior only.

See [`docs/TICKET_BOARD.md`](docs/TICKET_BOARD.md) for issue, Project, parent-child, and dependency rules.

## Definition of Ready

An issue is ready when:

- the problem and outcome are understandable;
- included and excluded scope are explicit;
- acceptance criteria are testable;
- the parent issue and blockers are linked or explicitly absent;
- an assignee is set;
- the issue is on the global Project;
- priority/execution rank, type, area, and relevant risks are assigned;
- milestone fit is explicit when the work maps to a roadmap phase, release checkpoint, or charter-level outcome;
- migration, compatibility, performance, privacy, and release effects are considered;
- the validation plan is credible;
- the issue fits one reviewable pull request or is split into child issues.

Do not apply `status:ready` to work that still needs a product or architecture decision.

## Parent and child issues

Use a parent issue for a multi-PR outcome. The parent contains the shared result and a task list of child issues. Each child owns one reviewable scope and links back to the parent.

Use `Blocked by #N` in the issue body for active dependencies. Add `flag:blocked` while the dependency is active and remove the flag when the blocker is resolved.

## Branch naming

Use the issue number and a short slug:

```text
feat/123-workout-template
fix/148-import-duplicate-crash
refactor/162-normalized-workout-schema
docs/175-architecture-overview
chore/181-clean-dependencies
spike/190-timer-restoration-options
```

## Pull-request rules

- One principal issue per pull request.
- Keep the pull request in draft while implementation is incomplete.
- Avoid unrelated cleanup.
- Explain why the approach was chosen.
- Link the issue using `Closes #N` so the Development sidebar is populated.
- Assign the implementer on the pull request.
- Request reviewers only when the pull request is ready for review.
- Keep milestone and Project metadata aligned with the linked issue; do not create duplicate Project work unless the pull request itself is the tracked review object.
- Add PR labels only when they clarify review scope, risk, release handling, or automation state.
- Include screenshots or video for visible UI changes.
- Record emulator or device validation for runtime changes.
- Do not merge when required checks did not run.
- Prefer squash merge.
- Delete merged branches.

## Required validation

Baseline commands:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Additional validation depends on the change, including Room migration tests, parser regression tests, import/export round trips, Compose tests, managed-device tests, or release builds.

See [`docs/TESTING.md`](docs/TESTING.md) for the detailed validation strategy. A note that tests could not run is not validation.

## Definition of Done

A change is done only when:

- issue acceptance criteria are complete;
- required checks pass;
- relevant automated and manual validation is recorded;
- migrations and compatibility are handled safely;
- error, loading, empty, and accessibility states are considered where relevant;
- durable architecture, ADR, contract, or user documentation is updated when behavior or boundaries change;
- temporary code and debug output are removed;
- known limitations are tracked in Issues;
- the linked issue closes on merge.

Routine ticket movement does not require roadmap or architecture edits.

## Commit messages

Use Conventional Commit-style messages:

```text
feat(editor): add typed set rows
fix(database): preserve workouts during migration
refactor(parser): return immutable domain results
test(import): cover multiline fields
docs(architecture): document canonical workout model
chore(build): remove duplicate dependencies
```

Avoid vague messages such as `update`, `fixed bugs`, or `icons`.

## Architecture changes

Create an ADR when a change:

- changes the canonical data model;
- changes persistence, backup, or restore strategy;
- changes import/export compatibility;
- introduces or removes a major dependency;
- changes Gradle-module boundaries;
- creates a long-term platform constraint.

## Scope discipline

When unrelated problems are found:

1. create or update the relevant Issue;
2. link it from the pull request when useful;
3. do not expand the current pull request unless required for correctness.

## Security and privacy

Do not commit:

- signing keys;
- credentials or tokens;
- personal workout exports;
- local paths or generated source dumps;
- production user data;
- private logs containing user content.

Use synthetic fixtures for tests.
