# Work tracking

GymTrack uses GitHub Issues as the canonical work records. The GitHub Project is a visual view over those issues, not a second tracker.

## Non-negotiable ticket-first rule

No substantive work begins without a principal GitHub Issue.

This applies to:

- implementation and refactoring;
- bug investigation and research that may affect project decisions;
- repository, workflow, architecture, or documentation changes;
- manual validation and release checks;
- migrations, cleanup, and process changes.

A brief clarification that creates no durable project change does not require a ticket. Once work may change code, documentation, data, architecture, workflow, scope, or validation state, it must be represented by an issue before execution continues.

The issue, linked pull request, checks, and latest checkpoint must be sufficient to understand and resume the work without relying on chat history or personal memory.

## Where information lives

| Information | Canonical location |
|---|---|
| Problem, outcome, scope, acceptance criteria | GitHub Issue |
| Priority, type, area, risk | Issue labels |
| Parent, child, and blocker relationships | Issue body and linked issues |
| Current execution state and exact next action | Latest issue or pull-request checkpoint |
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
- parent issue or roadmap outcome, or an explicit statement that the work is standalone;
- blockers and dependencies, when applicable;
- validation and rollback expectations;
- data, compatibility, performance, privacy, or release risks;
- enough context to explain why the work matters within the greater project scope.

The issue body is the current contract. Update it when scope or dependencies change instead of creating a separate status document. Comments should record decisions, evidence, checkpoints, and history rather than restating the issue.

## Definition of Ready

Work may enter `status:ready` only when:

- the issue has one principal outcome;
- scope and non-goals are explicit;
- acceptance criteria are testable;
- the parent, roadmap outcome, or standalone status is stated;
- dependencies and blockers are known;
- risks and validation requirements are recorded;
- the next implementation action is clear enough to begin without another planning pass.

An issue that does not satisfy these conditions remains in Triage, Backlog, Needs decision, or Blocked.

## Classification

Every triaged issue should have:

- exactly one `type:*` label;
- exactly one `priority:*` label;
- one or more `area:*` labels;
- relevant `risk:*` labels;
- at most one workflow label: `status:needs-triage`, `status:ready`, `status:validation`, `status:blocked`, or `status:needs-decision`.

Labels remain the canonical classification metadata. Project views may display and filter those labels without copying them into separate custom fields.

## Project views

The Project should contain the issue itself, never a duplicate draft item describing the same work.

Keep the Project intentionally small. It should answer three recurring questions:

1. What is active now?
2. What is ready or waiting?
3. What was recently finished?

Persistent Project views:

- **Active board:** board grouped by Status for triage, ready work, active implementation, review, validation, blocked work, decisions, and done work.
- **Backlog table:** table of open issues that are not actively being implemented, reviewed, or validated. Sort by priority, then updated date. Use labels for type, area, and risk.
- **Recently done:** closed issues and merged pull requests for recent review, cleanup, and release-note checks.

Do not maintain separate permanent Project views for priority, area, bug lists, documentation work, automation work, PR-only status, roadmap slices, or all completed work. Use issue labels, GitHub filters, or temporary ad-hoc views for those questions.

A new persistent Project view requires its own issue and should be approved only when it meets all of these conditions:

- it supports a repeated planning or execution decision;
- it cannot be answered cleanly by filtering labels in an existing view;
- it does not duplicate issue labels, pull-request state, or static documentation;
- it has a named owner or maintenance reason.

Temporary views may be created for audits, releases, or cleanup passes, but they should be deleted when the task is complete.

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

## Deterministic execution sequence

### 1. Select

Choose one principal issue for the execution stream. Confirm that it is Ready or explicitly record why an interruption must begin immediately.

### 2. Start

Before changing the repository:

- assign the issue;
- confirm classification and relationships;
- create a branch that includes the issue number when practical;
- create a linked draft pull request as soon as implementation begins;
- remove `status:ready` once the draft pull request exists.

The pull request should name one principal issue. Use `Closes #N` when merging the pull request should complete the issue. Use `Progress toward #N` only when the issue intentionally requires more than one independently reviewable pull request.

### 3. Execute

Implement only the issue contract.

When new work is discovered:

- create a linked child or follow-up issue before acting on it;
- do not silently add unrelated scope to the current issue or pull request;
- continue the current issue only when the discovered work is required for its acceptance criteria;
- otherwise leave the new issue in the appropriate backlog state.

When the current scope changes, update the issue body and acceptance criteria before continuing.

### 4. Pause

Before stopping or switching tasks, add a durable checkpoint to the principal issue or pull request.

Use this structure:

```markdown
## Execution checkpoint

**Current state:** In progress | In review | Validation | Blocked | Needs decision

**Completed:**
- concrete completed work

**Next exact action:**
- one unambiguous action that should happen first on resume

**Remaining acceptance criteria:**
- unchecked or unresolved criteria

**Evidence:**
- commits, checks, screenshots, logs, or manual results

**Blockers or decisions:**
- blocker, owner, and required resolution; or `None`

**Working location:**
- Branch: `type/123-description`
- Pull request: #456
```

The next action must be specific enough that another session can execute it without reconstructing prior reasoning.

### 5. Resume

Resume work in this order:

1. principal issue body;
2. latest execution checkpoint;
3. linked pull request and changed files;
4. current checks, reviews, and validation evidence;
5. parent issue or roadmap context.

Conversation history may provide convenience but is never the source of truth.

### 6. Review

Mark the pull request ready for review only when implementation and automated checks satisfy the issue contract. The board then moves to In Review unless an explicit workflow label overrides it.

### 7. Validate

Apply `status:validation` only when implementation and automated checks are complete and the remaining work is runtime, device, emulator, workflow-UI, migration-fixture, or other manual confirmation.

Record each result against the acceptance criteria. A failed validation returns the issue to implementation; it does not remain marked as complete.

### 8. Complete

Merge the principal pull request with the correct issue-closing reference. Confirm that:

- acceptance criteria are complete;
- validation evidence is recorded;
- follow-up work has separate linked issues;
- the issue is closed with the correct reason;
- no active branch or pull request falsely appears to remain in progress.

## Work-in-progress rule

Each execution stream has one principal implementation issue at a time.

A second issue may be opened for discovered work, but it does not become active implementation unless:

- the current issue is completed;
- the current issue is explicitly blocked;
- or an interruption is deliberately recorded with its reason and resume checkpoint.

Small repository changes are not exempt. A short task should use a short ticket, not no ticket.

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

The Project view may show these states, but the issue, pull request, checks, and checkpoint determine the truth.

## Parent and child issues

Use a parent issue only for an outcome that requires multiple independently reviewable changes.

- The parent contains the shared outcome and a task list of child issues.
- Each child issue has one reviewable scope and one principal pull request.
- Child issues link back to the parent.
- The parent closes when its child outcomes and parent-level acceptance criteria are complete.
- Do not duplicate child details in roadmap or status files.

Every issue must state one of:

- `Parent: #123`;
- `Roadmap outcome: <named outcome>`;
- `Standalone outcome: <reason no parent is required>`.

This relationship makes the greater scope visible directly from the ticket.

## Dependencies

Use `status:blocked` only when work cannot correctly begin or continue.

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
- One execution stream has one principal active implementation issue.
- No substantive work exists only in chat.
- Scope changes update the ticket before implementation continues.
- Discovered work becomes a linked ticket.
- Paused work receives a resumable checkpoint.
- The roadmap contains outcomes, not ticket state.
- Architecture documents contain system design, not the active implementation queue.
- Static status snapshots are not maintained.
