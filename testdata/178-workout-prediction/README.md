# Workout prediction seed data

This directory contains deterministic manual QA fixtures for #178.

## Seed cycle

`seed-cycle/` contains five completed workouts:

1. Push — 01/07/2026 18:00
2. Pull — 02/07/2026 18:00
3. Legs — 03/07/2026 18:00
4. Push — 05/07/2026 18:00
5. Pull — 06/07/2026 18:00

Expected prediction after importing all five files:

```text
Likely next workout: Legs
Reason mentions Pull followed by Legs
Confidence: Medium confidence
```

Why: the latest completed workout label is `Pull`, and historical completed history contains the transition `Pull -> Legs`.

## Import path

These files use the legacy CSV format because the debug app already exposes debug-only legacy CSV import.

Release builds should not expose CSV import. Use these fixtures only in debug/manual QA.
