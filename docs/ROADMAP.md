# GymTrack roadmap

**Last updated:** 5 July 2026

GitHub Issues, pull requests, labels, and required checks define live status. See [PROJECT_STATUS.md](PROJECT_STATUS.md).

## Completed foundation

- Android CI and repository governance.
- Explicit Room migrations (#119 / PR #146).
- Canonical workout model (#120 / PR #138).
- Canonical v9 schema (#139 / PR #149).
- Deterministic legacy backfill (#140 / PR #150).
- Canonical repositories and dual-read verification (#141 / PR #154).
- Versioned `TrainingSummary` projection (#152 / PR #155).

## Current queue

1. **#129:** validate work-item automation end to end. PR #156 merged, but the controlled test on #153 did not create the expected work item.
2. **#127 / PR #128:** complete Android 14+ manual timer restoration validation.
3. **#153:** add the completion-triggered local training-summary outbox.
4. **#122:** separate autosave, finalization, statistics synchronization, and export.
5. **#125:** replace hidden Unicode persistence with typed editor state after #122.
6. **#126:** remove the startup statistics rebuild after mutation paths are consistent.
7. **#121:** remove confirmed dead code and duplicate dependencies.
8. **#124:** configure permanent application identity and release signing before distribution.

## Phase status

- **Governance:** implementation exists; end-to-end automation validation remains.
- **Safety:** migrations are safe; timer and save-pipeline work remain.
- **Canonical data:** foundation complete; production cutover remains.
- **Editor:** autosave and typed-state refactors are queued.
- **Statistics:** blocked by reliable canonical mutation paths.
- **Release:** identity, signing, backup, privacy, and beta validation are planned.

## Rules

Safety work can interrupt planned work. Large changes are split into reviewable pull requests. Acceptance criteria do not weaken silently. Update this file after major merges or dependency changes.
