# Release identity, signing and Play checklist

## Permanent Android identity

The permanent release application ID is:

```text
app.znypr.gymtrack
```

This ID is the installed Android app identity for release builds. Treat it as permanent after external distribution starts because changing it later creates a separate Android app with separate local data.

The Kotlin/Android namespace currently remains `com.example.gymtrack` as an internal source namespace to avoid a broad package refactor. It is not the public installed app identity.

## Debug identity

Debug builds use this installed application ID:

```text
app.znypr.gymtrack.debug
```

The `.debug` suffix keeps debug and release installs, launch icons, and app data separate. A debug build must not overwrite release app data.

## Versioning rule

Version codes use a monotonic date-plus-sequence format:

```text
YYYYMMDDNN
```

Example: `2026070701` is the first permanent-ID release baseline.

Rules:

1. Never reuse a version code.
2. Never decrease a version code.
3. Increment the trailing sequence when more than one release candidate is produced on the same day.
4. Update `versionName` for user-facing release labels.

`validateReleaseConfig` enforces the current permanent-ID baseline so accidental rollback is caught locally and in CI when enabled.

### Bumping the version for a Play upload

Google Play rejects an upload whose `versionCode` is not higher than every code already uploaded for the app, on any track. Per release:

1. In `app/build.gradle.kts`, set `releaseVersionCode` to the date plus a two-digit sequence, e.g. `2026111501` (a second candidate that day: `2026111502`). It must be higher than the last uploaded code.
2. Set `releaseVersionName` to the user-facing label, `MAJOR.MINOR.PATCH`, e.g. `0.2.0`.
3. Merge through a PR, then tag the merge commit `v<versionName>` (e.g. `v0.2.0`) and push the tag. The release workflow refuses a tag that does not match `releaseVersionName`.

Play's limit is 2100000000, so `YYYYMMDDNN` works until 2099. An upload that reached Play burns its code even if the release is discarded; bump the sequence and rebuild.

## Release signing policy

Release builds must never use the debug signing configuration.

The repository must not contain local release credentials or signing files. If local release signing is needed, keep the local properties file and keystore outside Git. Without signing material, release packaging fails with a clear message unless unsigned output is requested explicitly (see "Signed App Bundle" below); release compile, lint and unit-test tasks still run. The debug-signed `releaseCheck` build type is a separate app ID and is never uploaded.

## Signed App Bundle (AAB) for Google Play

Google Play only accepts Android App Bundles for new apps. Build one with:

```powershell
.\gradlew.bat bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`, signed with the **upload key**.

16 KB page sizes: the only native library in the bundle is `libandroidx.graphics.path.so` (from Compose); its 64-bit builds have 16 KB-aligned LOAD segments (checked 2026-11-10, AAB about 4 MB). Recheck when adding a dependency that ships `.so` files.

Where the upload key comes from, in order:

1. `release-signing.properties` in the repository root (local builds), created from `release-signing.properties.example`. `storeFile` is relative to the repository root or absolute. Prefer a keystore outside the repository (an absolute path such as `C:/Users/<you>/keys/znyprfit-upload.jks`); `*.jks` is gitignored either way.
2. If that file does not exist: the environment variables `GYMTRACK_UPLOAD_STORE_FILE`, `GYMTRACK_UPLOAD_STORE_PASSWORD`, `GYMTRACK_UPLOAD_KEY_ALIAS`, `GYMTRACK_UPLOAD_KEY_PASSWORD` (used by CI).

If neither is complete, `bundleRelease` and `assembleRelease` stop at the `requireReleaseSigning` task with an error that says what is missing. Compiling, linting and unit-testing the release variant still work without a key. Two deliberate ways around the stop:

- `-Pgymtrack.allowUnsignedRelease=true` builds an **unsigned** AAB/APK with a warning. CI uses it before the secrets exist. Play rejects unsigned uploads.
- The `releaseCheck` build type (`assembleReleaseCheck`) is the release build with identical R8 settings, signed with the local debug key and installed as `app.znypr.gymtrack.releasecheck`, so it sits beside the real app and never touches its data. Use it to smoke-test the minified build on a device. Never upload it.

### Create the upload key (once)

Run in a terminal (JDK `keytool`; Android Studio bundles one under `jbr\bin`). It asks for the passwords interactively, so they stay out of shell history:

```powershell
keytool -genkeypair -v -keystore C:\Users\<you>\keys\znyprfit-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Then create `release-signing.properties` in the repository root (gitignored, never committed):

```properties
storeFile=C:/Users/<you>/keys/znyprfit-upload.jks
storePassword=<store password>
keyAlias=upload
keyPassword=<key password>
```

If the earlier local `keystores/gymtrack-release.jks` exists you may use it as the upload key instead; the trade-off is below.

### Play App Signing and the upload-key role

Play App Signing is mandatory for new apps that publish AABs. There are two keys:

| Key | Who holds it | What it does |
|---|---|---|
| **App signing key** | Google, in Google's key management | Signs the APKs users actually install. Fixed for the life of the app. |
| **Upload key** | You (`.jks` file plus two passwords) | Proves an upload comes from you. Google checks it, strips it and re-signs with the app signing key. |

**Recommendation: let Google generate the app signing key** (the default at the first upload). Then nothing that matters for the app's whole lifetime lives on your PC, and a lost upload key is recoverable (see below). Uploading your own app signing key only helps when users already installed builds signed with that key from outside Play; for ZnyprFit the only sideloaded builds are your own. Consequence: a sideloaded `app.znypr.gymtrack` signed with the local key cannot be updated by the Play build. Make a `.gymtrack-backup` in the app, uninstall it, install from Play, restore.

Enrolment (at the first upload):

1. Play Console > your app > **Test and release > Testing > Internal testing > Create new release**.
2. At the app integrity / app signing prompt keep **Use Google-generated key** (default) and continue.
3. Upload `app-release.aab`. The key that signed this first upload is registered as the upload key.
4. Afterwards **Test and release > App integrity > Play app signing** shows both certificates. Note the SHA-256 of each in your password manager.

Play Console menu names change over time; check them when you get there.

### Back up the upload key (outside the repository)

Keep **two offline copies** of everything needed to sign: the `.jks` file, the store password, the key password and the alias (`upload`).

1. Copy the `.jks` to two separate offline media, e.g. two USB sticks kept in different places, or one USB stick plus an encrypted archive on an external drive. Not only on the PC, never in the Git repository, an unencrypted cloud-sync folder, email or chat.
2. Store the passwords in a password manager **and** on paper kept with one of the copies.
3. Test each copy: `keytool -list -v -keystore <copy>.jks -alias upload` must accept the password and print the upload certificate SHA-256 that Play Console shows.
4. Keep each release's `mapping.txt` next to the backup (see "Mapping files").

### If the upload key is lost or leaked

Because Google holds the app signing key, losing the upload key does not lose the app:

1. Generate a new upload key (same `keytool -genkeypair` command, new file name).
2. Export its certificate: `keytool -export -rfc -keystore znyprfit-upload-new.jks -alias upload -file upload_certificate.pem`.
3. Play Console > **Test and release > App integrity > Play app signing > Request upload key reset**; choose the reason and upload the `.pem`. Only the account owner can do this.
4. Wait for Google's confirmation email, which states from when the new key is accepted (typically a couple of days). Then update `release-signing.properties`, the GitHub secrets and both backups.

If it leaked (e.g. committed or pasted somewhere), also delete the GitHub secrets right away, request the reset, and report any upload you did not make.

## CI release workflow and signing secrets

`.github/workflows/release.yml` ("Release bundle") runs on manual dispatch (Actions > Release bundle > Run workflow) and when a tag `v*` is pushed. It runs `validateReleaseConfig bundleRelease` once (no matrix, Gradle cache via `gradle/actions/setup-gradle`) and uploads two artifacts:

- `ZnyprFit-<versionName>-<versionCode>-signed-aab` (or `-UNSIGNED-aab`), kept 30 days;
- `ZnyprFit-<versionName>-<versionCode>-mapping` (`mapping.txt` and R8 `configuration.txt`), kept 90 days.

A tag must equal `v` + `releaseVersionName`, or the run fails. Uploading to Play stays manual.

Without secrets the job still passes: it builds an **UNSIGNED** AAB and shows a warning. To make CI sign with the upload key, add these repository secrets (GitHub > repository > Settings > Secrets and variables > Actions > New repository secret, or `gh secret set`):

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | The `.jks` file, base64 encoded on one line |
| `UPLOAD_STORE_PASSWORD` | Store password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | Key password |

From Git Bash in the repository root (`gh secret set` without `--body` prompts for the value, so it never lands in shell history):

```bash
base64 -w0 /c/Users/<you>/keys/znyprfit-upload.jks | gh secret set UPLOAD_KEYSTORE_BASE64
gh secret set UPLOAD_STORE_PASSWORD
gh secret set UPLOAD_KEY_ALIAS
gh secret set UPLOAD_KEY_PASSWORD
```

How the workflow handles them: secrets reach only step environment variables; the keystore is decoded to the runner's temp folder and deleted at the end of the job; nothing is echoed (GitHub also masks secret values in logs). A signed run checks the AAB with `jarsigner -verify` and fails if it is not signed. Only people with write access can push tags or dispatch the workflow, and pull requests from forks never receive secrets. Because Play App Signing makes the upload key resettable, keeping it in GitHub secrets is an acceptable risk; skip the secrets and build locally if you prefer.

## R8 minification and resource shrinking

Release builds (and `releaseCheck`) run R8 with `isMinifyEnabled = true` and `isShrinkResources = true`; `validateReleaseConfig` fails if either is turned off. Keep rules live in `app/proguard-rules.pro`:

- `-keep enum com.example.gymtrack.** { *; }`: enum constant names are persisted (Room columns, backups, the stats cache) and read back with `valueOf()` or by name. Keeping them stops R8 from renaming, merging or unboxing them, which would make old data fall back to defaults.
- `-keepattributes SourceFile,LineNumberTable`: line numbers in Play Console crash traces after deobfuscation.

Nothing else needs a rule: Room 2.6 ships consumer rules for its generated `_Impl` classes, Compose, Navigation, Lifecycle and DataStore ship their own, and backup/cache JSON uses the framework's `org.json` with hand-written keys (no reflection, no serialization library).

When new code loads classes or members by name (reflection, `Class.forName`, a serialization library, `valueOf` on an enum outside `com.example.gymtrack`), add a rule and check `app/build/outputs/mapping/release/mapping.txt`.

### Mapping files

`mapping.txt` turns obfuscated stack traces back into readable ones.

- It is packed into the AAB (`BUNDLE-METADATA`), so Play Console uses it automatically for that upload.
- CI keeps it as an artifact per run for 90 days.
- For a permanent archive, copy `app/build/outputs/mapping/release/mapping.txt` of every uploaded build to the offline backup as `mapping-<versionCode>.txt`. If a build ever lacks it in Play, upload it under **App bundle explorer > (version) > Downloads > ReTrace mapping file**.

Before opening a release PR, check:

```powershell
git status --short
```

Do not commit local signing files.

## Target API level

`compileSdk` and `targetSdk` are 36 (Android 16), the Google Play minimum for new apps and updates since 2026-08-31 (#336). AGP 8.11.1 supports API 36, so no toolchain bump was needed. Android 16 behaviour reviewed for this app:

- Edge-to-edge: already enforced since target 35; the app never used `android:windowOptOutEdgeToEdgeEnforcement`, so nothing changes.
- Predictive back: `android:enableOnBackInvokedCallback="true"` was already set and the editor uses Compose `BackHandler` (no `onBackPressed()` override, no `KEYCODE_BACK` handling), so back keeps working with the system animation.
- Large screens (sw600dp+): Android 16 ignores orientation, resizability and aspect-ratio limits. The app declares none, so tablets and foldables behave as before. `configChanges` stays, so rotation and resizing do not recreate the activity.

Each future target bump needs the same review plus a smoke test on an emulator of the new API level.

## Android Auto Backup

Auto Backup is on with explicit include-only rules (#339): Room database and settings only. Rules and reasons are in `docs/BACKUP_FORMAT.md`. Keep `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml` in sync when adding stored files.

## Validation commands

From the repository root on Windows:

```powershell
.\gradlew.bat validateReleaseConfig
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleReleaseCheck
.\gradlew.bat bundleRelease
```

`bundleRelease` needs the upload key (see above). On a low-RAM PC run one command at a time, e.g. `.\gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=-Xmx1536m" bundleRelease`, then `.\gradlew.bat --stop`.

Optional emulator/device validation:

```powershell
.\gradlew.bat connectedDebugAndroidTest
adb install -r app\build\outputs\apk\releaseCheck\app-releaseCheck.apk
```

## Release checklist

### Before every upload

- [ ] `testDebugUnitTest`, `lintDebug`, `assembleDebug` pass (locally or in CI) on the release commit.
- [ ] Device run of the **minified** build: install `app-releaseCheck.apk`, then create a workout, edit and finish it, open Stats, switch weight unit, make a backup, restore it and check counts and units, rotate the screen. Optionally `connectedDebugAndroidTest` on an emulator.
- [ ] `releaseVersionCode` bumped above the last upload, `releaseVersionName` updated, PR merged, tag `v<versionName>` pushed.
- [ ] Release bundle workflow green and **signed** (artifact ends `-signed-aab`), or `bundleRelease` built locally with the upload key.
- [ ] `mapping.txt` for this `versionCode` copied to the offline backup.
- [ ] Release notes written (what changed, plus any backup reminder required by `docs/PRE_MIGRATION_BACKUP_POLICY.md`).
- [ ] `git status --short` shows no keystore and no `release-signing.properties`.

### Play tracks, in order

1. **Internal testing**: up to 100 testers, available within minutes. Upload here first, install from the Play opt-in link on your own phone and smoke-test. Read the **pre-launch report** (automatic Robo crawl on Google's test devices) for crashes and accessibility warnings.
2. **Closed testing**: the gate for a new personal developer account is at least **12 testers opted in continuously for 14 days**, then **Apply for production** on the Dashboard. Recruit 15 to 20 so dropouts do not break the count, and collect feedback; the application asks about it.
3. **Production, staged rollout**: start at 10 to 20 %, watch Vitals for 2 to 3 days, then 50 %, then 100 %. Halt the rollout on the release page if something regresses; fix, bump `versionCode`, upload again.

### Android Vitals checks (at each rollout step)

- Play Console > **Monitor and improve > Android vitals > Overview**: user-perceived crash rate and ANR rate. Google's bad-behaviour thresholds are 1.09 % crashes and 0.47 % ANRs overall (8 % on any single device model); stay well below them.
- **Crashes and ANRs**: open every new cluster. Traces are deobfuscated with the uploaded `mapping.txt`; a trace full of short names like `a.b.c` means the mapping is missing for that version.
- Compare the new version with the previous one before raising the rollout percentage.
- Policy numbers seen 2026-11-10; check Play Console for current values.
