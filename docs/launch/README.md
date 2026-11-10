# Google Play launch readiness (assessed 2026-11-10)

Four role reviews (product, logistics/tech, legal, marketing) assessed the repo at master `bf4f3fe` against Google Play's requirements and user expectations. Their full reports are in this folder:

| Report | Lens |
|---|---|
| [01-product.md](01-product.md) | What users need before a public release makes sense, with a competitor baseline |
| [02-logistics.md](02-logistics.md) | Play Console, SDK targets, signing, AAB, release CI, forms, assets |
| [03-legal.md](03-legal.md) | Privacy policy, GDPR, DSA trader status, Impressum, health data, licences, name |
| [04-marketing.md](04-marketing.md) | Positioning, EN/DE listing drafts, screenshots, promo/devlog/Shorts, onboarding, launch channels |

Policy facts come from web sources seen on 2026-11-10. Items the reports mark *unverified* must be checked in Play Console before relying on them. None of this is legal advice; items marked **[pro]** need a professional.

## Critical path (ranked)
1. **Data safety of the app itself.** Fix the P1 data-loss and corruption bugs: #291, #292, #299, #300, #301, #320, #325. Wrong-stats bugs follow: #321, #322, #323, #326.
2. **Target API 36.** New apps and updates must target API 36 since 2026-08-31; the repo targets 35. A first upload would be rejected.
3. **Name and brand clearance.** "GymTrack" already exists on Google Play (another developer) and on iOS. A distinctive name is needed before any assets are made; check EUIPO/DPMA. **[decision]**
4. **Account type and trader status.** Personal vs organization account (health-app ambiguity; D-U-N-S lead time for an organization). DSA trader declaration: traders' address and phone are shown publicly. **[decision, pro]**
5. **Privacy policy** at a public URL plus an in-app link. Add an About/Legal screen with privacy policy, Impressum link and open-source licence notices (Apache-2.0 requires notices).
6. **Auto Backup decision.** `allowBackup=true` with the template's empty rules sends data to the user's Google Drive by default. Decide, configure, and declare it consistently in the Data safety form (#199).
7. **Release pipeline.** Signed AAB (`bundleRelease`), Play App Signing, upload-key backup, R8/shrinking with mapping upload, and a release CI job.
8. **Play Console forms.** Data safety, Health apps declaration (Activity & Fitness), ads (none), content rating (IARC), target audience 18+.
9. **Closed test.** A new personal account needs ≥12 testers opted in for 14 continuous days before applying for production. Recruit 20+ early (~3–4 weeks of lead time).
10. **First-run and help.** Empty state, format hints, active-workout resume, UI clarity (#293, #294, #295, #297); in-app help/FAQ and a feedback channel.
11. **Store assets.** Icon check, feature graphic 1024×500, 2–8 phone screenshots (storyboard in 04), EN/DE descriptions, landing page with privacy policy and Impressum.
12. **Quality pass.** Accessibility (TalkBack on the keypad, font scale, contrast), localization (string extraction + German, decimal comma), layout robustness, low-end performance (#303, #307), and Vitals monitoring.

## What v1.0 can leave out
Muscle map (#309–#311), coaching suggestions (#308, #312), Notebook theme and design v2 (#333), and the planner (#314) are differentiators, not launch blockers. The store listing may only claim what the release build contains.

## Video and marketing (after the critical path)
- 30–60 s promo for the listing (YouTube URL).
- An 8–12 min "I built my own gym app" devlog.
- 5 Shorts concepts with hooks.

Full storyboards are in [04-marketing.md](04-marketing.md).
