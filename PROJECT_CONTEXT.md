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
`app/build.gradle.kts`. The latest checked commit before the editor split
continuation was `bf62c24 feat: add editable project sidecars` on `main`,
aligned with `origin/main`.

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
- Android minSdk 29, targetSdk 35, compileSdk 36.
- Gradle wrapper 9.4.1.
- Android Gradle Plugin 9.2.1 with AGP built-in Kotlin. Do not reapply
  `org.jetbrains.kotlin.android`; keep the Compose compiler plugin pinned from
  the version catalog.
- Kotlin/Compose compiler plugin 2.3.21, Compose BOM 2026.05.00,
  Material 3 1.4.0, Activity Compose 1.13.0, Lifecycle Runtime KTX 2.10.0,
  Navigation Compose 2.9.8, Core KTX 1.18.0.
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
  crop/resize/rename, PDF report export, stitch/collage/device-frame/video
  workflows.
- `CropActivity`: bitmap loading, crop/export pipeline, share/clipboard/save,
  SVG sidecar generation for visible vector annotations and redaction
  rectangles, and `.snapcrop.json` project sidecar open/save.
- `CropEditorScreen`: large Compose editor surface. Current line count is about
  2,423 lines after the first split. It still owns the central gesture/canvas
  workflow, so future tool additions should prefer extracted helpers or
  adaptive-layout driven components.
- `EditorModels`: shared editor data/state helpers, draw tools, aspect ratios,
  image-filter metadata, adjustment defaults, and undo snapshot model.
- `EditorCanvas`: reusable canvas helpers for crop handles and gradient
  backgrounds.
- `EditorLayers`: draw-layer panel and layer labels.
- `EditorPreview`: before/after preview rendering and divider gestures.
- `GalleryScreen`: MediaStore-backed gallery, optional local screenshot
  intelligence index integration, smart auto-albums, search, favorites,
  multi-select, batch rename, PDF report export, photo/video viewer.
- `ExportWorkflowModels`: export metadata and batch rename template token
  expansion/sanitization shared by gallery export workflows.
- `ScrollCaptureService`: AccessibilityService long-screenshot capture. It uses
  `takeScreenshot()`, throttles capture attempts, strips system bars, scrolls,
  stops on repeated/stuck content, stitches up to ten frames behind a time
  guard, and opens a review flow before save.
- `LongScreenshotReviewActivity`: preview, Save & Edit, Retry, and Discard for
  captured long screenshots before they are committed to MediaStore.
- `LongScreenshotStore`: shared temporary-review and MediaStore save helpers
  for long screenshots.
- `ScreenshotIndexStore`: opt-in local SQLite index for screenshot/media source
  hints, dimensions, favorite state, categories, and OCR/barcode tokens captured
  from the editor OCR flow.
- `SmartEraseEngine`: local mask-based edge-aware fill. No large ONNX inpainting
  model is bundled.
- `SmartReframeEngine`: ML Kit object, text, and face bounds unioned into
  content-aware crop repositioning.
- `SensitiveTextDetector`: regex plus ML Kit Entity Extraction for email,
  phone, payment-card candidates, IP, MAC, IBAN, and address-style entities.
- `TextTranslator`: ML Kit language ID plus on-device translation with Wi-Fi
  model download.
- `AppCropProfiles`, `UserAppProfileStore`, and `ConditionalAutoActions`:
  built-in Reddit/X profile rules plus user-created source/OCR crop profiles
  with Quick Crop album, redaction, and export-format actions.
- `SECURITY.md`: permission matrix, backup posture, local-first privacy notes,
  release hygiene, and policy references.
- `SnapCropProjectSidecar`: versioned JSON schema for source URI/hash, crop
  rect, adjustment state, pixelate rectangles, draw layers, and export settings.
- `EditorAdaptiveLayout`: phone vs wide editor layout thresholds and side-panel
  sizing for tablet/foldable/desktop-mode editor surfaces.

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
- Media workflows: gallery, smart albums, batch crop/resize/rename, video
  frame/trim, stitch, collage, PDF report export, and device mockup.
- Release hygiene improved over prior versions: externalized signing secrets and
  signed release build path documented.

## Current Gaps

- Root roadmap previously mixed completed features with open ideas. The
  2026-05-17 `ROADMAP.md` replaces it with prioritized evidence-backed work.
- A starter JVM/Robolectric unit-test surface now covers auto-crop, app profile
  matching, sensitive text pattern detection, and Smart Erase behavior.
- GitHub Actions now has lint/test/debug assemble/release assemble,
  dependency-review, and CycloneDX SBOM artifact lanes.
- Android dependency baselines are now current stable as of 2026-05-17 metadata
  for AGP, Gradle, Kotlin/Compose compiler, Compose BOM, Core KTX, Activity
  Compose, Lifecycle, Navigation Compose, and Material 3. ML Kit dependencies
  remain intentionally separate because the research pass found the current
  ML Kit selections already at latest stable or latest beta metadata.
- Permissions and Play-policy posture are hardened: SnapCrop no longer requests
  all-files access, Android 11+ deletes use scoped-storage confirmation,
  display-over-apps is optional with notification fallback, Long Screenshot
  shows an Accessibility disclosure before settings, foreground-service
  special-use metadata is more explicit, and `android:allowBackup` is disabled.
- Non-destructive editing now has a first project sidecar path. Future work can
  expand it into richer source relinking, thumbnails, migration tests, and
  user-facing sidecar management.
- App profiles now have a user-visible Settings rules system. Built-ins cover
  Reddit and X/Twitter; custom profiles can match source/package hints or OCR
  keywords, apply crop bands, and drive Quick Crop album/redaction/export
  actions. Future work can add richer visual training and profile sharing UX.
- Export/reporting now has a stronger local-first foundation. Gallery
  selections can create PDF incident reports with title, notes, timestamps,
  image metadata, source hints, dimensions, index categories, and optional OCR
  appendix pages; selected images can also be batch-renamed with `%app%`,
  `%date%`, `%time%`, `%timestamp%`, `%counter%`, and `%profile%` templates.
  P1.10 still needs explicit opt-in network export targets and Android share
  shortcuts before it is complete.
- Smart albums now use an opt-in local intelligence index for metadata,
  source-hint categories, favorite state, and editor OCR/barcode tokens. Future
  work can add background OCR/model-state progress, but the current design
  avoids rescanning every image unless the user explicitly rebuilds the index.
- Long-screenshot capture now has ten-frame/time-guarded capture,
  repeated-frame stop detection, sticky-header/footer aware stitching, and
  review/retry before save. It still needs real-device QA across chat, browser,
  settings, and feed apps because Accessibility scroll behavior is app-specific.
- The first editor split now extracts model/state, layer panel, before/after
  preview, and small canvas helpers. Wide windows now use a persistent right
  inspector for mode/crop/redaction/draw/layer/adjust controls plus keyboard
  shortcuts and wheel zoom. Future work can continue extracting the inspector
  into smaller reusable tool panels.
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
