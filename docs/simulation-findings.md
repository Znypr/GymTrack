# Simulation findings: months of daily use, run in minutes

Problems a daily user would only meet after weeks or months, found by simulating a year of training in compressed time.

## How it works

| Layer | What runs | Time | Status |
| --- | --- | --- | --- |
| Generator (`app/src/sharedTest/.../simulation/WorkoutHistoryGenerator.kt`) | Seeded and deterministic. 12 months of push/pull/legs, 4–6 sessions a week. Progressive overload, a deload every 6th week, missed weeks. Alias drift (`bench`/`BP`), typos, new exercises in weeks 8 and 12. Uni and SS work, timed sets, a kg → lb switch at month 6. Category add, rename and delete. Learnings text. Planted edge days: DST back and forward, year boundary, a midnight span, a week in New York, a late first exercise, killed mid-set, Back then Finish next morning, Back never finished followed by a new workout next day, two sessions on one day, and editing a workout from 7 months earlier. | – | done |
| JVM simulation (`LongHorizonSimulationTest`) | Each session is turned into the exact note the editor writes (`EditorTranscript`). It then runs through the real parser, canonical projector, exercise identity, timer state and Stats functions. Invariants are checked after every workout and every simulated week. | about 30 s for 3 seeds × 12 months (737 workouts, 9.4k sets) | **run, results below** |
| Device simulation (`androidTest/.../e2e/LongHorizonAppSimulationTest`) | The same histories go through the app's own save paths: draft (`NoteRepository.saveNote`), Finish (`WorkoutRepository.saveCompletedWorkout`), the startup path (`CanonicalImportRunner` + `checkAndMigrate`), timer, settings, and backup → restore. Every Nth workout plus all edge days is typed through the real editor and keypad. It also covers an old-schema (v8) database at month 0, the settings relaunch race (#291), and perf budgets at months 3, 6 and 12. | estimated minutes | **compiles; not run yet (no emulator)** |
| Single-workout UI suite (`WorkoutSimulationTest`) | Push day, interruption and resume, recreate, plus three probe tests. | seconds | compiles; not run yet |

Reproduce:

```bash
./gradlew testDebugUnitTest --tests com.example.gymtrack.simulation.LongHorizonSimulationTest
```

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.gymtrack.e2e.LongHorizonAppSimulationTest -Pandroid.testInstrumentationRunnerArguments.simSeed=11
```

The JVM report is written to `app/build/reports/simulation/jvm-long-horizon.md`. Device reports go to logcat (tag `WorkoutSimulation`) and to `/sdcard/Android/data/app.znypr.gymtrack.debug/files/simulation/`. The device tests run only on an emulator, because they wipe the debug app's data.

**Evidence column:**
- **JVM** means the real app code produced the violation in the JVM run.
- **code** means the finding comes from reading the code; a device check for it is written but has not run.
- **device** means the device run is still pending.

**Count** is how many sets, workouts or weekly checks hit the finding across seeds 11, 23 and 47. **Day** is the first simulated day for seed 11, where day 0 is Monday 2025-10-06.

## Findings

| # | Sev | Finding | First seen (seed 11) | Evidence | Count | Issue |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | HIGH | **Unfinished workout becomes un-finishable.** Leave a workout with Back, then start a new one the next day. The new workout takes over the timer, the old one loses its Finish button for good, and its sets never reach the `sets` table or the Stats progress charts. | day 95 (2026-01-09), workout 61 (old: 60) | JVM (`NoteTimerState` + `shouldShowActiveWorkoutControls`); UI check written | 3 | extends #293 |
| 2 | HIGH | **App start may downgrade finished workouts.** `CanonicalImportRunner` runs on every launch and re-projects every note with status `PARTIAL`. Any row that differs from the projection, which a `COMPLETED` row does, gets overwritten, so finished workouts drop back to PARTIAL and the import cost grows with history. | first relaunch after a Finish: day 7 (2025-10-13) | code; device check runs at every simulated relaunch | – | new |
| 3 | HIGH | **Editing a past workout does not update Stats charts.** A workout that is no longer active has no Finish button, and Back only saves a draft. The note changes, but the `sets` table keeps the old numbers forever (`checkAndMigrate` only repairs when the table is empty). | day 211 (2026-05-05), editing workout 10 | code; device check `LEGACY-STALE-AFTER-EDIT` | – | new |
| 4 | MED | **Uni flag set after Enter is lost.** If Uni is tapped after the exercise's Enter, the first set row was already created as Bi and every later set inherits that. Sets are saved bilateral while the header says Uni. | day 0 (2025-10-06), workout 0 | JVM | 534 sets | new |
| 5 | MED | **Uni flag leaks into the next exercise.** A new exercise row inherits the previous exercise's flag. Example: Cable Fly (SS) after Lateral Raise (Uni) is saved unilateral. | day 20 (2025-10-26), workout 15 | JVM | 144 sets | new |
| 6 | MED | **Aliases and typos split one exercise into several progress groups.** Confirmed splits: `BP`, `Curls`, `Pushdown`, `legpress`, `hang`, `split squat`, `romanian dl`, `Sqaut`, `Lateral Rasie`. Autocorrect already fixes some typos. | day 10 (2025-10-16), workout 7 (`Curls`) | JVM (parser identity, as stored) | 38 | new |
| 7 | MED | **Keypad sets store no unit in the canonical table.** `workout_sets.weight_unit` is NULL for every keypad set, contrary to docs/WEIGHT_UNITS.md. | day 0 | JVM | 734 workouts | new |
| 8 | MED | **Volume and progress mix lb and kg after a unit switch.** Volume and progress averages add lb and kg as if they were the same unit, and the progress SQL ignores `sets.weightUnit`. Best bench goes from 100 (kg) to 225 (lb), which is really 102 kg. | day 188 (2026-04-12) | JVM | 76 weekly checks | new |
| 9 | MED | **Late first exercise shifts canonical set times.** The grey column counts from the first exercise Enter, but `started_at` and the timer count from the + tap. Canonical set times end up early by the delay (−70 s in the first case). After a reopen the base switches, so rows mix two starting points. | day 15 (2025-10-21), workout 11 | JVM | 78 workouts | new |
| 10 | MED | **Half-typed set becomes a real set after a kill.** ON_STOP stamps a half-typed `8x8`, then the app is killed and the user taps Finish. The note saves a real 8×8 kg set, and canonical gets a nameless extra exercise that is marked NEEDS_REVIEW. | day 28 (2025-11-03), workout 20 | JVM (+ UI probe) | 3 | new |
| 11 | MED | **Overnight workout logged as 13 hours.** Back, then one forgotten set logged the next morning: the workout is saved as 804 min. Stats then quietly drops it from duration charts. | day 50 (2025-11-25), workout 35 | JVM | 3 (+135 weekly) | new |
| 12 | LOW | **Timer runs overnight.** The timer of a workout left with Back keeps running for 13 h with no prompt and no cap. | day 50 | JVM | 3 | #293 |
| 13 | LOW | **Stats counts unfinished workouts.** Stats "Workouts" includes workouts that were never finished. | day 97 (2026-01-11) | JVM | 118 weekly | new |
| 14 | LOW | **Card duration stops at the last set.** Saved duration ends at the last set, not at Finish, so the Home card shows less than the timer did. | day 12 (2025-10-18), workout 9 | JVM | 200 | new |
| 15 | LOW | **Insights always say kg.** Training insights label weights "kg" even after the switch to lb. | day 188 | JVM | 76 | new |
| 16 | LOW | **Default categories can't be renamed or deleted cleanly.** Renaming a default category keeps the old name, because defaults are re-added on load. Deleting a category leaves its workouts with a name the editor no longer knows; opening one falls back to Push and Back saves that change. | rename day 154 (2026-03-09); delete day 273 (2026-07-06) | code; device check written | – | #296 |
| 17 | LOW | **Old sets table has wrong per-set times.** Rows in the legacy `sets` table carry the exercise header's time, truncated to `0'00`, not their own. | day 0 | JVM (`EditorWorkoutTranscriptTest`) | all sets | new |

Also found by the single-workout suite (`WorkoutSimulationTest`; written, not yet run on a device):
- docs/EDITOR_SAVE_PIPELINE.md says Back finalizes; the code only saves a draft.
- Home has no in-progress marker (#293).
- A half-typed set loses the keypad after the app is backgrounded.

### Checked with no finding

These checks were run without finding a problem:
- **DST forward and back, year boundary, workout across midnight:** durations and weekly counts are correct, because elapsed times are epoch-based.
- **No lost or duplicated workouts:** every simulated workout's sets matched truth, apart from the phantom-set case (#10).
- **Weekly counts add up:** weekly counts summed to the workout count every week.

The timezone week shift (`WEEK-SHIFTED-BY-TIMEZONE`) never triggered, so it is untested rather than passed: with these seeds no New York evening session crossed a Sunday/Monday boundary in Berlin time.

## Performance (JVM, desktop; phone numbers come from the device run)

| Simulated point | Workouts | Parse all (Stats build) | Project all (startup import) | Insights |
| --- | --- | --- | --- | --- |
| month 3 | 59–62 | 22–33 ms | 48–70 ms | 21–38 ms |
| month 6 | 120–131 | 48–67 ms | 93–134 ms | 48–52 ms |
| month 12 | 243–250 | 92–133 ms | 202–344 ms | 147–166 ms |

The startup import grows linearly with history and re-runs on every launch (see #2). On a phone it is likely to cross 1 s within the first year.

Device budgets are defined in `PerfBudget` but not yet measured: Home 500 ms, editor 300 ms, Stats first build 2 s, Stats cached 300 ms, startup import 1.5 s, backup 3 s / 5 MB.

## Not verified

- Nothing in androidTest has run yet. The emulator boot is waiting on the user's go.
- Real process death is approximated by closing the activity while stopped. The ViewModels die, but the process stays alive.
- The backup → restore round trip, settings relaunch race (#291), migrated-DB history and all device performance numbers are written but not run.
- JVM findings 1, 4, 5, 9, 10 and 11 rely on `EditorTranscript` reproducing what the editor writes. On the device, `UI-NOTE-DIFFERS-FROM-MODEL` checks that against the real editor on every UI-sampled day.
