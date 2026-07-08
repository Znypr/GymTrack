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
- `progressComparisonKey`: load-compatible grouping for progress charts
- `variantLabels`: short UI chips such as `Prime`, `Atlantis`, `Straight bar`, `Cable`, or `Unilateral`

## Project-wide grouping decision

Use different grouping keys for different product questions.

### Prediction / suggestions

Use `baseComparisonKey`.

This answers: "What exercise is this generally?" It should group things like lateral raise variants when learning user exercise order and routine patterns.

### Progress / PR / weight charts

Use `progressComparisonKey` / strict load context for the individual plotted series.

This answers: "Am I stronger on this exact comparable setup?" It must separate load-incompatible variants:

- dumbbell lateral raise vs machine lateral raise vs cable lateral raise
- dumbbell curl vs cable curl vs machine curl
- barbell press vs dumbbell press vs machine press
- unilateral vs bilateral variants when the stored load is not directly comparable
- Prime vs Atlantis or other brand-specific machine variants when the machine changes the load curve or stack/lever mechanics
- attachment variants when they materially change loading or setup, such as rope vs straight bar pushdown

The Exercise Progress dropdown should still select the base exercise, then show available progress variants underneath. The chart should plot the variants as overlapping individual graph lines. Clicking one variant pill focuses that line and fades the other lines instead of removing context.

## Exercise Progress UI policy

The dropdown is for selecting the base exercise, not fully explaining every variant. It should therefore use a compact summary only:

- show canonical base exercise name and total set count
- show at most a few tiny summary chips
- hide side-mode labels such as `Unilateral` in dropdown summaries when they create clutter
- avoid long combined chips like `Hammer Strength · Unilateral` in the dropdown
- use the main chart area variant pills for detailed variant selection

The chart area is where variants are meaningful:

- show each load-compatible variant as its own pill
- use distinct colors per variant, not only semantic colors, so adjacent lines/pills are visually separable
- show overlapping graph lines by default
- if one variant pill is selected, emphasize that line and fade the others
- only show anomaly warnings automatically for a single visible series or a focused variant; do not run anomaly detection across mixed variants

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

- **Stats exercise-progress selector:** selected by base exercise, with load-compatible variant pills controlling individual chart lines.
- **Note editor exercise headers:** raw text remains stored, but the header visually renders as the canonical name with outlined pill chips underneath.

For example, `Tbar Rows Prime` should visually render as:

```text
T-Bar Row
[Prime] [Machine]
```

not as both raw and canonical names.

## Label colors

Use semantic label colors in the note editor:

- brand / machine line: green, e.g. `Prime`, `Atlantis`, `Hammer Strength`
- attachment: purple, e.g. `Straight bar`, `Rope`, `Handle`
- equipment class: blue, e.g. `Cable`, `Machine`, `Dumbbell`
- side mode: red where needed, but do not show `Unilateral` in the editor because the row flag already shows it
- warning/review labels: error color

Use distinct series colors in Exercise Progress so multiple visible graph lines are distinguishable even when the variants are from the same semantic label family.

## Grouping examples

| Raw wording | Canonical name | Variant fields | Prediction grouping | Progress grouping |
| --- | --- | --- | --- | --- |
| `lateral raise db` | Lateral Raise | dumbbell | same as lateral raise | separate line from machine/cable |
| `lateral raise machine` | Lateral Raise | machine | same as lateral raise | separate line from dumbbell/cable |
| `tricep pushdown bar` | Triceps Pushdown | cable + straight bar | same as pushdown | separate line from rope/no-attachment cable |
| `tricep extension prime` | Triceps Extension | machine + Prime | same base movement | separate line from Atlantis |
| `tricep extension at` | Triceps Extension | machine + Atlantis | same base movement | separate line from Prime |
| `leg extension rl` | Leg Extension | unilateral | same base movement | separate line from bilateral |
| `latpulldown rl` | Lat Pulldown | unilateral | same base movement | separate line from bilateral |
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
- No chart redesign beyond multi-series progress display

## Validation target

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Manual check after a local force statistics repair or new completed workout:

- exercise dropdown should show canonical base names, not raw legacy spellings
- progress dropdown summary chips should be small and sparse, not overflowing
- progress graph should show overlapping individual lines for variants like DB vs machine lateral raise, or straight-bar pushdown vs cable/no-attachment pushdown
- clicking a variant pill should focus that line and fade the others
- anomaly warning should not appear for mixed-variant charts unless a single variant is focused or only one variant exists
- editor exercise headers should show only the canonical name visually, with outlined color-coded variant chips underneath
- editor should not duplicate `Unilateral` as a chip when the row already has a Uni flag
- canonical projection should store alias rows for non-canonical user wording
- raw workout note text should remain unchanged
