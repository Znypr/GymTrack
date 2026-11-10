# ZnyprFit Privacy Policy

**Version 1.0 — effective 2026-11-10**

> Draft by the developer, not a lawyer. Points marked **[pro]** should be checked by a German lawyer before publication. Remove these notes and the [pro] markers from the public page once checked.

This policy covers the Android app **ZnyprFit** (package `app.znypr.gymtrack`, formerly "GymTrack") and this website.

## Short version

- ZnyprFit works **offline**. It has no account, no ads, no analytics and no tracking.
- The app has **no internet permission**. It cannot send your data to us or to anyone else.
- Your workouts stay **on your phone**. We, the developer, never receive them.
- Android may back up your data to **your own Google account** (Auto Backup). You can turn this off.
- You can export backup and CSV files. Those files are yours, and you decide where they go.

## 1. Who is responsible (controller)

The publisher of ZnyprFit is an individual developer in Germany:

ZnyprFit — [NAME — to be filled by the owner]
[ADDRESS — to be filled by the owner]
Email: [znyprfitness@gmail.com](mailto:znyprfitness@gmail.com)

Full details are in the [Impressum](IMPRESSUM.md). **[pro]** Confirm whether the developer counts as a "controller" under the GDPR at all, given that no personal data reaches the developer.

## 2. What data the app handles

Everything you enter stays in the app's private storage on your phone:

- workouts: dates and times, exercises, sets, reps, weights and units;
- your notes and learnings for a workout (these may include things like how you felt or an injury, if you choose to write that);
- your categories and their colours;
- app settings (for example dark mode, 24-hour clock, default weight unit, rounding);
- the start time of a running workout timer.

Workout data and notes can be **health-related data** (Art. 9 GDPR). That is one reason it never leaves your device through us.

ZnyprFit does **not** collect: your name, email address, location, contacts, photos, device identifiers, advertising ID, usage statistics or crash reports. It has no third-party SDKs for ads, analytics or tracking.

## 3. Where your data is stored

- **On your phone**, inside the app's private storage. Other apps cannot read it.
- **Android Auto Backup** (see section 4), in your own Google account, if your phone's backup is on.
- **Files you export** (see section 5), wherever you choose to save them.

There is no ZnyprFit server. We have no copy of your data.

## 4. Android Auto Backup

ZnyprFit allows Android's built-in backup so you do not lose your history when you change or reset your phone. This is a feature of Android and your Google account, not a service we run.

- **What is backed up:** the workout database and your settings. The running timer, a rebuildable statistics cache and old CSV copies are excluded.
- **Where it goes:** to the backup storage of **your own Google account**, or directly to your new phone during device-to-device transfer. We cannot access it.
- **Encryption:** on Android 12 and later, ZnyprFit only allows cloud backup when your phone has a screen lock, so the backup is end-to-end encrypted with your screen lock. On Android 9–11 Google encrypts it end-to-end when a screen lock is set. On Android 8 Google encrypts it, but not end-to-end.
- **Restore:** when you install ZnyprFit on a phone signed in to the same Google account, Android restores the data automatically.
- **Turn it off:** in your phone's system settings (usually *Settings → Google → Backup*). Google's own privacy terms apply to your Google account backup.

## 5. Backup and CSV files you export

In the app's settings you can create a one-file backup (`.gymtrack-backup`) or export CSV files.

- These files contain your workouts, notes and (for backups) your settings.
- They are **not encrypted**. Anyone who gets the file can read it.
- You choose where to save or send them (for example your phone storage, a cloud drive or a messenger). The privacy terms of that place apply. ZnyprFit does not upload them anywhere by itself.
- Keep them somewhere safe, and delete copies you no longer need.

## 6. Contact by email

If you email us, for example for support or feedback, we receive your email address and whatever you write. We use it only to answer you and to improve the app. Legal basis: Art. 6(1)(b) GDPR (your request) or Art. 6(1)(f) GDPR (our interest in answering and improving the app). We delete the emails when the matter is done, unless the law requires us to keep them. Our email is hosted by Google (Gmail), so Google processes these emails as our provider. **[pro]** Check the wording for the Gmail provider and any transfer outside the EU.

A planned in-app "Send feedback" button will only open your own email app with a prefilled message (for example the app version). Nothing is sent unless you press send yourself. *(Not shipped yet.)*

## 7. Google Play

You download ZnyprFit from Google Play. Google processes data for the store, installation, and Play Console statistics such as installs and crash reports from Android vitals, under Google's own privacy policy. We only see aggregated, anonymous statistics there. **[pro]** Confirm this description of Play Console data.

## 8. This website

This website is hosted on **GitHub Pages** (GitHub, Inc., USA). When you open it, GitHub processes technical data such as your IP address in server logs to deliver the page and keep it secure. We have no access to these logs. The site sets no cookies, has no tracking, no analytics and loads no external fonts or scripts. Legal basis: Art. 6(1)(f) GDPR. See the [GitHub Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement). **[pro]** Check the wording for the US transfer (GitHub is certified under the EU-US Data Privacy Framework — verify).

## 9. How long data is kept

- **In the app:** until you delete it. Deleting a workout removes it. **Uninstalling the app or using *Settings → Apps → ZnyprFit → Storage → Clear data*** removes all app data from your phone.
- **Auto Backup:** controlled by Google and your account settings. You can delete it in your Google account's backup settings.
- **Exported files:** until you delete them.
- **Emails:** see section 6.

## 10. Your rights

Under the GDPR you have the right to access, rectification, erasure, restriction of processing, data portability and to object (Art. 15–21 GDPR). For data in the app you can do all of this yourself: view, edit and delete your workouts, and export them as CSV or a backup file. For emails you sent us, contact us at the address above.

You also have the right to complain to a data protection supervisory authority, for example the one in your country or in the German federal state where the developer lives. **[pro]** Name the responsible authority once the address is fixed.

## 11. Children

ZnyprFit is meant for adults (18+). It is not designed for children, and we do not knowingly receive data from children.

## 12. Not medical advice

ZnyprFit is a training log. It does not diagnose, treat or prevent any injury or illness and is not a medical device.

## 13. Changes to this policy

If the app ever starts sending data anywhere (for example opt-in crash reporting or sync), we will update this policy **before** that version is released, ask for your consent where needed, and update the Google Play Data safety section. The date at the top shows the current version.

## 14. Contact

Questions about privacy: [znyprfitness@gmail.com](mailto:znyprfitness@gmail.com)

German version: [Datenschutzerklärung](DATENSCHUTZ.md).

---

## Appendix (internal, not for the public page): Google Play Data safety answers (#340)

Answers for Play Console → App content → Data safety, based on the facts above. Re-check if any network feature, SDK or crash reporting is added. **[pro]**/[unverified]: whether Auto Backup counts as "collection" — Google's guidance treats data that stays on the device or goes to the user's own backup via Android as not collected by the developer; verify in the current Play help before submitting.

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not shown when "No" above (no data collected) |
| Do you provide a way for users to request that their data is deleted? | Not shown when "No" above. (Users delete in-app or by uninstalling.) |
| Data shared with third parties | None |
| Privacy policy URL | `https://znypr.github.io/GymTrack/privacy.html` |

Consistency checks before submitting:
- [ ] Release `AndroidManifest.xml` has no `android.permission.INTERNET` (merged manifest, including libraries).
- [ ] No analytics, ads, crash-reporting or Firebase SDK in the release dependencies.
- [ ] Health apps declaration: "Activity and fitness".
- [ ] Target audience: 18+.
- [ ] In-app link to the privacy policy (#338 About/Legal screen).
