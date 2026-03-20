# SnapCrop

## Overview
Android screenshot autocrop editor with annotation, gallery, and ML Kit integration. Detects screenshots via foreground service, auto-crops system bars, provides full annotation toolkit.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- AMOLED black theme (Catppuccin accent colors)
- ML Kit: Object Detection, Text Recognition, Face Detection, Barcode Scanning
- Coil for async image loading
- minSdk 29, targetSdk 35

## Architecture
- `ScreenshotService` - Foreground service with ContentObserver on MediaStore. Detects screenshots, launches editor, shows notification with Edit/Share/Quick Crop actions. Falls back to notification if background activity launch fails (Android 12+).
- `MonitorTileService` - Quick Settings tile to toggle screenshot monitoring on/off.
- `CropActivity` - Loads bitmap, runs AutoCrop, hosts CropEditorScreen composable. Supports share via FileProvider.
- `CropEditorScreen` - Compose Canvas with draggable corner/edge handles, dim overlay, rule-of-thirds grid. 8 draw tools, pixelate, OCR, crop presets, zoom.
- `StitchActivity` - Combine 2+ images vertically or horizontally into one stitched image.
- `AutoCrop` - Multi-strategy: (1) uniform-border scan, (2) system bar strip using exact device heights, (3) full image fallback.
- `SystemBars` - Queries exact status_bar_height and navigation_bar_height from Android resources.
- `SmartCropEngine` - ML Kit Object Detection wrapper for content-aware cropping.
- `GalleryScreen` - Album grid, photo grid with pinch-to-zoom columns, fullscreen viewer, multi-select, favorites, sort, search.
- `MainActivity` - Home screen with service toggle, permission management, manual pick, batch crop, stitch.

## Key Files
- `AutoCrop.kt` - Multi-strategy edge detection (border scan + exact system bar strip)
- `SystemBars.kt` - Device system bar height queries
- `SmartCropEngine.kt` - ML Kit object detection for AI-powered crop
- `CropEditorScreen.kt` - Crop UI: Canvas, handles, preview, share, 8 draw tools, OCR, pixelate
- `ScreenshotService.kt` - Screenshot detection + notification actions + quick save
- `StitchActivity.kt` - Image stitching (vertical/horizontal combine)
- `MonitorTileService.kt` - Quick Settings tile for service toggle
- `CropActivity.kt` - Activity with save/share/delete logic, FileProvider sharing
- `GalleryScreen.kt` - Gallery with albums, viewer, favorites, multi-select

## Build
```
./gradlew assembleDebug
./gradlew assembleRelease
```
Sign: `zipalign` + `apksigner` with `snapcrop.jks` (keystore in repo root, gitignored)

## Version
v5.1.0

## Version History
- v5.1.0: Spotlight/focus draw tool (dims everything outside selected area), image stitching (vertical/horizontal combine 2+ images), Quick Settings tile for monitor toggle, notification actions on screenshot detect (Edit/Share/Quick Crop with 30s auto-dismiss), fixed 6 bitmap memory leaks (rotate/flip/pixelate/destroy), fixed SQL injection in favorites query, fixed applyDraw always copies bitmap (prevents state corruption), added READ_MEDIA_VIDEO permission for gallery video support, AutoCrop edge case validation for scanTop >= scanBottom
- v5.0.0: Stroke smoothing (Catmull-Rom spline on pen/highlighter paths), video support in gallery (play icon overlay, duration badge, system player launch), video+image mixed albums
- v4.9.0: More crop presets (3:4, 9:16, 2:1 + scrollable row), eyedropper color picker (tap image to sample pixel color), gallery pinch-to-zoom grid (2-6 columns), current color preview swatch
- v4.8.0: EXIF metadata stripping on gallery share (re-encodes to clean PNG, setting toggle), gallery sort (date/name/size cycle button), Photo data class extended with name/size fields
- v4.7.0: Favorites system (SharedPreferences store, heart toggle in viewer, Favorites album card, loadFavoritePhotos query), FavoritesStore utility object, auto-contrast callout numbers
- v4.6.0: Highlighter tool (semi-transparent wide strokes, 40% alpha), numbered callouts (tap to place circles with auto-incrementing numbers), 7 draw tools total
- v4.5.0: Fix delete on Android 11+ (MediaStore.createDeleteRequest for scoped storage), viewer share/delete/info buttons, album search bar
- v4.4.0: Pinch-to-zoom on crop canvas (1-5x, double-tap to reset, zoom indicator badge), photo info panel in gallery viewer
- v4.3.0: Smart face redaction (ML Kit Face Detection), QR/barcode scanner (ML Kit Barcode Scanning)
- v4.2.0: OCR text extraction (ML Kit Text Recognition, tap text blocks to copy)
- v4.1.0: Fullscreen photo viewer (HorizontalPager), multi-select, "All Photos" timeline
- v4.0.0: Built-in gallery with album grid, Coil AsyncImage, MediaStore queries
- v3.3.0: Text annotation, filled shapes, haptic undo/redo
- v3.2.0: Custom icon, batch autocrop, async save, mode indicator
- v3.1.0: Rectangle/circle shapes, 4 draw tools
- v3.0.0: Freehand draw, pixelate undo, edit mode system
- v2.9.0: Pixelate/redact tool, IS_PENDING fix
- v2.8.0: Settings screen, clipboard copy, async loading
- v2.7.0: Flip H/V, preview toggle, crop % indicator, undo/redo
- v2.6.0: Fix infinite loop, OOM protection, bitmap leak fixes
- v2.5.0: Fix service auto-disabling, recent crops gallery
- v2.4.0: Flash in CropActivity, dead code cleanup
- v2.3.0: White flash, undo/redo, share-to-SnapCrop, CI/CD
- v2.2.0: Aspect ratios, rotate, save copy, haptic, boot receiver
- v2.1.0: Exact system bar heights, floating overlay
- v2.0.0: ML Kit AI crop, preview toggle, instant share
- v1.0.0: Initial release

## Competitor Research (2026-03-20)
Top open-source competitors: ImageToolbox (12.1k stars, 310+ filters), ScreenshotTile (1.9k stars, QS tile + partial capture), PhotoEditor (10k stars, emoji/stickers).

### High-value features to add next:
- **Scrolling/long screenshot** — AccessibilityService auto-scroll + stitch. Highest demand.
- **Emoji/sticker overlay** — Draggable, resizable, rotatable image overlays.
- **Device frame mockup** — Wrap screenshot in phone frame (Pixel/Samsung/iPhone).
- **Photo filters** — Brightness, contrast, saturation, warmth sliders (GPU-accelerated).
- **Collage maker** — Grid layouts for combining multiple screenshots.
- **Image-to-PDF export** — Single or batch conversion.
- **Background removal** — ML Kit or U2Net for one-tap BG removal.
- **Custom filename templates** — Date/app/counter placeholders.

## Gotchas
- `foregroundServiceType="specialUse"` required for Android 14+
- ContentObserver debounce needed — MediaStore fires multiple events per screenshot
- ML Kit runs via Google Play Services — no APK size cost, but requires Play Services on device
- FileProvider needed for share intent — cache dir `shared_crops/`
- SYSTEM_ALERT_WINDOW needed for background activity launch — notification actions serve as fallback
- Android 12+ edge-to-edge: status bar is transparent, pixel analysis CANNOT detect it. Must use exact device dimensions via `resources.getIdentifier("status_bar_height", "dimen", "android")`
- `getIdentifier` returns configured height even with gesture nav / hidden bars — correct for screenshots since they capture full framebuffer
- ContentObserver fires TWICE per screenshot: IS_PENDING=1 (created) and IS_PENDING=0 (finalized). Service validates by attempting to decode the stream.
- applyDraw() must ALWAYS copy the bitmap — mutating the original causes corruption on subsequent operations
- Favorites SQL query must use ? placeholders, not direct ID interpolation
- Bitmap.asImageBitmap() wraps the same native bitmap — do NOT recycle the source Bitmap while ImageBitmap is in use
