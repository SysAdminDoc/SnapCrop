# Source Register

Research date: 2026-05-17

This register lists local and external sources used for the 2026-05-17
repository research and roadmap planning pass.

## Local Sources

| ID | Source | Use |
|---|---|---|
| L1 | `AGENTS.md` | Agent instruction scope and context pointer. |
| L2 | `CLAUDE.md` | Stack, architecture, version history, gotchas, UI rules. |
| L3 | `PROJECT_CONTEXT.md` | New canonical consolidated context created by this run. |
| L4 | old `ROADMAP.md` | Reconciled completed vs open roadmap items before replacement. |
| L5 | `README.md` | Public product positioning, privacy claims, build notes. |
| L6 | `CHANGELOG.md` | Current v6.19.0 release evidence. |
| L7 | `app/build.gradle.kts` | App version, SDK levels, signing, build config. |
| L8 | `gradle/libs.versions.toml` | Dependency versions. |
| L9 | `gradle/wrapper/gradle-wrapper.properties` | Gradle wrapper version. |
| L10 | `.github/workflows/build.yml` | CI/release workflow state. |
| L11 | `app/src/main/AndroidManifest.xml` | Permissions, services, exported components, backup. |
| L12 | `app/src/main/java/com/sysadmindoc/snapcrop/CropEditorScreen.kt` | Editor size, UI architecture, layer/tool surface. |
| L12a | `app/src/main/java/com/sysadmindoc/snapcrop/EditorModels.kt` | Extracted editor model/state, filters, snapshots. |
| L12b | `app/src/main/java/com/sysadmindoc/snapcrop/EditorCanvas.kt` | Extracted reusable crop-handle and gradient rendering helpers. |
| L12c | `app/src/main/java/com/sysadmindoc/snapcrop/EditorLayers.kt` | Extracted draw layer panel. |
| L12d | `app/src/main/java/com/sysadmindoc/snapcrop/EditorPreview.kt` | Extracted before/after preview surface. |
| L12e | `app/src/main/java/com/sysadmindoc/snapcrop/EditorAdaptiveLayout.kt` | Adaptive editor phone/wide thresholds and side-panel sizing. |
| L12f | `docs/EDITOR_REGRESSION_CHECKLIST.md` | Manual regression checklist for editor refactors. |
| L13 | `app/src/main/java/com/sysadmindoc/snapcrop/CropActivity.kt` | Save/share/export/SVG sidecar pipeline. |
| L14 | `app/src/main/java/com/sysadmindoc/snapcrop/MainActivity.kt` | Home, permissions, batch operations, recent crops. |
| L15 | `app/src/main/java/com/sysadmindoc/snapcrop/GalleryScreen.kt` | Gallery, index-backed smart albums, favorites, PDF export. |
| L15a | `app/src/main/java/com/sysadmindoc/snapcrop/ScreenshotIndexStore.kt` | Opt-in local screenshot intelligence index. |
| L15b | `app/src/main/java/com/sysadmindoc/snapcrop/SettingsActivity.kt` | Index enable/rebuild/purge controls and App rules management UI. |
| L15c | `app/src/test/java/com/sysadmindoc/snapcrop/ScreenshotIndexClassifierTest.kt` | Source/category classifier coverage. |
| L15d | `app/src/main/java/com/sysadmindoc/snapcrop/ExportWorkflowModels.kt` | Export metadata and batch rename token expansion/sanitization. |
| L15e | `app/src/test/java/com/sysadmindoc/snapcrop/BatchRenameTemplateTest.kt` | Batch rename template regression coverage. |
| L16 | `app/src/main/java/com/sysadmindoc/snapcrop/ScreenshotService.kt` | Screenshot monitoring, quick crop, delayed capture, last action. |
| L17 | `app/src/main/java/com/sysadmindoc/snapcrop/ScrollCaptureService.kt` | Long screenshot capture and stitch implementation. |
| L17a | `app/src/main/java/com/sysadmindoc/snapcrop/LongScreenshotReviewActivity.kt` | Long screenshot review, retry, and save handoff. |
| L17b | `app/src/main/java/com/sysadmindoc/snapcrop/LongScreenshotStore.kt` | Long screenshot temporary review files and MediaStore persistence. |
| L17c | `app/src/test/java/com/sysadmindoc/snapcrop/ScrollStitcherTest.kt` | Stitcher regression coverage for sticky chrome and stuck frames. |
| L18 | `app/src/main/java/com/sysadmindoc/snapcrop/AppCropProfiles.kt` | Built-in Reddit/X crop profile rules plus user profile matching/previews. |
| L18a | `app/src/main/java/com/sysadmindoc/snapcrop/UserAppProfiles.kt` | JSON-backed user app profile packs, source/OCR matching, crop/action settings. |
| L18b | `app/src/test/java/com/sysadmindoc/snapcrop/UserAppProfileStoreTest.kt` | User profile JSON and matching regression coverage. |
| L19 | `app/src/main/java/com/sysadmindoc/snapcrop/ConditionalAutoActions.kt` | Conditional Quick Crop automation rules, including user rule actions. |
| L20 | `app/src/main/java/com/sysadmindoc/snapcrop/SensitiveTextDetector.kt` | Sensitive text detection. |
| L21 | `app/src/main/java/com/sysadmindoc/snapcrop/TextTranslator.kt` | ML Kit translation flow. |
| L22 | `app/src/main/java/com/sysadmindoc/snapcrop/BackgroundRemover.kt` | Subject segmentation/background remove. |
| L23 | `app/src/main/java/com/sysadmindoc/snapcrop/SmartEraseEngine.kt` | Local smart erase engine. |
| L24 | `app/src/main/java/com/sysadmindoc/snapcrop/SmartReframeEngine.kt` | Object/text/face based reframe. |
| L25 | `git log -10 --oneline --decorate --date=short` | Recent development trajectory. |
| L26 | `git ls-files "*.apk" "*.idsig"` | Tracked release byproduct finding. |
| L27 | `rg --files app/src/main` and line counts | Source size survey. |
| L28 | `rg` TODO/FIXME/test/UI-radius scans | Repo hygiene checks. |

## Shared Memory Sources

| ID | Source | Use |
|---|---|---|
| M1 | `C:\Users\--\.claude\CLAUDE.md` | Shared behavior rules. |
| M2 | `C:\Users\--\CLAUDE.md` | Shared working protocol and definition of done. |
| M3 | `C:\Users\--\.claude\projects\c--Users----repos\memory\MEMORY.md` | Shared memory index. |
| M4 | `C:\Users\--\.claude\projects\c--Users----repos\memory\snapcrop.md` | Current SnapCrop memory. |
| M5 | `C:\Users\--\.claude\projects\c--Users----repos\memory\stack-android.md` | Android stack conventions. |
| M6 | `C:\Users\--\.claude\projects\c--Users----repos\memory\android-apk.md` | APK signing/release memory. |
| M7 | `C:\Users\--\.codex\memories\MEMORY.md` | Historical SnapCrop Codex memory. |
| M8 | `C:\Users\--\.codex\memories\rollout_summaries\2026-05-13T15-01-45-ivbZ-snapcrop_premium_polish_signed_release_adb_reinstall.md` | Historical release/ADB reinstall memory. |

## Android Platform And Policy Sources

| ID | URL | Use |
|---|---|---|
| A1 | https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback) | AccessibilityService screenshot API and rate-limit errors. |
| A2 | https://developer.android.com/develop/background-work/services/fgs/service-types | Foreground service type and special-use guidance. |
| A3 | https://support.google.com/googleplay/android-developer/answer/10467955 | Google Play all-files access policy. |
| A4 | https://developer.android.com/training/data-storage/shared/photopicker | Android Photo Picker. |
| A5 | https://developer.android.com/about/versions/14/changes/partial-photo-video-access | Android 14 selected photo/video access. |
| A6 | https://developer.android.com/about/versions/15/behavior-changes-15 | Android 15 behavior changes. |
| A7 | https://developer.android.com/about/versions/16/behavior-changes-all | Android 16 behavior changes. |
| A8 | https://developer.android.com/training/sharing/send | Android sharing guidance. |
| A9 | https://developer.android.com/reference/android/webkit/WebView#createPrintDocumentAdapter() | Android print/PDF reference. |
| A10 | https://support.google.com/googleplay/android-developer/answer/10964491 | Google Play AccessibilityService policy and disclosure expectations. |
| A11 | https://developer.android.com/guide/topics/manifest/application-element | Manifest backup attribute reference. |
| A12 | https://developer.android.com/identity/data/autobackup | Android Auto Backup behavior and include/exclude rules. |

## ML Kit And AI Edge Sources

| ID | URL | Use |
|---|---|---|
| ML1 | https://developers.google.com/ml-kit/vision/text-recognition/v2/android | Text recognition reference. |
| ML2 | https://developers.google.com/ml-kit/vision/barcode-scanning/android | Barcode scanning reference. |
| ML3 | https://developers.google.com/ml-kit/vision/face-detection/android | Face detection reference. |
| ML4 | https://developers.google.com/ml-kit/vision/object-detection/android | Object detection reference. |
| ML5 | https://developers.google.com/ml-kit/vision/subject-segmentation/android | Subject segmentation reference. |
| ML6 | https://developers.google.com/ml-kit/language/translation/android | Translation reference. |
| ML7 | https://developers.google.com/ml-kit/language/entity-extraction/android | Entity extraction reference. |
| ML8 | https://developers.google.com/ml-kit/known-issues | ML Kit known issues. |
| ML9 | https://github.com/googlesamples/mlkit | Official samples. |
| ML10 | https://github.com/googlesamples/mlkit/issues/824 | Subject segmentation emulator initialization issue. |
| ML11 | https://github.com/googlesamples/mlkit/issues/858 | Play Services subject segmentation error example. |
| ML12 | https://github.com/googlesamples/mlkit/issues/958 | Device-specific ML Kit crash report example. |
| ML13 | https://ai.google.dev/gemma | Local model research reference. |
| ML14 | https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter | Image segmentation research reference. |
| ML15 | https://ai.google.dev/edge/litert | On-device runtime research reference. |
| ML16 | https://onnxruntime.ai/docs/get-started/with-android.html | ONNX Runtime Android reference. |
| ML17 | https://github.com/advimman/lama | LaMa inpainting research reference. |

## Dependency Sources

| ID | URL | Use |
|---|---|---|
| D1 | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml | AGP latest metadata. |
| D2 | https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml | Kotlin latest metadata. |
| D3 | https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml | Compose BOM metadata. |
| D4 | https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/maven-metadata.xml | AndroidX core metadata. |
| D5 | https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml | Activity Compose metadata. |
| D6 | https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml | Navigation Compose metadata. |
| D7 | https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/maven-metadata.xml | Lifecycle runtime metadata. |
| D8 | https://repo1.maven.org/maven2/io/coil-kt/coil-compose/maven-metadata.xml | Coil 2 metadata. |
| D9 | https://repo1.maven.org/maven2/io/coil-kt.coil3/coil-compose/maven-metadata.xml | Coil 3 metadata. |
| D10 | https://developer.android.com/build/releases/gradle-plugin | AGP release notes. |
| D11 | https://kotlinlang.org/docs/releases.html | Kotlin releases. |
| D12 | https://developer.android.com/develop/ui/compose/bom/bom-mapping | Compose BOM mapping. |
| D13 | https://developer.android.com/jetpack/androidx/releases/core | AndroidX core releases. |
| D14 | https://developer.android.com/jetpack/androidx/releases/activity | AndroidX activity releases. |
| D15 | https://developer.android.com/jetpack/androidx/releases/navigation | AndroidX navigation releases. |
| D16 | https://developer.android.com/jetpack/androidx/releases/lifecycle | AndroidX lifecycle releases. |
| D17 | https://coil-kt.github.io/coil/changelog/ | Coil changelog. |
| D18 | https://repo.maven.apache.org/maven2/org/json/json/maven-metadata.xml | JVM test dependency metadata for sidecar JSON tests. |

## Security And Supply Chain Sources

| ID | URL | Use |
|---|---|---|
| S1 | https://mas.owasp.org/MASVS/ | Mobile app security verification standard. |
| S2 | https://mas.owasp.org/MASTG/ | Mobile application security testing guide. |
| S3 | https://source.android.com/docs/security/bulletin/2023-03-01 | Android bulletin containing CVE-2023-21036. |
| S4 | https://nvd.nist.gov/vuln/detail/CVE-2023-21036 | aCropalypse CVE entry. |
| S5 | https://acropalypse.app/ | Supplementary aCropalypse explainer. |
| S6 | https://github.com/actions/dependency-review-action | Dependency review action. |
| S7 | https://github.com/CycloneDX/cyclonedx-gradle-plugin | CycloneDX Gradle plugin. |

## Competitor And Adjacent Product Sources

| ID | URL | Use |
|---|---|---|
| C1 | https://github.com/T8RIN/ImageToolbox | Android image-toolbox competitor. |
| C2 | https://github.com/T8RIN/ImageToolboxLite | Lightweight ImageToolbox variant. |
| C3 | https://github.com/cvzi/ScreenshotTile | Android screenshot tile/reference capture app. |
| C4 | https://github.com/burhanrashid52/PhotoEditor | Android photo editor library. |
| C5 | https://github.com/Satty-org/Satty | Modern screenshot annotation. |
| C6 | https://github.com/ksnip/ksnip | Cross-platform screenshot/annotation. |
| C7 | https://github.com/ksnip/kImageAnnotator | Annotation engine separation reference. |
| C8 | https://github.com/flameshot-org/flameshot | Desktop screenshot/annotation reference. |
| C9 | https://github.com/ShareX/ShareX | Workflow/upload automation reference. |
| C10 | https://shottr.cc/ | macOS screenshot/OCR/productivity reference. |
| C11 | https://cleanshot.com/ | Commercial capture/share workflow reference. |
| C12 | https://getgreenshot.org/ | Desktop screenshot/annotation reference. |
| C13 | https://support.google.com/pixelphone/answer/15218276 | Pixel Screenshots/support reference. |
| C14 | https://www.samsung.com/us/support/answer/ANS00086231/ | Samsung screenshot/scroll capture reference. |

## Dataset, API, And Integration Sources

| ID | URL | Use |
|---|---|---|
| I1 | http://interactionmining.org/rico | RICO mobile UI dataset. |
| I2 | https://github.com/google-research-datasets/screen2words | Screen2Words dataset. |
| I3 | https://github.com/google-research/google-research/tree/master/android_in_the_wild | Android in the Wild dataset. |
| I4 | https://github.com/google-research-datasets/uicrit | UICrit dataset. |
| I5 | https://apidocs.imgur.com/ | Imgur upload API. |
| I6 | https://www.rfc-editor.org/rfc/rfc4918 | WebDAV RFC. |
