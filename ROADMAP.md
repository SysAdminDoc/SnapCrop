# SnapCrop Roadmap

Roadmap for SnapCrop, the Android screenshot tool with auto-detect, auto-crop, annotate, redact, and ML-Kit-powered features. Focus: deeper automation, smarter AI assists, and tighter share-targeting.

## Planned Features

### Capture & automation
- Long-screenshot / scroll capture via AccessibilityService with auto-stitch (Huawei-style)
- App-specific auto-crop profiles (e.g. always strip Reddit's top bar, always strip Twitter navigation) via template-match
- Conditional auto-actions: "if screenshot came from app X, auto-redact phone numbers + save to album Y"
- Screen-recording clip trim + frame-grab pipeline (record -> pick frame -> edit)
- Quick-tile "one-tap last action" - reruns the last edit pipeline on the newest screenshot

### AI assists
- OCR auto-detect + "Translate this text" via ML Kit on-device translation
- Smart-erase (LaMa/PowerPaint ONNX) replacing the heal tool for object removal
- Auto-redact patterns (emails, phone numbers, credit cards, MAC/IP addresses) via regex + ML Kit entity extraction
- AI reframe - content-aware repositioning when changing aspect ratio
- Auto-tag + auto-album (group screenshots of the same conversation/game/site)

### Editor depth
- Layered editing with reorderable layers panel (current tools are flat)
- Vector annotation export to SVG alongside rasterized PNG
- Snap-to-grid / snap-to-element for pixel-perfect placement
- Text styles library (title/callout/caption presets with brand colors)
- Gradient fill and pattern fill for shape crops
- Speech-bubble tool for tutorials/memes

### Sharing & export
- Direct-share integrations: Telegram compress bypass, Imgur anonymous upload, self-hosted endpoint
- Share as animated GIF from stitched screenshots or before/after
- Batch rename with template (`%app%_%date%_%counter%`) on multi-select
- Watermark presets per app destination (subtle for LinkedIn, loud for Twitter)
- PDF report generation from a set of annotated screenshots (incident/bug reports)

### Gallery & library
- Smart albums (by app, by detected face, by OCR keyword, by resolution)
- Storage health: "Screenshots >90 days, unused, not favorited" bulk-clean UX
- Per-album themes and covers
- Cloud-sync optional: WebDAV/Nextcloud/Google Drive/Dropbox (user-provided creds)

## Competitive Research

- **Xiaomi Gallery / Samsung Screenshot Studio** - long-screenshot and smart-crop are baseline; ML Kit coverage plus stitching puts SnapCrop on par with OEM tools.
- **Markup (iOS) / Screenshot Easy** - strength is immediate inline annotations; SnapCrop matches but should expose an **in-notification** quick-annotate bar (mini palette over the thumbnail).
- **Shottr (macOS)** - pro strength is scrolling capture and OCR; scrolling capture via AccessibilityService is the biggest remaining gap for Android.
- **Greenshot (desktop, GPL)** - step-number tool and obfuscation-first design; already mirrored. Add their "quick-annotate templates" (bug / tutorial / redact) pattern into the Compose editor.
- **CleanShot X** - recording + GIF trim pipeline is their flagship - add this as a stretch feature.

## Nice-to-Haves

- Tasker/Automate integration via `ACTION_SEND` + deep-links for advanced user pipelines
- Wear OS companion - view latest screenshot, favorite it, or trigger "retake after 5s" from watch
- Samsung DeX / desktop-mode larger editor canvas with mouse-first shortcuts
- Backup/restore per-album tags and favorites as JSON
- Optional Material You dynamic color theme alongside the AMOLED Catppuccin default
- On-device "explain this screenshot" LLM (Gemma 2B / Phi-3.5 on-device) for accessibility summaries

## Open-Source Research (Round 2)

### Related OSS Projects
- **ksnip** — https://github.com/ksnip/ksnip — cross-platform screenshot + annotation (desktop); features to borrow: obfuscate with blur/pixelate, drop shadow, grayscale, invert, border, watermark, OCR plugin
- **Satty** — https://github.com/gabm/Satty — modern screenshot annotation with GPU-accelerated rendering, fullscreen annotate, post-shot crop; tools: pointer, crop, line, arrow, rectangle, ellipse, text, marker, blur, highlight, brush
- **Flameshot** — https://github.com/flameshot-org/flameshot — feature-rich Linux/Windows/macOS screenshot + markup with clean toolbar UX
- **Shutter Encoder (not that Shutter)** — skip; use Shotwell/Shutter for reference on Linux only
- **kImageAnnotator** — https://github.com/ksnip/kImageAnnotator — Qt library extracted from ksnip; demonstrates clean separation of annotation engine from host UI
- **ShareX** — https://github.com/ShareX/ShareX — Windows-only but the workflow engine model (capture → OCR → upload/save → notify) is worth emulating
- **Google ML Kit Android samples** — https://github.com/googlesamples/mlkit — text-recognition, doc-scanner, subject-segmentation, selfie-segmentation reference code
- **Pensela** — https://github.com/nashidsalim/pensela — screen annotation tool; live-drawing overlay patterns

### Features to Borrow
- Subject-segmentation mask for one-tap background removal on shots (Google ML Kit subject-seg sample — SnapCrop already has BG remove; extend to selfie-seg for portraits)
- Smart crop that uses ML Kit's object/text detection to suggest trimmed bounds (ML Kit doc-scanner APIs)
- OCR-select: draw a rectangle, extracted text copies to clipboard with one tap (ML Kit text recognition; ksnip OCR plugin)
- Undo/redo history list panel showing thumbnails of each state (ksnip, Satty)
- Magnifier that follows the cursor while drawing pixel-accurate arrows (Flameshot, Satty)
- "Blur behind rectangle" blur that preserves layout but censors content (Satty, ksnip)
- Live watermark editor with draggable position + opacity (ksnip watermark)
- Annotation layer serialized as JSON, re-editable later (kImageAnnotator)
- Cascading toolbar pattern: contextual tools appear only when the active tool is selected (Satty, Flameshot)
- Workflow engine: after-capture recipes (auto-OCR to clipboard, auto-upload to Imgur, auto-scale to 1280px) (ShareX workflows)
- GIF-from-sequence mode: take N screenshots with a timer and merge (ShareX)

### Patterns & Architectures Worth Studying
- Annotation library extracted as a separate module (kImageAnnotator) — lets host apps swap UIs without touching draw logic
- GPU-accelerated canvas via OpenGL ES on Android for smooth pinch/zoom/stroke on high-DPI screenshots (Satty uses OpenGL on desktop; equivalent is SurfaceView/OpenGL on Android)
- Immutable annotation model with a command-pattern undo stack — each tool stroke is a command (standard NLE/editor pattern; kImageAnnotator uses this)
- Two-surface architecture: base image + decorations layer that renders on top; flatten only on export (every tool above)
- ML Kit models bundled vs downloaded: bundle small (text recog), download-on-demand large (subject-seg) to keep APK lean (Google ML Kit best practices)

## Implementation Deep Dive (Round 3)

### Reference Implementations to Study
- **cvzi/ScreenshotTile/app/src/main/java/com/github/cvzi/screenshottile/services/ScreenshotAccessibilityService.kt** — https://github.com/cvzi/ScreenshotTile — `AccessibilityService#takeScreenshot` no-root capture pattern. Template for SnapCrop's scroll-stitch capture roadmap item.
- **PGSSoft/scrollscreenshot** — https://github.com/PGSSoft/scrollscreenshot — host/adb-driven scroll-stitch with `--stitch full` mode. Reference algorithm for matching overlapping scrolled frames via phase-correlation (same approach needed on-device).
- **google/android-wear-stitch-script/stitch.py** — https://github.com/google/android-wear-stitch-script/blob/master/stitch.py — image processing merge logic: overlap detection by normalized cross-correlation + fade blend. Port to Kotlin + `RenderScript`/`Renderscript-alternatives` for on-device.
- **ksnip/kImageAnnotator/src/annotations/items/AnnotationPath.cpp** — https://github.com/ksnip/kImageAnnotator/blob/main/src/annotations/items/AnnotationPath.cpp — immutable annotation model + command-pattern undo. Blueprint for SnapCrop's layer-system roadmap item.
- **gabm/Satty/src/tools/mod.rs** — https://github.com/gabm/Satty/blob/main/src/tools/mod.rs — cascading toolbar + contextual tool UI pattern. Reference for SnapCrop's draw-tool sub-panels.
- **advimman/lama + onnxruntime-mobile** — https://github.com/advimman/lama — LaMa inpainting for "Smart-erase" roadmap item; `big-lama-int8.onnx` + ONNX Runtime Android (`com.microsoft.onnxruntime:onnxruntime-android:1.17.0`).
- **googlesamples/mlkit/android/subject-segmentation** — https://github.com/googlesamples/mlkit/tree/master/android/subject-segmentation — canonical subject-segmenter usage with async result handler; compare SnapCrop's `BackgroundRemover` for cancellation/recycled-bitmap guards.
- **googlesamples/mlkit/android/translate-showcase** — https://github.com/googlesamples/mlkit/tree/master/android/translate-showcase — on-device translation + text-recognition composed flow. Direct template for SnapCrop's "OCR auto-detect + Translate" roadmap item.
- **ShareX/ShareX/ShareX.UploadersLib** — https://github.com/ShareX/ShareX/tree/develop/ShareX.UploadersLib — Imgur anonymous upload client as reference for SnapCrop's "Imgur anonymous upload" share-target. Strip Windows-specific bits; the HTTP calls are portable.

### Known Pitfalls from Similar Projects
- **`AccessibilityService#takeScreenshot` is rate-limited to 1 call per second** — googlesamples/mlkit community — attempting to scroll-capture at >1 fps returns `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`. Throttle with a 1100ms delay between captures. https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot
- **Subject Segmentation fails to init on emulators** — googlesamples/mlkit#824 — `MlKitException: Failed to init module subject segmenter` on x86 emulators. SnapCrop must degrade to "open BG Remove on physical device only" when emulator detected. https://github.com/googlesamples/mlkit/issues/824
- **SubjectSegmenter crashes after Play Services storage clear** — googlesamples/mlkit#858 — intermittent `SecurityException: Unknown calling package name 'com.google.android.gms'`. SnapCrop must catch SecurityException in BG-remove path + retry once after `GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(activity)`. https://github.com/googlesamples/mlkit/issues/858
- **ML Kit SIGSEGV on RK3588 devices** — googlesamples/mlkit#958 — bitmap pixel buffer becomes GPU-unmapped after several minutes of repeated inference. SnapCrop mitigates by recreating input bitmaps per call; never reuse a static `InputImage`. https://github.com/googlesamples/mlkit/issues/958
- **`MANAGE_EXTERNAL_STORAGE` triggers Google Play policy review** — apps declaring this permission face delisting unless use-case justifies it. SnapCrop's delete-without-prompt use case should document the Play Console declaration. https://support.google.com/googleplay/android-developer/answer/10467955
- **ContentObserver fires twice per screenshot (IS_PENDING=1 then =0)** — SnapCrop already validates by decoding stream. Also verify URI before decode (some launchers produce phantom transient URIs).
- **`foregroundServiceType="specialUse"` requires Play Console `declared_use_case` descriptor** — Android 14+ — missing descriptor → `ForegroundServiceTypeException`. Verify SnapCrop's manifest declares `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="screenshot_detection"/>`.
- **`createDeleteRequest()` on Android 11+ only works with `PendingIntent` + `startIntentSenderForResult()`** — not callable from services. SnapCrop's ScreenshotService already skips delete without MANAGE_EXTERNAL_STORAGE — correct.
- **PorterDuff.Mode.CLEAR eraser requires hardware-accelerated canvas off** — on some devices (pre-Android 12), hardware-accelerated `Canvas` ignores CLEAR mode. Wrap in a software-backed `Bitmap.createBitmap(w, h, Config.ARGB_8888)` offscreen pass.
- **`AccessibilityService` must be whitelisted per-user in Settings > Accessibility** — users who uninstall and reinstall lose permission. Provide an in-app deep link via `Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)` and an in-app tutorial card.

### Library Integration Checklist
- **ML Kit Subject Segmentation** — `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1` — entry: `SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()).process(inputImage)`. Gotcha: first-call triggers background download of a ~20MB model; wrap in a progress UI since the first call can take 5-15s. Also: `enableForegroundBitmap()` returns raw ARGB; must composite with alpha over transparent for BG-remove — SnapCrop does this already.
- **ML Kit on-device Translation** — `com.google.mlkit:translate:17.0.3` — entry: `TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build() → Translation.getClient(opts).downloadModelIfNeeded()` then `translator.translate(text)`. Gotcha: each language pair is ~30MB; prompt user for Wi-Fi + disk-space before downloading. Use `DownloadConditions.Builder().requireWifi().build()` to avoid cellular-data surprise.
- **ONNX Runtime (LaMa inpaint for smart-erase)** — `com.microsoft.onnxruntime:onnxruntime-android:1.17.0` — entry: `OrtEnvironment.getEnvironment(); val session = env.createSession(modelPathBytes, SessionOptions())`. Gotcha: LaMa expects 512×512 fixed input; must tile-and-blend for larger strokes. Mask channel must be `{0,1}` float tensor, not `{0,255}` byte — easy off-by-255 bug.


