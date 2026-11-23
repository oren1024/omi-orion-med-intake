# OMI: Orion Med Intake

A personal Android app for logging medication intake and blood pressure.
Data is stored as plain JSON files in one folder (`Documents/OMI`) that
FolderSync keeps in step with OneDrive, so two phones share the same records.
The app never talks to a cloud itself, and no account or registration is needed.

**Status: v0.1.0, test build. Do not use it for real medical records yet.**

## How it fits together

```
Phone A: OMI app <-> Documents/OMI <-FolderSync-> OneDrive /OMI <-FolderSync-> Documents/OMI <-> OMI app: Phone B
                                  \__ share or copy __> WhatsApp group
```

- Every record is its own small file and is never rewritten, so two phones cannot conflict.
- A delete is a new "deleted" file, not a removal.
- WhatsApp gets a ready-made message through the share sheet. OMI cannot post by itself.

## Repository layout

| Path | What is in it |
| --- | --- |
| `app/` | The Android app (Kotlin, no third-party libraries) |
| `docs/` | Task checklist and conventions |
| `tools/foldersync-test/` | Optional script to test FolderSync on its own |
| `.github/workflows/` | Builds the APK on GitHub |
| `CHANGELOG.md` | What changed in each version |

## Getting the APK

GitHub builds it; nothing needs installing on a computer.

1. Push to `main`: the APK appears under Actions, in the run's Artifacts.
2. Publish a release with tag `vX.Y.Z`: the APK `OMI-vX.Y.Z.apk` is attached to
   the release page, which is the easiest place to download it on a phone.

See `docs/TASKS-v0.1.md` for the full step-by-step list.

## Naming rules

See `docs/CONVENTIONS.md`.
