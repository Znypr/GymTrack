# Architecture Decision Records

Architecture Decision Records document consequential technical choices and their trade-offs.

## When to create an ADR

Create an ADR when a change:

- changes the canonical data model;
- changes persistence, migration, backup, or restore strategy;
- changes import/export compatibility;
- introduces or removes a major dependency;
- changes Gradle-module boundaries;
- changes the offline-first model;
- creates a long-term platform constraint.

Routine implementation details do not require an ADR.

## Naming

```text
ADR-0001-canonical-workout-model.md
ADR-0002-single-gradle-module.md
ADR-0003-structured-storage-note-like-editor.md
```

Numbers are sequential and are never reused.

## Template

```markdown
# ADR-XXXX: Decision title

- Status: Proposed | Accepted | Superseded | Rejected
- Date: YYYY-MM-DD
- Related issue: #
- Supersedes: optional

## Context

What problem or constraint requires a decision?

## Decision drivers

- ...

## Options considered

### Option A

Benefits, costs, and risks.

### Option B

Benefits, costs, and risks.

## Decision

What was selected and why?

## Consequences

### Positive

- ...

### Negative

- ...

### Follow-up

- ...
```

## Process

1. Open a research spike or technical issue when evidence is incomplete.
2. Add the ADR as proposed in the implementation pull request or a dedicated decision pull request.
3. Review alternatives and migration consequences.
4. Mark the ADR accepted when the related pull request is merged.
5. Supersede rather than rewriting historical decisions when direction changes.
