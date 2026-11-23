# Changelog

One entry per version, newest first. The version in `app/build.gradle.kts`
(`versionName`) and the git tag (`vX.Y.Z`) always match this file.

## v0.1.0: first test build

**Test only. Do not store real medical records in this version.**

Included:
- Medication list: name, Hebrew name, strength, form, shape, colour, usual quantity, notes
- Log a dose in a few taps, with an editable time and a reason
- Log blood pressure with sanity checks
- Pills left per medication (refill and recount)
- Medication page: first logged, last logged, total doses, pills left
- History with filter and text search; delete an entry
- WhatsApp message (English or Hebrew) with Share and Copy
- CSV export (doses, blood pressure) and a text PDF summary for a date range
- Backup to a zip file in a separate folder
- Data is plain JSON files in one folder you pick, ready for FolderSync

Differences from spec v0.1:
- Record ids are 8 characters, not 4, so two records can never share an id by chance
- Files are written directly under their final name, not through a `.tmp` rename
- Folder scan re-reads the folder on each refresh, with an in-memory cache only

Not included yet (planned):
- Restore from backup (backups are plain zips; unzip them with any tool)
- Editing a dose or reading (delete it and log it again)
- Medication photos, search database, reminders, dose schedules and adherence
- BP chart in the PDF, Hebrew in the PDF
- WhatsApp history import, optional encryption
- One message for several medications, weekly automatic backup
