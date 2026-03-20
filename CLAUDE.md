# SnapCrop

## Overview
Android screenshot autocrop editor. Detects screenshots via foreground service, shows floating thumbnail overlay (tap to edit, swipe to dismiss), auto-crops system bars using exact device dimensions.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- AMOLED black theme (Catppuccin accent colors)
- ML Kit Object Detection (smart crop fallback)
- minSdk 29, targetSdk 35

## Architecture
- `ScreenshotService` - Foreground service with ContentObserver on MediaStore. Detects screenshots, shows floating overlay thumbnail. Quick-save via intent action.
- `ScreenshotOverlay` - TYPE_APPLICATION_OVERLAY floating thumbnail. GestureDetector for tap (open editor) and fling (dismiss). Slide-in animation, auto-dismiss after 4.5s. Requires SYSTEM_ALERT_WINDOW.
- `CropActivity` - Loads bitmap, runs AutoCrop, hosts CropEditorScreen composable. Supports share via FileProvider.
- `CropEditorScreen` - Compose Canvas with draggable corner/edge handles, dim overlay, rule-of-thirds grid. Preview toggle, share button, AI crop button. Shows crop method indicator (Border/System bars/AI).
- `AutoCrop` - Multi-strategy: (1) uniform-border scan, (2) system bar strip using exact device heights, (3) full image fallback. No pixel analysis for bars — uses `SystemBars` helper.
- `SystemBars` - Queries exact status_bar_height and navigation_bar_height from Android resources via `getIdentifier`. Works from Service context.
- `SmartCropEngine` - ML Kit Object Detection wrapper. Detects dominant objects, returns encompassing bounding box. Used as fallback when border scan finds nothing.
- `MainActivity` - Home screen with service toggle, permission management (media + overlay), manual image picker.

## Key Files
- `AutoCrop.kt` - Multi-strategy edge detection (border scan + exact system bar strip)
- `SystemBars.kt` - Device system bar height queries
- `SmartCropEngine.kt` - ML Kit object detection for AI-powered crop
- `CropEditorScreen.kt` - Crop UI: Canvas, handles, preview, share, method indicator
- `ScreenshotService.kt` - Screenshot detection + overlay + quick save
- `ScreenshotOverlay.kt` - Floating thumbnail (GestureDetector, fling dismiss, animations)
- `CropActivity.kt` - Activity with save/share/delete logic, FileProvider sharing

## Build
```
./gradlew assembleDebug
./gradlew assembleRelease
```
Sign: `zipalign` + `apksigner` with `snapcrop.jks` (keystore in repo root, gitignored)

## Version
v4.5.0

## Version History
- v4.5.0: Fix delete on Android 11+ (MediaStore.createDeleteRequest for scoped storage), viewer share/delete/info buttons, album search bar, gallery refresh after viewer delete. CRITICAL: contentResolver.delete() fails silently for media the app didn't create on Android 11+.
- v4.4.0: Pinch-to-zoom on crop canvas (1-5x, double-tap to reset, zoom indicator badge), photo info panel in gallery viewer (name, dimensions, size, date, path — tap photo to toggle), detectTransformGestures for two-finger zoom/pan
- v4.3.0: Smart face redaction (ML Kit Face Detection, one-tap blur all faces with 15% padding), QR/barcode scanner (ML Kit Barcode Scanning, tap to copy decoded content, green overlays), parallel text+barcode scan in OCR mode
- v4.2.0: OCR text extraction (ML Kit Text Recognition, tap text blocks to copy to clipboard, purple overlay on detected text, loading spinner), TextExtractor engine, OCR edit mode
- v4.1.0: Fullscreen photo viewer (HorizontalPager, swipe between photos, edit button), multi-select (long-press, select all, share/delete batch), "All Photos" timeline view, selection mode top bar with count
- v4.0.0: Built-in gallery (album grid → photo grid → tap to edit), bottom navigation (Home/Gallery tabs), Coil AsyncImage for fast thumbnails, MediaStore album queries, all photos openable in crop editor
- v3.3.0: Text annotation tool (tap to place, dialog to type, renders via nativeCanvas), filled shapes toggle (rect/circle), haptic feedback on undo/redo, 5 draw tools total
- v3.2.0: Custom adaptive icon, async save with progress overlay, batch autocrop from gallery (multi-select + progress), mode indicator banner, aspect ratio chips hidden in draw/pixelate mode
- v3.1.0: Rectangle + circle shape tools, 4 draw tools (pen/arrow/rect/circle), shapes render on export, delete confirmation toast, README overhaul, settings about version bump
- v3.0.0: Freehand draw tool (6 colors, anti-aliased, undo/clear), pixelate undo/clear, edit mode system (crop/pixelate/draw), draw paths rendered on export, notification channel fix for existing installs
- v2.9.0: Pixelate/redact tool, IS_PENDING=0 fix for screenshot detection (file fully written before opening), removed retry loop/delay bandaids, widened recency window to 10s
- v2.8.0: Settings screen (delete original, JPEG/PNG format, quality slider, auto-start), copy to clipboard, async bitmap loading with spinner, landscape lock on CropActivity, settings gear on home screen
- v2.7.0: Flip H/V, double-tap preview toggle, crop % indicator, undo on all crop changes (aspect/reset/auto/AI), scrollable home screen, share button in bottom bar, reorganized toolbar
- v2.6.0: Fix infinite loop (exclude own saves from ContentObserver), fix bitmap memory leak on rotate (recycle old), OOM protection (scale down images >4096px), AI crop loading spinner, async recent crops query, onDestroy bitmap cleanup, time-based debounce
- v2.5.0: Fix service auto-disabling (static isRunning flag + auto_start pref check on resume, auto-restarts if killed), recent crops gallery on home screen (LazyRow thumbnails), crop count stat, redesigned home layout
- v2.4.0: Move flash into CropActivity (Compose Animatable, can't get stuck), delete ScreenFlash/ScreenshotOverlay/RoundedOutlineProvider/RoundedBorderDrawable dead code, remove SYSTEM_ALERT_WINDOW requirement, increase debounce to 2s, simplify ScreenshotService to just launch editor directly
- v2.3.0: White screen flash on screenshot + auto-open editor, undo/redo crop history, edge midpoint handle dots, delete button, share-to-SnapCrop intent, GitHub Actions CI/CD
- v2.2.0: Aspect ratio presets (Free/1:1/4:3/16:9), rotate 90°, save copy (keep original), overlay quick-save + close buttons, thumbnail bitmap scaling (prevent OOM), haptic feedback, boot auto-start receiver, proguard rules for ML Kit
- v2.1.0: Exact system bar heights from device resources (no pixel analysis), floating thumbnail overlay with GestureDetector fling, removed all notifications, overlay permission flow
- v2.0.0: ML Kit AI crop, notification quick actions, preview toggle, instant share, status bar auto-strip, crop method indicator
- v1.0.0: Initial release — border scan autocrop, draggable crop editor, screenshot detection service

## Gotchas
- `foregroundServiceType="specialUse"` required for Android 14+
- ContentObserver debounce needed — MediaStore fires multiple events per screenshot
- ML Kit runs via Google Play Services — no APK size cost, but requires Play Services on device
- Quick save in ScreenshotService runs on main thread — bitmaps are small (screenshots)
- FileProvider needed for share intent — cache dir `shared_crops/`
- SYSTEM_ALERT_WINDOW needed for thumbnail overlay — gracefully skipped if not granted
- Android 12+ edge-to-edge: status bar is transparent, pixel analysis CANNOT detect it. Must use exact device dimensions via `resources.getIdentifier("status_bar_height", "dimen", "android")`
- `getIdentifier` returns configured height even with gesture nav / hidden bars — correct for screenshots since they capture full framebuffer
- ContentObserver fires TWICE per screenshot: IS_PENDING=1 (created, not written) and IS_PENDING=0 (finalized). MUST check IS_PENDING=0 before reading the file or you get "Failed to load image"
- The 500ms delay / retry loop approaches are bandaids. The proper fix is querying IS_PENDING in the ContentObserver handler
