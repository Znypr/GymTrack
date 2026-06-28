# Contributing to GymTrack

GymTrack uses issues, short-lived branches, pull requests, automated checks, and documented decisions. Direct feature work on `master` is not part of the normal workflow.

## Sources of truth

| Information | Source |
|---|---|
| Product direction | `docs/PROJECT_CHARTER.md` |
| Current and target architecture | `docs/ARCHITECTURE.md` |
| Sequencing and priorities | `docs/ROADMAP.md` |
| Live work status | GitHub Issues, labels, linked pull requests, and CI |
| Ticket-board rules | `docs/TICKET_BOARD.md` |
| Implementation and validation | Pull requests |
| Consequential technical decisions | ADRs under `docs/decisions/` |

A GitHub Project is not required. Do not maintain a second manual status system.

## Standard workflow

1. Create or triage an issue.
2. Define scope, non-scope, acceptance criteria, risks, and validation.
3. Add `status:ready` only when the issue satisfies the Definition of Ready.
4. The automation creates a branch and draft pull request, or the branch and PR are created manually if automation is unavailable.
5. Remove `status:ready` once a draft pull request exists; PR state now determines the board column.
6. Implement only the linked issue's scope.
7. Add or update tests and documentation.
8. Complete the pull-request checklist.
9. Mark the pull request ready after required checks pass.
10. Validate manually on an emulator or device when behavior changes.
11. Squash merge and delete the branch.

See [`docs/TICKET_BOARD.md`](docs/TICKET_BOARD.md) for the exact Inbox, Backlog, Ready, In progress, In review, Validation, Blocked, and Done rules.

## Definition of Ready

An issue is ready when:

- the problem is understandable;
- the user or technical outcome is stated;
- scope and non-scope are explicit;
- acceptance criteria are testable;
- dependencies are known;
- priority and affected area are assigned;
- data, migration, compatibility, and performance risks are identified;
- the issue is small enough for one pull request or has been split.

Do not apply `status:ready` to an idea that still requires product or architecture decisions. Use a research spike first.

## Branch naming

The issue automation uses:

```text
feat/123-workout-template
fix/148-import-duplicate-crash
refactor/162-normalized-workout-schema
docs/175-architecture-overview
chore/181-clean-dependencies
spike/190-timer-restoration-options
```

Branches are created from the latest protected `master`.

## Pull-request rules

- One principal issue per pull request.
- Keep the pull request in draft while implementation is incomplete.
- Avoid unrelated cleanup.
- Keep migrations and behavioral changes reviewable.
- Explain why the approach was chosen, not only what changed.
- Link the issue using `Closes #<number>`.
- Include screenshots or video for visible UI changes.
- Record emulator or device validation for runtime changes.
- Do not merge when required checks did not run.
- Prefer squash merge.
- Delete merged branches.

## Required checks

The initial required commands are:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Additional checks are required when relevant:

- Room migration tests;
- parser regression tests;
- import/export round-trip tests;
- Compose UI tests;
- managed-device smoke tests;
- release build validation.

A note saying that tests could not run is not validation. Resolve the environment problem before merge.

## Definition of Done

A change is done only when:

- acceptance criteria are complete;
- required CI checks pass;
- relevant behavior is tested;
- database changes include explicit migrations and migration tests;
- manual validation is recorded where relevant;
- visible UI changes include evidence;
- error, loading, and empty states are handled;
- accessibility impact is considered;
- documentation is updated;
- no new high-severity lint issue is introduced;
- temporary code and debug output are removed;
- the pull request is linked to the issue;
- known limitations are documented or tracked;
- the issue closes on merge.

## Commit messages

Use Conventional Commit-style messages:

```text
feat(editor): add typed set rows
fix(database): preserve workouts during v8-to-v9 migration
refactor(parser): return immutable domain results
test(import): cover quoted multiline learnings
docs(architecture): document canonical workout model
chore(build): remove duplicate Compose dependencies
```

Avoid messages such as `update`, `fixed bugs`, or `icons`.

## Testing expectations

### Pure logic

Parsing, calculations, mappings, validation, and date logic should be pure Kotlin where possible and covered by unit tests.

### Database

Every schema change requires:

- an explicit Room migration;
- a migration test from every supported prior version;
- representative data assertions;
- confirmation that data is not dropped or silently changed.

### UI

Use Compose tests for critical interactions, state restoration, and regression-prone behavior. Visual evidence supplements tests; it does not replace them.

### Import and export

Test:

- round trips;
- quoted commas and multiline fields;
- legacy formats;
- duplicates;
- malformed files;
- partial failure reporting;
- version compatibility.

## Architecture changes

Create an Architecture Decision Record when a change:

- changes the canonical data model;
- changes persistence or backup strategy;
- changes import/export compatibility;
- introduces or removes a major dependency;
- changes Gradle-module boundaries;
- creates a long-term platform constraint.

An ADR must describe context, options, decision, consequences, and status.

## Scope discipline

When unrelated problems are found:

1. record them as a new issue;
2. link the issue from the pull request when useful;
3. do not expand the current pull request unless the new work is required for correctness.

This keeps review, testing, rollback, and history understandable.

## Security and privacy

Do not commit:

- signing keys;
- credentials or tokens;
- personal workout exports;
- local paths or generated source dumps;
- production user data;
- private logs containing user content.

Use synthetic fixtures for tests.
