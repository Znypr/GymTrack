# Repository settings

Some GitHub controls cannot be enforced through tracked source alone. Apply and review these settings in the repository UI.

## Merge settings

Recommended settings:

- enable squash merging;
- disable merge commits unless a specific history need exists;
- automatically delete head branches after merge;
- allow pull-request branches to be updated from the UI;
- enable auto-merge only after required checks and branch protection are active.

## Protect `master`

Create a branch protection rule or ruleset that:

- requires a pull request before merging;
- requires status checks to pass;
- requires the branch to be current before merging;
- requires conversations to be resolved;
- blocks force pushes and branch deletion;
- restricts direct pushes;
- includes administrators where practical.

Required checks:

```text
Android CI / testDebugUnitTest
Android CI / lintDebug
Android CI / assembleDebug
```

Add focused required checks when their workflows are stable, such as Room migration or managed-device tests.

For a solo-maintained project, formal approval can initially remain optional. Require review approval when another maintainer joins.

## Labels

`.github/labels.json` is the declarative label catalogue. Run the **Sync repository labels** workflow whenever it changes.

Label groups:

- `type:*` — nature of the work;
- `priority:*` — urgency and impact;
- `area:*` — affected product or technical area;
- `status:*` — triage, readiness, blockers, and required decisions;
- `risk:*` — migration, compatibility, performance, privacy, or release risk.

Every triaged issue should have one type, one priority, one or more areas, and relevant risks. Workflow status labels are mutually exclusive.

## GitHub Project

The Project is a visual planning surface over GitHub Issues.

Configuration rules:

- add the actual Issue, not a duplicate draft item;
- keep one Project item per Issue;
- use a board grouped by Status for active work;
- use table views for backlog, priority, and area filters;
- display issue labels instead of duplicating type, priority, and area into separate fields unless a field has a demonstrated Project-specific purpose;
- automate issue addition and Status synchronization where possible;
- treat manual movement as fallback behavior only.

The Project does not replace the Issue body. Scope, acceptance criteria, blockers, and validation remain in the Issue.

## Issue and pull-request workflow

- New issues receive `status:needs-triage`.
- Triage assigns type, priority, areas, risks, parent, and blockers.
- A triaged issue without a workflow label is backlog.
- `status:needs-decision` marks unresolved decisions.
- `status:blocked` requires an explicit `Blocked by #N` relationship.
- `status:ready` is applied only after Definition of Ready.
- A draft pull request represents implementation in progress.
- A ready pull request represents review.
- Green checks with remaining runtime confirmation represent validation.
- Squash merge with `Closes #N` closes the Issue.

See [`docs/TICKET_BOARD.md`](TICKET_BOARD.md) for the canonical tracking rules.

## Work-item automation

The workflow that creates branches and draft pull requests needs:

- Contents: write;
- Issues: write;
- Pull requests: write;
- repository permission allowing GitHub Actions to create pull requests, or a configured `WORK_ITEM_TOKEN`.

See [`docs/WORK_ITEM_AUTOMATION.md`](WORK_ITEM_AUTOMATION.md) for setup and recovery.

## Operating requirement

Do not maintain current queues in Markdown files. GitHub Issues contain the work, the Project displays it, and pull requests contain implementation and validation evidence.
