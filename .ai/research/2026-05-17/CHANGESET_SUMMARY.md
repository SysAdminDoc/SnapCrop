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
