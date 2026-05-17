# State Of Repo

Research date: 2026-05-17

## Git Snapshot

- Working repo: `C:\Users\--\repos\SnapCrop`
- Branch at reconnaissance: `main`
- Remote: `origin https://github.com/SysAdminDoc/SnapCrop.git`
- Sync state before this research changes: `main...origin/main`
- Recent head: `d34b7b2 feat: export svg annotation sidecars`

Recent commits inspected:

```text
d34b7b2 feat: export svg annotation sidecars
e698200 feat: add layered draw editing
486f0f7 feat: add smart auto albums
20275ce feat: add ai reframe
42148c4 feat: add sensitive text auto redaction
1c4435f feat: replace heal with smart erase
ce8833f feat: add ocr translation flow
2755940 feat: add last action quick tile
8765e2e feat: add screen recording frame workflow
ad061fd feat: add conditional quick crop actions
```

`rtk git log -10` was required by shared instructions but `rtk` was not
available in this PowerShell session. Plain `git` was used as the fallback.

## Project Structure

Key repository files:

- `AGENTS.md`: agent pointer file. Locally updated to point at
  `PROJECT_CONTEXT.md`; ignored by global Git configuration.
- `CLAUDE.md`: detailed working notes, stack, build commands, architecture,
  version history, gotchas, and UI rules. Locally updated to point at
  `PROJECT_CONTEXT.md`; ignored by this repo's `.gitignore`.
- `README.md`: public feature/build/privacy overview.
- `CHANGELOG.md`: v6.19.0 changelog.
- `ROADMAP.md`: replaced with the 2026-05-17 prioritized roadmap.
- `app/build.gradle.kts`: Android app build, signing, version.
- `gradle/libs.versions.toml`: dependency catalog.
- `.github/workflows/build.yml`: lint/test/build/dependency-review/SBOM
  workflow after P0.1 continuation.

Kotlin source size indicators from `app/src/main`:

| File | Approx lines | Observation |
|---|---:|---|
| `CropEditorScreen.kt` | 2,423 | Main editor gesture/canvas workflow after first split |
| `EditorModels.kt` | 224 | Editor model/state helpers, filters, snapshots |
| `EditorLayers.kt` | 169 | Draw layer panel and labels |
| `EditorPreview.kt` | 118 | Before/after preview surface and divider gesture |
| `EditorCanvas.kt` | 43 | Crop handle and gradient rendering helpers |
| `CropActivity.kt` | 1,519 | Bitmap load/export/save/share/SVG sidecars |
| `MainActivity.kt` | 1,240 | Home, permissions, recent crops, batch tools |
| `GalleryScreen.kt` | 1,171 | Gallery, smart albums, viewer, PDF |
| `ScreenshotService.kt` | 533 | Screenshot monitor, quick crop, last action |
| `SettingsActivity.kt` | 467 | Export and behavior preferences |
| `CollageActivity.kt` | 453 | Collage workflow |
| `ScrollCaptureService.kt` | 412 | Long screenshot capture/stitch |
| `StitchActivity.kt` | 376 | Stitch workflow |
| `VideoClipActivity.kt` | 325 | Video frame/trim UI |
| `DeviceFrameActivity.kt` | 308 | Device mockup export |
| `SmartEraseEngine.kt` | 295 | Local mask fill smart erase |
| `AutoCrop.kt` | 238 | Border/system-bar crop engine |
| `AppCropProfiles.kt` | 194 | Reddit and X/Twitter profile rules |
| `VideoClipExporter.kt` | 179 | MP4 trim/extract backend |

## Build And Dependencies

Live build settings from `app/build.gradle.kts` and version catalog:

- `applicationId`: `com.sysadmindoc.snapcrop`
- `namespace`: `com.sysadmindoc.snapcrop`
- `compileSdk`: 36
- `minSdk`: 29
- `targetSdk`: 35
- `versionCode`: 67
- `versionName`: `6.19.0`
- Gradle wrapper: 9.4.1
- AGP: 9.2.1
- Kotlin/Compose compiler plugin: 2.3.21
- Compose BOM: 2026.05.00
- Material3: 1.4.0
- Coil Compose: 2.7.0
- AndroidX core/activity/navigation/lifecycle were upgraded to the latest
  stable metadata found on 2026-05-17.
- ML Kit dependencies were at latest stable or latest beta metadata for the
  specific artifacts inspected on 2026-05-17.

Release signing:

- Release signing reads `keystore.properties` or environment variables.
- If signing material is absent, contributor release builds fall back to debug
  signing instead of failing.
- `CLAUDE.md` warns unsigned APKs cannot be installed and the release APK path is
  `app/build/outputs/apk/release/app-release.apk`.

## CI State

`.github/workflows/build.yml` currently runs lint, JVM unit tests, debug
assemble, release assemble, dependency review on pull requests, and CycloneDX
SBOM artifact generation for release/manual runs.

## Manifest And Permissions

Observed in `AndroidManifest.xml`:

- media read permissions for images/video and legacy external storage maxSdk 32,
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`,
- `POST_NOTIFICATIONS`,
- `RECEIVE_BOOT_COMPLETED`,
- optional `SYSTEM_ALERT_WINDOW` with notification fallback,
- `VIBRATE`,
- no all-files access permission,
- `android:allowBackup="false"`,
- exported `MainActivity`,
- exported `CropActivity` for `ACTION_SEND image/*`,
- exported `ScrollCaptureService` with `BIND_ACCESSIBILITY_SERVICE`,
- exported Quick Settings tile services with `BIND_QUICK_SETTINGS_TILE`,
- non-exported `ScreenshotService`, `BootReceiver`, and `FileProvider`,
- foreground-service special-use subtype property for screenshot monitoring
  and persistent user controls.

Security/policy implications are covered in
`SECURITY_AND_DEPENDENCY_REVIEW.md`.

## Functional State

Implemented features confirmed by live files and history:

- screenshot monitoring and quick crop,
- delayed capture,
- last-action Quick Settings tile,
- long screenshot capture,
- Reddit/X app crop profiles,
- conditional auto-actions with sensitive text redaction,
- OCR, barcode, face detection, object detection, subject segmentation,
  translation, entity extraction,
- smart erase,
- AI reframe,
- gallery with heuristic smart albums,
- video frame extraction and MP4 trim,
- stitch, collage, device mockup, PDF export,
- layered draw editing,
- SVG annotation sidecars,
- editable `.snapcrop.json` project sidecars,
- first editor split into model, canvas-helper, layer-panel, and preview files.

## Repo Hygiene Findings

- `rg` found no TODO/FIXME markers outside build output.
- JVM/Robolectric tests now cover auto-crop, app profiles, sensitive text
  patterns, Smart Erase behavior, project sidecars, and extracted editor model
  helpers.
- `rg` found no `RoundedCornerShape(50...)`, `RoundedCornerShape(999...)`, or
  `CircleShape` matches in source during the UI-rule scan.
- `git ls-files "*.apk" "*.idsig"` found tracked `.idsig` artifacts even though
  signature/build byproducts are ignored. These should be reviewed and likely
  removed from source control in a separate cleanup.

## Reconnaissance Limits

- No emulator/device runtime validation was performed during the research phase.
- No Gradle build had been run before artifact writing; verification results are
  recorded in `CHANGESET_SUMMARY.md` after completion.
- External dependency freshness is a 2026-05-17 snapshot and should be refreshed
  before any upgrade PR.
