# Ticket Board

GitHub Issues are the only source of truth for GymTrack work status. A GitHub Project board is not required and must not be maintained as a second status system.

The board is derived from existing issue labels, linked pull-request state, CI state, and issue closure. This keeps status visible, queryable, and maintainable through the repository API.

## Column rules

Each open issue belongs to exactly one column. Evaluate the rules from top to bottom.

| Column | Rule |
|---|---|
| Inbox | Open issue with `status:needs-triage` |
| Needs decision | Open issue with `status:needs-decision` |
| Blocked | Open issue with `status:blocked` |
| Validation | Linked open PR has all required checks green but manual validation remains |
| In review | Linked open PR is ready for review and validation is not the remaining step |
| In progress | Linked open PR is a draft |
| Ready | Open issue has `status:ready` and no open linked PR |
| Backlog | Triaged open issue has no workflow status label and no open linked PR |
| Done | Issue is closed, normally by a merged PR using `Closes #<number>` |

`status:ready` must be removed after implementation starts. Draft/ready PR state then represents progress and review without duplicating status labels.

## Classification labels

Every triaged issue should have:

- one `type:*` label;
- one `priority:*` label;
- one or more `area:*` labels;
- relevant `risk:*` labels;
- at most one workflow label from `status:needs-triage`, `status:ready`, `status:blocked`, or `status:needs-decision`.

No workflow label means Backlog unless a linked pull request moves the issue to In progress, In review, or Validation.

## Movement rules

1. New issue: add `status:needs-triage`.
2. Triaged but unscheduled: remove workflow labels; the issue is Backlog.
3. Decision required: use `status:needs-decision`.
4. Dependency prevents work: use `status:blocked` and document the dependency.
5. Definition of Ready satisfied: use `status:ready`.
6. Draft PR opened: remove `status:ready`; the issue is In progress.
7. PR marked ready: the issue is In review.
8. Required checks pass and only runtime/manual confirmation remains: the issue is Validation.
9. PR merged with `Closes #N`: the issue closes and is Done.
10. PR closed without merge: return the issue to Ready, Backlog, Blocked, or Needs decision based on the reason.

## Dependency rules

Use `status:blocked` only when work cannot correctly begin. The issue body or latest status comment must name the dependency.

Current architecture dependencies:

- #123 is blocked by #119 and #120.
- #125 is blocked by #120 and #123.
- #126 is blocked by #120 and #123.

Once all named dependencies are merged or resolved, remove `status:blocked` and return the issue to Backlog or Ready.

## Current-board queries

Use GitHub issue searches for the label-defined columns:

- Inbox: `is:issue is:open label:"status:needs-triage"`
- Needs decision: `is:issue is:open label:"status:needs-decision"`
- Blocked: `is:issue is:open label:"status:blocked"`
- Ready: `is:issue is:open label:"status:ready"`
- Backlog: `is:issue is:open -label:"status:needs-triage" -label:"status:needs-decision" -label:"status:blocked" -label:"status:ready"`
- Done: `is:issue is:closed`

In progress, In review, and Validation are derived from linked pull requests and checks. They are reported in issue comments and the current-status summary when needed.

## Current-status summary format

When reporting the board, use this order:

```text
Validation
In review
In progress
Ready
Needs decision
Blocked
Backlog
Inbox
Recently done
```

For every active ticket include issue number, title, priority, linked PR when present, and the specific blocker or remaining validation step.

## Operating principle

Do not copy ticket state into roadmap documents, checklists, or a GitHub Project. Roadmaps define sequence and intent. Issues, labels, pull requests, checks, and closure define live status.
