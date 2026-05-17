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
  intended in this run.
