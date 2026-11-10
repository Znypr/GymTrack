# 02 Logistics and technical requirements (seen 2026-11-10)

Sources: [closed-test rule](https://support.google.com/googleplay/android-developer/answer/14151465), [target API](https://developer.android.com/google/play/requirements/target-sdk), [16 KB](https://developer.android.com/guide/practices/page-sizes), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291), [account types](https://support.google.com/googleplay/android-developer/answer/13634885), [contact info](https://support.google.com/googleplay/android-developer/answer/10840893), [asset specs](https://support.google.com/googleplay/android-developer/answer/9866151), [text limits](https://support.google.com/googleplay/android-developer/answer/9859152). Items marked (unverified) come from third-party posts or my own knowledge.

## A. Account
- [ ] **Must | Missing | S** Play Console account. One-time $25 (third-party sources, not on fetched Google pages), 18+, government ID and card in legal name, verified email/phone. Personal accounts also verify a real Android device via the Play Console mobile app. Personal needs a legal address; developer email is public.
- [ ] **Must | Missing | S** EU trader status declaration (DSA). Contents not verified; read Google's "Trader status declaration" article before choosing. This decides whether address/phone are public.
- [ ] **Must | Missing | M** Personal vs organization. Google's page says organization is required for "health apps", but also lists "Activity and Fitness" as a Health apps declaration category. Whether a plain workout logger without medical claims needs an organization account is **unresolved** (forum reports conflict). Ask Play support or read the Health apps policy before paying. An organization needs a D-U-N-S number (free from Dun & Bradstreet, takes days to weeks); name and address must match its D&B profile. Google says the account type can be changed later (a third-party source says it is effectively re-registration).
- [ ] **Must | Missing | M** **New personal account gate:** closed test with at least 12 testers opted in continuously for 14 days, then "Apply for production" (3-part form: test feedback, app info, readiness). Review is usually 7 days or less, so budget about 3 to 4 weeks from first upload. Open testing does not count (third-party). Organization exemption: not stated by Google (third-party says exempt). Recruiting: YouTube/Shorts audience, r/androidapps and tester-swap communities, friends and gym members, a Google Group or email list of testers; keep 15 to 20 to survive dropouts; ask for feedback (the form asks for it).
- [ ] **Should | Missing | S** Android developer verification (sideloaded apps; starts Sep 2026 in BR/ID/SG/TH, global 2027, per press). Registering the Play account is expected to cover Play-distributed apps; the personal APK sideloads in docs/PHONE_INSTALL.md may need this later (unverified).

## B. Build and signing
- [ ] **Must | Missing | S** **Target API 36.** From Aug 31 2026, new apps and updates must target Android 16 (API 36). `app/build.gradle.kts` has `targetSdk = 35` and `compileSdk = 35`, so the first upload would be rejected. Bump both, test edge-to-edge/predictive-back behaviour, and revisit `configChanges` in the manifest.
- [ ] **Must | Missing | S** Android App Bundle. Play requires AAB for new apps. No `bundleRelease` in docs/RELEASE.md or CI; only `assembleRelease` and `assembleDebug`.
- [ ] **Must | Partial | S** Play App Signing. Enrol at first upload. The local `gymtrack-release.jks` becomes the **upload key** (gitignored: Have). Back it up in two places; losing it means a reset request. Existing sideloaded installs signed with this key can only be updated by Play builds if the same app signing key is imported at enrolment. Decide: import the current key or let Google generate one (then sideloaded users must reinstall, and the debug/release data is separate anyway).
- [ ] **Must | Have | S** Versioning: `versionCode` = YYYYMMDDNN (2026070701), monotonic, enforced by `validateReleaseConfig`; fits in int. `versionName` = 0.1.0. Play needs a higher code per upload; the current scheme works.
- [ ] **Should | Partial | M** R8: `isMinifyEnabled=false`, `isShrinkResources=false`, `proguard-rules.pro` is empty comments; docs/RELEASE.md says "separate compatibility PR". Not a Play requirement, but it cuts size and obfuscates; Room/Compose are R8-safe. If enabled, the AAB carries `mapping.txt` automatically; upload it for readable Vitals crashes. Needs release-build UI test pass and backup/restore round trip on the minified build.
- [ ] **Must (check) | Partial | S** 16 KB pages: required for apps targeting 35+ (updates blocked from **Feb 1 2027**). A pure Kotlin/Java app is already compatible. Verify no `.so` in the AAB (Compose, Room, DataStore ship none, I did not build to confirm); AGP 8.11.1 is above 8.5.1. Add an APK Analyzer or `bundletool` check to the checklist.
- [ ] **Should | Missing | S** App size: expected a few MB; check the AAB with `bundletool`. Play limit is 200 MB base.
- [ ] **Must | Partial | S** `allowBackup="true"` with template-default backup rules (empty, commented). Cloud backup would copy the Room DB under defaults. Decide deliberately (include DB, or exclude and rely on the in-app backup file), and describe it in the privacy policy. Relevant to #291/#292 data-safety bugs.

## C. Declarations (Policy > App content)
- [ ] **Must | Missing | S** Privacy policy URL (required even for no-data apps; needs a public page, e.g. GitHub Pages). Repo has none.
- [ ] **Must | Missing | S** Data safety: expected answers "no data collected, no data shared" (all data on device; CSV/backup go to user-chosen files; no INTERNET permission, verify merged manifest). Fitness info stored only locally need not be disclosed. If a crash SDK or network is added, this changes.
- [ ] **Must | Missing | S** Health apps declaration: mandatory for every app. Pick "Activity and Fitness"; training suggestions and the muscle map (#308/#309/#312) must stay "not medical advice" and avoid disease claims, otherwise the Medical categories and an organization account apply.
- [ ] **Must | Missing | S** Ads declaration: "No ads". Advertising ID: no ads ID used (target 33+ declares `AD_ID` in the manifest only if used; the app has none, merged-manifest check). 
- [ ] **Must | Missing | S** Content rating (IARC questionnaire): expect "Everyone"; no user content, no sharing.
- [ ] **Must | Missing | S** Target audience: 13+ or 18+, not children (avoids the Families policy). News app: No. Government: No. Financial features: None.
- [ ] **Must | Have | S** Permissions: manifest declares none; nothing to justify. (Foreground service for the timer would need a declaration if added.)
- [ ] **Must | Missing | S** App access: no login, so "all functionality available without special access".
- [ ] Not applicable: Health Connect (not used; would need its own declaration and access form).

## D. Store listing
- [ ] **Must | Partial | S** Icon 512x512 32-bit PNG, max 1024 KB: `app/src/main/ic_launcher-playstore.png` exists (verify size/alpha).
- [ ] **Must | Missing | M** Feature graphic 1024x500 JPEG/24-bit PNG, no alpha.
- [ ] **Must | Missing | M** Screenshots: minimum 2 total, up to 8 per device type, JPEG/24-bit PNG no alpha, sides 320 to 3840 px, long side at most 2x short; use 4+ at 1080x1920 phone for large-format eligibility. Tablet shots (4+, 7"/10") are recommended for ranking.
- [ ] **Nice | Missing | S** Promo video = YouTube URL (public/unlisted, embeddable, monetization off, not age-restricted). Fits the YouTube plan.
- [ ] **Must | Missing | S** Text: name 30 chars, short 80, full 4,000. Contact email, optional website.

## E. Quality and operations
- [ ] **Should | Missing | S** Pre-launch report runs automatically on test-track uploads (Robo test on Firebase devices). Fix crashes and accessibility flags; add robo login-free path (none needed).
- [ ] **Should | Missing | S** Crash/ANR: use **Play Vitals / Android Vitals** (no SDK, nothing leaves the app beyond Google's own opt-in device reporting). Keeps "no analytics". Avoid Crashlytics (it forces a Data safety "diagnostics" disclosure).
- [ ] **Nice | Missing | S** In-app review prompt (Play Review API) and in-app updates add `com.google.android.play:review/app-update` libraries; they are Play services calls, so re-check the Data safety answer first. Defer past 1.0.
- [ ] **Must | Missing | M** CI: `.github/workflows/ci.yml` runs `testDebugUnitTest lintDebug assembleDebug` only; no `bundleRelease`, no signing in CI, no artifact upload. Add a manual-dispatch workflow: secrets for the upload keystore, `bundleRelease`, attach AAB + mapping, optional Play upload via `r0adkll/upload-google-play` or Gradle Play Publisher (service account) (unverified choice).
- [ ] **Must | Partial | S** Tracks: internal (up to 100 testers, instant) → closed (the 12×14 gate) → [open optional] → production with staged rollout (start 10 to 20%, halt if Vitals regress). Rollout percentage is usable only once in production.

## Top 10 launch blockers
1. Raise `targetSdk`/`compileSdk` to 36 (rejection otherwise).
2. Decide personal vs organization (fitness/health ambiguity; D-U-N-S lead time).
3. Closed test: 12 testers for 14 days, then production application (about 3 to 4 weeks).
4. Privacy policy URL, hosted.
5. Produce a signed AAB (`bundleRelease`) and enrol in Play App Signing; decide on key import; back up the keystore.
6. App content forms: Data safety, Health apps, ads, IARC, audience.
7. Store assets: feature graphic, 2 to 8 phone screenshots, descriptions.
8. Release CI for AAB plus mapping.
9. Backup rules decision (`allowBackup` and the empty rules).
10. R8 enablement with validation, 16 KB `.so` check, Vitals monitoring set-up.

Unverified: $25 fee and device-verification details (third-party), EU trader/address visibility, organization exemption from the 12-tester rule, and whether a plain logger needs an organization account.
