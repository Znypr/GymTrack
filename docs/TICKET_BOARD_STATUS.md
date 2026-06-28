# Initial derived ticket-board state

This snapshot records the issue classification when the label-driven ticket board was introduced. Live status remains derived from current issue labels, linked pull requests, checks, and closure.

## Validation

- #127 — Replace the workout timer short-service implementation — PR #128; automated checks pass; Android 14+ manual validation remains.

## In review

- #119 — Replace destructive Room migration fallback and add migration tests — PR #131.
- #129 — Fix status-ready work-item automation — PR #130.

## In progress

- #132 — Replace GitHub Project board with label-driven ticket board.

## Needs decision

- #120 — Decide and document the canonical workout data model.

## Blocked

- #123 — Introduce normalized workout schema with legacy-data migration — blocked by #119 and #120.
- #125 — Replace hidden separator persistence with typed editor state — blocked by #120.
- #126 — Remove full statistics rebuild from application startup — blocked by #120 and #123.

## Backlog

- #121 — Remove confirmed dead code and duplicate dependencies.
- #122 — Separate autosave from parsing, statistics synchronization, and export.
- #124 — Configure permanent application identity and production release build.

This file is a migration snapshot, not a manually maintained board. For current status, apply the rules in `docs/TICKET_BOARD.md` to live GitHub data.
