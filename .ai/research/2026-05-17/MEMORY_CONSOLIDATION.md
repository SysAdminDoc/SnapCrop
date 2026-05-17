# Memory Consolidation

Research date: 2026-05-17

## Instruction Files Inspected

| File | Role | Reconciliation |
|---|---|---|
| `AGENTS.md` | Agent entry point | Points agents to `CLAUDE.md` and shared global rules. Locally updated with a pointer to `PROJECT_CONTEXT.md`; ignored by Git. |
| `CLAUDE.md` | Repo working notes | Contains current stack, architecture, version history, gotchas, and UI rules. Locally updated with a pointer to `PROJECT_CONTEXT.md`; ignored by Git. |
| `C:\Users\--\.claude\CLAUDE.md` | Shared behavior rules | Read and treated as global behavior guidance. |
| `C:\Users\--\CLAUDE.md` | Shared working protocol | Read and treated as global working protocol. |
| `C:\Users\--\.claude\projects\c--Users----repos\memory\MEMORY.md` | Shared project-memory index | SnapCrop entry pointed to current v6.19.0 state. |
| `C:\Users\--\.claude\projects\c--Users----repos\memory\snapcrop.md` | Shared SnapCrop memory | Matched live repo on v6.19.0, signing path, architecture, and near-term ideas. |
| `C:\Users\--\.claude\projects\c--Users----repos\memory\stack-android.md` | Android stack conventions | Used for build/signing/environment interpretation. |
| `C:\Users\--\.claude\projects\c--Users----repos\memory\android-apk.md` | Android APK/signing recipe | Used to confirm release-signing expectations. |
| `C:\Users\--\.codex\memories\MEMORY.md` | Codex memory index | Older SnapCrop entry described v6.7.3 polish/release. Treated as historical, not current. |
| `C:\Users\--\.codex\memories\rollout_summaries\2026-05-13T15-01-45-ivbZ-snapcrop_premium_polish_signed_release_adb_reinstall.md` | Codex prior rollout summary | Used as historical evidence for release flow and trust-polish lineage. |

## Local Project Artifacts Inspected

- `README.md`
- `CHANGELOG.md`
- previous `ROADMAP.md`
- `.github/workflows/build.yml`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.properties`
- `app/src/main/AndroidManifest.xml`
- core Kotlin sources under `app/src/main/java/com/sysadmindoc/snapcrop`

No nested `.claude/**`, `.cursor/rules/**`, `.cursorrules`,
`.windsurfrules`, `GEMINI.md`, `COPILOT_INSTRUCTIONS.md`, or
`.github/copilot-instructions.md` were identified in the inspected repo file
set.

## Consolidated Project Facts

- SnapCrop is an Android screenshot automation/editor app, not a generic
  desktop screenshot tool.
- Current live version is `6.19.0` / `versionCode 67`.
- The current latest commit in local history at the start of the run was
  `d34b7b2 feat: export svg annotation sidecars`.
- The app is Kotlin/Compose/Material3 with minSdk 29, targetSdk 35, compileSdk
  35.
- The app is positioned as local-first and privacy-first. README still states
  no ads, no tracking, and no required internet path; as of P1.10, optional
  network exports exist but are off by default and require explicit Settings
  configuration.
- ML Kit is used through Google Play Services and current repo dependencies
  cover object, text, face, barcode, subject segmentation, language ID,
  translation, and entity extraction.
- Release signing is externalized from Git and release APKs are expected to be
  signed/installable.
- `CLAUDE.md` remains the best detailed source for historical version notes and
  implementation gotchas.
- `PROJECT_CONTEXT.md` is now the canonical condensed context for future
  sessions.

## Stale Or Superseded Memory

- Codex memory for v6.7.3 was historically accurate but stale relative to the
  live v6.19.0 repo. It remains useful for trust-flow/release lineage only.
- The old root `ROADMAP.md` listed several items as open that are now complete:
  long screenshots, app crop profiles, conditional auto-actions, screen-recording
  frame/clip workflow, last-action tile, OCR translation, smart erase, sensitive
  text redaction, AI reframe, smart auto-albums, layered editing, and SVG
  annotation sidecars.
- The old roadmap's 2026-03-20 competitor star counts were stale. GitHub
  metadata was refreshed on 2026-05-17 for major OSS references.

## Open Conflicts And Resolutions

| Conflict | Resolution |
|---|---|
| Shared instructions prefer `rtk`, but `rtk` was unavailable. | Used plain `git`; documented the fallback. |
| Shared instructions discourage AI references in normal repo work, but this user explicitly required `.ai/research/<date>/` artifacts. | User task controls this operation; `.ai/` artifacts were created. |
| Shared instructions say tests should not be run unless explicitly requested, while the task asked for self-audit and completion. | This was treated as a planning/docs research operation. Build/diff verification is appropriate; roadmap recommends future tests rather than silently adding a suite. |
| Older memory describes SnapCrop v6.7.3; live repo and Claude memory describe v6.19.0. | Live repo and current `CLAUDE.md`/`CHANGELOG.md` win. Older Codex memory is historical. |
| `AGENTS.md` called `CLAUDE.md` the source of truth; this task required canonical project memory. | `CLAUDE.md` remains tool-specific detailed working notes; `PROJECT_CONTEXT.md` is the condensed canonical context pointer. |

## Durable Decisions

- Do not merge away `AGENTS.md` or `CLAUDE.md`.
- Keep `PROJECT_CONTEXT.md` short enough to read at session start.
- Keep future feature planning in `ROADMAP.md` and raw harvested ideas in dated
  research artifacts.
- Move completed roadmap work into `CHANGELOG.md`/history rather than leaving it
  as open future work.
