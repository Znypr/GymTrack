# 01 Product / UX lens: what a public v1.0 needs (seen 2026-11-10)

Repo evidence: open issues list, `strings.xml` (only `app_name`), AndroidManifest, build.gradle.kts, SettingsStore.kt, docs/ROADMAP.md, docs/RELEASE_MILESTONE_ROADMAP.md (M7 Public Polish = onboarding, support, crash policy: none started).
Competitor baseline: web search of 2026 comparison blogs (e.g. https://www.sensai.fit/blog/hevy-vs-strong-vs-fitbod-vs-jefit, https://www.strongermobileapp.com/blog/best-workout-tracker-apps). These are commercial blogs, so treat them as opinion. Not verified against official pages: rest-timer and export details of Hevy/JEFIT.

Stranger's test: install, understand in 30 s, log a full workout without losing data, trust it with months of history, still use it next week.

## 1. Data safety and logging reliability (biggest 1-star risk)
- [ ] **Must** | Partial | Fix P1 data-loss bugs: #291 settings race overwrites settings, #299 startup re-imports and downgrades workouts every launch, #320 half-typed set becomes real after kill, #325 past edits don't update Stats, #292 delete without confirm/undo, #301 parser drops valid sets and decimal commas (German users type "52,5"), #300 autocorrect renames real exercises | effort M-L
- [ ] **Must** | Partial | Stats must be correct: #321 overnight = 13 h, #322 unfinished counted, #326 kg/lb mixed, #323/#324/#319 wrong times. Wrong numbers = review text "stats are wrong" | M
- [ ] **Must** | Have | One-file backup/restore (#179 done, interruption validated). Gap: no preview/inspect (#197), no merge restore (#198), no encryption decision (#199) | S for copy, M-L for merge
- [ ] **Must** | Partial | Export is user-chosen and visible: CSV export exists, but #298 (choose save location) open. Users must leave with their data (Strong's export is a selling point) | S
- [ ] **Must** | Partial | Auto Backup: `allowBackup="true"`, but `backup_rules.xml` is the unedited template. Decide if workout DB survives phone change via Google backup (best free safety net for strangers); otherwise state it | S
- [ ] **Should** | Missing | Periodic backup reminder or auto-backup to a user-picked folder. Reinstall = total loss is the classic local-first review | M
- [ ] **Should** | Have | No silent changes: guardrails #304 flag mistakes with a one-tap fix; keep this principle for #300/#318 | M

## 2. First run, onboarding, empty states, discoverability
- [ ] **Must** | Missing | Home empty state (#294), input-format hints in empty editor (#295), first-use clarity (#297). The note-style logger with a custom keypad is unlike Strong/Hevy; a stranger will not guess "exercise line, then reps × weight" | S-M
- [ ] **Must** | Missing | Resume active workout from Home after Back (#293). Today Back can look like loss of the workout | S
- [ ] **Should** | Missing | 3-screen or interactive "log your first set" tutorial plus sample workout; short in-app "How logging works" page | M
- [ ] **Should** | Missing | In-app help/FAQ and feedback channel (Settings > Help, mailto or GitHub issues link, version + export-diagnostics button). Also needed for store listing | S
- [ ] **Nice** | Missing | Demo data toggle so Stats isn't empty on day 1 | S

## 3. Settings basics
- [ ] **Must** | Partial | Settings today: 24 h clock, rounding seconds, dark mode (boolean), categories, default weight unit. Missing: follow-system theme (dark_mode is a boolean, not System/Light/Dark), and #296 (can't remove default categories) | S
- [ ] **Must** | Partial | kg/lb: default exists, but #326 means units are not stored per set. Fix before release; US users will hit it at once | M
- [ ] **Should** | Missing | Weight increment step, rest-timer length (rest timers are table stakes in Strong/Hevy; roadmap puts configurable rest timer under "later") | M
- [ ] **Nice** | Missing | Per-app language via Android 13 locale picker (needs `locales_config.xml`) | S after localization

## 4. Localization
- [ ] **Must** | Missing | `strings.xml` holds only `app_name`; 26 hard-coded UI strings sites in Kotlin and no `stringResource` use anywhere. App is English-only and not translatable. Decision: ship v1.0 EN-only (honest on the listing) or extract strings (L) | M-L
- [ ] **Should** | Missing | German (developer's market, YouTube audience): extract strings, then values-de. Decimal comma input must work first (#301) | L
- [ ] **Must** | Missing | Locale-correct number/date/time formatting (not only strings) | S-M

## 5. Accessibility
- [ ] **Must** | Partial | Only ~26 contentDescription uses; the custom keypad, chips and chart are the risk. TalkBack pass on log-a-set and Stats; keypad buttons need labels/roles | M
- [ ] **Must** | Unverified | Font scaling 130-200% and contrast (notebook/handwriting theme #333 planned: check contrast and OFL font legibility); touch targets 48 dp | M
- [ ] **Should** | Missing | Chart alternatives (text summary of progression for TalkBack) | M

## 6. Devices: tablets, landscape, foldables, low-end
- [ ] **Must** | Unverified | Manifest handles `orientation|screenSize|screenLayout` config changes itself, no orientation lock, no window-size-class code (grep found none). Test landscape + split-screen + keyboard-open editor (#328 list jumps) on small phone and tablet. Play flags large-screen quality; acceptable as "phone-first, works on tablet" | M
- [ ] **Should** | Missing | Foldable/tablet two-pane (history + editor); defer | L
- [ ] **Must** | Partial | Performance on low-end phones: #303 editor/stats quick fixes, #307 macrobenchmark gate. Release build has `isMinifyEnabled=false` (bigger, slower start; see build lens). Test on a 3-4 GB RAM device with 1+ year of data | M
- [ ] **Must** | Have | Offline: fully local, no network permission in manifest. Strong privacy selling point; say so on the listing

## 7. Quality bar
- [ ] **Must** | Missing | Crash visibility: no crash reporter. Plan: closed test via Play, rely on Play Console vitals (Android vitals needs no SDK). Target: crash rate under ~1% daily-active users, ANR under 0.47% (Play bad-behaviour thresholds; verify current numbers in the policy lens) | S
- [ ] **Must** | Partial | Migration safety on upgrade: tested migrations exist; add an upgrade test from the oldest shipping DB to current | M
- [ ] **Should** | Missing | Undo for destructive actions everywhere (#292, #330 delete in editing rows) | S-M

## 8. Identity
- [x] Resolved 2026-11-12, renamed to ZnyprFit (#342). Was: **Must** | Partial | Name "GymTrack" is generic and probably crowded on Play (many "Gym Track" apps). Check store search for collision; consider a distinctive subtitle. Namespace is `com.example.gymtrack` internally; applicationId already `app.znypr.gymtrack` (fine, permanent) | S
- [ ] **Should** | Partial | Launcher icon padding/notebook mark (#246); check adaptive + monochrome themed icon | S
- [ ] **Must** | Missing | Store assets: screenshots showing the keypad flow (video-friendly: the developer can cut a 30 s demo), feature graphic, short copy | M

## 9. Competitor table stakes vs repo
| Expected by users | GymTrack |
|---|---|
| Log sets fast, history, per-exercise progress charts | Have (fast keypad is a differentiator) |
| Rest timer with notification | Partial (workout timer only, no rest timer) |
| Routines/templates, repeat last workout | Missing (#305, #314) |
| Prefill from last session | Missing (#308) |
| Exercise library with muscle info and search | Partial (catalog internal, no browsable library; Strong 200+, Hevy 400+, JEFIT 1,400+) |
| PR tracking / 1RM | Unverified, likely missing |
| CSV export/import (Strong format) | Have, with caveats |
| Bodyweight/measurements | Missing (roadmap "later") |
| Plate calculator, warm-up calc | Missing (#306 partial) |
| Wear OS, widgets, cloud sync, social | Missing: leave out |

## 10. Safe MVP scope: leave OUT of v1.0
Muscle map (#309-#311), suggestion engine (#312, avoids medical-claim exposure), planner (#314), handwritten import (#177), Notebook theme and fonts (#333), editor v2 layout (#332) unless done, sync/accounts/social, merge restore, encryption, Wear OS, bodyweight tracking, German if schedule is tight.
Position as: "the fastest note-style set logger; private, offline, no ads, no account".

## Top 10 blockers (rank by launch impact)
1. Data-loss/corruption P1s: #291, #299, #320, #292, #325 (+ #301/#300 silent parser changes)
2. Wrong stats: #321, #322, #326, #323
3. First-run: empty state, format hints, resume active workout (#294, #295, #293, #297)
4. Backup story users can act on: export location (#298), Auto Backup rules, restore preview (#197), reminder
5. In-app help + feedback/support channel + privacy copy in Settings
6. Localization decision: EN-only v1 or string extraction; decimal comma parser (#301)
7. Accessibility pass: TalkBack on keypad, font scale, contrast
8. Layout robustness: landscape/tablet/keyboard (#328), low-end performance (#303, #307), minify
9. Rest timer and repeat-last-workout (#305) as the minimum competitive features
10. Name/icon/store screenshots and crash monitoring plan (Play vitals) before a closed test
