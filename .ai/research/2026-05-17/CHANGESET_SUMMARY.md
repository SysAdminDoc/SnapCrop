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
