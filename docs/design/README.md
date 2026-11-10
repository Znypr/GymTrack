# Design specs (v2)

Source of truth for the redesign. Mockups for every screen are on the private "GymTrack Screens" canvas (sections: Editor proposal, Design language v2, Theme · Normal, Theme · Notebook, Anatomy v1).

| Spec | Ticket |
|---|---|
| [DESIGN_LANGUAGE_V2.md](DESIGN_LANGUAGE_V2.md): semantic tokens, Normal + Notebook themes, navigation | #333 |
| [EDITOR_V2.md](EDITOR_V2.md): editor display and reach rules (storage format unchanged) | #332 |
| [ANATOMY.md](ANATOMY.md) + [ANATOMY_MAPPING.md](ANATOMY_MAPPING.md): 26 muscle regions, sourced exercise mapping | #309, #311 |

## Decisions that override anything older in these files
1. **Set grammar is REPS × WEIGHT** (`8x 80` = 8 reps at 80). Never weight × reps. See `docs/EDITOR_BEHAVIOUR.md`.
2. **One hand.**
   - Every tappable control sits in the bottom ~60% of the screen. The top area is read-only.
   - No top-right actions. Back = system/edge swipe or a bottom control.
   - Bottom nav, active-workout bar, Finish and Next are all thumb-reachable.
3. **No repeats.** Information is shown once:
   - unit only when it differs from the default;
   - flag once per exercise (Bi = nothing shown);
   - category once;
   - one time per set row (the rest before it).
4. **Notebook visuals are hand-drawn.**
   - Graphite, wobbly re-traced strokes with weight variation.
   - Pencil hatching, no opaque fills, paper and ruled lines showing through.
   - Caveat/Patrick Hand text.
   - Never crisp vector "stickers". The Normal theme stays crisp.
5. **Colour roles have one meaning each.**
   - Red = the Push category and errors only.
   - Amber (Normal) / blue pen (Notebook) = the primary action.
   - Teal (Normal) / pencil hatching (Notebook) = muscles.
   - Mistake hints = amber outline + "!" + text (Normal) or red-pen correction (Notebook).
   - Never colour alone.
6. **Never save anything without a tap.** Suggestions are pills you tap; mistake hints offer Fix/Keep.
7. **Lightweight.** No parsing or history queries on the keystroke/Enter path; no added cold-start work (budgets in #307).
8. **App name is ZnyprFit** (#342); the package id stays `app.znypr.gymtrack`.
9. **New logic stays platform-neutral** (no new Android imports in parsing, stats, identity or domain code; time via `AppClock`), so a later iOS port stays cheap (#357, parked).
