# Exercise identity canonicalization

Issue: #233

This document defines the first non-destructive exercise identity layer for GymTrack.

## Current corpus snapshot

The 2026-07-08 backup contains:

- 38 legacy notes
- 982 legacy sets
- 50 canonical exercise rows
- 0 canonical exercise aliases

The current history already contains repeated wording patterns that should be grouped for stats and future prediction, while preserving raw workout text.

## Core rule

Raw note text remains the source of truth and is not rewritten.

Parser output now carries an `ExerciseIdentity` next to the parsed display name. The identity separates:

- `canonicalName`: common gym term used for default grouping
- `aliases`: user spellings, abbreviations, and legacy names
- `equipment`: cable, machine, dumbbell, barbell, Smith machine, bodyweight, or unknown
- `attachment`: rope, straight bar, V-bar, EZ-bar, handle, or unknown
- `brand`: machine brand such as Prime, Atlantis, Hammer Strength, Cybex
- `sideMode`: bilateral, unilateral, alternating, or unknown
- `baseComparisonKey`: default stats/prediction grouping
- `strictComparisonKey`: stricter chart/PR grouping including equipment, attachment, brand, and side mode
- `variantLabels`: short UI chips such as `Prime`, `Atlantis`, `Straight bar`, `Cable`, or `Unilateral`

## Display policy

GymTrack should let users type fast during workouts while displaying a cleaner interpretation later.

Example raw input:

```text
tricep pushdown prime
tricep pushdown cable
tricep pushdown bar
```

The app should preserve the text exactly, but show cleaner UI around it:

```text
Triceps Pushdown
[Prime] [Cable]

Triceps Pushdown
[Cable]

Triceps Pushdown
[Straight bar] [Cable]
```

This is now applied in two places:

- **Stats exercise-progress selector:** grouped by canonical base exercise, with variant chips under the option.
- **Note editor exercise headers:** raw text remains editable, with a canonical preview and variant chips shown underneath when the clean name differs or useful labels exist.

## Grouping policy

Default stats and future prediction should generally group by `baseComparisonKey`.

Strict progress views and PR-style comparisons should use `strictComparisonKey` when loading is not directly comparable.

Examples:

| Raw wording | Canonical name | Variant fields | Default grouping | Strict grouping |
| --- | --- | --- | --- | --- |
| `tricep pushdown bar` | Triceps Pushdown | cable + straight bar | same as pushdown | separate from rope/V-bar |
| `tricep extension prime` | Triceps Extension | machine + Prime | same base movement | separate from Atlantis |
| `tricep extension at` | Triceps Extension | machine + Atlantis | same base movement | separate from Prime |
| `leg extension rl` | Leg Extension | unilateral | same base movement | separate from bilateral |
| `latpulldown rl` | Lat Pulldown | unilateral | same base movement | separate from bilateral |
| `seated hamstring` | Seated Leg Curl | machine/brand if known | grouped with leg curl | strict by machine when known |

## Important ambiguity

`tricep extension bar` is ambiguous. It can mean:

- cable triceps extension using a straight-bar attachment
- lying barbell/EZ-bar triceps extension
- overhead cable extension with a bar attachment

The resolver marks this as ambiguous instead of silently deciding the exact variant.

## Backup-derived aliases added in the first pass

The first implementation covers the most obvious current backup patterns:

- `latpulldown` / `latpulldown rl` -> Lat Pulldown
- `diag rowing` / `diagonal rowing` -> Diagonal Row
- `tbar row` / `tbar rowing` / `tbar machine` -> T-Bar Row
- `seated hamstring` -> Seated Leg Curl
- `calve machine` / `calf machine` -> Calf Raise
- `situp l6` / `sit up l6` -> Sit-Up
- `rear delt` / `rear fly` -> Rear Delt Fly
- `tricep pushdown bar` / `bar tricep pushdown` -> Triceps Pushdown
- `tricep extension at` -> Triceps Extension with Atlantis brand when parsed from `at`

## Non-goals in this pass

- No raw note rewriting
- No destructive historical migration
- No user-facing alias-management UI
- No automatic merge restore changes
- No broad muscle-group taxonomy
- No chart redesign

## Validation target

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Manual check after a local force statistics repair or new completed workout:

- exercise dropdown should show canonical grouped names, not raw legacy spellings
- progress graph should aggregate grouped legacy exercise IDs for the selected canonical exercise
- editor exercise headers should show canonical preview labels without modifying the text field contents
- canonical projection should store alias rows for non-canonical user wording
- raw workout note text should remain unchanged
