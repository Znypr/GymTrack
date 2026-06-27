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

For a solo-maintained project, formal approval can initially remain optional. The pull-request checklist and required checks still apply. Require at least one approval when another maintainer joins.

## Labels

The tracked `.github/labels.json` file is the declarative catalogue. Run the **Sync repository labels** workflow after merge and whenever the catalogue changes.

Label groups:

- `type:*` — nature of the work;
- `priority:*` — urgency and impact;
- `area:*` — affected product or technical area;
- `status:*` — triage and readiness states;
- `risk:*` — migration, compatibility, or performance risk.

## GitHub Project

Create one project named **GymTrack Development**.

Recommended fields:

| Field | Values |
|---|---|
| Status | Inbox, Backlog, Ready, In Progress, In Review, Validation, Done |
| Priority | P0, P1, P2, P3 |
| Type | Bug, Feature, Refactor, Chore, Documentation, Research |
| Area | Editor, Workouts, Data, Parser, Stats, Timer, Import/Export, Settings, Build |
| Size | XS, S, M, L, XL |
| Target | Stabilization, Architecture, Beta, v1.0, Later |
| Risk | Low, Medium, High |
| Release | Optional milestone/version |

Recommended views:

1. **Current** — Ready through Validation;
2. **Roadmap** — grouped by Target;
3. **Bugs** — Type = Bug;
4. **Architecture** — refactors and Data/Parser/Build work;
5. **Backlog** — unscheduled items;
6. **Recently completed** — Done in the last 30 days.

GitHub Issues and this project are the live work-status source. Do not duplicate ticket status manually in roadmap documents.

## Issue workflow

- New issues begin in Inbox or Backlog.
- Triage adds priority, type, area, size, target, and risk.
- `status:ready` is applied only after Definition of Ready is satisfied.
- Applying `status:ready` triggers branch and draft-pull-request creation.
- Pull-request creation represents In Progress.
- Ready-for-review represents In Review.
- Passing checks plus manual validation represents Validation.
- Squash merge closes the issue and represents Done.

Project automation can be added after the project exists.
