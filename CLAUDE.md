# SnapCrop

## Overview
Android screenshot autocrop editor. Detects screenshots via foreground service, opens them in a crop editor with automatic edge detection.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- AMOLED black theme (Catppuccin accent colors)
- minSdk 29, targetSdk 35
- Coil for image loading (available but crop editor uses raw Bitmap)

## Architecture
- `ScreenshotService` - Foreground service with ContentObserver on MediaStore. Detects new screenshots by path/name matching and recency check.
- `CropActivity` - Loads bitmap, runs AutoCrop, hosts CropEditorScreen composable.
- `CropEditorScreen` - Compose Canvas with draggable corner/edge handles, dim overlay, rule-of-thirds grid.
- `AutoCrop` - Scans rows/columns from each edge inward looking for uniform color strips. Uses color tolerance (30) and 85% uniformity threshold.
- `MainActivity` - Home screen with service toggle, permission management, manual image picker.

## Key Files
- `app/src/main/java/com/sysadmindoc/snapcrop/AutoCrop.kt` - Edge detection algorithm
- `app/src/main/java/com/sysadmindoc/snapcrop/CropEditorScreen.kt` - Crop UI with Canvas
- `app/src/main/java/com/sysadmindoc/snapcrop/ScreenshotService.kt` - Screenshot detection service
- `app/src/main/java/com/sysadmindoc/snapcrop/CropActivity.kt` - Crop activity with save/delete logic

## Build
```
./gradlew assembleDebug
./gradlew assembleRelease
```

## Version
v1.0.0

## Gotchas
- `foregroundServiceType="specialUse"` required for Android 14+ (no built-in type for media monitoring)
- ContentObserver debounce needed — MediaStore fires multiple events per screenshot
- Crop handles use L-shaped corner brackets (not circles) for cleaner look
- Saved to `Pictures/SnapCrop/`, original screenshot is deleted after crop save
