# SnapCrop Project Context

Updated: 2026-05-17

This file is the canonical project-memory entry point for future AI sessions.
It consolidates the live repository, `AGENTS.md`, `CLAUDE.md`, shared memory,
and the 2026-05-17 research run. Use it before making product or architecture
decisions, then verify against the current source and `git log`.

## Project Identity

SnapCrop is a privacy-first Android screenshot automation and editing app. It
detects screenshots, auto-crops system bars and borders, opens a Compose editor,
and supports annotation, redaction, OCR, translation, gallery workflows, stitch,
collage, device frames, video frame extraction, long screenshots, and ML Kit
assists.

The current repository version is `6.19.0` / `versionCode 67` in
`app/build.gradle.kts`. The latest checked commit at this research pass was
`d34b7b2 feat: export svg annotation sidecars` on `main`, aligned with
`origin/main`.

## Working Rules

- Start each session by reading `AGENTS.md`, `CLAUDE.md`, `PROJECT_CONTEXT.md`,
  and recent `git log`.
- `rtk` is preferred by the shared instructions, but it was not available in
  this PowerShell session. Plain `git` was used as the practical fallback.
- Preserve tool-specific instruction files. Do not collapse `AGENTS.md` and
  `CLAUDE.md` into this file.
- Treat memory as point-in-time. Local source and current Git history override
  older memory.
- Keep public behavior, AMOLED/Catppuccin design language, and privacy-first
  local processing unless a product decision explicitly changes that.
- Release APKs must be signed. `assembleRelease` is configured to sign from
  `keystore.properties` or environment variables, with debug signing fallback
  for contributor builds.

## Stack

- Kotlin, Jetpack Compose, Material 3.
- Android minSdk 29, targetSdk 35, compileSdk 35.
- Gradle wrapper 8.11.1.
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01.
- Coil 2.7.0.
- ML Kit: object detection, text recognition, face detection, barcode scanning,
  subject segmentation, language identification, translation, and entity
  extraction.
- Android `PdfDocument`, `MediaStore`, `MediaMetadataRetriever`,
  `MediaExtractor`, `MediaMuxer`, foreground services, Quick Settings tiles,
  and AccessibilityService screenshot capture.

## Core Architecture

- `ScreenshotService`: foreground MediaStore observer, screenshot detection,
  quick crop, delayed capture, notification actions, last-action execution.
- `MainActivity`: home screen, permissions, recent crops, manual pick, batch
  crop/resize, stitch/collage/device-frame/video workflows.
- `CropActivity`: bitmap loading, crop/export pipeline, share/clipboard/save,
  SVG sidecar generation for visible vector annotations and redaction rectangles.
- `CropEditorScreen`: large Compose editor surface. Current line count is about
  2,821 lines, so future work should avoid increasing this file without a
  clear extraction plan.
- `GalleryScreen`: MediaStore-backed gallery, smart auto-albums, search,
  favorites, multi-select, PDF export, photo/video viewer.
- `ScrollCaptureService`: AccessibilityService long-screenshot capture. It uses
  `takeScreenshot()`, throttles capture attempts, strips system bars, scrolls,
  stitches, saves, and opens the editor.
- `SmartEraseEngine`: local mask-based edge-aware fill. No large ONNX inpainting
  model is bundled.
- `SmartReframeEngine`: ML Kit object, text, and face bounds unioned into
  content-aware crop repositioning.
- `SensitiveTextDetector`: regex plus ML Kit Entity Extraction for email,
  phone, payment-card candidates, IP, MAC, IBAN, and address-style entities.
- `TextTranslator`: ML Kit language ID plus on-device translation with Wi-Fi
  model download.
- `AppCropProfiles` and `ConditionalAutoActions`: built-in Reddit and X/Twitter
  profile/automation rules.

## Product Philosophy

SnapCrop should remain a screenshot-first workflow app, not a generic image
toolbox clone. The strongest product lane is:

- immediate post-capture handling,
- reliable crop/redaction/export defaults,
- on-device privacy-preserving ML,
- recoverable destructive actions,
- fast workflow recipes for repeat screenshot tasks,
- transparent source/dependency/release posture.

External research shows broad image suites are crowded. SnapCrop should compete
by being better at screenshot-specific trust, automation, annotation recovery,
and Android system integration.

## Current Strengths

- Deep editor: crop, pixelate, draw, OCR, adjust, layer ordering, SVG sidecars,
  shape crops, filters, curves, preview, undo/redo, and before/after.
- Screenshot automation: MediaStore observer, foreground service, delayed
  capture, Quick Settings tiles, last-action quick tile, app crop profiles, and
  conditional auto-actions.
- Privacy tooling: sensitive text detection, face blur, pixelate mode, delete
  confirmations, source/replacement copy, EXIF strip, and local-first ML.
- Media workflows: gallery, smart albums, batch crop/resize, video frame/trim,
  stitch, collage, PDF export, and device mockup.
- Release hygiene improved over prior versions: externalized signing secrets and
  signed release build path documented.

## Current Gaps

- Root roadmap previously mixed completed features with open ideas. The
  2026-05-17 `ROADMAP.md` replaces it with prioritized evidence-backed work.
- No dedicated unit/instrumentation test suite was found under source control.
- GitHub Actions currently builds release APK on manual dispatch only; it does
  not run lint, tests, dependency review, SBOM generation, or tag/release upload.
- Dependencies are behind current stable AndroidX/Kotlin/Compose baselines as of
  2026-05-17 metadata.
- `MANAGE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, AccessibilityService, and
  foreground-service special use all require careful user education, fallback
  behavior, and Play policy justification.
- `android:allowBackup="true"` is enabled. That may be acceptable, but privacy
  settings, recent/favorites metadata, and automation profiles should be audited
  against expected backup behavior.
- Non-destructive editing is incomplete. SVG sidecars preserve visible vector
  annotation output, but there is no re-openable project state sidecar for crop,
  adjustments, layer properties, and source URI lineage.
- App profiles and conditional actions currently cover Reddit and X/Twitter only.
- Smart albums are heuristic and do not yet maintain a persistent OCR/entity/app
  index.
- Long-screenshot stitch quality is basic and capped at five frames.
- The editor is dense and monolithic; tablet/foldable/DeX layouts and extracted
  editor modules are high-leverage future work.
- Two `.idsig` artifacts are tracked even though APK/signature byproducts are in
  `.gitignore`.

## Recommended Next Session Ritual

1. Read this file, `ROADMAP.md`, `AGENTS.md`, and `CLAUDE.md`.
2. Run `git status --short --branch` and `git log -10 --oneline --decorate`.
3. Check the newest `.ai/research/<date>/CHANGESET_SUMMARY.md`.
4. Pick the highest uncompleted `P0` roadmap item unless the user directs
   otherwise.
5. Verify with the smallest meaningful build/test command for the touched area.

## Durable Research Artifacts

The 2026-05-17 deep research run lives in `.ai/research/2026-05-17/`:

- `STATE_OF_REPO.md`
- `MEMORY_CONSOLIDATION.md`
- `SOURCE_REGISTER.md`
- `RESEARCH_LOG.md`
- `COMPETITOR_MATRIX.md`
- `FEATURE_BACKLOG.md`
- `PRIORITIZATION_MATRIX.md`
- `SECURITY_AND_DEPENDENCY_REVIEW.md`
- `DATASET_MODEL_INTEGRATION_REVIEW.md`
- `CHANGESET_SUMMARY.md`
