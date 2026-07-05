# Work tracking

GymTrack uses GitHub Issues as the canonical work records. The GitHub Project is a visual view over those issues, not a second tracker.

## Where information lives

| Information | Canonical location |
|---|---|
| Problem, outcome, scope, acceptance criteria | GitHub Issue |
| Priority, type, area, risk | Issue labels |
| Parent, child, and blocker relationships | Issue body and linked issues |
| Visual planning and filtering | GitHub Project view |
| Implementation, review, checks, validation | Pull request |
| Long-term product sequence | `docs/ROADMAP.md` |
| Architecture and durable decisions | `docs/ARCHITECTURE.md` and ADRs |

Do not copy the current queue, completed tickets, or dependency lists into static Markdown files.

## Issue contract

Every implementation issue should contain:

- the concrete problem or user need;
- the intended observable outcome;
- included and excluded scope;
- testable acceptance criteria;
- parent issue and blockers, when applicable;
- validation and rollback expectations;
- data, compatibility, performance, privacy, or release risks.

The issue body is the current contract. Update it when scope or dependencies change instead of creating a separate status document. Comments should record decisions, evidence, and history rather than restating the issue.

## Classification

Every triaged issue should have:

- exactly one `type:*` label;
- exactly one `priority:*` label;
- one or more `area:*` labels;
- relevant `risk:*` labels;
- at most one workflow label: `status:needs-triage`, `status:ready`, `status:validation`, `status:blocked`, or `status:needs-decision`.

Labels remain the canonical classification metadata. Project views may display and filter those labels without copying them into separate custom fields.

## Project view

The Project should contain the issue itself, never a duplicate draft item describing the same work.

Recommended views:

- **Board:** grouped by Status for active work;
- **Backlog:** table filtered to open issues without active implementation;
- **Priority:** grouped or sorted by `priority:*` labels;
- **Area:** filtered by `area:*` labels;
- **Recently done:** closed issues and merged pull requests.

Canonical board columns:

1. Triage
2. Backlog
3. Ready
4. In Progress
5. In Review
6. Validation
7. Blocked
8. Needs decision
9. Done

Project Status is a visual projection of issue and pull-request state. Automation should add issues, create missing canonical Status options, and synchronize status. Manual card movement is fallback behavior, not the normal workflow.

## Lifecycle

1. New issue: `status:needs-triage`.
2. Triaged backlog: classification labels present, no workflow label.
3. Decision required: `status:needs-decision`.
4. Blocked: `status:blocked` with `Blocked by #...` in the issue body.
5. Ready: `status:ready` after the Definition of Ready is satisfied.
6. In progress: linked draft pull request exists; remove `status:ready`.
7. In review: linked pull request is ready for review.
8. Validation: automated checks pass and only runtime or manual confirmation remains; apply `status:validation`.
9. Done: pull request merges with `Closes #N`, closing the issue.

Explicit workflow labels take precedence over pull-request state. For example, an issue with a draft pull request and `status:validation` appears in Validation rather than In Progress.

The Project view may show these states, but the issue, pull request, and checks determine the truth.

## Parent and child issues

Use a parent issue only for an outcome that requires multiple independently reviewable changes.

- The parent contains the shared outcome and a task list of child issues.
- Each child issue has one reviewable scope and one principal pull request.
- Child issues link back to the parent.
- The parent closes when its child outcomes and parent-level acceptance criteria are complete.
- Do not duplicate child details in roadmap or status files.

## Dependencies

Use `status:blocked` only when work cannot correctly begin.

The issue body should state:

```text
Blocked by #123
```

Remove the label and update the body when the dependency is resolved. Completed dependency history can remain in comments or Git history; it does not need to stay in the active dependency section.

## Validation

Use `status:validation` only after implementation and automated checks are complete and the remaining work is runtime, device, emulator, workflow-UI, or other manual confirmation.

The validation ticket or parent issue should list the exact test matrix and required evidence. Remove the label when validation fails and implementation resumes, or close the issue when validation passes and the associated pull request merges.

## Operating rules

- One real work item equals one GitHub Issue.
- One Project card points to that issue.
- One pull request has one principal issue.
- The roadmap contains outcomes, not ticket state.
- Architecture documents contain system design, not the active implementation queue.
- Static status snapshots are not maintained.
