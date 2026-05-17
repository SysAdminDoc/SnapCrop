# SnapCrop Roadmap

Updated: 2026-05-17

This roadmap supersedes the older root roadmap after reconciling completed
v6.8.0 through v6.19.0 work with live source, project memory, competitor
research, Android platform guidance, dependency metadata, and security review.

## Source Key

Local evidence:

- L1: `app/build.gradle.kts` version `6.19.0`, minSdk 29, targetSdk 35,
  compileSdk 36, release signing fallback behavior, and AGP built-in Kotlin
  compiler configuration.
- L2: `gradle/libs.versions.toml` dependency versions.
- L3: `gradle/wrapper/gradle-wrapper.properties` Gradle 9.4.1.
- L4: `.github/workflows/build.yml` verification, dependency-review, and
  release artifact workflow.
- L5: `app/src/main/AndroidManifest.xml` permissions, exported components,
  foreground-service special-use metadata, no all-files access, and disabled
  app-data backup setting.
- L6: `app/src/main/java/com/sysadmindoc/snapcrop/CropEditorScreen.kt`, about
  2,423 lines after the first editor split, plus extracted
  `EditorModels.kt`, `EditorCanvas.kt`, `EditorLayers.kt`, and
  `EditorPreview.kt`.
- L7: `CropActivity.kt` SVG sidecar export and raster save pipeline.
- L8: `ScrollCaptureService.kt` five-frame AccessibilityService long screenshot
  pipeline.
- L9: `AppCropProfiles.kt` and `ConditionalAutoActions.kt` Reddit/X profile
  scope.
- L10: `GalleryScreen.kt` heuristic auto-albums and MediaStore gallery.
- L11: `SensitiveTextDetector.kt`, `TextTranslator.kt`,
  `BackgroundRemover.kt`, `SmartEraseEngine.kt`, `SmartReframeEngine.kt`.
- L12: `git ls-files "*.apk" "*.idsig"` found tracked `.idsig` artifacts.
- L13: `README.md` privacy positioning: no ads, no tracking, no required
  internet path; optional network exports must remain explicit.
- L14: `CHANGELOG.md` current v6.19.0 release story.

External evidence:

- E1: Android AccessibilityService screenshot API and rate-limit error:
  https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback)
- E2: Android foreground service type guidance:
  https://developer.android.com/develop/background-work/services/fgs/service-types
- E3: Google Play all-files access policy:
  https://support.google.com/googleplay/android-developer/answer/10467955
- E4: Android Photo Picker and selected-photo access:
  https://developer.android.com/training/data-storage/shared/photopicker
  and https://developer.android.com/about/versions/14/changes/partial-photo-video-access
- E5: Android 15 behavior changes:
  https://developer.android.com/about/versions/15/behavior-changes-15
- E6: Android 16 behavior changes:
  https://developer.android.com/about/versions/16/behavior-changes-all
- E7: ML Kit docs and known issues:
  https://developers.google.com/ml-kit and
  https://developers.google.com/ml-kit/known-issues
- E8: Google ML Kit samples:
  https://github.com/googlesamples/mlkit
- E9: Dependency metadata from Google Maven and Maven Central on 2026-05-17.
- E10: OWASP MASVS and MASTG:
  https://mas.owasp.org/MASVS/ and https://mas.owasp.org/MASTG/
- E11: Android security bulletin for CVE-2023-21036 aCropalypse:
  https://source.android.com/docs/security/bulletin/2023-03-01 and
  https://nvd.nist.gov/vuln/detail/CVE-2023-21036
- E12: Direct competitors and adjacent references:
  ImageToolbox, ScreenshotTile, PhotoEditor, Satty, ksnip, kImageAnnotator,
  Flameshot, ShareX, Shottr, CleanShot X, Greenshot.
- E13: Dataset/model references: RICO, Screen2Words, Android in the Wild,
  UICrit, Gemma, MediaPipe/Image Segmenter, LiteRT, ONNX Runtime Android, LaMa.
- E14: GitHub supply-chain references:
  https://github.com/actions/dependency-review-action and
  https://github.com/CycloneDX/cyclonedx-gradle-plugin

Full source inventory and query notes are in
`.ai/research/2026-05-17/SOURCE_REGISTER.md` and
`.ai/research/2026-05-17/RESEARCH_LOG.md`.

## Strategic Direction

SnapCrop should stay screenshot-first, privacy-first, and local-first. The
repository already has a broad editor and many generic image-toolbox features.
The next high-value work is not feature sprawl. It is trust, recoverability,
automation reliability, policy readiness, and a re-editable screenshot workflow
that competitors and OEM screenshot tools do not combine cleanly.

## P0: Stabilize The Foundation

### 1. [x] Add a real verification and release-quality CI lane

Status: Completed 2026-05-17. CI now runs lint, unit tests, debug assemble,
and release assemble; pull requests get dependency review; release/manual runs
produce a CycloneDX SBOM artifact; the repo has a starter unit-test surface for
auto-crop, app profile matching, sensitive text patterns, and Smart Erase; and
`docs/RELEASE_CHECKLIST.md` records the signed-release checklist.

Evidence: L4 shows only manual `assembleRelease`; L2/L3 show modern Android
stack versions; E14 provides dependency review and SBOM tooling.

Deliverables:

- Run `lint`, unit tests, and at least debug/release assemble in CI.
- Add dependency review for pull requests.
- Generate a CycloneDX SBOM artifact on release builds.
- Add a small unit test surface for pure or mostly pure logic: `AutoCrop`,
  `SensitiveTextDetector` regex/Luhn helpers, `SmartEraseEngine` mask behavior,
  filename templates, save-format helpers, and app-profile matching.
- Add a release checklist that confirms signed APK, version sync, changelog,
  Play policy notes, and smoke install.

Acceptance:

- CI fails on lint or tests.
- Release workflow produces traceable build artifacts and dependency metadata.
- Local contributors can still build with debug signing fallback.

### 2. [x] Update and gate Android dependency baselines

Status: Completed 2026-05-17. The project now builds on Gradle 9.4.1,
AGP 9.2.1, Kotlin/Compose compiler plugin 2.3.21, compileSdk 36,
Compose BOM 2026.05.00, Core KTX 1.18.0, Activity Compose 1.13.0,
Lifecycle Runtime KTX 2.10.0, Navigation Compose 2.9.8, and Material 3 1.4.0.
The migration follows AGP 9 built-in Kotlin by removing the legacy
`org.jetbrains.kotlin.android` plugin and using `kotlin.compilerOptions`.
`targetSdk` intentionally remains 35; target-behavior changes belong to the
separate policy/platform audit.

Evidence: Before completion, L2 used AGP 8.7.3, Kotlin 2.0.21, Compose BOM
2024.12.01, core-ktx 1.15.0, activity-compose 1.9.3, navigation-compose 2.8.5,
and lifecycle-runtime-ktx 2.8.7. E9 metadata on 2026-05-17 showed newer stable
baselines including AGP 9.2.1, Kotlin 2.3.21, Compose BOM 2026.05.00,
core-ktx 1.18.0, activity-compose 1.13.0, navigation-compose 2.9.8, and
lifecycle-runtime-ktx 2.10.0. Android's AGP guidance maps AGP 9.2 to Gradle
9.4.1, and Android's built-in Kotlin migration guide requires removing
`kotlin-android` under AGP 9.

Deliverables:

- Upgrade in controlled steps, not as one blind version bump.
- Run build/lint after each logical group.
- Audit Compose and Activity behavior changes, especially edge-to-edge and
  predictive/back behavior.
- Keep ML Kit dependencies separate because the current ML Kit versions are
  already at the latest stable or latest beta metadata found in the research
  pass.

Acceptance:

- App builds with upgraded stable baseline.
- Any behavior changes are captured in `CHANGELOG.md` and `CLAUDE.md`.
- Rollback path is clear in commits.

### 3. [x] Harden permissions, privacy posture, and Play policy documentation

Status: Completed 2026-05-17. The manifest no longer requests
`MANAGE_EXTERNAL_STORAGE`; Android 11+ cleanup uses scoped-storage delete
confirmation. `android:allowBackup` is now false, `SECURITY.md` and README
document permissions/privacy/release posture, display-over-apps is optional with
notification fallback, Long Screenshot shows an in-app Accessibility disclosure
before opening system settings, and the foreground-service special-use subtype
now names the screenshot-monitoring/user-controls use case.

Evidence: Before completion, L5 declared `MANAGE_EXTERNAL_STORAGE`,
`SYSTEM_ALERT_WINDOW`, AccessibilityService, foreground-service special use,
media permissions, and `android:allowBackup="true"`. E2 and E3 required careful
special-use and all-files justification; Google Play Accessibility guidance also
requires clear user-facing disclosure for AccessibilityService use. E10 gives
mobile security control categories.

Deliverables:

- Document why each sensitive permission exists and what happens without it.
- Remove or justify all-files access, and verify in-app education for
  overlay/background launch fallback, AccessibilityService long screenshots,
  and notification permission.
- Audit backup behavior against stored preferences such as favorites,
  automation rules, save paths, and recent crop metadata. Decide whether to
  exclude sensitive keys or disable backup.
- Verify foreground-service special-use subtype text and Play Console wording.
- Add privacy/security notes to README or a dedicated `SECURITY.md`.

Acceptance:

- A reviewer can map every sensitive permission to a user-visible feature.
- The app remains usable without optional permissions.
- Backup behavior is intentional and documented.

### 4. [x] Build non-destructive project sidecars

Status: Completed 2026-05-17. SnapCrop now has a versioned
`.snapcrop.json` project schema, an `Editable project sidecars` setting,
MediaStore JSON sidecar export next to image/SVG exports, JSON share/view intent
support for reopening projects, editor initialization from saved crop,
adjustment, pixelate, and draw-layer state, and a recoverable source-missing
screen. While editable sidecars are enabled, the main save path keeps the source
image so the project can actually reopen.

Evidence: L7 shows SVG sidecars now preserve visible vector output, but project
state is not re-openable. Competitors and annotation engines in E12 commonly
separate base image plus editable annotation model.

Deliverables:

- Define a `.snapcrop.json` project schema for source URI/hash, crop rect,
  rotation, shape crop, adjustments, filters, pixelate rectangles, draw layers,
  layer order, visibility, text properties, and export settings.
- Save project sidecars next to exports when enabled.
- Reopen a sidecar into the editor without mutating the original screenshot.
- Keep SVG sidecars as an interoperability/export artifact.

Acceptance:

- An annotated crop can be closed, reopened, edited, and re-exported.
- Sidecar versioning handles future schema changes.
- Missing source image produces a clear recoverable state.

### 5. [x] Split the editor before adding more surface area

Status: Completed 2026-05-17. The editor now has focused model/state helpers
in `EditorModels.kt`, reusable canvas helpers in `EditorCanvas.kt`, the draw
layer panel in `EditorLayers.kt`, and the before/after preview in
`EditorPreview.kt`. `CropEditorScreen.kt` still owns the main gesture/canvas
workflow, but it is no longer the permanent home for every future editor
surface. `docs/EDITOR_REGRESSION_CHECKLIST.md` records the manual verification
surface for crop handles, pinch zoom, draw tools, layer visibility/order,
undo/redo, SVG export, sidecar reopen, and save/share flows.

Evidence: L6 previously showed `CropEditorScreen.kt` at about 2,821 lines and
owning UI, gesture handling, rendering, layers, tools, controls, and state.

Deliverables:

- Extract editor model/state, layer panel, export preview, and reusable canvas
  rendering helpers into focused files or modules.
- Preserve current gesture behavior; do not introduce parallel gesture detectors
  that conflict with the established `awaitEachGesture` pattern documented in
  `CLAUDE.md`.
- Add a regression checklist for crop handles, pinch zoom, draw tools, layer
  visibility/order, undo/redo, SVG export, sidecar reopen, and save/share.

Acceptance:

- No single editor file remains the permanent home for every future tool.
- Existing behavior is preserved under focused verification.

## P1: Deepen Screenshot-Specific Workflows

### 6. [x] Long-screenshot stitcher v2

Status: Completed 2026-05-17. Long screenshot capture now allows up to ten
frames behind an 18-second safety guard, stops with explicit reasons for end of
content, repeated/stuck frames, capture failure, frame cap, or time cap, and
opens a review screen before gallery save. The review screen previews the tall
image and offers Save & Edit, Retry, and Discard. The stitcher now searches for
true overlap below repeated top chrome, trims repeated bottom chrome from
intermediate frames, uses denser normalized band/edge scoring, and has
Robolectric coverage for stuck-frame detection and sticky-header/footer
reduction.

Evidence: L8 previously capped capture at five frames and used a simple sampled
pixel difference stitcher. E1 documents `takeScreenshot()` behavior and
rate-limit errors. ScreenshotTile in E12 is an Android reference for
AccessibilityService capture.

Deliverables:

- Detect repeated/stuck scrolls and stop cleanly.
- Improve overlap matching with normalized crop-band comparison or phase-style
  scoring.
- Remove or reduce sticky headers/footers during merge.
- Allow more than five frames behind a battery/time guard.
- Add preview and retry controls before saving.

Acceptance:

- Better merges on chat, browser, settings, and feed screenshots.
- Failures produce understandable messages instead of corrupted tall images.

### 7. [x] Persistent screenshot intelligence index

Status: Completed 2026-05-17. SnapCrop now has an opt-in local
`ScreenshotIndexStore` SQLite index for media IDs, URIs, names, album/source
hints, dimensions, date, size, favorite state, screenshot state, categories, and
search text. Settings exposes enable, rebuild, and purge controls with local
storage copy. Gallery rebuilds/loads the index when enabled, smart albums use
persisted categories for chats, games, sites, documents, codes, payments, and
sensitive/payment-like screenshots, and album views can search names, source
hints, dimensions, categories, and OCR/barcode tokens captured when the user
runs OCR in the editor. The cleanup button now selects non-favorite
screenshots so users can bulk-delete old raw captures more safely.

Evidence: L10 smart albums were previously heuristic; L11 already had OCR,
barcode, entity, face, and object capabilities. E13 datasets provide evaluation
ideas for UI/screenshot understanding.

Deliverables:

- Maintain a local opt-in index of OCR tokens, entity categories, app/source
  hints, dimensions, date, album, and favorite state.
- Power albums such as conversations, sites, games, documents, codes, payments,
  sensitive screenshots, and user-created saved searches.
- Keep index local and explain storage/deletion.
- Add reindex and purge controls.

Acceptance:

- Search works by text/entity/app-like hints without rescanning every image.
- Users can bulk-clean old non-favorite screenshots safely.

### 8. [x] Expand app profiles into a user-visible rules system

Evidence: L9 currently covers Reddit and X/Twitter only. ShareX in E12 shows
the value of capture -> action recipes, while SnapCrop already has Quick Crop
and last-action plumbing.

Deliverables:

- Expose built-in app profiles with confidence, preview, and test image support.
- Add user-trained profiles: crop bands, source app/package, OCR keywords,
  album destination, redaction rules, and export format.
- Add import/export for profile packs.
- Keep destructive actions opt-in with clear review states.

Acceptance:

- A user can create a profile for one app without code changes.
- Conditional auto-actions explain what they changed and where the image went.

Implemented 2026-05-17:

- Added a JSON-backed user app profile store with source/package hints, OCR
  keywords, crop bands, album destination, redaction preference, and export
  format.
- Exposed built-in Reddit/X profiles and custom rule management in Settings,
  including crop previews, confidence copy, image testing, and profile-pack
  copy/import.
- Wired user profiles into editor/batch auto-crop and Quick Crop. Quick Crop
  now uses rule-specific album/redaction/export settings and explains the save
  destination plus redaction/export outcome.
- Added regression tests for profile-pack round-trip, source/OCR matching, and
  user crop-band application.

### 9. [x] Tablet, foldable, and desktop-mode editor layout

Evidence: L6 editor density, existing Compose stack, Android large-screen
guidance implied by platform changes in E5/E6, and user workflows that benefit
from precision placement.

Deliverables:

- Use adaptive layout for side panels on wide screens.
- Keep dense phone toolbar but move layer/tools/adjustments into persistent
  side panels on tablets, foldables, and DeX-style windows.
- Continue extracting toolbar/tool controls as part of the adaptive layout work
  so the split follows the actual phone/tablet UI shape instead of a
  parameter-heavy wrapper.
- Add keyboard shortcuts and mouse wheel/pan affordances where platform-typical.

Acceptance:

- Phone UI remains recognizable.
- Wide layouts reduce toolbar scrolling and improve layer/edit precision.

Implemented 2026-05-17:

- Added an adaptive editor layout classifier and tested phone vs wide-window
  thresholds.
- Kept the phone toolbar path intact while wide windows move mode selection,
  crop controls, redaction tools, draw tools/layers, and adjustment sliders into
  a persistent right-side inspector.
- Added keyboard shortcuts for save, undo/redo, crop nudging, preview toggling,
  and keyboard zoom, plus mouse-wheel zoom while preserving existing drag/pinch
  behavior.
- Reduced wide-layout toolbar density by hiding the phone-only mode/tool rows
  and moving reset/auto/AI/background/color controls into the inspector.

### 10. [x] Export and reporting workflows

Evidence: Existing PDF export and SVG sidecars in L7/L10; competitors in E12
emphasize upload, report, and workflow automation.

Deliverables:

- PDF report builder for annotated screenshot sets with title, notes, timestamp,
  app/source hint, dimensions, and optional OCR text appendix.
- Batch rename using templates including `%app%`, `%date%`, `%counter%`, and
  profile name.
- Optional upload targets: self-hosted HTTP endpoint, WebDAV/Nextcloud, and
  Imgur anonymous upload. Network features must be off by default and explicit.
- Android share shortcuts for frequent destinations.

Acceptance:

- A bug report or incident bundle can be created entirely inside SnapCrop.
- Network exports are opt-in, auditable, and never undermine the local-first
  default.

Implemented 2026-05-17:

- Replaced the old image-only gallery PDF action with a report dialog that
  captures title, notes, and an optional OCR appendix.
- PDF reports now include a cover page, creation timestamp, selected-image
  metadata, app/source hints where MediaStore exposes them, dimensions, size,
  date, category hints from the local index, and image pages sized for review.
- Added gallery batch rename with templates for `%app%`, `%date%`, `%time%`,
  `%timestamp%`, `%counter%`, and `%profile%`, plus filename sanitization and
  unit coverage for token expansion.
- Added Settings-gated network exports that are off by default. HTTP endpoint
  exports use multipart PDF upload, WebDAV/Nextcloud exports use PUT, and Imgur
  anonymous export uploads selected images with a user-provided client ID.
- Added chooser-backed share shortcuts by recording chosen share components and
  surfacing the most-used destinations first in later Android share sheets.
- Added regression coverage for network export configuration and share target
  shortcut persistence.

## P2: Research And Advanced Capabilities

### 11. [x] ML Kit robustness and model-state UX

Evidence: L11 uses several ML Kit clients. E7/E8 include official guidance and
known issue classes; subject segmentation and translation may involve model
download or device-specific failures.

Deliverables:

- Centralize ML capability checks and user-facing fallback messages.
- Cache model availability/status where supported.
- Show first-run download progress and Wi-Fi/storage guidance for translation
  and subject segmentation.
- Add retry guidance for Play Services errors.

Acceptance:

- Failed ML features degrade predictably and do not silently look like no-ops.

Implemented 2026-05-17:

- Added centralized `MlKitStatus` and `MlKitStatusStore` helpers for ML Kit
  feature labels, Google Play services checks, retry guidance, and cached
  model readiness/error state.
- Translation now reports language identification, Wi-Fi model download, and
  on-device translation progress, and caches successful language-pair model
  readiness.
- Background removal now returns a structured subject-segmentation result
  instead of silently returning the original bitmap on ML failure; the editor
  shows first-run model/download guidance and failure/no-subject messages.
- Added Play Services/storage/Wi-Fi retry messages for ML failures and unit
  coverage for the new status text.

### 12. [ ] Optional advanced erase backend

Evidence: L11 local Smart Erase avoids a large model. E13 lists LaMa and ONNX
Runtime Android as possible research references.

Deliverables:

- Keep current Smart Erase as default.
- Prototype optional downloaded/on-device model packs only if size, licensing,
  latency, and battery behavior are acceptable.
- Evaluate on a small private screenshot mask benchmark before shipping.

Acceptance:

- No large model is bundled by default.
- Any advanced backend is opt-in and measurably better on screenshot objects.

### 13. [ ] Dataset-backed evaluation harness

Evidence: L11 has ML and heuristic engines; E13 includes UI/screenshot datasets
and model/tooling references.

Deliverables:

- Create a small repo-local synthetic fixture set for crop, stitch, OCR
  redaction, and app-profile matching.
- Use public datasets only for research/evaluation scripts where licenses fit.
- Track precision/recall style metrics for sensitive text and profile matching.

Acceptance:

- Changes to heuristics can be compared before/after.
- Dataset license constraints are documented before any fixture is committed.

### 14. [ ] Screenshot explanation and accessibility summaries

Evidence: E13 includes local model directions such as Gemma and LiteRT, but the
repo currently has no local LLM runtime. This remains exploratory.

Deliverables:

- Research on-device summarization feasibility after the foundation work.
- Prefer small, optional, local models and clear privacy controls.
- Start with OCR/entity-based summaries before adding a language model.

Acceptance:

- No remote AI dependency is introduced without an explicit product decision.

## Repo Hygiene Tasks

- Remove tracked signature/build byproducts identified by L12 if they are not
  intentionally versioned release evidence.
- Add `SECURITY.md`, `CONTRIBUTING.md`, and a release checklist if the project
  is intended for public contributors.
- Keep `PROJECT_CONTEXT.md`, `CLAUDE.md`, `CHANGELOG.md`, and this roadmap in
  sync after material work.
- Convert completed roadmap items into changelog/history, not permanent open
  roadmap entries.

## Completion Policy

The next implementation session should start with P0. Complete one coherent
vertical slice, verify it locally, update this roadmap, and commit the result.
