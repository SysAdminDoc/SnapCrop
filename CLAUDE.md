# SnapCrop

## Overview
Android screenshot autocrop editor. Detects screenshots via foreground service, opens them in a crop editor with automatic edge detection and ML Kit AI fallback.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- AMOLED black theme (Catppuccin accent colors)
- ML Kit Object Detection (smart crop fallback)
- minSdk 29, targetSdk 35

## Architecture
- `ScreenshotService` - Foreground service with ContentObserver on MediaStore. Detects screenshots, shows notification with Quick Save / Edit / Dismiss actions. Handles quick-save (autocrop + save) directly in service.
- `CropActivity` - Loads bitmap, runs AutoCrop, hosts CropEditorScreen composable. Supports share via FileProvider.
- `CropEditorScreen` - Compose Canvas with draggable corner/edge handles, dim overlay, rule-of-thirds grid. Preview toggle, share button, AI crop button. Shows crop method indicator (Border/System bars/AI).
- `AutoCrop` - Multi-strategy: (1) uniform-border scan, (2) status/nav bar strip, (3) full image fallback. Uses density-aware system bar height detection.
- `SmartCropEngine` - ML Kit Object Detection wrapper. Detects dominant objects, returns encompassing bounding box. Used as fallback when border scan finds nothing.
- `MainActivity` - Home screen with service toggle, permission management, manual image picker.

## Key Files
- `AutoCrop.kt` - Multi-strategy edge detection (border scan + system bar strip)
- `SmartCropEngine.kt` - ML Kit object detection for AI-powered crop
- `CropEditorScreen.kt` - Crop UI: Canvas, handles, preview, share, method indicator
- `ScreenshotService.kt` - Screenshot detection + notification quick actions
- `CropActivity.kt` - Activity with save/share/delete logic, FileProvider sharing
- `ScreenshotOverlay.kt` - Floating thumbnail overlay (TYPE_APPLICATION_OVERLAY), tap to edit, swipe to dismiss

## Build
```
./gradlew assembleDebug
./gradlew assembleRelease
```

## Version
v2.1.0

## Version History
- v2.1.0: Fix top-crop status bar detection (edge-sample reference color, lower threshold), screenshot thumbnail overlay (tap to edit, swipe to dismiss), overlay permission flow
- v2.0.0: ML Kit AI crop, notification quick actions, preview toggle, instant share, status bar auto-strip, crop method indicator
- v1.0.0: Initial release — border scan autocrop, draggable crop editor, screenshot detection service

## Gotchas
- `foregroundServiceType="specialUse"` required for Android 14+
- ContentObserver debounce needed — MediaStore fires multiple events per screenshot
- ML Kit runs via Google Play Services — no APK size cost, but requires Play Services on device
- Quick save in ScreenshotService runs on main thread for simplicity — bitmaps are small (screenshots)
- FileProvider needed for share intent — cache dir `shared_crops/`
- Preview mode creates a new bitmap each recomposition with crop changes — acceptable for screenshots
- SYSTEM_ALERT_WINDOW needed for thumbnail overlay — gracefully skipped if not granted
- Status bar detection: reference color sampled from edge pixels (not center) to avoid clock text
