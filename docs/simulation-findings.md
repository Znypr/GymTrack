# Simulation findings: months of daily use, run in minutes

Problems a daily user would only meet after weeks or months, found by simulating a year of training in compressed time.

## How it works

| Layer | What runs | Time | Result |
| --- | --- | --- | --- |
| Generator (`app/src/sharedTest/.../simulation/WorkoutHistoryGenerator.kt`) | Seeded and deterministic. 12 months of push/pull/legs, 4–6 sessions a week. Progressive overload, a deload every 6th week, missed weeks. Alias drift (`bench`/`BP`), typos, new exercises in weeks 8 and 12. Uni and SS work, timed sets, a kg → lb switch at month 6. Category add, rename and delete. Learnings text. Planted edge days: DST back and forward, year boundary, a midnight span, a week in New York, a late first exercise, killed mid-set, Back then Finish next morning, Back never finished followed by a new workout next day, two sessions on one day, and editing a workout from 7 months earlier. | – | – |
| JVM simulation (`LongHorizonSimulationTest`) | Each session is turned into the note the editor writes (`EditorTranscript`), then run through the real parser, canonical projector, exercise identity, timer state and Stats functions. Invariants are checked after every workout and every week. | about 30 s; 3 seeds × 12 months (737 workouts, 9.4k sets) | pass |
| Device: `repositoryHistory_…` | Seed 11, 12 months (244 workouts) through the app's own save paths: draft (`NoteRepository.saveNote`), Finish (`WorkoutRepository.saveCompletedWorkout`), the start-up path (`CanonicalImportRunner` + `checkAndMigrate`) every simulated week, timer, settings events, backup → restore at months 4 and 10, and an edit of an old workout. | 825 s | pass |
| Device: `migratedDatabase_…` | An old v8 database is seeded at month 0, then 3 months of history run on top of it. | 71 s | pass |
| Device: `uiSampleDays_…` | Every 25th workout plus every edge day is typed through the real editor and keypad. Also covers kill/reopen, Back + next morning, orphan check, the settings relaunch race every 4 weeks, and the Stats screen at the end. | 1,241 s | pass |
| Device: `WorkoutSimulationTest` | Single push day, interruption and resume, recreate, and 3 probe tests. | 44 s | 6/6 pass |

All device runs used the Galaxy_S23_Ultra AVD (Android 16), headless and with audio off.

Reproduce:

```bash
./gradlew testDebugUnitTest --tests com.example.gymtrack.simulation.LongHorizonSimulationTest
```

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.gymtrack.e2e.LongHorizonAppSimulationTest -Pandroid.testInstrumentationRunnerArguments.simSeed=11
```

Findings and metrics are logged under the logcat tag `WorkoutSimulation`. The device tests run only on an emulator, because they wipe the debug app's data. Findings never fail a test; only a broken harness step does.

**Evidence column:**
- **device** means the real app on the emulator produced the finding.
- **JVM** means the real app code produced it in the JVM run.
- **code** means it comes from reading the code only.

**Day** is the first simulated day for seed 11, where day 0 is Monday 2025-10-06. **Count** is the number of sets, workouts or weekly checks that hit the finding, from the 12-month device run unless marked otherwise.

## Findings

| # | Sev | Finding | First seen (seed 11) | Evidence | Count | Issue / status |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | HIGH | **Every app start downgrades finished workouts.** `CanonicalImportRunner` runs on each launch, re-projects every note as `PARTIAL` and overwrites `COMPLETED` rows. In the real UI, a workout finished right after a relaunch was already PARTIAL (workout 20). | day 7 (2025-10-13) | device | 51 of 51 weekly relaunches | #299 — **fixed in master by #352 (needs re-run on device)** |
| 2 | HIGH | **The start-up import grows with history and runs on every launch.** 0.2 s at 5 workouts, 1.7 s at month 3, 3.5 s at month 6, 6.7 s at month 12, with one spike of 11.7 s. | day 91 (2026-01-05) | device | 39 launches over budget | #299 — **improved in master by #352: second launch does zero canonical writes (needs re-run on device to measure)** |
| 3 | HIGH | **Unfinished workout becomes un-finishable.** Leave a workout with Back and start a new one the next day: the old one loses its Finish button for good, and its sets never reach the `sets` table or the Stats progress charts. | day 95 (2026-01-09), workout 60 | device (real UI) | 1 | #293 |
| 4 | HIGH | **Editing a past workout does not update the `sets` table.** The note changes, but Stats progress keeps the old numbers forever. Example: note `9x62.5`, table `9x57.5`. | edit day 211 → seen day 216 (2026-05-10), workout 10 | device | every later weekly check | #325 |
| 5 | MED | **Stats is slow and its cache rarely helps.** First build takes 2.2 s at month 3, 5.5 s at month 6 and 9.5–10.5 s at month 12. A second build at months 3 and 6 took as long as the first (2.2 s / 5.0 s); only at month 12 was it served from cache (55–63 ms). | day 91 | device | 3 | no issue yet |
| 6 | MED | **Keypad sometimes doesn't appear after an exercise Enter in a long workout.** Focus stays on the exercise line and the user has to tap the set row. It is intermittent: workouts 150 and 200 at row 21 in one run, workouts 88 and 123 in another. | day 227 (2026-05-21), workout 150 | device (real UI) | 4 across 2 runs of ~40 UI days | no issue yet |
| 7 | MED | **Reopening a long active workout doesn't bring the keypad back.** The editor jumps to the top, the open set row is below the fold, and the keypad only returns after the user scrolls down and taps the row. | day 50 (2025-11-25), workout 35 | device (real UI) | 1 | #293 |
| 8 | MED | **First rest of a late-started workout is measured from the + tap.** When the first exercise is typed a few minutes after tapping +, the first set's rest is counted from when the editor opened (6'10'' instead of 1'15''). This is consistent with the editor state being rebuilt after the first save of a new workout (the loading spinner briefly replaces the editor). | day 323 (2026-08-25), workout 212 | device (real UI) | 2 | #319 (related; root cause differs) |
| 9 | MED | **Uni flag set after Enter is lost.** If Uni is tapped after the exercise's Enter, its sets are saved bilateral. | day 0, workout 0 | device | 169 sets | #316 |
| 10 | MED | **Uni flag leaks into the next exercise.** A new exercise row inherits the previous exercise's flag, so Cable Fly (SS) after Lateral Raise (Uni) is saved unilateral. | day 20 (2025-10-26), workout 15 | device | 56 sets | #317 |
| 11 | MED | **Aliases split one exercise into several progress groups.** Confirmed: `BP`, `Curls`, `Pushdown`, `legpress`, `hang`, `split squat`, `romanian dl`, `Sqaut`, `Lateral Rasie`. | day 10 (2025-10-16), workout 7 | device + JVM | 38 (JVM) | #318 |
| 12 | MED | **Keypad sets store no unit in the canonical table.** `workout_sets.weight_unit` is NULL for every keypad set, contrary to docs/WEIGHT_UNITS.md. | day 0 | device | 243 workouts | #326 |
| 13 | MED | **Volume and progress mix lb and kg after a unit switch.** They add lb and kg as if they were the same unit, and the progress SQL ignores `sets.weightUnit`. Best bench jumps from 100 to 225, which is really 102 kg. | day 188 (2026-04-12) | device + JVM | 26 | #326 |
| 14 | MED | **Late first exercise shifts canonical set times.** The grey column counts from the first exercise Enter, but `started_at` and the timer count from the + tap, so canonical set times are early by that gap. | day 15 (2025-10-21), workout 11 | device | 22 workouts | #319 |
| 15 | MED | **Half-typed set becomes a real set after a kill.** ON_STOP stamps a half-typed `8x8`, the app is killed, and Finish then saves a real 8×8 kg set, plus a nameless canonical exercise marked NEEDS_REVIEW. After returning, the keypad is gone for that row. | day 28 (2025-11-03), workout 20 | device (real UI) | 1 | #320 |
| 16 | MED | **Overnight workout logged as 13 hours.** Back, then one forgotten set logged the next morning: the workout is saved as 804 min, and Stats then quietly drops it from duration charts. | day 50, workout 35 | device | 1 (+45 weekly) | #321 |
| 17 | MED | **Deleting a category orphans its workouts.** The workouts keep a name the editor no longer knows; opening one falls back to Push. | day 273 (2026-07-06) | device | 2 workouts | #296 |
| 18 | LOW | **Default categories can't really be renamed.** Renaming one keeps the old name too: the list becomes `[Upper, Leg Day, Push, Pull, Legs]`. | day 154 (2026-03-09) | device | 1 | #296 |
| 19 | LOW | **Stats counts unfinished workouts.** "Workouts" includes workouts that were never finished. | day 97 (2026-01-11) | device | 78 weekly | #322 |
| 20 | LOW | **Card duration stops at the last set.** Saved duration ends at the last set, not at Finish, so the Home card shows less than the timer did. | day 12 (2025-10-18), workout 9 | device | 66 | #323 |
| 21 | LOW | **Insights always say kg.** Training insights label weights "kg" even after the switch to lb. | day 188 | device | 26 | #326 |
| 22 | LOW | **Timer runs overnight.** A workout left with Back keeps its timer running for 13 h with no prompt or cap. | day 50 | JVM | 3 (JVM) | #293 |
| 23 | LOW | **Old sets table has wrong per-set times.** Rows in the legacy `sets` table carry the exercise header's time, truncated to `0'00`. | day 0 | device + JVM | all sets | #324 |
| 24 | LOW | **Save-pipeline doc contradicts the code.** docs/EDITOR_SAVE_PIPELINE.md says Back finalizes; the code only saves a draft, so 0 sets are written on Back. | – | device | – | no issue yet |
| 25 | LOW | **Home has no in-progress marker.** Nothing on Home shows that a workout left with Back is still running. | – | device | – | #293 |

### Re-run after rebasing onto master (2026-11-10)

The branch is rebased onto `origin/master` (#351, #352, #353, #354), and the JVM suite ran again: 135 unit tests, 0 failed, 2 skipped (the known deviations). Only the JVM side was re-run; the emulator was left off to save RAM.

- **#352 (closes #299)** fixes finding 1, and probably finding 2. Both need a device re-run to confirm, and #2 to measure the new start-up time.
- **#353 (closes #291, #292)** gives settings a single load and save path, and adds Undo for deleted workouts. The relaunch-race check never reproduced #291 before this change either. Delete is not part of the simulation.
- **#351 (closes #300, #301)** no longer autocorrects typos into a different exercise. As a result, finding 11 grows: alias-split hits went from 38 to 44, and `Leg Pres` now splits from day 19 instead of day 31. That matches #351's intent (no silent renames), so typo and alias grouping now depends on #318.
- **No other change:** all other JVM findings (9–16, 19–23) appear with the same first day and the same counts.
- **#354 (API 36, backup rules)** does not affect any finding.

### Checked with no finding

These ran on the device or JVM without finding a problem:
- **Backup → restore** at months 4 and 10 (2,516 and 6,570 records): no data change, and the manifest counts matched.
- **Settings relaunch race (#291):** 13 launches, settings kept every time, before #353. #353 now closes #291 in master.
- **v8 → current migration plus 3 months on top:** old workout and sets kept, unit = KG.
- **Time edges:** DST forward and back, year boundary, workout across midnight.
- **No lost or duplicated workouts:** workout counts and the Stats "Workouts" badge (244) matched what was done.
- **Weekly counts:** summed to the workout count every week.

The timezone week shift never triggered, so it is untested rather than passed: with these seeds no New York evening crossed a Sunday/Monday boundary in Berlin time.

## Performance (device, emulator on a desktop CPU)

| Point | Workouts | Home data | Editor open | Stats first | Stats second | Start-up import | Backup / restore / size |
| --- | --- | --- | --- | --- | --- | --- | --- |
| month 3 | 59 | 4–11 ms | 1–4 ms | 2.2–2.4 s | 2.2–2.3 s | 1.7 s | 161–214 ms / 311–336 ms / 82 KiB (month 4) |
| month 6 | 126 | 6–71 ms | 1–5 ms | 5.2–5.5 s | 5.0–8.4 s | 3.5 s | – |
| month 12 | 244 | 4–11 ms | 2 ms | 9.5–10.5 s | 55–63 ms | 6.7 s | 344–364 ms / 724–905 ms / 209 KiB (month 10) |

Launch to idle stayed at 0.5–0.9 s, because the start-up import runs in the background. It still burns 6–12 s of CPU on every launch at 12 months. A real phone will be slower than this emulator.

## Harness fix made during the run

- `NoteEditorState.finishExit` now calls `onSaveSuccess` on `Dispatchers.Main`. Under the Compose test scheduler, navigation after the timer-stop suspend resumed off the main thread, and every instrumented Finish crashed. In the app this changes nothing, because that scope already ran on main.

## Not verified

- **Real process death:** approximated by destroying the activity while it was stopped.
- **Other seeds on the device:** only seed 11 ran there; the JVM run covered seeds 11, 23 and 47.
- **Root causes of #6 and #8:** inferred from UI-tree dumps and timing, not proven.
- **Stats "second build" numbers:** may include a race with the cache write.
- **Timezone week shift:** never triggered (see above).
