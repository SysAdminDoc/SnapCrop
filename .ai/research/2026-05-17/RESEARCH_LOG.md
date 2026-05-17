# Research Log

Research date: 2026-05-17

## Objective

Produce a durable, evidence-backed understanding and improvement plan for
SnapCrop by reconciling local source, project memory, Android platform guidance,
dependency metadata, competitors, security sources, datasets/models, and
integration opportunities.

## Local Reconnaissance

Commands/classes of commands used:

- Read `AGENTS.md`, `CLAUDE.md`, old `ROADMAP.md`, `README.md`, `CHANGELOG.md`,
  build files, workflow files, and Android manifest.
- Attempted `rtk git log -10`; failed because `rtk` was not recognized.
- Used `git log -10 --oneline --decorate --date=short` and
  `git status --short --branch`.
- Used `rg --files` for repository/source discovery.
- Used `rg` for TODO/FIXME, test-source indicators, forbidden pill/oval UI
  shapes, dependencies, permissions, and key feature classes.
- Used PowerShell and Maven metadata URLs to inspect latest dependency metadata.
- Used `gh repo view` to refresh GitHub project metadata for OSS competitors.

Local search outcomes:

- No TODO/FIXME markers found outside build output.
- No obvious test source directories or test dependencies found.
- No forbidden `RoundedCornerShape(50...)`, `RoundedCornerShape(999...)`, or
  `CircleShape` source matches found in the UI scan.
- Core source has a large editor file and no clear test harness.
- Prior root roadmap was materially stale because many items are complete in
  v6.8.0 through v6.19.0.

## External Search Passes

### Pass 1: Android platform constraints

Targets:

- AccessibilityService screenshot capture and rate limits.
- Foreground-service type and special-use requirements.
- All-files access policy.
- Android Photo Picker and selected-photo permissions.
- Android 15 and 16 behavior changes.
- Sharing and PDF/printing APIs.

Result:

- Platform constraints strongly support P0 permission/policy hardening and P1
  stitcher improvements.

### Pass 2: ML Kit and on-device intelligence

Targets:

- Text, barcode, face, object, subject segmentation, translation, entity
  extraction docs.
- ML Kit known issues and sample repo.
- Open issues around subject segmentation failures.
- On-device model references for optional future work.

Result:

- Existing ML Kit coverage is broad.
- Roadmap should emphasize robust model-state UX, failure messages, first-run
  download handling, and evaluation instead of adding more ML APIs immediately.

### Pass 3: Dependencies and supply chain

Targets:

- Google Maven metadata for AGP, Compose BOM, AndroidX core/activity/navigation/
  lifecycle.
- Maven Central metadata for Kotlin and Coil.
- Android Gradle Plugin, Kotlin, AndroidX, Compose, Coil release notes.
- Dependency review and CycloneDX Gradle references.

Result:

- Android/Kotlin/Compose/AndroidX baseline is behind current stable metadata.
- ML Kit artifacts inspected are current for their stable/beta channels.
- CI lacks dependency review and SBOM generation.

### Pass 4: Direct and adjacent competitors

Targets:

- Android image editors and screenshot tools.
- Desktop annotation/capture tools with strong workflow ideas.
- Commercial capture/share products.
- OEM screenshot references.

Result:

- SnapCrop already exceeds many competitors in local Android screenshot workflow
  breadth.
- Highest leverage is re-editable sidecars, workflow recipes, smart indexing,
  long-screenshot reliability, policy-safe permissions, and reporting/export
  flows.

### Pass 5: Datasets, models, and integrations

Targets:

- Mobile UI/screenshot datasets: RICO, Screen2Words, Android in the Wild,
  UICrit.
- Optional local model/runtime references: Gemma, MediaPipe/Image Segmenter,
  LiteRT, ONNX Runtime Android, LaMa.
- Export integrations: Imgur API, WebDAV RFC.

Result:

- Public datasets are useful for research inspiration and possible evaluation
  scripts but require license review before committing fixtures.
- Remote/network integrations should remain off by default because the product
  positioning is local-first.

### Pass 6: P0.3 policy-hardening refresh

Targets:

- Google Play all-files access policy.
- Google Play AccessibilityService policy and disclosure expectations.
- Android manifest backup attributes and Auto Backup behavior.
- Current app permission prompts, deletion paths, overlay fallback, and
  Accessibility setup flow.

Result:

- `MANAGE_EXTERNAL_STORAGE` was not defensible for prompt-free media deletion
  because scoped-storage delete confirmation already covers the workflow.
- App-data backup should be disabled because preferences can expose local save
  paths, favorites, automation toggles, and screenshot workflow habits.
- Overlay access should stay optional because notification actions can open the
  editor when Android blocks background launch.
- Long Screenshot needs a clear in-app disclosure before Accessibility settings.

### Pass 7: P0.4 sidecar implementation pass

Targets:

- Existing SVG sidecar export flow.
- `CropActivity` save/open flow and `CropEditorScreen` state handoff.
- Draw layer, pixelate rectangle, crop, and adjustment state representation.
- JVM test coverage for JSON encode/decode.

Result:

- Added a versioned `.snapcrop.json` schema and serializer.
- Added save/open wiring without rewriting the monolithic editor.
- Added a project-sidecar setting; enabled sidecars keep the source image so
  re-openable projects do not point at a deleted original.
- Added sidecar serialization tests using a JVM `org.json` test dependency.

### Pass 8: P0.5 editor split implementation pass

Targets:

- `CropEditorScreen.kt` top-level models and helpers.
- Draw layer panel rendering.
- Before/after preview surface and divider gesture.
- Low-risk extracted model regression tests.

Result:

- Extracted editor models, draw tools, aspect ratios, filters, snapshots, and
  adjustment defaults into `EditorModels.kt`.
- Extracted crop-handle and gradient helpers into `EditorCanvas.kt`.
- Extracted the layer panel into `EditorLayers.kt`.
- Extracted preview rendering into `EditorPreview.kt` while preserving the
  existing `awaitEachGesture` pattern.
- Added `EditorModelsTest` and `docs/EDITOR_REGRESSION_CHECKLIST.md`.

### Pass 9: P1.6 long-screenshot stitcher v2 implementation pass

Targets:

- `ScrollCaptureService` frame capture loop, stop conditions, and stitcher.
- Long screenshot save flow.
- Regression coverage for overlap matching and stuck scroll detection.

Result:

- Raised the long-screenshot frame cap from five to ten behind an 18-second
  safety guard.
- Added explicit stop reasons for repeated/stuck content, capture failure, end
  of scrollable content, frame cap, and time cap.
- Added `LongScreenshotReviewActivity` so captures are previewed before
  MediaStore save, with Save & Edit, Retry, and Discard controls.
- Added `LongScreenshotStore` for temporary review files and shared MediaStore
  persistence.
- Reworked `ScrollStitcher` to search for overlap below repeated top chrome,
  trim repeated bottom chrome, and use denser band/edge scoring.
- Added `ScrollStitcherTest` for sticky chrome reduction and repeated-frame
  detection.

### Pass 10: P1.7 screenshot intelligence index implementation pass

Targets:

- Gallery smart albums and album/photo search.
- Settings controls for opt-in local index management.
- Editor OCR/barcode flow as a source of durable tokens.

Result:

- Added `ScreenshotIndexStore`, a local SQLite index for media IDs, URIs,
  source hints, dimensions, date, size, favorites, screenshot state,
  categories, and search text.
- Added source/category classification for chats, games, sites, documents,
  codes, payments, sensitive/payment-like screenshots, and favorites.
- Added Settings enable/rebuild/purge controls with local-only storage copy.
- Wired Gallery to rebuild/load the index when enabled, enrich photos with
  indexed categories/search text, power expanded smart albums, and search album
  contents by indexed hints.
- Captured OCR and barcode text into the index when the user runs OCR in the
  editor.
- Changed the screenshot cleanup selection to select non-favorite screenshots.
- Added `ScreenshotIndexClassifierTest`.

## Failed Or Thin Searches

- `rtk` was unavailable.
- No active local issues/PRs were discovered from repository files.
- No repo-local nested Cursor/Gemini/Copilot instruction files were found.
- No local test corpus was found.
- No official single Android "screenshot editor" API equivalent exists; workflow
  planning must combine MediaStore, AccessibilityService, foreground services,
  Photo Picker, sharing, and ML Kit evidence.

## Source Saturation Check

The source set includes:

- local instructions,
- shared memories,
- current Git history,
- build/dependency files,
- manifest and core implementation files,
- Android official docs,
- Google Play policy docs,
- ML Kit official docs and samples,
- dependency metadata and release notes,
- security standards/advisories,
- direct OSS competitors,
- adjacent desktop/commercial products,
- datasets/models/runtimes,
- integration API/spec references.

New searches after Pass 5 repeated the same categories and did not change the
top-priority roadmap. The remaining uncertainty is implementation detail, not
strategic direction.

## Research Limits

- GitHub star/release metadata is a 2026-05-17 snapshot and will drift.
- Dependency "latest" values are a 2026-05-17 metadata snapshot and should be
  refreshed before upgrade implementation.
- No emulator/device QA was performed in this planning run.
- No source code changes beyond documentation/context/roadmap pointers were
  intended in the initial planning run. Later autonomous roadmap continuation
  passes did include code changes for P0.1 through P0.5, P1.6, and P1.7.
