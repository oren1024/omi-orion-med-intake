# OMI v0.1.0: your task list

Test build only. Do not enter real medical records until we agree a version is safe.

## Part 1: GitHub (once, on a computer)

- [ ] Sign in at github.com (create a free account if needed).
- [ ] Create a new **private** repository named `omi-orion-med-intake`. Leave "Add a README" unticked so it starts empty.
- [ ] Put the project in it. Easiest: tell Claude the repository exists and Claude pushes it. Otherwise unzip `omi-orion-med-intake-v0.1.0-source.zip` and drag the contents into the repository page (Add file, Upload files).
- [ ] Open the **Actions** tab. Wait for "Build OMI APK" to finish (a few minutes). If it fails, copy the error text to Claude.
- [ ] Publish the release: **Releases**, **Draft a new release**, choose tag, type `v0.1.0`, create it, title `OMI v0.1.0`, Publish. A minute later the file `OMI-v0.1.0.apk` appears on that release.
- [ ] Local folder on your computer: keep the unzipped project in a folder named `omi-orion-med-intake`.

## Part 2: each phone (do both)

- [ ] In the Files app, create `Documents/OMI` and `Documents/OMI-Backups`.
- [ ] Install FolderSync. Add your OneDrive account.
- [ ] FolderSync pair 1: `Documents/OMI` and OneDrive `/OMI`. Two-way. Sync on file change plus about every 15 minutes. Turn "sync deletions" off if offered.
- [ ] FolderSync pair 2: `Documents/OMI-Backups` and OneDrive `/OMI-Backups`. Upload only.
- [ ] Exempt FolderSync from battery saver.
- [ ] Open the release page in the phone's browser, download `OMI-v0.1.0.apk`, allow "install from this source" when asked, install.
- [ ] Open OMI. Pick Phone A on one phone and Phone B on the other.
- [ ] Tap Choose data folder, pick `Documents/OMI`, and allow access.
- [ ] Settings, Choose backup folder, pick `Documents/OMI-Backups`.

## Part 3: try it and report

- [ ] Phone A: add a medication. Wait about 15 minutes. Does it appear on phone B (Home, Refresh, Medications)?
- [ ] Phone B: log a dose of it. Does it appear on phone A?
- [ ] Log a blood pressure reading on each phone.
- [ ] Refill: add a refill of 30 and check "pills left" goes down by one per dose.
- [ ] Share a dose to your WhatsApp group in English, then switch to Hebrew in Settings and share another.
- [ ] Delete one entry on A. Does it disappear on B?
- [ ] Export a CSV and a PDF. Open them. Do the numbers match?
- [ ] Tap Back up now. Is there a zip in `OMI-Backups`?
- [ ] Tell Claude what worked, what looked wrong, and what was confusing. Screenshots are fine.
