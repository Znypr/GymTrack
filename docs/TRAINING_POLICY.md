# Training policy: goal presets and suggestions

Issues #312 (policy, goal presets, engine) and #308 (history pill in the editor). Decision record: [ADR 0004](decisions/0004-training-policy-and-suggestion-engine.md).
Code: `app/src/main/java/com/example/gymtrack/feature/suggest/` (`GoalPolicy`, `SuggestionEngine`), pure Kotlin with no Android imports.

> **Not medical advice; stop if pain.** Every coaching surface in the app shows this line. The numbers below are group averages from studies on healthy adults. They are not a prescription for any one person.

## 1. Two sources, always labelled

| Source | What it is | Default | Shown as |
|---|---|---|---|
| **History** | What *you* logged: last session's matching set, the smallest load step you have used, your usual rest, your last set count. | **On** for everyone. | "from your history" |
| **Policy** | Literature-based ranges for the goal you picked (table in §2). | **Off** until you pick a goal (Settings or the chip on Progress). | "your goal: …" and the disclaimer |

The engine keeps them apart. A policy number never replaces a history number without being labelled. Nothing is saved without a tap.

## 2. Goal presets (literature-checked)

| Goal | Weekly sets per muscle | Reps | RIR (reps in reserve) | Rest between sets |
|---|---|---|---|---|
| **Build muscle** | 10–20 [1][2] | 6–15 compound, 10–20 isolation [3][4] | 0–3 (isolation 0–2) [5][6] | 2–3 min compound, 1–2 min isolation, at least 90 s [9][10][11] |
| **Lose fat** | Keep pre-diet volume, at least 8–10 [12][13] | Same as Build muscle [3] | 1–3 [12] | **Same as Build muscle. Not shorter** [9][11] |
| **Get stronger** | About 4–10 on main lifts, plus accessories [14][15] | 1–6 on main lifts [3][4] | 1–3 [6] | 3–5 min on main lifts [15] |

What the code stores (`GoalPolicy`):

| Field | Build muscle | Lose fat | Get stronger |
|---|---|---|---|
| `weeklySetsPerMuscle` | 10..20 | 8..20 | 4..10 |
| `compoundReps` / `isolationReps` | 6..15 / 10..20 | 6..15 / 10..20 | 1..6 / 6..15 ¹ |
| `compoundRir` / `isolationRir` | 0..3 / 0..2 | 1..3 / 1..3 | 1..3 / 1..3 |
| `compoundRestSeconds` / `isolationRestSeconds` | 120..180 / 90..120 | 120..180 / 90..120 | 180..300 / 120..180 ¹ |
| `setsPerExercise` ² | 2..4 | 2..4 | 3..5 |

¹ Accessory work in the strength preset uses hypertrophy-style reps and moderate rest. This is **expert practice**, not a tested prescription.
² Per exercise per session: the weekly range spread over about 2 sessions and 1–3 exercises per muscle. This is **expert practice** (a planning convenience), not a study result.

Compound or isolation is guessed from the exercise name (`ExerciseKind.guess`: squat, press, row, deadlift, pull-up, dip, lunge … are compound; curl, raise, fly, extension, pushdown, calf … are isolation). When the guess is unknown, the engine shows the union of both ranges rather than a wrong one.

## 3. Expert practice (labelled as such in the app)

These are common coaching practice. They have not been tested as prescriptions in trials.

- **Warm-up ramp.** About 40 % × 8, 60 % × 5 and 80 % × 3 of the first working load, rounded down to your load step [16]. Shown only when a goal is set, inside the "why" panel. It is never typed for you. Warm-up sets are detected automatically from loads (#306), so they never count as volume.
- **Double progression.** Keep the load until every working set reaches the top of your rep range, then add the smallest load step and work back up from the bottom of the range. ACSM's progression guidance (add about 2–10 % once you exceed the target reps) [15] backs the direction of change. The exact rule is coaching practice.

## 4. What the suggestion engine does

Input: the last ≤ 5 finished sessions of the exercise, read from the derived `exercise_recent` index (#307, working sets only); the sets already typed today in this exercise block; the goal (or none).

1. **Matching set.** Today's working set *n* is matched to last session's working set *n*. Today's sets lighter than 80 % of last session's top load count as warm-ups and are skipped. When last session had fewer sets, there is no suggestion.
2. **Rep range** = lowest..highest working reps over the last ≤ 3 sessions (your own range, from history).
3. **Double progression.** When every working set of the last session was at one load and reached the top of that range, the suggestion is that load **+ one step**, with reps at the bottom of the range (at most 4 below the top). Otherwise the matching set is repeated as it was.
4. **Load step** = the smallest gap between two different loads you have used on this exercise, snapped down to a real plate or dumbbell increment (kg: 0.5, 1, 1.25, 2, 2.5, 5; lb: 1, 2.5, 5, 10). With no gap in your history, the step is 2.5 kg or 5 lb. The step is never more than 5 kg or 10 lb.
5. **Hard cap.** A suggested load is never more than one step above the matched set. A months-long simulation in the unit tests checks this (`SuggestionEngineTest`).
6. **No suggestion** (low confidence): no history; mixed kg/lb in the last session; a bodyweight, timed or unparsed matched set; a matched load more than 1.5 × the median top load of the other recent sessions (a typo that was kept); an unparsed set already typed in the block; a set above that has an open mistake hint (#304).
7. **Coaching extras** (only with a goal): the goal's rep, RIR, rest and set ranges for this exercise kind, the warm-up ramp, and the disclaimer.

Each output carries its source (`SuggestionSource.HISTORY` or `POLICY`).

## 5. Editor behaviour (#308)

- The active set row shows a grey dashed pill on the right, e.g. `history 8 × 82.5`, in the same order as the set rows (REPS × WEIGHT).
- **Tap fills the row.** Typing overwrites it. Enter / Next still means "next row". Nothing is logged until the user commits the row.
- **Long-press** shows the "why" (history lines, then goal lines and the disclaimer when a goal is set). It is never shown inline.
- The pill appears only on an empty active row in the normal (LIVE) keypad mode. Its slot sits inside the existing row, so it causes no layout shift.
- History is loaded **once** when the editor opens, off the main thread. Enter never queries the database.

## 6. Caveats

- **Averages.** Meta-analyses describe group means. Individual responses vary widely.
- **RIR self-rating error.** People misjudge reps in reserve, mostly underestimating how many reps they have left, and more so far from failure and on high-rep sets [7][8]. RIR ranges are guides, not targets to hit exactly.
- **Preprints.** [2] was a preprint when this policy was written. The per-session ceiling of about 11 sets for one muscle (used by the mistake hints) also comes from preprint evidence. Re-check both before release.
- **Diet phases.** The evidence for training in a deficit [12][13] is mostly from lean, trained people. Muscle gain is blunted in large deficits whatever the training [13].
- **Not medical advice.** The app does not screen for injury, illness or pregnancy. Stop if pain.

## 7. References

1. Schoenfeld BJ, Ogborn D, Krieger JW. Dose-response relationship between weekly resistance training volume and increases in muscle mass: a systematic review and meta-analysis. *J Sports Sci.* 2017;35(11):1073–1082.
2. Pelland JC, Remmert JF, Robinson ZP, et al. The resistance training dose-response: meta-regressions exploring the effects of weekly volume and frequency on muscle hypertrophy and strength gain. *SportRxiv* preprint, 2024.
3. Schoenfeld BJ, Grgic J, Van Every DW, Plotkin DL. Loading recommendations for muscle strength, hypertrophy, and local endurance: a re-examination of the repetition continuum. *Sports.* 2021;9(2):32.
4. Lopez P, Radaelli R, Taaffe DR, et al. Resistance training load effects on muscle hypertrophy and strength gain: systematic review and network meta-analysis. *Med Sci Sports Exerc.* 2021;53(6):1206–1216.
5. Refalo MC, Helms ER, Trexler ET, Hamilton DL, Fyfe JJ. Influence of resistance training proximity-to-failure on skeletal muscle hypertrophy: a systematic review with meta-analysis. *Sports Med.* 2023;53:649–665.
6. Robinson ZP, Pelland JC, Remmert JF, et al. Exploring the dose–response relationship between estimated resistance training proximity to failure, strength gain, and muscle hypertrophy: a series of meta-regressions. *Sports Med.* 2024.
7. Halperin I, Malleron T, Har-Nir I, et al. Accuracy in predicting repetitions to task failure in resistance exercise: a scoping review and exploratory meta-analysis. *Sports Med.* 2022;52:377–390.
8. Zourdos MC, Klemp A, Dolan C, et al. Novel resistance training–specific rating of perceived exertion scale measuring repetitions in reserve. *J Strength Cond Res.* 2016;30(1):267–275.
9. Schoenfeld BJ, Pope ZK, Benik FM, et al. Longer interset rest periods enhance muscle strength and hypertrophy in resistance-trained men. *J Strength Cond Res.* 2016;30(7):1805–1812.
10. Grgic J, Lazinica B, Mikulic P, Krieger JW, Schoenfeld BJ. The effects of short versus long inter-set rest intervals in resistance training on measures of muscle hypertrophy: a systematic review. *Eur J Sport Sci.* 2017;17(8):983–993.
11. Singer A, Wolf M, Generoso L, et al. Give it a rest: a systematic review with Bayesian meta-analysis on the effect of inter-set rest interval duration on muscle hypertrophy. *Front Sports Act Living.* 2024.
12. Helms ER, Fitschen PJ, Aragon AA, Cronin J, Schoenfeld BJ. Recommendations for natural bodybuilding contest preparation: resistance and cardiovascular training. *J Sports Med Phys Fitness.* 2015;55(3):164–178.
13. Murphy C, Koehler K. Energy deficiency impairs resistance training gains in lean mass but not strength: a meta-analysis and meta-regression. *Scand J Med Sci Sports.* 2022;32(1):125–137.
14. Ralston GW, Kilgore L, Wyatt FB, Baker JS. The effect of weekly set volume on strength gain: a meta-analysis. *Sports Med.* 2017;47:2585–2601.
15. American College of Sports Medicine. Position stand: progression models in resistance training for healthy adults. *Med Sci Sports Exerc.* 2009;41(3):687–708.
16. Haff GG, Triplett NT, eds. *Essentials of Strength Training and Conditioning* (NSCA), 4th ed. Human Kinetics; 2016. Warm-up chapter (practice guidance, not a trial).

The intake record `docs/intake/2026-11-10-feature-intake.md` holds the science role's corrections (for example, no shorter rest when losing fat).
