# Feature intake, 2026-11-10: muscle map, smart suggestions, vision update

Pipeline: log → 8 role assessments (vision, UX, science, architecture, performance, visual design, minimal/intuitive, critical review) → elaborate → prioritize. A 9th role, the code-vs-vision auditor, is still running.
Inputs: the user's requests (muscle map and planning; type less with evidence-based suggestions and a goal switch; vision update).
Status: **filed 2026-11-10.**
- P1 bugs from the code audit: #299, #300, #301.
- Queue tickets 1–13: #302–#314, in order.

User decisions (decision deck), all as recommended:
- accept by tapping the pill;
- teal + hatch colours;
- ~26 regions;
- planning v1 = start-from-last plus a weekly card;
- mistakes: suggest a fix, never auto-correct;
- history suggestions on, coaching opt-in;
- neutral volume labels.

Added by the user: warm-ups, rest and tempo are **inferred** from loads and timestamps, not typed (#306).

## Vision update (to record in the charter)
> Save as much time as possible during the workout without losing functionality. Make tracking very easy and planning easy. Identify mistakes, correct the user and help. Be lightweight and responsive. Give visual guidance.

Charter changes proposed by the vision role:
- add a 5th test, "saves time during the workout";
- add the principles "Guidance, not autopilot" and "Lightweight & responsive (perf budget on the logging screen)";
- add the non-goals "medical/injury advice, AI-generated programs";
- promote planning/templates/guidance to a named roadmap phase.

## Where the roles agree
- **Keep every capability and simplify the surface.**
  - The live map becomes a collapsed chip row.
  - The summary becomes a sheet or card, not a screen.
  - Plan lives in Stats, not as a new top-level screen.
  - Settings become defaults plus long-press overrides.
- **Never save anything silently.** Every suggestion or fix needs a tap. Observed history and coaching policy are labelled separately.
- **Mistake detection is missing from both drafts**, and it is the cheapest high-value piece. It becomes its own ticket, ahead of suggestions, because bad input would poison suggestions and muscle math.
- **A typed warm-up/working set type is a hard prerequisite.** Without it, PRs and volume get corrupted.
- **Performance gates are release gates.**
  - No parsing or history query on Enter.
  - Derived indexes are written at Finish.
  - The editor reads an in-memory snapshot.
  - Budgets: Enter ≤ 16 ms main thread, tally ≤ 2 ms, cold start +0 ms.
- **Data-safety P1s come first:** #291 (settings race), #292 (delete without undo).
- **B (suggestions) gets folded into #178** as children, not a parallel epic.

## Science corrections (literature-checked, with citations in the science report)
- 0.5 credit for secondary muscles: supported (Pelland et al.). 10–20 weekly sets: supported, with diminishing returns.
- **"≥4 sets per workout = trained" is unsupported.** Show counts only. Use neutral weekly labels: none / low 1–4 / moderate 5–9 / high 10+. Warn on **0 sets for ≥2 weeks**.
- **The fat-loss preset had shorter rest: unsupported.** Use the same rest as hypertrophy, keep pre-diet volume (≥8–10/muscle/week) and keep loads progressing.
- Strength preset 1–6 reps, 3–5 min rest: supported. Warm-up ramp and double progression: expert practice.
- Mistakes the app can detect with evidence behind them:
  - more than ~11 sets for one muscle in one session (diminishing returns; preprint);
  - RIR ≥5 on hypertrophy work;
  - a stall over ~3 sessions;
  - rest under 60 s on compounds;
  - a muscle neglected for 2+ weeks;
  - warm-ups counted as volume.
- Every coaching screen carries the disclaimer "not medical advice".

## Contested decisions (the user decides)
1. **How to accept a suggestion.**
   - UX: Enter stays "next row", and a ghost pill needs an explicit tap, so nothing gets logged by accident.
   - Visual and minimal: Enter accepts.
   - Lead recommendation: **tap the ghost pill, Enter unchanged**.
2. **Muscle colours.**
   - You asked for red / light red / gray. Visual design: red collides with the Push category and with error, light red vs red is only 1.74:1, and colour-blind users can't tell them apart.
   - Alternative: a teal ramp with hatching for "low" and an outlined "unmapped" state.
   - Lead recommendation: **teal ramp + hatch**.
3. **Planning in 1.0?**
   - Roles: v1 = "Start from last workout" plus a weekly coverage card. Templates and a week planner come after 1.0.
4. **Suggestions on by default, or opt-in?** Lead recommendation: last-session prefill on by default; coaching policy (goal presets) opt-in.
5. **Mistake hints: suggest only, or auto-correct?** Lead recommendation: **suggest only, one-tap fix**.

## Proposed queue (ranked)
| # | Ticket | P | Area | Depends on |
|---|---|---|---|---|
| 0 | #291 settings race · #292 delete undo (filed) | P1 | data | — |
| 1 | Charter + roadmap update with the vision (docs PR) | P2 | governance | — |
| 2 | Quick perf fixes: hoist the Regex in `NoteEditorState`; stop the full reparse in `StatsViewModel` | P2 | editor, stats | — |
| 3 | **Input guardrails**: inline check at Enter (decimal slip, >2× recent max, kg/lb mismatch, duplicate set, unparsed line), one-tap fix or keep, flagged sets excluded from stats until confirmed, false positives under 2% | P2 | parser, editor | 2 |
| 4 | **Start from last workout**: prefill exercise names with empty set rows (simplest planning) | P2 | editor | #178 ph.1 |
| 5 | **Set type** warm-up/working: typed field, Room migration, backup field, parser token, stats/PR filters | P2 | data | 0 |
| 6 | **Ghost prefill** from last session plus double progression, with a "why" on tap (#178 child) | P2 | editor | 3, 5 |
| 7 | Derived indexes `exercise_recent` + `muscle_week`, written at Finish; Macrobenchmark gate (40 Enter presses on a 1,000-workout DB) | P2 | data, build | 2 |
| 8 | Muscle taxonomy (17-value enum) + seed in code + resolver (user override → seed → parent → unmapped) + pure calculator; ADR 0003 | P3 | data | 7 |
| 9 | `exercise_muscles` table (user edits only), migration, backup, long-press "Edit muscles" | P3 | data | 8 |
| 10 | Body map component (Canvas paths, hit-test, accessible states incl. unmapped) + post-Finish sheet + Stats weekly card | P3 | ui, stats | 8 |
| 11 | Training-policy doc (`docs/TRAINING_POLICY.md`, citations) + goal presets "Build muscle / Lose fat / Get stronger" + suggestion engine (#178 children) | P3 | data, editor | 6 |
| 12 | Live muscle chip row in the editor (collapsed, perf-gated) | P3 | editor | 7, 10 |
| 13 | Planner: templates + "Fill gaps" + planned preview in Stats (separate epic, after 1.0) | P3 | workouts, ui | 4, 10 |

## Body map accuracy requirement (user, 2026-11-10)
The current canvas mockups are placeholders. The user's words: "good enough for now, but not good enough later, its not scientific enough, not accurate enough." The production body map must meet these requirements:
- **Anatomically accurate muscle shapes** drawn from real anatomy references, not ellipses. Correct origin/insertion outlines, proportions and overlaps (e.g. pec over front delt, lats wrapping to the front, traps upper/mid/lower).
- **Scientifically meaningful granularity.** Regions match how exercises actually load muscles. Candidates:
  - front/side/rear delts;
  - upper vs mid/lower chest;
  - upper/mid/lower traps;
  - lats vs rhomboids/teres;
  - biceps and brachialis;
  - triceps (long head split?);
  - forearm flexors and extensors;
  - rectus abdominis and obliques;
  - erectors;
  - glute max and med;
  - quads (rectus femoris / vasti);
  - adductors;
  - hamstrings;
  - gastrocnemius and soleus;
  - tibialis.

  The exercise→muscle seed mapping (ticket 8) uses the same taxonomy, with a source for each mapping (EMG/biomechanics literature or a reputable exercise database).
- **Art provenance.** Either original artwork traced from our own construction, or an asset with a licence compatible with distribution, checked before use. No copied app art.
- **Validation.** A review by the science role; the shape matches a reference at front and back; every region is tappable (≥48 dp hit area) and labelled.
- Scope: affects tickets 8 and 10. Taxonomy granularity becomes an open decision (more regions = more accurate, but more mapping work).

## Mockup revisions queued (after decisions 1–2)
- Swap the muscle palette and add hatch and "unmapped" states.
- Restyle the ghost row as a white dashed pill; amber stays for the primary action only.
- Add a mistake-flag row (amber outline, "!" icon, reason, Fix/Keep).
- Plan: neglected list first, "Fill gaps" button.
- Summary as a bottom sheet ending with a "Next time…" line.
- Live map collapsed to a chip row.
- Light-mode frames.
