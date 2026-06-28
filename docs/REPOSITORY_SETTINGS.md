# Repository Settings

Some GitHub controls cannot be enforced through tracked source alone. Apply these settings after the governance pull request is merged.

## Merge settings

Recommended repository settings:

- enable squash merging;
- keep rebase merging optional;
- disable merge commits unless a specific history need exists;
- automatically delete head branches after merge;
- allow pull-request branches to be updated from the UI;
- enable auto-merge only after required checks and branch protection are active.

## Protect `master`

Create a branch protection rule or ruleset for `master`:

- require a pull request before merging;
- require status checks to pass;
- require the branch to be current before merging;
- require all conversations to be resolved;
- block force pushes;
- block branch deletion;
- restrict direct pushes;
- include administrators where practical.

Initial required checks:

```text
Android CI / testDebugUnitTest
Android CI / lintDebug
Android CI / assembleDebug
```

Add focused required checks when their workflow becomes stable, such as Room migration tests.

For a solo-maintained project, formal approval can initially remain optional. The pull-request checklist and required checks still apply. Require at least one approval when another maintainer joins.

## Labels

The tracked `.github/labels.json` file is the declarative catalogue. Run the **Sync repository labels** workflow after merge and whenever the catalogue changes.

Label groups:

- `type:*` — nature of the work;
- `priority:*` — urgency and impact;
- `area:*` — affected product or technical area;
- `status:*` — triage, readiness, blockers, and required decisions;
- `risk:*` — migration, compatibility, performance, or privacy risk.

Every triaged issue should have one type, one priority, one or more areas, and relevant risks. Workflow status labels are mutually exclusive.

## Ticket board

Do not create or maintain a GitHub Project as a second status system.

The live ticket board is derived from:

- existing `status:*` labels;
- linked pull-request draft or ready state;
- required CI checks;
- manual validation requirements;
- issue closure after merge.

The complete column rules and queries are documented in [`docs/TICKET_BOARD.md`](TICKET_BOARD.md).

GitHub Issues are the live work-status source. Pull requests provide implementation and validation evidence. Roadmap documents define sequence and intent but do not duplicate ticket state.

## Issue workflow

- New issues receive `status:needs-triage`.
- Triage assigns type, priority, area, and relevant risks.
- A triaged issue with no workflow status is Backlog.
- `status:needs-decision` identifies unresolved product or architecture decisions.
- `status:blocked` identifies an explicit dependency that prevents progress.
- `status:ready` is applied only after Definition of Ready is satisfied.
- Applying `status:ready` may trigger branch and draft-pull-request creation.
- Once a draft pull request exists, remove `status:ready`; the issue is In progress.
- Ready-for-review represents In review.
- Green checks with remaining runtime confirmation represent Validation.
- Squash merge with `Closes #N` closes the issue and represents Done.

## Operating requirement

A ticket must not exist in both a GitHub Project column and the label-driven board. Existing Project views may be deleted or ignored; they are not authoritative.
