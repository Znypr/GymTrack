# Editor v2 rules (lead decision, 2026-11-10). These override BRIEF.md where they conflict.

Read `docs/EDITOR_BEHAVIOUR.md` first, sections 2, 3, 4, 7 and 8. It describes how the editor works today. Everything in §8.1 "Must keep" stays. These rules change only how things are **shown and reached**, not the storage format.

## 1. Set grammar: REPS × WEIGHT (critical)
The app is **reps first**: `10x 60` = 10 reps at 60. **All earlier mockups wrongly wrote weight × reps (80×8). Never do that.**
- Display a set as `10 × 60`: reps prominent (ink, weight 600), weight in normal ink.
- **Weight carry-over** (`8x` reuses the previous weight). Show the inherited weight **muted** (40% ink), e.g. `8 × 70` with "70" muted, so the user sees what was counted without having typed it.
  - Notebook theme: show a ditto mark `8 × 〃`. Its long-press tooltip shows 70.
- **Myo-reps** `10+3+2x 45`: display as `10+3+2 × 45` and a small muted total `=15`.
- **Timed set** `60s`: display `60 s`. With weight, `45 s × 10`.
- **Unit:** never shown when it equals the default. A per-set override is shown inline (`8 × 70 lb`).
- **Ghost suggestion pill:** same grammar, `8 × 82.5`. Dashed, tap to use, ⓘ for why ("last time 8 × 80 on all sets → +2.5").

## 2. Times: one per row, no duplicates
Today each set row shows the rest written into its text (`(1'38'')`) **and** an absolute elapsed time in a right column. That's redundant.
- Set row, right side, muted: **rest before this set**, `1'38`. Nothing else.
- Exercise row: start time as a small muted "at 8'20" (optional). The absolute times per set are available on long-press only.
- Warm-ups (inferred, #306): muted row + small `W` tag. Rest still shown.

## 3. Flags: once per exercise, only when not default
- Default **Bi** shows **nothing** (no chip, no tag).
- **Uni** = a neutral chip `L/R` next to the exercise name, ink outline, NOT red (red is reserved for Push and errors).
- **Superset** = a thin vertical bracket on the left joining the 2+ paired exercise blocks, plus a small `SS` label on the bracket. Never repeated per set.
- **No per-set 1x/2x tags. No left gutter.** That frees about 50 dp of width.
- **Toggle in one tap, in the thumb zone:** the keypad accessory bar has a 3-way segmented control `Bi · L/R · SS` acting on the **current exercise**. It applies to the exercise and all its sets (fixes flag drift). A new exercise starts at Bi (fixes leak #316/#317).

## 4. Keypad accessory bar (bottom, above the keypad, ~48 px)
Left to right: `‹ Back` (or swipe from the left edge), the flag segment `Bi · L/R · SS`, then **Finish** (amber pill; disabled until 1 set is logged).
- The timer is shown in the read-only top line; tap-to-pause lives in the accessory bar's overflow `⋯` together with Notes.
- Nothing actionable sits in the top 40% of the screen.

## 5. Keypad (unchanged grammar, 4×4, keys ≥48 px)
`1 2 3 ⌫ / 4 5 6 X / 7 8 9 + / . 0 S Next↵`. Next↵ = commit and new set row (Enter on an empty row = next exercise, as today).
- Show an inline hint once: "Enter on empty row → next exercise".

## 6. Rows: fix the pain points visibly
- Each exercise block ends with a small `+ set` affordance (tap → new set row with the keypad; fixes B9).
- Swipe left on a set row → Delete (with Undo) (fixes B8).
- Tapping a committed set edits it with the keypad, not the IME (fixes B14).
- Viewing an old workout: the keypad stays closed until the user taps a row (fixes B6).

## 7. Top area (read-only, compact, one line ≈44 px)
Category dot + name · elapsed `24:10` · date small. Then the collapsed muscle chip row (teal dots), then the list.

## 8. Example data (reps × weight; use exactly this)
**Push day**, 24:10 elapsed.
- **Bench Press** · BAR chip. Last: `8 × 80 · 8 × 80 · 8 × 80`.
  - W `8 × 40`, W `5 × 60`, W `2 × 70`
  - `8 × 80` rest 2'30
  - `8 × 80` rest 2'45 (80 muted: inherited)
  - active row → ghost `8 × 82.5`
- **Incline DB Press** · DB chip, flag L/R? No: Bi. Last: `10 × 30 · 9 × 30 · 8 × 30`.
- **Cable Fly** + **Lateral Raise** as a **superset** (bracket + SS). Lateral Raise is **L/R** (unilateral). Last: Fly `12 × 20`, Raise `15 × 12`.
- **Mistake example (E3):** typed `8x 825` (meant 82.5) → "825 is 10× your last weight (80)". Buttons `Fix → 82.5` and `Keep`.
