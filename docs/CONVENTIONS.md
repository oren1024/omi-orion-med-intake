# Conventions

Keep names predictable so folders, releases and the repository stay tidy.

## Versions

- Format `MAJOR.MINOR.PATCH`, for example `0.1.0`.
- `0.x` means test: not for real data. The first version safe for real data is
  called `1.0.0`, and only after both phones have synced correctly for a while.
- A new test build that changes behaviour raises MINOR (`0.2.0`). A small fix
  raises PATCH (`0.1.1`).
- Three places always agree: `versionName` in `app/build.gradle.kts`, the git
  tag `vX.Y.Z`, and the top entry in `CHANGELOG.md`. Raise `versionCode` by 1
  with every release so Android accepts the new APK as an update.

## Files

| Kind | Pattern | Example |
| --- | --- | --- |
| APK | `OMI-vX.Y.Z.apk` | `OMI-v0.1.0.apk` |
| Git tag | `vX.Y.Z` | `v0.1.0` |
| Docs | `UPPER-NAME-vX.Y.md` or `UPPER-NAME.md` | `TASKS-v0.1.md` |
| Export | `OMI-<kind>_<from>_to_<to>.<ext>` | `OMI-doses_2026-10-15_to_2026-11-14.csv` |
| Backup | `omi-backup-<date>_<time>.zip` | `omi-backup-2026-11-14_0421.zip` |
| Record file | `<local time>_<phone>_<id>.json` | `2026-11-06T21-46-12_A_1a2b3c4d.json` |

## Commits

Short, present tense, one change each: `Add refill screen`, `Fix pills-left count`.

## Branches

`main` is the only long-lived branch. Larger experiments go on `feature/<name>`.
