# Dataset, Model, And Integration Review

Research date: 2026-05-17

SnapCrop has a relevant data/model/integration angle because it uses ML Kit,
captures/edits screenshots, performs OCR/redaction, groups screenshots, and may
eventually support upload/report workflows.

## Existing Model And Intelligence Surface

Implemented locally:

- ML Kit text recognition via `TextExtractor.kt`.
- ML Kit barcode scanning via `BarcodeScanner.kt`.
- ML Kit face detection via `FaceDetector.kt`.
- ML Kit object detection via `SmartCropEngine.kt` and reframe paths.
- ML Kit subject segmentation via `BackgroundRemover.kt`.
- ML Kit language ID and translation via `TextTranslator.kt`.
- ML Kit Entity Extraction plus regex/Luhn in `SensitiveTextDetector.kt`.
- Local mask-based smart erase via `SmartEraseEngine.kt`.
- Heuristic smart albums in `GalleryScreen.kt`.
- App/profile heuristics in `AppCropProfiles.kt` and `ConditionalAutoActions.kt`.

## Dataset Opportunities

| Dataset/source | URL | Possible use | Cautions |
|---|---|---|---|
| RICO | http://interactionmining.org/rico | UI screenshot/category research, app-screen layout heuristics. | License and redistribution review required before committing fixtures. |
| Screen2Words | https://github.com/google-research-datasets/screen2words | Screenshot summarization and OCR/entity evaluation ideas. | Research dataset; do not imply production model quality. |
| Android in the Wild | https://github.com/google-research/google-research/tree/master/android_in_the_wild | Mobile UI interaction/screenshot understanding research. | Large/research-oriented; license and scope review needed. |
| UICrit | https://github.com/google-research-datasets/uicrit | UI critique/screenshot text-image reasoning research. | Useful for future summaries, not immediate app feature. |
| Synthetic private fixture set | repo-local future work | Deterministic tests for crop/stitch/redaction/profile matching. | Should avoid private user screenshots and generated sensitive data should be fake. |

Recommended near-term dataset path:

1. Do not import public datasets into the app repo yet.
2. Build a small synthetic fixture set for tests first.
3. Use public datasets only for research scripts after license review.
4. Track metrics for crop bounds, redaction detection, profile matching, and
   stitch correctness.

## Model Opportunities

| Model/runtime | URL | Fit | Priority |
|---|---|---|---|
| ML Kit current APIs | https://developers.google.com/ml-kit | Already integrated; improve robustness and UX. | High |
| MediaPipe Image Segmenter | https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter | Possible alternative segmentation path. | Low until current BG remove UX is hardened |
| LiteRT | https://ai.google.dev/edge/litert | Future local model runtime path. | Research |
| ONNX Runtime Android | https://onnxruntime.ai/docs/get-started/with-android.html | Possible optional advanced inpainting runtime. | Research |
| LaMa | https://github.com/advimman/lama | Possible advanced erase backend reference. | Low until model size/latency/license prove acceptable |
| Gemma | https://ai.google.dev/gemma | Possible local screenshot summary/explanation. | Low/research after OCR index exists |

## Integration Opportunities

| Integration | Source | Fit | Guardrails |
|---|---|---|---|
| Android share shortcuts | https://developer.android.com/training/sharing/send | High fit for frequent destinations. | Keep Android-native; no network risk. |
| Imgur anonymous upload | https://apidocs.imgur.com/ | Useful for bug report/sharing workflows. | Must be off by default and explicit. |
| WebDAV/Nextcloud | https://www.rfc-editor.org/rfc/rfc4918 | Good self-hosted option for local-first users. | User-provided endpoint/credentials; clear privacy warnings. |
| Self-hosted HTTP endpoint | Product-specific | Simple power-user workflow. | Explicit opt-in; no background upload surprises. |
| PDF reports | Android `PdfDocument` and current gallery export | Very high fit. | Keep local; include source/annotation metadata only by user choice. |

## Evaluation Ideas

Crop/autocrop:

- expected crop rect for screenshots with light/dark borders,
- system-bar strip correctness,
- no zero-sized crop outputs,
- regression cases from version history.

Sensitive redaction:

- fake emails, phone numbers, IP/MAC/IBAN/card-like numbers,
- Luhn positive/negative card candidates,
- OCR text block bounding behavior,
- false-positive review samples.

Profiles/automation:

- Reddit/X positive and negative fixtures,
- confidence threshold tests,
- action preview text,
- destination album naming.

Stitching:

- synthetic scroll frames with known overlaps,
- sticky header/footer synthetic cases,
- repeated/no-scroll frame detection.

Export safety:

- redacted output pixel checks,
- no stale source-byte append,
- sidecar JSON/SVG coordinate mapping.

## Recommendation

Do not add a new model first. Build the test/evaluation foundation, improve ML
capability UX, then decide whether optional advanced models are worth their
size, latency, battery, and maintenance cost.
