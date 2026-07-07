# Issue creation policy

Every non-trivial issue must be created with a complete metadata matrix. This applies to manual issue creation, ChatGPT-created issues, Jarvis-created issues, and automation.

## Required metadata

- assignee
- one `status:*` label
- one `type:*` label
- one `priority:*` label
- one or more `area:*` labels
- one `energy:*` label
- one `confidence:*` label
- milestone when tied to a finite checkpoint
- linked pull request through `Closes #N` when implementation completes the issue

## Pre-create checklist

```text
status:
type:
priority:
area:
energy:
confidence:
assignee:
milestone:
parent_or_linked_issue:
validation:
```

Issue automation should not create an issue until the required fields are resolved. If uncertainty exists, represent it explicitly:

- `status:ideas` for raw or unclear work
- `status:planned` for valid work that is not executable yet
- `type:spike` for discovery work
- `confidence:low` when uncertainty affects scheduling
- `flag:decision-needed` when a decision blocks execution

## Examples

Executable action:

```text
area:content
type:action
priority:p1
energy:medium
confidence:medium
status:ready
```

Research or design:

```text
area:content
type:spike
priority:p2
energy:medium
confidence:medium
status:planned
```

## Enforcement

The `lint-issue-metadata` workflow checks issue metadata on issue create and metadata changes.

If metadata is missing, it adds `flag:metadata-invalid`, adds `status:needs-manual-review`, removes other status labels, and comments with the missing fields. When metadata becomes valid, it removes `flag:metadata-invalid`.
