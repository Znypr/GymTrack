# Work tracking

GymTrack uses GitHub Issues as the canonical work records. The GitHub Project is only a visual workflow board.

## Core rules

- No substantive work starts without a GitHub Issue.
- One real work item equals one issue.
- One pull request has one principal issue.
- Scope changes update the issue before implementation continues.
- Discovered work becomes a linked follow-up issue.
- Paused work receives a resumable checkpoint.
- The roadmap contains outcomes, not ticket state.
- Architecture docs contain system design, not the active queue.

## Where information lives

| Information | Canonical location |
|---|---|
| Problem, outcome, scope, acceptance criteria | GitHub Issue |
| Priority, type, area, risk | Issue labels |
| Parent, child, and dependency relationships | Issue body and linked issues |
| Current state and next action | Latest issue or PR checkpoint |
| Implementation, review, checks, validation | Pull request |
| Visual workflow | GitHub Project |

## Issue contract

Every implementation issue should state:

- the concrete problem or user need;
- the intended observable outcome;
- included and excluded scope;
- testable acceptance criteria;
- parent issue, roadmap outcome, or standalone status;
- dependencies and blockers when applicable;
- validation expectations.

## Board columns

Use five board columns:

| Column | Meaning |
|---|---|
| **Ideas** | Raw ideas, unclear bugs, unclassified work, or decision-needed items. |
| **Planned** | Valid work that belongs in the project but is not executable yet. |
| **Ready** | Specified work that can start without another planning pass. |
| **In Progress** | Active implementation, pull-request review, or validation. |
| **Completed** | Merged, closed, or otherwise finished work. |

Do not maintain separate permanent columns for `Triage`, `Backlog`, `In Review`, `Validation`, `Blocked`, `Needs decision`, or `Done`.

## Existing-column migration

| Current column | New column |
|---|---|
| Triage | Ideas |
| Needs decision | Ideas |
| Backlog | Planned |
| Ready | Ready |
| In Progress | In Progress |
| In Review | In Progress |
| Validation | In Progress |
| Blocked | Planned, Ready, or In Progress plus `status:blocked` |
| Done | Completed |

Blocked work is not a board column. Add `status:blocked`, state the dependency in the issue, and leave the card in the most accurate workflow column.

## Ready definition

Move work to `Ready` only when:

- the issue has one principal outcome;
- scope and non-goals are explicit;
- acceptance criteria are testable;
- dependencies and blockers are known;
- the next implementation action is clear enough to start.

## Labels

Every triaged issue should have:

- exactly one `type:*` label;
- exactly one `priority:*` label;
- one or more `area:*` labels;
- relevant `risk:*` labels when needed;
- at most one workflow label: `status:needs-triage`, `status:ready`, or `status:blocked`.

Review, validation, and decision state should be recorded in the issue, PR, checks, or checkpoint rather than in separate board columns.

## Execution flow

1. Select one principal issue.
2. Confirm the issue is `Ready` or record why it interrupts current work.
3. Create a branch that includes the issue number when practical.
4. Create a linked draft pull request as soon as implementation begins.
5. Keep implementation, review, and validation in `In Progress`.
6. Merge with the correct issue-closing reference after acceptance criteria and validation evidence are complete.

## Checkpoint format

Use this before pausing or switching tasks:

- Current state: Ideas, Planned, Ready, In Progress, or Completed.
- Completed work.
- Next exact action.
- Remaining acceptance criteria.
- Evidence: commits, checks, screenshots, logs, or manual results.
- Blockers or decisions.
- Working location: branch and pull request.

The next action must be specific enough that another session can resume without reconstructing prior reasoning.

## Project views

The default board is the main view. Additional permanent views should be rare.

Recommended persistent views:

- **Board:** the five-column workflow above.
- **Recently completed:** closed issues and merged pull requests for recent review, cleanup, and release-note checks.

Use labels, filters, or temporary views for priority, area, bugs, docs, automation, release audits, or roadmap slices.
