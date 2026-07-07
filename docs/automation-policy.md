# Automation policy

This repository allows automation to reduce manual supervision while keeping production, data, privacy, and release decisions human-controlled.

## Default behavior

Automation may inspect issues, pull requests, CI checks, workflow logs, and uploaded diagnostics. It may comment with diagnoses and recommended next steps.

Automation must not merge pull requests, publish releases, delete user data, rewrite production behavior, or make destructive changes without an explicit human instruction.

## Low-risk auto-fix scope

Automation may patch the current pull request branch when the failure is local to that pull request and one of these categories applies:

- missing or unused imports
- formatting or lint-only failures
- type-check failures with a mechanical fix
- test API mismatch
- test-only compile failures
- missing test fixtures or small test harness fixes
- documentation, metadata, or workflow-output fixes

## Human review required

Automation must report findings and wait when the failure touches or could hide risk in any of these areas:

- production behavior changes
- database migrations or persisted data behavior
- import, export, backup, or restore semantics
- authentication, privacy, secrets, tokens, or permissions
- emulator/device flakiness where the root cause is unclear
- performance regressions
- release, signing, deployment, publishing, or spending
- ambiguous expected behavior

## Labels

Use these labels to control automation behavior:

- `automation:watch` — automation should monitor and report on this item.
- `automation:auto-fix-ok` — low-risk fixes may be pushed to the pull request branch.
- `automation:needs-human` — automation must report but not patch.
- `automation:blocked` — automation cannot safely proceed.
- `ci:failed` — latest relevant CI run failed.
- `ci:compile` — failure is compile, import, type, or build related.
- `ci:test` — failure is test related.
- `ci:migration` — failure touches migrations or persisted data behavior.
- `ci:flaky` — failure appears intermittent or environment-dependent.

## Pull request contract

Each pull request should state whether low-risk automation fixes are allowed and identify excluded areas. If the template is left blank, automation may still diagnose and comment, but patching should stay conservative.

## CI diagnostics contract

Workflows should prefer fast, specific gates before expensive jobs and upload diagnostics that automation can parse:

- command output logs
- JUnit XML when available
- lint or type-check reports when available
- changed file context via the pull request
- clear job names that identify compile, lint, test, migration, or release failures
