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
- `priority:01-now` through `priority:05-icebox` — execution rank. The numeric prefix is intentional so labels sort in execution order.
- `area:*` — affected product or technical area.
- `risk:*` — migration, compatibility, performance, privacy, or release risk.
- `flag:*` — cross-cutting state such as blockers or pending decisions.

Workflow labels are mutually exclusive and mirror the Project `Status` field:

- `status:ideas`
- `status:planned`
- `status:ready`
- `status:in-progress`
- `status:needs-manual-review`
- `status:completed`

## Automation rules

- New issues go to Ideas and receive `status:ideas`.
- Adding `status:planned` moves the ticket to Planned.
- Adding `status:ready` moves the ticket to Ready and can start the branch + draft-PR workflow.
- A linked draft pull request moves the ticket to In Progress and applies `status:in-progress`.
- A linked ready-for-review pull request moves the ticket to Needs Manual Review and applies `status:needs-manual-review`.
- Closed issues or merged linked pull requests move the ticket to Completed and apply `status:completed`.
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
