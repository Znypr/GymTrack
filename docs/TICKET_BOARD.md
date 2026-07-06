# Work tracking

GymTrack uses GitHub Issues as the canonical work records. The GitHub Project is the visual workflow board.

## Board columns

Use the Project `Status` field as the workflow field. The board view must be grouped by `Status`.

| Status | Meaning |
|---|---|
| Ideas | Raw ideas, unclear bugs, unclassified work, or decision-needed items. |
| Planned | Valid work that belongs in the project but is not executable yet. |
| Ready | Specified work that can start without another planning pass. |
| In Progress | Active implementation. Usually represented by a linked draft pull request. |
| Needs Manual Review | Work that needs manual review, runtime validation, or final user confirmation. |
| Completed | Merged, closed, or otherwise finished work. |

## Migration

| Old column / label | New status or label |
|---|---|
| Triage | Ideas |
| Needs decision | Ideas plus `flag:decision-needed` |
| Backlog | Planned |
| Doing | In Progress |
| In Review | Needs Manual Review |
| Validation | Needs Manual Review |
| Done | Completed |
| `status:blocked` | `flag:blocked` while keeping the best matching workflow status |

Blocked work is not a board column. Use `flag:blocked` and keep the ticket in the best matching workflow status.

## Labels

Use labels for ticket metadata and workflow transition commands.

Metadata labels:

- `type:*` — kind of work.
- `priority:p0` through `priority:p3` — importance bucket. P0 is urgent or critical; P3 is low-priority or optional.
- `rank:NNN` — optional explicit order inside a priority bucket.
- `area:*` — affected product, technical, or governance area.
- `risk:*` — migration, compatibility, performance, privacy, or release risk.
- `flag:*` — cross-cutting state such as blockers or pending decisions.

Workflow labels are mutually exclusive and mirror the Project `Status` field:

- `status:ideas`
- `status:planned`
- `status:ready`
- `status:in-progress`
- `status:needs-manual-review`
- `status:completed`

## Minimum metadata

The goal is a professional work record, not administrative noise. Fill the sidebar and body fields that help prioritization, review, and later resumption.

For every non-trivial issue:

| Field | Rule |
|---|---|
| Assignee | Assign the current owner. Use `Znypr` for solo-owned GymTrack work unless another owner is explicit. |
| Labels | Include exactly one `type:*`, one workflow `status:*`, one `priority:*`, and at least one `area:*`. Add `rank:NNN`, `risk:*`, or `flag:*` only when useful. |
| Project | Add the issue to `Creator OS - Operations`; do not create duplicate operational cards elsewhere. |
| Milestone | Set a milestone only when the issue advances a roadmap phase, release checkpoint, or charter-level outcome. Do not use milestones for status, priority, or permanent areas. |
| Development | Link implementation through a branch and pull request; the PR must use `Closes #N`. |

Milestone choice follows the charter and roadmap before local convenience:

- safety and migration work maps to Phase 1;
- canonical workout model work maps to Phase 2;
- editor, autosave, and timer reliability map to Phase 3;
- statistics and product-quality work map to Phase 4;
- signing, release, privacy, and beta-readiness work map to Phase 5.

For pull requests:

- keep the linked issue as the main Project card unless the pull request itself needs independent tracking;
- assign the implementer;
- request reviewers only after the draft becomes ready for review;
- copy the linked issue milestone when the PR represents that milestone work;
- add labels only when they clarify review scope, risk, or release handling.

## Automation rules

- New issues go to Ideas and receive `status:ideas`.
- Adding `status:planned` moves the ticket to Planned.
- Adding `status:ready` moves the ticket to Ready and can start the branch + draft-PR workflow.
- A linked draft pull request moves the ticket to In Progress and applies `status:in-progress`.
- A linked ready-for-review pull request moves the ticket to Needs Manual Review and applies `status:needs-manual-review`.
- Closed issues or merged linked pull requests move the ticket to Completed and apply `status:completed`.
- Full reconciliation runs include open and closed issues so the Completed column is backfilled instead of only showing issues closed after automation was installed.
- Adding one workflow status label removes the other workflow status labels.
- The board should not be manually dragged except as recovery. Change the issue status label or pull-request state instead.

## Operating rules

- One real work item equals one GitHub Issue.
- One pull request has one principal issue.
- Scope changes update the issue before implementation continues.
- Discovered work becomes a linked follow-up issue.
- Paused work receives a resumable checkpoint.
- The roadmap contains outcomes, not ticket state.
- Architecture docs contain system design, not the active queue.
- Missing metadata is fixed during triage before work is marked Ready.
