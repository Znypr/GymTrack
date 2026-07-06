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

An issue that does not satisfy these conditions remains in Inbox, Backlog, or Blocked.

## Classification

Every triaged issue should have:

- exactly one `type:*` label;
- exactly one `priority:*` label;
- one or more `area:*` labels;
- relevant `risk:*` labels when needed;
- at most one workflow label: `status:needs-triage`, `status:ready`, or `status:blocked`.

Labels remain the canonical classification metadata. Project views may display and filter those labels without copying them into separate custom fields.

Do not create workflow labels for normal implementation phases such as review, validation, or decision required. Those states belong in the issue body, pull request, checks, or latest checkpoint.

## Project board

The Project should contain the issue itself, never a duplicate draft item describing the same work.

Keep the board intentionally small. It should answer one question: what should happen next?

Canonical board columns:

1. **Inbox** — new, unclear, unclassified, or decision-needed work.
2. **Backlog** — valid work that is not ready or active.
3. **Ready** — specified work that can start without another planning pass.
4. **Doing** — active implementation, review, or validation.
5. **Blocked** — work that cannot continue until a real dependency is resolved.
6. **Done** — completed work.

Do not maintain separate permanent columns for `In Progress`, `In Review`, `Validation`, or `Needs decision`.

Use this mapping when simplifying an existing board:

| Current column | New column |
|---|---|
| Triage | Inbox |
| Needs decision | Inbox |
| Backlog | Backlog |
| Ready | Ready |
| In Progress | Doing |
| In Review | Doing |
| Validation | Doing |
| Blocked | Blocked |
| Done | Done |

Review state is visible from the pull request. Validation state is visible from checks, acceptance criteria, and checkpoint comments. Decision state is visible from the issue body and latest checkpoint.

Project Status is a visual projection of issue and pull-request state. Automation should add issues and synchronize the small column set. Manual card movement is fallback behavior, not the normal workflow.

## Project views

The default board is the main view. Additional permanent views should be rare.

Recommended persistent views:

- **Board:** the six-column workflow above.
- **Recently done:** closed issues and merged pull requests for recent review, cleanup, and release-note checks.

Do not maintain separate permanent Project views for priority, area, bug lists, documentation work, automation work, PR-only status, roadmap slices, or all completed work. Use issue labels, GitHub filters, or temporary ad-hoc views for those questions.

A new persistent Project view requires its own issue and should be approved only when it meets all of these conditions:

- it supports a repeated planning or execution decision;
- it cannot be answered cleanly by filtering labels in an existing view;
- it does not duplicate issue labels, pull-request state, or static documentation;
- it has a named owner or maintenance reason.

Temporary views may be created for audits, releases, or cleanup passes, but they should be deleted when the task is complete.

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

**Current state:** Doing | Blocked | Done

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

Keep the issue in Doing while the pull request is under review. The pull request itself records review state.

Mark the pull request ready for review only when implementation and automated checks satisfy the issue contract.

### 7. Validate

Keep the issue in Doing while runtime, device, emulator, workflow-UI, migration-fixture, or other manual confirmation remains.

Record each result against the acceptance criteria. A failed validation stays in Doing and updates the next exact action.

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

1. New issue: Inbox, with `status:needs-triage` when classification is missing.
2. Triaged backlog: Backlog, classification labels present, no workflow label.
3. Ready: Ready, with `status:ready` after the Definition of Ready is satisfied.
4. Doing: active branch, draft pull request, pull request review, or remaining validation.
5. Blocked: Blocked, with `status:blocked` and `Blocked by #...` in the issue body.
6. Done: pull request merges with `Closes #N`, closing the issue.

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

Manual validation is not a separate board state. It remains part of Doing until the acceptance criteria and required evidence are complete.

The issue or parent issue should list the exact test matrix and required evidence. If validation fails, update the next exact action and keep the issue in Doing. If validation passes, record the evidence and complete the associated pull request or issue.

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
