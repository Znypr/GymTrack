# Editor guardrails and rest timer (#304, #349, #366)

Code: `feature/editor/guardrail/` (rules, history, replay), `feature/editor/RestCountdown.kt`,
`feature/editor/components/RestTimer.kt`. Tests: `SetGuardrailsTest`, `NoteEditorGuardrailFlowTest`,
`GuardrailReplayTest`, `RestCountdownTest`, `ActiveSetScrollPolicyTest`.

## 1. Input guardrails (#304)

At Enter / Next a committed set is checked once. The set is **saved exactly as typed**; Enter is
never blocked and nothing is auto-corrected. A hint shows `!` + reason + **Fix** / **Keep**
(core `HintRow`: amber outline in Normal, red pen in Notebook), and the set row gets a warn outline
plus a `!` so colour is never alone.

| Rule | Fires when | Needs history | Fix button |
|---|---|---|---|
| Unparsed | the parser drops the line (#301 diagnostics: no weight to carry, lone number, 0 s timed set) | no | Edit (keypad) |
| Duplicate | same entry as the set before and under **15 s** since it | no | Remove (with Undo) |
| Decimal slip | typed weight has no `.`, is over **2×** the reference, and weight ÷ 10 is within **0.5–1.5×** of it | yes | Fix → `82.5` |
| Unit mismatch | as typed the weight is outside **0.5–1.9×** the reference, read in the other unit it is within **0.8–1.25×** | yes | Use lb / Use kg |
| Over recent max | over **2×** the reference | yes | Edit |
| High reps | over **50** reps and the exercise's history never had over 50 | yes | Fix → first digit (`60`→`6`) when it lands in the recent rep range, else Edit |

- **History gate:** at least **3** earlier workouts with the exercise (strict identity key, so a
  dumbbell and a machine variant, or L/R and Bi, never share a reference).
- **Reference** = max(recent max, heavier sets already logged today). Recent max = heaviest set of the
  newest **5** sessions, ignoring a session max above **2.5×** their median (an old unfixed `825`).
  Weights are compared in kg (1 lb = 0.45359237 kg), so kg and lb sessions mix.
- Only a weight **typed** on the set is checked; a carried weight (`8x`) was checked when typed.
  Timed sets skip the weight and reps rules.
- An edited old workout is checked against the other workouts only (its own sets are excluded).
- **Keep** is remembered per row for the session; changing the set's entry checks it again.
- Where it shows: the newest hint sits in the keypad row's reserved hint line, right under the set
  it is about (the keypad row does not move); older open hints sit under their own set.
- Cost: history is parsed once per editor open on `Dispatchers.Default`
  (`EditorViewModel.loadSetHistory`); Enter does one parse of the current exercise block plus a map
  lookup (tested under 2 ms; typically far less).
- Thresholds live in `GuardrailThresholds`.

**False-positive rate:** `replayGuardrails(notes)` replays a history oldest-first, each set checked
against only the workouts before it. On the synthetic 40-workout test history: 0 hints. **Not yet
measured on the user's real history** (needs the device DB); acceptance is under 2 %.

**Not done (needs core/Stats work):** excluding flagged sets from stats, suggestions and muscle load
until fixed or kept; persisting Keep across editor sessions; the coaching hints (rest < 60 s on
compounds, > ~11 sets per muscle, RIR ≥ 5).

## 2. Rest countdown (#349)

- Off by default. Turn on from **⋯ → Rest timer after each set**. From then every committed set
  starts a rest; the next set restarts it.
- Length: a length picked in **⋯ → Rest timer** wins; else the exercise's usual rest (median of
  the recent rests between sets, first set of each session excluded, rounded to 15 s, at least 3
  samples between 20 s and 10 min); else **2:00**.
- While it runs the ⋯ button shows the countdown (`1:47`), so the bar keeps its width on a 360 dp
  phone; **⋯ → Skip rest** stops it.
- At zero: one quiet double buzz (90 ms, low amplitude), no sound. Needs `VIBRATE` (normal
  permission, added to the manifest).
- No service and no notification: it lives in the editor's composition and ticks only while a
  rest runs. It does not fire when the app process is gone; battery impact not measured on device.

## 3. Active row above the bar (#366)

The active row (keypad row, else the focused exercise row) is re-revealed whenever the list's
viewport height changes (IME sliding away, keypad appearing) or a hint opens under it, instead of
on two fixed delays that lost the race on slow frames. A row cut by the bottom edge is pushed up
just enough; an off-screen row comes back with two rows of context. Scrolling by hand changes
neither trigger, so it is never fought.
