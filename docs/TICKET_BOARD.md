# Work tracking

GymTrack uses GitHub Issues as the canonical work records. The GitHub Project is only a visual workflow board.

## Board columns

Use six board columns:

| Column | Meaning |
|---|---|
| Ideas | Raw ideas, unclear bugs, unclassified work, or decision-needed items. |
| Planned | Valid work that belongs in the project but is not executable yet. |
| Ready | Specified work that can start without another planning pass. |
| In Progress | Active implementation. |
| Needs Review | Work that needs manual review, manual checking, or final user confirmation. |
| Completed | Merged, closed, or otherwise finished work. |

## Migration

| Old column | New column |
|---|---|
| Triage | Ideas |
| Needs decision | Ideas |
| Backlog | Planned |
| In Review | Needs Review |
| Validation | Needs Review |
| Done | Completed |

Blocked work is not a board column. Use status:blocked and keep the card in the best matching workflow column.

## Labels

Use type, priority, area, and risk labels for classification.

Use workflow labels only when they add useful information:

- status:needs-triage
- status:ready
- status:needs-review
- status:blocked

## Automation rules

- New or unclear issues go to Ideas.
- status:ready goes to Ready.
- Draft pull requests go to In Progress.
- Ready-for-review pull requests go to Needs Review.
- status:needs-review goes to Needs Review.
- Closed or merged work goes to Completed.

## Operating rules

- One real work item equals one GitHub Issue.
- One pull request has one principal issue.
- Scope changes update the issue before implementation continues.
- Discovered work becomes a linked follow-up issue.
- Paused work receives a resumable checkpoint.
- The roadmap contains outcomes, not ticket state.
- Architecture docs contain system design, not the active queue.
