## Linked issue

Closes #

## Sidebar metadata

- [ ] Assignee is set.
- [ ] Reviewers are requested when this PR is ready for review.
- [ ] Labels clarify type, status/risk, automation, CI, or release handling where useful.
- [ ] Project usage matches the linked issue; no duplicate Project card unless this PR itself needs tracking.
- [ ] Milestone matches the linked issue when this PR advances a roadmap phase or release checkpoint.

## Problem

What user or technical problem does this solve?

## Charter alignment

Which product principle, goal, roadmap phase, or milestone does this support?

## Solution

What changed, and why was this approach selected?

## Scope

### Included

-

### Not included

-

## Acceptance criteria

- [ ]

## Validation

- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew lintDebug`
- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew assembleDebugAndroidTest`
- [ ] Database migration tests, when applicable
- [ ] Import/export round-trip tests, when applicable
- [ ] Manual test on emulator or device, when behavior changes
- [ ] Regression scenario covered
- [ ] Screenshots or video for visible UI changes

## Automation policy

- [ ] Auto-fix low-risk CI failures on this PR.
- [ ] Only report CI failures; do not patch this branch automatically.

Allowed auto-fix scope:

- [ ] imports / formatting / lint
- [ ] tests only
- [ ] workflow, docs, or metadata only
- [ ] no production behavior changes

Do not touch automatically:

-

### Manual test environment

- Device/emulator:
- Android version:
- Build type:
- Steps and result:

## Data and compatibility

- Schema impact:
- Migration:
- Import/export compatibility:
- Rollback considerations:

## Documentation

- [ ] README
- [ ] Project charter
- [ ] Architecture
- [ ] Roadmap
- [ ] Testing documentation
- [ ] ADR
- [ ] No documentation change required

## Risks and limitations

Known edge cases, limitations, or follow-up issues.

## Final checklist

- [ ] The change is limited to the linked issue.
- [ ] Required checks ran and passed.
- [ ] Temporary code and debugging output are removed.
- [ ] Error, loading, and empty states are handled where relevant.
- [ ] No destructive database fallback was introduced.
- [ ] Known follow-up work has a linked issue.
