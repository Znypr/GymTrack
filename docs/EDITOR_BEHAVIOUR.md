# Editor behaviour (as of master c3702fe, 2026-11-10)

Source: `master` @ c3702fe. Paths below are relative to `app/src/main/java/com/example/gymtrack/` unless they start with `docs/` or `app/src/test`. Line numbers come from master.
Status of claims: **[code]** means read directly from code or tests. **[inferred]** means it follows from the code but was not run on a device.

---

## 1. Row model

### 1.1 Storage
A workout is one `NoteLine` (`core/data/Models.kt:38-46`) with two parallel text blobs:

| Field | Content |
|---|---|
| `text` | The visible rows joined with `\n`. One editor row is one line. Raw user text is kept as typed, never canonicalised. |
| `rowMetadata` | One line per row: `<absoluteTime>\t<flagCode>`, with `b` = BILATERAL, `u` = UNILATERAL, `s` = SUPERSET (`core/util/TextUtils.kt:92-99`). Blank rows also get a metadata line (`\tb`). |
| `title`, `categoryName/Color`, `learnings`, `timestamp` | `timestamp` is the workout id and the start time (the moment the editor opened for a new workout, `EditorViewModel.snapshot`). |

Legacy notes stored metadata inline with invisible separators `​` (time) and `‌` (flag). `parseNoteText` still reads them (`TextUtils.kt:47-90`); new saves never write them (test `TextUtilsMetadataTest`).

In memory (`feature/editor/NoteEditorState.kt:24-29, 44-46`) each row is a `NoteRow(id, text: TextFieldValue, flag, focusRequester)`, plus the parallel lists `timestamps[]` (absolute time strings) and `flags[]`.

### 1.2 Exercise row vs set row (`isMain`)
There is **no stored row type**. The type is derived from position every time:

```
isMain = index == 0 || previousRow.text.isBlank()
```
(`NoteEditorState.kt:114`, `EditorListSection.kt:118`, `isFastSetEntryCandidate` `EditorListSection.kt:78`)

- **Exercise (main) row**: the first row, or any row directly after a blank row.
- **Set row**: any row whose previous row has text.
- **Blank row**: separates exercises. Exactly one blank row is created between exercises by the "double Enter" gesture (section 2).

The parser uses a *different* rule. A line is a set if it looks like a set (`^\d+x`, `x\d+`, `\d+ reps`, timed `\d+ s`, a bare number) or starts with 4 spaces; anything else starts a new exercise (`core/util/WorkoutParser.kt:294-313`). CSV export uses "starts with 4 spaces = set" (`core/util/ExportUtils.kt`). The 6-space indent the editor adds to set rows is therefore **structural**: the parser and export rely on it.

### 1.3 What is stored per row

| | Exercise row | Set row | Blank row |
|---|---|---|---|
| text | raw name, e.g. `latpulldown rl` | `      10x 50 (1'40'')`: 6 spaces + typed entry + relative time in parentheses | `""` |
| relative time | none (stripped on load, `NoteEditorState.kt:68-70`) | **inside the text**: `(m'ss'')`, seconds since the previous non-blank Enter, not rounded | none |
| absolute time | `rowMetadata` col 1: elapsed since workout start, rounded to `settings.roundingSeconds` (default 5 s), format `m'ss''` (minutes never roll into hours, e.g. `72'05''`) | same | `""` |
| flag | the flag the user set (button) | **copied from the row where Enter was pressed** at creation time; not updated later | copied as well (invisible) |

---

## 2. Typing flow, keystroke by keystroke

### 2.1 Opening
- **New workout** (`noteId=-1`): one empty row. It auto-focuses with the **system keyboard** (`EditorListSection.kt:127-133, 169-179`). The timer starts automatically (`NoteTimer.startOnOpen`).
- **Existing workout**: the list scrolls to the top (`NoteEditor.kt:72-76`). Nothing is focused. [inferred] If the last stored row is a blank row after a set (the normal case, because Enter always appends a row), that row qualifies for fast set entry. Its `LaunchedEffect` then opens the keypad and scrolls to it, even when you only view history (see 8.2 B6).

### 2.2 First exercise (system keyboard)
1. Type `Bench press` on the system keyboard. The field is a multi-line `BasicTextField` with default options, so autocorrect and capitalisation follow the IME.
2. As soon as the text is non-blank, the **flag button** (`Bi`) appears in the 50 dp left gutter.
3. Press the IME **Enter**. This inserts `\n`, and `onTextChange` sees `text.endsWith("\n")` (`NoteEditorState.kt:107`):
   - the row text becomes the content without `\n` (exercise rows get no relative time);
   - `startTime` is set to *now* if this is the first exercise (`:115`). **The absolute clock starts at the first exercise Enter, not at the timer start.**
   - `timestamps[i]` = absolute (`0'00''` for the first exercise);
   - a new empty row is inserted below it with the **same flag** (`:128-135`);
   - a draft save runs (`:150`).
4. The new row is a set row (its previous row has text). It has no timestamp and no following row, so `isFastSetEntryCandidate` is true. The UI clears focus, **hides the IME**, sets `activeFastSetRowId` and **shows the custom keypad** (`EditorListSection.kt:157-164`). The row is anchored with 2 rows of context above it (`activeSetAnchorIndex`, test `ActiveSetScrollPolicyTest`).

### 2.3 Set rows (custom keypad, `components/SetEntryKeypad.kt`)
Layout is 4×4 at the bottom of the screen, each key at least 48 dp high:
```
[1] [2] [3] [⌫]
[4] [5] [6] [X]
[7] [8] [9] [+]
[.] [0] [S] [Enter]
```
The active row is a read-only field drawn as plain text plus a blinking `│` caret (`EditorListSection.kt:246-273, 400-418`). The cursor is effectively always at the end.

| Key | Effect | Guard |
|---|---|---|
| digits | insert at cursor | none |
| **X** | inserts `"x "`, which ends the reps and starts the weight | only if no `x`/`s` is present yet and a digit is before the cursor (`:361-368`) |
| **S** | inserts `"s "`, making a **timed set** (`30s` = 30 s; `30s 20` = 30 s holding 20 units) | same guard as X (`:375-382`). Always enabled. |
| **+** | inserts `+` in the reps part: myo-reps / rest-pause / partials. `8+4+4x 50` = **16 reps** at 50 (`WorkoutParser.calculateReps` sums them) | only before `x`/`s`, right after a digit, and not before another `+` (`canInsertMyoRepPlus`) |
| **.** | decimal point | only after `x`/`s` (weight part), at most once (`:353-360`) |
| **⌫** | deletes the selection or the previous character; does nothing at position 0 | there is **no row deletion** |
| **Enter** | appends `\n` and goes through the same `onTextChange` path | none |

Enter on a non-empty set row: the text becomes `"      " + content + " (" + relative + ")"`, the absolute time is stamped, a new empty set row with the same flag is inserted, and the keypad moves to it automatically. **One tap per set to commit and advance.**

The grammar is **reps first**: `reps x weight`. Typical keypad sequences:
- `1 0 X 6 0 Enter` gives `10x 60`
- `8 X Enter` gives `8x`, which inherits the weight (section 4)
- `6 0 S Enter` gives `60s` (timed)

### 2.4 Next exercise: the "double Enter"
- Press **Enter on an empty set row** (keypad Enter with nothing typed). `formatted = ""` and the row stays blank, so it becomes the separator. A new row is inserted after it. Because its previous row is blank, it is **main**, so `pendingExerciseFocusRowId` is set and the list scrolls to it (`NoteEditorState.kt:137-149`). The UI focuses it and **shows the system keyboard** (`EditorListSection.kt:169-179`).
- So the sequence is: last set `Enter` (keypad), `Enter` again (keypad) on the empty row, then type the next exercise name on the system keyboard.
- The new exercise row inherits the flag of the previous exercise (see 3.3).

### 2.5 Which input each row uses
| Row state | Input |
|---|---|
| Exercise row | system keyboard (focus shows the raw text, see 4.5) |
| Blank set row at the very end of the list (no next row), no timestamp | **custom keypad** |
| Set row that already has an absolute timestamp (committed) | system keyboard. Tapping it edits the full raw text, including the indentation and `(m'ss'')` (#282) |
| Blank row that has a row after it (separator, or a new set row inserted mid-workout) | system keyboard, and **not** auto-focused (`isSeparatorBeforeExercise`, `EditorListSection.kt:81`) |
| Set row typed with the IME but not yet Entered (no timestamp) and not last | system keyboard |

### 2.6 Focus rules (from #277, #281, #282, e30468b, 157c662, 29b53c9)
- Only one owner at a time: entering keypad mode calls `focusManager.clearFocus(force=true)` and `keyboardController.hide()` twice (before and after the scroll).
- Focusing any IME row clears `activeFastSetRowId`, so the keypad disappears and the IME shows (`:279-291`).
- Tapping a fast-set candidate row (re)activates the keypad (`:229-238`).
- Scroll reveal: first `scrollToItem(index-2)`, then `bringIntoView`, then a second pass after 180 ms to absorb keypad/IME height changes. The list has `imePadding` and 150 dp bottom padding.

### 2.7 Leaving
- **Back** (top-left arrow or system back): `saveNote(exit=true)`, then pop. On the active workout it saves a **draft** and the timer keeps running. On a past workout (finished, or no longer the active one), every save, Back included, also re-derives its `sets` and canonical rows (#325, see section 6).
- **Finish** (top-right text button, only on the active workout): `saveNote(exit=true, finishWorkout=true)` **finalises** (parse + derived set rows + training-summary outbox, in one transaction), stops the timer, then pops. See section 6.

---

## 3. Flags: BILATERAL / UNILATERAL / SUPERSET

### 3.1 Where they are set
- An `ExerciseFlagButton` sits in the 50 dp **left gutter of exercise rows**, visible only once the row has text (`EditorListSection.kt:207-213`). Labels: `Bi` (outlined, neutral), `Uni` (red tint), `SS` (blue tint) (`components/ExerciseFlagButton.kt`).
- Each tap cycles **Bi → Uni → SS → Bi** (`core/data/ExerciseFlag.kt` `next()`). It changes only that row's flag and saves a draft (`NoteEditorState.toggleFlag`, `:156-165`).
- Set rows have no button. They show a read-only `ExerciseFlagTag` computed from the **parent exercise row's current flag** (walks up to the row after the last blank row, `EditorListSection.kt:215-218`).

### 3.2 The "1x" / "2x" labels
`ExerciseFlagTag` (`components/ExerciseFlagTag.kt:44-48`): BILATERAL → `1x` (grey), UNILATERAL → `2x` (red), SUPERSET → `2x` (blue). CSV export uses the same mapping (`ExportUtils.toSubEntryString`), and import reads `2x` back as UNILATERAL and `1x` as BILATERAL.
Meaning (inferred; nothing in the code documents it): "this set line stands for two efforts". For unilateral that is left + right side. For superset that is exercise A + exercise B back to back. **No stats code multiplies by 2.** Volume is always `weight × reps` (`core/data/Database.kt:282`, `feature/stats/components/StatsOverview.kt:28`). The label is purely informational and is the same on every set row of an exercise.

### 3.3 Inheritance (actual behaviour)
- New rows copy the flag of the row where Enter was pressed (`NoteEditorState.kt:131, 134`). So:
  - sets inherit the exercise's flag *as it was when each set row was created*;
  - the separator copies the last set's flag, and the **next exercise inherits the previous exercise's flag**. A Uni or SS exercise makes the next exercise Uni or SS until toggled. For supersets this saves a tap on exercise B, but it costs taps afterwards: from SS, 1 tap back to Bi; from Uni, 2 taps.
- Toggling an exercise **does not update its existing set rows' stored flags**. The UI tags read the parent, so they look correct, while `rowMetadata` keeps the old per-set flag (see bug B1).

### 3.4 How the parser, stats and identity use them
- `WorkoutParser.parseWorkout`: `isUnilateral = setRowFlag == UNILATERAL || setLine.contains("uni")` (`WorkoutParser.kt:375`). It uses the **set row's** flag, not the exercise's.
- `isUnilateral` goes into the legacy `sets` table and into `ExerciseIdentity.sideMode`. That splits progress series (`progressComparisonKey`): unilateral and bilateral are charted as separate lines (`docs/EXERCISE_IDENTITY.md`). It is not used to double volume.
- **SUPERSET has no parser effect.** It is stored only as the canonical `workout_exercises.mode` from the exercise header's flag (`core/data/transition/LegacyWorkoutProjector.kt:96`). There is **no superset pairing**: nothing links exercise A to B, rest timing is not adjusted, and no grouping happens in the UI.
- `CanonicalExerciseVisualTransformation(isUnilateral)` and `ExerciseIdentityPreview` pass the flag so side mode is resolved. The red "Unilateral" chip is **deliberately hidden** in the editor because the flag button already shows it (`EditorListSection.kt:469`; docs/EXERCISE_IDENTITY.md "Label colors").

---

## 4. Shorthand and redundancy avoidance that already exists

1. **Weight carry-over.** A set with reps but no weight (`8x`) reuses the last weight given *within the same exercise*. The unit carries over too (`WorkoutParser.kt:360-374`; tests `handles shortcut entry with inherited weight`, `carriedWeightKeepsExplicitUnit`). The carry resets at each new exercise (`:302`; test `resets weight when exercise changes`). Timed sets neither use nor update the carried weight (test `seconds-only set does not inherit previous weight`). **The inherited weight is never shown on screen**: the row just reads `8x`.
2. **Unit implicit from settings.** Omitted unit = `Settings.defaultWeightUnit` (KG/LB). Typed `kg`/`lb` wins per set (`docs/WEIGHT_UNITS.md`). The keypad cannot type units, so keypad sets are always in the default unit.
3. **`+` myo/rest-pause reps** are summed (`8+4+4x 50` = 16 reps).
4. **`S` timed sets**: `30s`, `30s 20`. Duration goes to `durationSeconds`, reps = 0.
5. **Bodyweight exercises** (pullup, dip, pushup, crunch, leg raise, ...) are valid with reps only. **Cardio** (`treadmill`, `bike`, ...) with a lone number counts it as reps (`WorkoutParser.kt:45-56, 353-358`).
6. **Aliases and abbreviations in exercise names** (resolved for display and stats; raw text is never rewritten):
   - Brands: `hs` → Hammer Strength, `cy` → Cybex, `g80`/`syg` → Gym80, `lf` → Life Fitness, `at`/`atl` → Atlantis, `prime`, `rl` → Realleader, `bb` → **BootyBuilder** (not barbell, #280), and more (`ExerciseIdentity.kt:139-163`, `WorkoutParser.kt:69-80`).
   - Equipment: `db`/`dbs` → Dumbbell; a leading `bar ` → **Barbell** (unless pushdown/cable); `bar` elsewhere → Straight bar attachment; `ezbar`, `vbar`, `rope`, `og`/`ug` grips.
   - Core names: `latpulldown`/`lat pull` → Lat Pulldown, `tbar row` → T-Bar Row, `diag rowing` → Diagonal Row, `seated hamstring` → Seated Leg Curl, `butterfly` → Pec Deck, `side`/`lat raise` → Lateral Raise, `hacksquat`, `calve machine` → Calf Raise, and more. The parser also autocorrects typos per word by Levenshtein (≤1 edit for short words, ≤2 for words over 5 letters, `WorkoutParser.kt:150-164`).
   - `tricep extension bar` is flagged **ambiguous**, which shows a red "Review" chip.
7. **Display canonicalisation** (`core/util/CanonicalExerciseVisualTransformation.kt`): when an exercise row is **not focused**, it shows `ExerciseIdentityResolver.resolve(raw).canonicalName`, e.g. `latpulldown rl` → "Lat Pulldown". `hs`/`rl`/`hammer strength`/`realleader` are stripped first so they are not duplicated with the chip (tests in `CanonicalExerciseVisualTransformationTest`). When focused, the raw text is shown (`EditorListSection.kt:188-190`).
8. **Variant chips** under the exercise name (`ExerciseIdentityPreview`, `EditorListSection.kt:459-481`): up to 4 outlined pills. Brand is green, attachment purple, equipment blue, Review is the error colour. Side chips are hidden. "Machine" is hidden when a brand is present.
9. **Automatic time capture (zero typing)**:
   - *relative* `(m'ss'')` is appended to each set's text on Enter. It is the time since the previous non-blank Enter, so the first set measures from the exercise name and later sets measure set + rest. It is not rounded and is tinted by `RelativeTimeVisualTransformation`.
   - *absolute* is the elapsed time since the first exercise, rounded to 5 s by default. It is stamped on Enter on any non-blank row and shown in a faint right-hand column with small raised seconds (`SmallSecondsVisualTransformation`). Rows that were never Entered get stamped at save time (`NoteEditorState.saveNote`).
   - Both are tagged in `docs/intake` as the basis for inferring rest, tempo and warm-ups (#306), so they must be kept.
10. **Auto-advance**: the keypad Enter commits the set and opens the next set row, and the double Enter opens the next exercise with the IME. No "add set" or "add exercise" buttons.
11. **Draft autosave** on every Enter, flag toggle and `ON_STOP`. No save button.

---

## 5. What is displayed per row, and what is redundant

Screen, top to bottom (`feature/editor/NoteEditor.kt`):
1. Top bar: Back · (Finish, active workout only) · Learnings (☰ icon).
2. Hero header (`components/EditorHeroHeader.kt`): weekday (large), start time, date, category dropdown chip, category-tinted gradient.
3. Timer row (active workout only): `m'ss''`, Pause/Play, Stop (`components/NoteTimer.kt`).
4. Text line "Default weight unit: kg" (`NoteEditor.kt:206-217`).
5. Notebook list: rows with 1 dp rules and ruled filler to the page bottom.
6. Keypad (set mode only).

Per row:
| Exercise row | Set row |
|---|---|
| `[Bi/Uni/SS]` button · **canonical name 22 sp black** · abs time (faint, right) · chips below | `[1x/2x]` tag · `      10x 60 (1'40'')` 14 sp · abs time (faint, right) |

Redundant or low-value today:
- **R1** The `1x/2x` tag on **every set row** repeats the parent flag. `1x` (the default) conveys nothing.
- **R2** The `Bi` button on every exercise shows the default state.
- **R3** **Two times per set**: relative in the text and absolute in a column. Users mostly need rest since the last set *live*, not a history column. The exercise row also gets an absolute time.
- **R4** The 6-space indent and the parentheses are visible characters, and become editable once the row is focused.
- **R5** "Default weight unit: kg" is permanent text, although the unit never changes mid-workout. Meanwhile **no unit is shown next to any weight**.
- **R6** Hero header: weekday, time and date take about 120 dp of vertical space during the workout. The user knows the date. The timer duplicates "time since start".
- **R7** Canonical names can duplicate chips for unmatched exercises, e.g. `incline db press` → "Incline Db Press" + [Dumbbell], or `dips prime` → "Dips Prime" + [Prime]. Only `hs`/`rl` are stripped from the title (`CanonicalExerciseVisualTransformation.kt:19-23`).
- **R8** Missing info that should be shown: the inherited weight on `8x` rows, last session's numbers for this exercise, rest timer since the last set, set count.

---

## 6. Save and finish semantics

- **Draft** = `NoteRepository.saveNote` (note row only, no parsing). It is triggered by Enter (every row), flag toggle, `ON_STOP`, and Back on the **active** workout. Each save serialises `text` + `rowMetadata` + title, category and learnings.
- **History-edit save** (#325) = `WorkoutRepository.saveEditedWorkout`. The same triggers on a workout that is not the active one, or is already `COMPLETED` (`shouldRefreshDerivedDataOnSave`). It runs in one transaction: note, parse, derived legacy sets and canonical rows. Status, Finish time (`ended_at`) and `created_at` are kept, and no training summary is enqueued.
- **Finalize** = `WorkoutRepository.saveCompletedWorkout` (`core/data/WorkoutRepository.kt:183-207`). It runs in one Room transaction: upsert note, parse, replace derived legacy sets, canonical projection (`ended_at` = Finish time per `docs/WORKOUT_DURATION.md`), training-summary outbox. It is triggered **only by Finish**.
- `EditorSaveCoordinator`: a mutex serialises writes and skips a queued draft once a newer revision exists. A finalize is never dropped. On failure the editor stays open and shows a snackbar error. `finishing=true` blocks later edits and autosaves.
- `saveNote` early-returns when `saved && !exit` (nothing changed). A new empty workout with no title or learnings is not saved at all on exit.
- Timer (`docs/TIMER.md`): starts automatically for a new workout. Back keeps it running, so you can browse history mid-workout. Finish stops it after a successful finalize (`shouldStopTimerOnExit`, tested in `NoteEditorExitPolicyTest`). Stop on the timer row clears it without leaving.
- **Finish visibility**: `isLastNote = noteId == -1 || noteId == activeTimer.noteTimestamp` (`ActiveWorkoutControls.kt`, `NavigationHost.kt:147-164`). Historical workouts have **no Finish**.
- **Doc vs code** (resolved by #325): `docs/EDITOR_SAVE_PIPELINE.md` used to say that Back finalizes. It now describes the real behaviour: Back is a draft on the active workout and a history-edit save on a past one. Back never finalizes.

---

## 7. Worked examples

Each example shows the stored `text` and `rowMetadata` (`\t` shown as `⇥`), then what is rendered when no row is focused. Default unit kg, rounding 5 s.

### 7.1 Push day: inherited weight, one Uni-free flow
Keystrokes: IME `Bench press` ⏎ · keypad `1 0 X 6 0 ⏎` · `8 X 7 0 ⏎` · `8 X ⏎` · `⏎` (ends exercise) · IME `incline db press` ⏎ · keypad `1 2 X 2 6 ⏎` · `1 0 X ⏎` · Finish.
```
text:
Bench press
      10x 60 (1'38'')
      8x 70 (2'51'')
      8x (2'44'')

incline db press
      12x 26 (1'52'')
      10x (2'31'')
                                   ← trailing empty set row
rowMetadata:
0'00''⇥b
1'40''⇥b
4'30''⇥b
7'15''⇥b
⇥b
8'20''⇥b
10'10''⇥b
12'40''⇥b
⇥b
```
Rendered:
```
[Bi]  Bench Press                                0'00''
[1x]        10x 60 (1'38'')                      1'40''
[1x]        8x 70 (2'51'')                       4'30''
[1x]        8x (2'44'')                          7'15''
      (blank rule)
[Bi]  Incline Db Press                           8'20''
      [Dumbbell]
[1x]        12x 26 (1'52'')                     10'10''
[1x]        10x (2'31'')                        12'40''
[1x]  │  ← keypad active (on reopen too, see B6)
```
Parsed: Bench 10×60, 8×70, **8×70** (inherited); "Incline db press" 12×26, **10×26**. Volume = Σ w×r.

### 7.2 Leg day: Realleader + Uni, myo-reps, timed core
Keystrokes: IME `leg extension rl`, tap flag once (Bi→Uni) **before** ⏎ · keypad `1 2 X 4 5 ⏎` · `1 0 + 3 + 2 X ⏎` · `⏎` · IME `plank` (inherits **Uni**, tap twice to get Bi) ⏎ · keypad `6 0 S ⏎` · `4 5 S 1 0 ⏎`.
```
text:
leg extension rl
      12x 45 (1'05'')
      10+3+2x (2'40'')

plank
      60s (1'30'')
      45s 10 (2'10'')

rowMetadata:
0'00''⇥u
1'05''⇥u
3'45''⇥u
⇥u
5'00''⇥b
6'30''⇥b
8'40''⇥b
⇥b
```
Rendered:
```
[Uni] Leg Extension                              0'00''
      [Realleader]
[2x]        12x 45 (1'05'')                      1'05''
[2x]        10+3+2x (2'40'')                     3'45''

[Bi]  Plank                                      5'00''
[1x]        60s (1'30'')                         6'30''
[1x]        45s 10 (2'10'')                      8'40''
```
Parsed: Leg Extension (Realleader, unilateral side mode) 12×45 and **15×45** (myo sum + carried weight). Plank 60 s at 0 and 45 s at 10 kg. If Uni had been tapped **after** ⏎, the first set row would be stored `b`, so it would count as bilateral while showing `2x` (bug B1).

### 7.3 Superset pair + brand chips
Keystrokes: IME `lateral raise db`, flag ×2 (SS) ⏎ · `1 5 X 1 2 . 5 ⏎` · `1 2 X ⏎` · `⏎` · IME `rear delt prime` (inherits **SS**) ⏎ · `1 5 X 4 0 ⏎` · `1 5 X ⏎` · `⏎` · IME `tricep pushdown bar` (inherits SS; tap once to get Bi) ⏎ · `1 2 X 3 0 ⏎`.
```
text:
lateral raise db
      15x 12.5 (0'50'')
      12x (2'05'')

rear delt prime
      15x 40 (0'40'')
      15x (2'10'')

tricep pushdown bar
      12x 30 (1'00'')

rowMetadata: 0'00''⇥s / 0'50''⇥s / 2'55''⇥s / ⇥s / 3'30''⇥s / 4'10''⇥s / 6'20''⇥s / ⇥s / 7'05''⇥b / 8'05''⇥b / ⇥b
```
Rendered:
```
[SS]  Lateral Raise          0'00''     [Dumbbell]
[2x]        15x 12.5 (0'50'')
[2x]        12x (2'05'')
[SS]  Rear Delt Fly          3'30''     [Prime] [Machine]
[2x]  ...
[Bi]  Triceps Pushdown       7'05''     [Straight bar] [Cable]
[1x]        12x 30 (1'00'')
```
The superset is logged as two consecutive blocks. Nothing pairs them, and the real alternation (A1, B1, A2, B2) cannot be expressed without retyping names. `SS` changes no stats.

---

## 8. Constraints a redesign must keep, and pain points

### 8.1 Must keep (time savers)
1. **One tap per set to commit and advance** (keypad Enter auto-creates the next set row and keeps the keypad up).
2. **Double Enter to start the next exercise**, with the IME auto-focused on the new name row. No menus.
3. **Reps-first keypad grammar** (`reps X weight`, `+` myo, `S` timed, `.` only in weight) with its guards, so malformed sets cannot be typed.
4. **Weight carry-over** within an exercise (type `8 X ⏎` only).
5. **Implicit unit** from settings, with a per-set override kept possible.
6. **Automatic relative and absolute timestamps** on every Enter: no timing input, and they feed future rest, tempo and warm-up inference.
7. **Raw text preserved; canonical name and chips shown** (fast messy typing, clean display; abbreviations hs, rl, bb, db, bar, at, prime, ...).
8. **Flag toggle in one tap on the exercise**, with the inherited flag going to its sets.
9. Silent **draft autosave** on Enter, toggle and backgrounding; **Back keeps the timer**, so history can be checked mid-set.
10. The keypad lives at the **bottom** (thumb zone), its keys are at least 48 dp, and the active row is auto-scrolled to stay visible above it.
11. The storage format (`text` with 6-space set indent + `rowMetadata` `abs⇥flag`) is relied on by the parser, CSV export/import, the canonical projector and backups. Change the UI, not the format, unless you migrate.

### 8.2 Bugs and pain points

**Data correctness**
- **B1 Flag drift.** Toggling an exercise flag after its sets exist updates only the exercise row (`NoteEditorState.kt:156-165`). Set rows keep the old flag in `rowMetadata`, and the parser reads the *set* flag (`WorkoutParser.kt:375`), while the UI tag reads the *parent* (`EditorListSection.kt:215-218`). What is shown and what is counted disagree.
- **B2 Flag carries into the next exercise** through the separator (`NoteEditorState.kt:131`). Uni and SS leak forward, so the user must remember to reset.
- **B3 History edits never reach stats.** *(Fixed by #325: past-workout saves now re-derive `sets` and canonical rows. An abandoned active workout is re-derived as soon as it is edited after it stops being the active one.)* Historical workouts have no Finish button, and Back saves only a draft (`NoteEditor.kt:94,121,130-139`), so derived set rows and charts stay stale. An abandoned active workout (Back, never Finish) is never finalised either. This also contradicts `docs/EDITOR_SAVE_PIPELINE.md`.
- **B4 Re-Enter restamps.** Pressing Enter at the end of an already committed row overwrites its absolute time with *now* (`NoteEditorState.kt:124`; only the relative is protected by `hasTimestamp`, `:109,119`). It also inserts an extra row.
- **B5 Clock base shifts on reopen.** A new session measures absolute time from the first exercise Enter (`:115`). After reopening, `startTime = note.timestamp` (editor-open time, `:86`), so absolute times jump by the delay between opening and the first exercise. `lastEnter` is reconstructed from that base (`:87`), so the first relative time after reopening can also be wrong or huge.
- **B6 [inferred] Keypad pops when viewing history.** The trailing blank set row that every Enter leaves is persisted. On reopen it is a fast-set candidate, so the keypad opens and the list scrolls to the bottom, against `scrollToItem(0)` (`EditorListSection.kt:157-164` vs `NoteEditor.kt:72-76`).
- **B7 Mid-text Enter.** The `endsWith("\n")` check (`NoteEditorState.kt:107`) means an IME Enter with the cursor mid-text embeds `\n` inside a row. On save and reload that row splits into two rows, with misaligned metadata.
- **B8 Rows cannot be deleted.** ⌫ stops at position 0 (`SetEntryKeypad.kt:49`) and there is no merge or delete. Clearing a row's text turns it into a separator, so the next set row becomes an "exercise" row (22 sp, canonicalised `10x 60 (1'38'')`). An accidental empty Enter ends the exercise, and the only fix is retyping.
- **B9 Adding a set to an earlier exercise** inserts a blank row that has a next row. It is not a fast-set candidate (`isSeparatorBeforeExercise`, `EditorListSection.kt:81`), so there is no keypad and no auto-focus. It needs a tap, then the IME, then manual typing.
- **B10 Invalid sets vanish silently.** `8x` with no earlier weight on a weighted exercise is dropped by the parser (`WorkoutParser.kt:377-381`), with no on-screen warning. The same happens for unknown formats.
- **B11 Brand duplication** in titles for brands other than hs/rl, plus `Db`/`Bar` left in titles next to equipment chips (R7).

**One-handed reachability and ergonomics**
- **B12** Finish, Back, Learnings, the category chip and the timer Pause/Stop are all in the **top ~25%** of the screen, out of thumb reach. Finish is a small `TextButton` right next to the Learnings icon, an easy mis-tap.
- **B13** The flag button is in the **left gutter** of the exercise row, mid-screen. That is a long stretch for a right thumb, and the button is only 44×30 dp.
- **B14** Editing a committed set needs the **system keyboard** and exposes the raw indentation and `(m'ss'')`. The cursor can land inside the timestamp and corrupt it.
- **B15** Exercise names are typed on the full IME every time. There are no suggestions or "repeat last workout" (`intake` #302-#314 plan this), and the IME's autocorrect can mangle abbreviations (`rl` → "re").
- **B16** The hero header, timer row and unit line take roughly 200 dp above the list. With the keypad (about 240 dp) up, only a few rows stay visible on small phones, hence the scroll anchoring hacks with fixed delays of 30/60/100/180 ms (`EditorListSection.kt:136-155`).

**Dead code** (the redesign can ignore or remove it): `components/NoteEditorRow.kt`, `NoteEditorHeader.kt` (the only workout-title input; the title is now never editable), `NoteEditorTopBar.kt`.
