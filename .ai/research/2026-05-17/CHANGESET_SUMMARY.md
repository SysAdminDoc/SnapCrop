# Changeset Summary

Research date: 2026-05-17

## Files Created

- `PROJECT_CONTEXT.md`: canonical consolidated project context for future
  sessions.
- `.ai/research/2026-05-17/STATE_OF_REPO.md`: local repository reconnaissance
  memo.
- `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md`: instruction/memory
  inventory and reconciliation.
- `.ai/research/2026-05-17/SOURCE_REGISTER.md`: local and external source
  inventory.
- `.ai/research/2026-05-17/RESEARCH_LOG.md`: research passes, query classes,
  failed/thin searches, and saturation notes.
- `.ai/research/2026-05-17/COMPETITOR_MATRIX.md`: competitor/adjacent product
  comparison and lessons.
- `.ai/research/2026-05-17/FEATURE_BACKLOG.md`: raw harvested backlog.
- `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md`: scored candidates and
  tiering.
- `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md`: dependency,
  manifest, policy, and security review.
- `.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md`: datasets,
  models, integrations, and evaluation opportunities.

## Files Modified

- `ROADMAP.md`: replaced stale mixed roadmap with a dated, prioritized,
  evidence-backed roadmap.

Local ignored pointer edits were also made to `AGENTS.md` and `CLAUDE.md` so
this workspace points at `PROJECT_CONTEXT.md`. Those files are intentionally
ignored by the repo/global gitignore and were not forced into version control.

## Why

The repository already completed many items from the previous roadmap. This
changeset consolidates current memory, preserves tool-specific instructions,
documents source evidence, and gives future sessions a clear P0/P1/P2 execution
plan.

## Verification

- `git diff --check`: passed. Git reported only the existing line-ending warning
  that `ROADMAP.md` LF will be replaced by CRLF when Git touches it.
- `.\gradlew.bat :app:assembleDebug`: passed in 1m 25s. Existing Kotlin
  warnings were observed for deprecated `onActivityResult`, deprecated
  AutoMirrored icon replacements, and deprecated `Bitmap.recycle()` calls in
  existing source.
- Generated transient `.kotlin/errors/errors-1779041807951.log` was removed.

## Roadmap Continuation - 2026-05-17

Implemented P0.1, "Add a real verification and release-quality CI lane":

- Added JUnit/Robolectric test dependencies and enabled Android resources for
  local unit tests.
- Added tests for `AutoCrop`, `AppCropProfiles`, `SensitiveTextPatterns`, and
  `SmartEraseEngine`.
- Extracted sensitive text regex/Luhn matching into `SensitiveTextPatterns` so
  the privacy rules can be tested without invoking OCR or ML Kit entity
  extraction.
- Replaced the manual-only GitHub Actions workflow with lint, test, debug
  assemble, release assemble, dependency-review, and release SBOM artifact jobs.
- Applied the CycloneDX Gradle plugin to the app project.
- Added `docs/RELEASE_CHECKLIST.md`.
- Removed a tracked generated Kotlin compiler error log and ignored `.kotlin/`
  for future local builds.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, and `CHANGELOG.md`.

Verification for this continuation batch:

- `.\gradlew.bat :app:testDebugUnitTest :app:cyclonedxDirectBom`: passed.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`: passed in 2m 52s.
- The build still prints existing Kotlin deprecation warnings for
  `onActivityResult` and one AutoMirrored icon migration.
- CycloneDX prints a configuration-time dependency-resolution warning for
  `releaseRuntimeClasspath`; the SBOM task still succeeds and writes JSON/XML
  to `app/build/reports/cyclonedx-direct/`.
- Transient `.kotlin/errors/errors-1774623214671.log` was removed.

## Roadmap Continuation - P0.2 - 2026-05-17

Implemented P0.2, "Update and gate Android dependency baselines":

- Updated the Gradle wrapper from 8.11.1 to 9.4.1.
- Updated AGP from 8.7.3 to 9.2.1.
- Updated the Kotlin/Compose compiler plugin from 2.0.21 to 2.3.21.
- Updated compileSdk from 35 to 36 while intentionally keeping targetSdk 35
  for the separate platform behavior and policy audit.
- Updated AndroidX/Compose baselines: Compose BOM 2026.05.00, Core KTX 1.18.0,
  Activity Compose 1.13.0, Lifecycle Runtime KTX 2.10.0, Navigation Compose
  2.9.8, and Material 3 1.4.0.
- Migrated to AGP 9 built-in Kotlin by removing the legacy
  `org.jetbrains.kotlin.android` plugin and moving JVM target configuration to
  `kotlin.compilerOptions`.
- Fixed the ignored local `local.properties` SDK path escaping after newer lint
  flagged the Windows drive separator. That file is intentionally untracked.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, and local
  `CLAUDE.md` notes.

Verification for this continuation batch:

- `.\gradlew.bat :app:lintDebug --rerun-tasks`: passed after correcting the
  ignored local SDK path.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed.
- The build still prints the existing CycloneDX configuration-time resolution
  warning for `releaseRuntimeClasspath`; the SBOM task succeeds.
- Existing Kotlin deprecation warnings remain for legacy activity-result
  callbacks, AutoMirrored icon migrations, and `Bitmap.recycle()` calls.

## Roadmap Continuation - P0.3 - 2026-05-17

Implemented P0.3, "Harden permissions, privacy posture, and Play policy
documentation":

- Removed `MANAGE_EXTERNAL_STORAGE` from the manifest. Android 11+ deletion now
  uses scoped-storage confirmation; service-side Quick Crop leaves the source in
  place on Android 11+ because services cannot show delete confirmation.
- Set `android:allowBackup="false"` after auditing stored preferences such as
  save paths, favorites, automation toggles, format settings, and last action.
- Made display-over-apps optional for monitoring. The home screen explains the
  notification fallback, and the monitor tile starts the foreground service
  without requiring overlay access.
- Added an in-app Accessibility disclosure before opening system Accessibility
  settings for Long Screenshot setup.
- Updated the foreground-service special-use subtype string to describe
  screenshot monitoring and persistent user controls.
- Added `SECURITY.md` and README privacy/permission notes.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`,
  `SOURCE_REGISTER.md`, `RESEARCH_LOG.md`,
  `SECURITY_AND_DEPENDENCY_REVIEW.md`, and local `CLAUDE.md` notes.

Verification for this continuation batch:

- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed in 2m 47s.
- Existing Kotlin warnings remain for legacy activity-result callbacks, one
  AutoMirrored icon migration, and one non-null Elvis expression.
- CycloneDX still prints the known configuration-time resolution warning for
  `releaseRuntimeClasspath`; the task succeeds.

## Roadmap Continuation - P0.4 - 2026-05-17

Implemented P0.4, "Build non-destructive project sidecars":

- Added `SnapCropProjectSidecar` and a versioned `.snapcrop.json` schema for
  source URI/hash, source dimensions, crop rect, adjustment array, pixelate
  rectangles, draw layers, visibility/order, text/fill/dash properties, and
  export settings.
- Added JSON project sidecar export through MediaStore next to image/SVG
  exports when the new `project_sidecars` setting is enabled.
- Added an `Editable project sidecars` setting. The setting defaults on and
  makes the main save path non-destructive so project sidecars do not point at a
  deleted source image.
- Added share/view intent filters for SnapCrop project JSON files and
  `CropActivity` loading of sidecars back into the editor.
- Added recoverable project-load UI when the sidecar source image is missing or
  inaccessible.
- Added `SnapCropProjectSidecarTest` for encode/decode defaults, project
  detection, and editable layer state preservation.
- Added a JVM-only `org.json` test dependency so sidecar serialization can be
  tested outside the Android runtime.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, `README.md`,
  `SOURCE_REGISTER.md`, `RESEARCH_LOG.md`, and local `CLAUDE.md` notes.

Verification for this continuation batch:

- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest`: passed after adding the JVM JSON test
  dependency and running the sidecar test under Robolectric for Android
  geometry classes.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed in 5m 19s.
- Existing warnings remain for legacy activity-result callbacks, AutoMirrored
  icons, `Bitmap.recycle()`, one non-null Elvis expression, unstripped bundled
  native ML Kit libraries, and the known CycloneDX configuration-time
  resolution warning.

## Roadmap Continuation - P0.5 - 2026-05-17

Implemented P0.5, "Split the editor before adding more surface area":

- Extracted editor model/state helpers into `EditorModels.kt`, including draw
  path state, drag handles, edit/draw tools, aspect ratios, adjustment defaults,
  undo snapshots, and image-filter matrices.
- Extracted reusable canvas helpers into `EditorCanvas.kt` for crop handles and
  gradient crop backgrounds.
- Extracted the draw layer panel into `EditorLayers.kt`.
- Extracted the before/after preview surface and divider gesture into
  `EditorPreview.kt`.
- Preserved the existing `awaitEachGesture` gesture approach and avoided
  introducing competing pointer detectors in the main edit canvas.
- Added `EditorModelsTest` for extracted adjustment defaults, aspect-ratio
  mapping, filter fallback, renderable filter matrices, and short-path
  smoothing behavior.
- Added `docs/EDITOR_REGRESSION_CHECKLIST.md` for manual crop, gesture, draw,
  layer, undo/redo, SVG export, sidecar reopen, save/share, and preview
  regression coverage.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, and `CHANGELOG.md`.

Verification for this continuation batch:

- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `git diff --check`: passed with only existing CRLF conversion warnings.
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`: passed.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed in 2m 14s.
- Existing warnings remain for the known CycloneDX configuration-time
  dependency-resolution message.

## Roadmap Continuation - P1.6 - 2026-05-17

Implemented P1.6, "Long-screenshot stitcher v2":

- Increased Accessibility long-screenshot capture from five frames to ten
  frames, with an 18-second safety cap.
- Added explicit stop reasons for repeated/stuck content, capture failure,
  end-of-scroll, frame cap, and time cap.
- Added `LongScreenshotReviewActivity` so a captured long screenshot is written
  to a temporary review file and previewed before gallery save. The review UI
  supports Save & Edit, Retry, and Discard.
- Added `LongScreenshotStore` for shared temporary review-file handling and
  MediaStore save behavior.
- Updated the FileProvider paths and manifest for the review flow.
- Reworked `ScrollStitcher` to search below repeated top chrome for the true
  overlap, trim repeated bottom chrome from intermediate frames, and use denser
  band/edge scoring.
- Added `ScrollStitcherTest` for sticky header/footer reduction and stuck-frame
  detection.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, `README.md`,
  `SOURCE_REGISTER.md`, `RESEARCH_LOG.md`, and `STATE_OF_REPO.md`.

Verification for this continuation batch:

- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`: passed.
- `git diff --check`: passed with only existing CRLF conversion warnings.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed in 2m 57s.
- Existing warnings remain for deprecated `Bitmap.recycle()` calls in
  `ScrollCaptureService` and the known CycloneDX configuration-time
  dependency-resolution message.

## Roadmap Continuation - P1.7 - 2026-05-17

Implemented P1.7, "Persistent screenshot intelligence index":

- Added `ScreenshotIndexStore`, an opt-in local SQLite index for media IDs,
  URIs, names, album/source hints, dimensions, dates, sizes, favorite state,
  screenshot state, category labels, and searchable text.
- Added source/category classification for screenshots, chats, games, sites,
  documents, codes, payments, sensitive/payment-like screenshots, and favorites.
- Added Settings controls to enable the index, rebuild it from MediaStore, and
  purge it.
- Wired Gallery to rebuild/load the index when enabled, enrich photos with
  indexed categories/search text, expand smart albums, and search photo grids
  by indexed source/category hints.
- Captured OCR and barcode text into the index when the user runs OCR in the
  editor on an indexed source image.
- Changed the screenshot cleanup shortcut to select non-favorite screenshots,
  making bulk cleanup safer.
- Added `ScreenshotIndexClassifierTest`.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, `README.md`,
  `SOURCE_REGISTER.md`, `RESEARCH_LOG.md`, and `STATE_OF_REPO.md`.

Verification for this continuation batch:

- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`: passed.
- `git diff --check`: passed with only existing CRLF conversion warnings.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed in 3m 34s.
- Existing warnings remain for deprecated `onActivityResult` in `CropActivity`
  and the known CycloneDX configuration-time dependency-resolution message.

## Roadmap Continuation - P1.8 - 2026-05-17

Implemented P1.8, "Expand app profiles into a user-visible rules system":

- Added `UserAppProfileStore`, a JSON-backed profile-pack store for custom app
  rules with source/package hints, OCR keywords, crop bands, album destination,
  redaction preference, and export format.
- Expanded `AppCropProfiles` with built-in profile summaries, profile-match
  previews, user-profile matching, and user crop-band application.
- Added a Settings App rules panel that exposes Reddit/X built-ins, creates and
  toggles/deletes user rules, copies/imports profile-pack JSON, and tests a
  selected image against the current rules.
- Wired user profiles into editor auto-crop, batch auto-crop, and Quick Crop.
  Quick Crop now optionally runs OCR for keyword-backed rules, applies
  rule-specific redaction and export format, saves to the rule album, and
  explains the save/redaction/export outcome in the toast.
- Added `UserAppProfileStoreTest` for profile-pack round-trip,
  source-plus-OCR matching, and user crop-band application.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, and `README.md`.

Verification for this continuation batch:

- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`: passed in
  2m 56s.
- `.\gradlew.bat :app:testDebugUnitTest`: passed after tightening the float
  assertion in the new profile-store test.
- `git diff --check`: passed with only existing CRLF conversion warnings.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed in 3m 49s.
- Existing warnings remain for deprecated activity-result callbacks, one
  AutoMirrored icon migration, and the known CycloneDX configuration-time
  dependency-resolution message.

## Roadmap Continuation - P1.9 - 2026-05-17

Implemented P1.9, "Tablet, foldable, and desktop-mode editor layout":

- Added `EditorAdaptiveLayout` with tested phone/wide thresholds and side-panel
  width selection.
- Kept the compact phone editor recognizable while wide windows use a
  persistent right-side inspector for mode selection, crop controls, redaction
  tools, draw tools/layers, and adjustment sliders.
- Moved phone-only scrolling mode/tool/adjust rows out of the wide path and
  moved reset, auto-crop, AI crop, background removal, and palette controls into
  the inspector for large screens.
- Added keyboard shortcuts for save, undo/redo, crop nudging, preview toggling,
  and keyboard zoom; added mouse-wheel zoom while preserving existing pinch and
  drag-pan behavior.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, and `README.md`.

Verification for this continuation batch:

- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`: passed in
  1m 6s after the side-panel toolbar and pointer-routing refinements.
- `git diff --check`: passed with only existing CRLF conversion warnings.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed in 3m 13s after the pointer-routing refinement.
- Existing warnings remain for the known CycloneDX configuration-time
  dependency-resolution message.

## Roadmap Continuation - P1.10 Local Reporting Batch - 2026-05-17

Implemented the local-first portion of P1.10, "Export and reporting workflows":

- Added `ExportWorkflowModels` with export metadata and batch rename template
  token expansion/sanitization.
- Replaced the gallery image-only PDF action with a report dialog for title,
  notes, and optional OCR appendix generation.
- PDF reports now include a cover page, timestamp, selected-image metadata,
  MediaStore source hints where available, dimensions, size, date, local-index
  category hints, image pages, and optional OCR appendix pages.
- Added gallery batch rename from multi-select using `%app%`, `%date%`,
  `%time%`, `%timestamp%`, `%counter%`, and `%profile%` tokens.
- Added `BatchRenameTemplateTest` for token expansion and filename
  sanitization.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, and `README.md`.

Verification for this continuation batch so far:

- `.\gradlew.bat :app:testDebugUnitTest`: passed in 29s after fixing PDF
  constants and tightening rename-template tests.
- `git diff --check`: passed with only existing CRLF conversion warnings.
- `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:cyclonedxDirectBom`:
  passed in 2m 29s.
- Existing warnings remain for deprecated activity-result callbacks and the
  AutoMirrored icon migration, plus the known CycloneDX configuration-time
  dependency-resolution message.

Remaining P1.10 work:

- Add explicit opt-in network export targets for self-hosted HTTP,
  WebDAV/Nextcloud, and Imgur anonymous upload.
- Add Android share shortcuts for frequent export destinations.
