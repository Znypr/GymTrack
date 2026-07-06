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

`.github/labels.json` is the declarative label catalogue. The Project sync workflow can also create missing global Creator OS labels when a touched issue needs them.

Global label groups shared with Creator OS:

- `status:*` — mutually exclusive workflow states that synchronize the Project `Status` field;
- `priority:p0` through `priority:p3` — global priority buckets;
- `rank:NNN` — optional explicit execution rank;
- `area:*` — high-level operating area;
- `type:*` — work type;
- `energy:*` and `confidence:*` — lightweight planning metadata.

GymTrack may keep implementation-specific labels such as `area:editor`, `area:data`, `risk:*` and `flag:*`, but global Project sorting should use Project fields rather than relying on label text.

Legacy GymTrack priority labels are still recognized by the sync workflow during transition:

```text
priority:01-now   -> P1, fallback rank 1
priority:02-next  -> P1, fallback rank 2
priority:03-soon  -> P2, fallback rank 3
priority:04-later -> P3, fallback rank 4
priority:05-icebox -> P3, fallback rank 5
```

New or touched issues should use the global labels instead:

```text
priority:p0
priority:p1
priority:p2
priority:p3
rank:001
rank:002
```

## Global GitHub Project

GymTrack does not use its own operational Project anymore.

Use the user-level Project:

```text
Creator OS - Operations
```

The global Project should show GymTrack work through the built-in `Repository` field and saved views/swimlanes, not through a separate GymTrack board.

Required global Project fields:

- `Status`
- `Area`
- `Type`
- `Priority`
- `Priority Score`
- `Execution rank`
- `Energy`
- `Confidence`
- `Source ID`

No target-date field is part of the standard board model.

Recommended main view:

```text
Columns: Status
Swimlanes/grouping: Repository
Sort 1: Priority Score ascending
Sort 2: Execution rank ascending
```

## Project automation

The **Sync Creator OS Operations Project** workflow targets the user-owned global Project `Creator OS - Operations` / Project #2.

Required repository secret:

```text
CREATOR_OS_PROJECT_TOKEN
```

During transition, the workflow also accepts the old `GYMTRACK_PROJECT_TOKEN` secret as a fallback.

The token must be able to read/write this repository and the user-owned Project.

Canonical `Status` order:

1. Ideas
2. Planned
3. Ready
4. In Progress
5. Needs Manual Review
6. Completed

Status mapping:

| Repository state | Project `Status` |
| --- | --- |
| Closed issue or merged linked pull request | Completed |
| Open linked draft pull request | In Progress |
| Open linked ready pull request | Needs Manual Review |
| `status:completed` | Completed |
| `status:needs-manual-review` / legacy `status:needs-review` | Needs Manual Review |
| `status:in-progress` | In Progress |
| `status:ready` | Ready |
| `status:planned` | Planned |
| `status:ideas` | Ideas |
| Other open issue | Planned |

The workflow also enforces one active `status:*` label at a time. Use `flag:blocked` or `flag:decision-needed` for metadata that is not a board column.

Use manual dispatch with an issue number to backfill one issue. Leave the issue number empty to sync all open issues.

## Issue and pull-request workflow

- New issues receive `status:ideas`.
- Triage assigns type, priority, execution rank, areas, risks, flags, parent, and blockers.
- A valid but not-yet-executable issue receives `status:planned`.
- `flag:decision-needed` marks unresolved product, architecture, or scope decisions.
- `flag:blocked` requires an explicit `Blocked by #N` relationship.
- `status:ready` is applied only after Definition of Ready.
- A draft pull request represents implementation in progress.
- A ready pull request represents manual review.
- Squash merge with `Closes #N` closes the Issue and moves it to Completed.

See [`docs/TICKET_BOARD.md`](TICKET_BOARD.md) for the canonical tracking rules.

## Work-item automation

The workflow that creates branches and draft pull requests needs:

- Contents: write;
- Issues: write;
- Pull requests: write;
- repository permission allowing GitHub Actions to create pull requests, or a configured `WORK_ITEM_TOKEN`.

See [`docs/WORK_ITEM_AUTOMATION.md`](WORK_ITEM_AUTOMATION.md) for setup and recovery.

## Operating requirement

Do not maintain current queues in Markdown files. GitHub Issues contain the work, the global Project displays it, and pull requests contain implementation and validation evidence.
