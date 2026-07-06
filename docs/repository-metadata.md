# Shared repository metadata contract

This repository participates in the cross-repo `Creator OS - Operations` work system.

The local source of truth for repository metadata is:

```text
.github/repository-metadata.json
```

All registered repositories should keep this contract identical unless a change is intentionally rolled out to every repo in the same governance pass.

## Registered repositories

- `Znypr/creator-os`
- `Znypr/GymTrack`
- `Znypr/thumbnail-pipeline`

## Standard Project fields

The global Project uses these fields with the same values across repos:

| Field | Values |
|---|---|
| Status | Ideas, Planned, Ready, In Progress, Needs Manual Review, Completed |
| Area | Systems, Content, Thumbnail, App, Fitness, Health, Nutrition, Finance, Admin, Routine, Research, Executive, Cross-area |
| Type | Action, Review, Decision, Initiative, Alert, Bug, Feature, Refactor, Chore, Docs, Spike |
| Priority | P0, P1, P2, P3 |
| Energy | Low, Medium, High |
| Confidence | Low, Medium, High |
| Priority Score | 0 for P0, 1 for P1, 2 for P2, 3 for P3 |
| Execution rank | Lower number executes first, mirrored from `rank:NNN` labels |
| Source ID | Legacy ID or originating module/event |

## Standard issue information

Every non-trivial issue should include enough information to sort, resume, and review:

- Outcome
- Context
- Scope
- Non-scope
- Acceptance criteria
- Validation
- Intended tracking

Required metadata:

- assignee
- one `status:*` label
- one `type:*` label
- one `priority:*` label
- one or more `area:*` labels
- `energy:*` when useful for workload planning
- `confidence:*` when uncertainty affects scheduling
- milestone when the issue advances a finite checkpoint
- linked pull request through `Closes #N` when implementation completes the issue

## Standard milestones

Use the same milestone names in every registered repo:

| Milestone | Meaning |
|---|---|
| M0 Governance Baseline | Shared labels, colors, Project fields, issue metadata, sync workflows, and milestone rules are consistent across repos. |
| M1 Safe Foundations | Core data, repository, workflow, backup, migration, and deterministic execution paths are safe enough for normal development. |
| M2 First Production Loop | Each repo has one complete useful production loop working end to end in its domain. |
| M3 Automation Hardening | Automation, validation, quality gates, and recovery workflows reduce manual supervision without hiding risk. |
| M4 Beta Readiness | Release, privacy, permissions, documentation, support, and operational recovery are ready for broader external use. |

## Applying metadata

The workflow in `.github/workflows/*metadata*.yml` reads `.github/repository-metadata.json` and creates or updates labels and milestones. It can be run manually and also runs when the metadata contract changes on the default branch.
