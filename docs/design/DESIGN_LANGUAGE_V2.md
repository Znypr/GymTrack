# Design language v2 (two themes)
n> **Override (lead, 2026-11-10):** set grammar is REPS × WEIGHT (`8 × 80`), never weight × reps. `EDITOR_V2_RULES.md` and `EDITOR_LOGIC.md` override this file wherever they conflict.


## The user's request (2026-11-10)
> "a reworked overall design of the app, or at least proposals where it's one single design language, smarter and more intuitive navigation and screens, also different themes: a normal theme similar look to what we have now, and a theme which looks like a notebook, handwritten, pencil sketches etc."

## Design-language principles (shared by both themes)
1. **One component set, two skins.** Every screen uses the same components, layout and spacing. A theme only swaps tokens: colours, fonts, borders, textures.
2. **Semantic roles, not raw colours.** Roles:
   - `bg`, `surface`, `ink` (text), `inkMuted`;
   - `action` (primary action, one per screen);
   - `active` (current row/selection);
   - `warn` (mistake hint);
   - `error`;
   - `cat.push` / `cat.pull` / `cat.legs` (small dots or marks only);
   - `muscle.none` / `muscle.low` / `muscle.high` (pattern-based).
3. **The thumb zone carries the primary action.** Bottom area: Start / Resume / Next / Finish. Nothing important sits only top-right.
4. **State = shape + icon + text**, never colour alone.
5. **Spacing:** 4-pt grid (4, 8, 12, 16, 24, 32). Radii: 8 small, 14 cards, 24 sheets. Touch targets ≥44 px.
6. **Type scale:** display 28, title 22, body 15, small 14, label 12 (raised 2026-11-12: text read too small; Notebook small 16, label 14) (uppercase tracking 0.08em).

## New navigation (information architecture)
Today the app has a Home grid with top-right icons (Stats, Settings), the editor and back buttons. Proposal:
- **Bottom navigation, 3 tabs:**
  - **Today**: start/resume, this week at a glance.
  - **History**: past workouts.
  - **Progress**: stats, weekly muscle map and planning; Settings opens from a gear on this tab.
- **Active-workout bar:** while a workout is running, a slim bar sits above the bottom nav on every tab: "● Push · 24:10 · Resume ›". This fixes "where is my running workout" (#293).
- **Editor:** full screen, no bottom nav. Back returns to the previous tab. Finish opens the finish sheet, then lands on Today.
- **Today tab layout, top to bottom:**
  - greeting/date (small);
  - a **Next up card**: "Pull · usually Tue/Fri · last 5 days ago", with a big **Start Pull** action button plus "other workout ▾" and "start blank";
  - a **This week strip**: 7 day dots (done / planned / rest) and a mini muscle bar ("Not trained yet: calves, rear delts");
  - **Recent**: the last 3 workouts as compact rows, with "See all" going to History.
- **History tab:** a search field (exercise or note), category filter chips, and month sections with compact workout rows (category mark, day/date, duration, top sets). Long-press for multi-select, with Delete showing an Undo snackbar (#292).
- **Progress tab:**
  - range segmented control (4 W / 3 M / 1 Y / All);
  - weekly muscle card (body map + text list);
  - exercise progress chart card (pick an exercise);
  - consistency card;
  - "Plan" section (start-from-last templates; the full planner comes later);
  - gear → Settings.
- **Settings:** grouped list (Preferences, Theme: Normal / Notebook / System light-dark, Categories, Data: backup/restore). Not drawn this round unless assigned.

## Theme 1: "Normal" (refined current look)
- Dark OLED default: `bg` #000000, `surface` #1C1C1E, `surface2` #111113, `ink` #FFFFFF, `inkMuted` #A1A1A6, hairline #2C2C2E.
- `action` amber #F2B705 with black text. `active` = 1 px amber outline. `warn` = amber outline + "!". `error` #FF453A.
- Categories: Push #FF3B30, Pull #AF52DE, Legs #34C759, as dots and thin left bars only. **Never a full red header.**
- Muscles: teal ramp (#2DD4BF solid, #1A8A7C hatch, #3A3A3C + #6C6C70 outline).
- Font: Inter. Numbers use `font-variant-numeric: tabular-nums`.
- Bottom nav: #0B0B0C, 64 px, icons + labels; the active tab gets an amber icon plus label.
- Cards: radius 14, no shadows, 1 px hairline.

## Theme 2: "Notebook" (handwritten, pencil sketches)
The feel of a gym notebook: paper, pencil, pen, ruled lines, sketched boxes, margin notes. It must stay **readable and fast**: the handwriting style must never cost legibility of numbers.
- **Paper:**
  - `bg` #F6F1E4 (cream);
  - ruled lines `background-image: repeating-linear-gradient(to bottom, transparent 0 27px, #C9DAEA 27px 28px)`;
  - a left margin line at x=44 px, 1.5 px #E3A6A6 (faint, decorative);
  - optional subtle paper grain via an inline SVG `feTurbulence` at very low opacity.
- **Ink:** `ink` graphite #2B2A28, `inkMuted` pencil #6E6A62.
- **Roles:**
  - `action` = blue fountain-pen ink #1F4FB5 (buttons: sketched outline + light blue wash #DCE6F7, text #1F4FB5);
  - `active` = yellow highlighter swipe behind the row (`linear-gradient(transparent 20%, rgba(255,236,92,.65) 20% 85%, transparent 85%)`);
  - `warn` and `error` = **red-pen correction** #C62828 (circled value + handwritten margin note + arrow). Red here means correction only.
- **Categories:** small hand-drawn tab markers in the margin (Push ▲ in #C0504D, Pull ◆ in #7E57C2, Legs ● in #2E7D32). Always shape + colour.
- **Fonts (Google Fonts):**
  - headings and margin notes: **Caveat** 600/700;
  - UI text and set rows: **Patrick Hand** 400;
  - numbers in set rows: Patrick Hand at 17–18 px. **Readability over style.**
- **Sketched borders:** `border: 2px solid #2B2A28; border-radius: 255px 15px 225px 15px / 15px 225px 15px 255px;` (vary the radii per element so they don't look identical). Optional slight rotation (≤0.6deg) on stickers and notes only.
- **Icons:** inline SVG with a hand-drawn look: stroke 1.8, round caps, slightly irregular paths. Optional `filter: url(#rough)` with `<filter id="rough"><feTurbulence type="fractalNoise" baseFrequency="0.04" numOctaves="2" result="n"></feTurbulence><feDisplacementMap in="SourceGraphic" in2="n" scale="1.6"></feDisplacementMap></filter>` defined once per artboard in a hidden `<svg width="0" height="0">`.
- **Muscles:** pencil shading, pattern-based by nature. Mount BodyMap with `palette="pencil"` (none = outline only, low = light pencil hatch, high = dense cross-hatch).
- **Bottom nav:** like index tabs sticking out of the notebook's bottom edge: 3 sketched tabs with handwritten labels. The active tab is pulled up 4 px and filled paper-white #FFFDF6.
- **Keypad:** sketched keys on paper, Patrick Hand 22 px. The Next key is the blue-ink action.
- **Finish sheet:** a torn-paper or sticky-note style sheet over the notebook page.

## Example data (consistent everywhere)
Today is Mon 10 Nov 2026. Rotation Push → Pull → Legs. Last workouts:
- Fri 7 Nov Push 64 min (Bench 80×8);
- Wed 5 Nov Legs 71 min (Squat 110×5);
- Mon 3 Nov Pull 58 min (Lat Pulldown 65×10).

Next up = **Pull**. This week (Mon start): nothing done yet today. Week strip shows Mon (today, planned Pull), Wed planned Legs, Fri planned Push. Not trained in the last 7 days: calves, rear delts.

The active-workout example is the BRIEF.md Push data (24:10 elapsed). Progress: 38 workouts, 3.1 / week; Bench Press e1RM trend over 10 points 92 → 101 kg. Muscle week levels: chest 2, shoulders 2, triceps 1, lats 2, upperBack 2, biceps 1, quads 2, glutes 2, hamstrings 1, calves 0, rearDelts 0, abs 1.

