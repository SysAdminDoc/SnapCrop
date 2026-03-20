# SnapCrop

## Overview
Android screenshot autocrop editor with full annotation toolkit, image adjustments, gallery, stitching, and ML Kit integration. Detects screenshots via foreground service, auto-crops system bars, provides 9 draw tools + 5 edit modes.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- AMOLED black theme (Catppuccin accent colors: blue=primary, green=secondary, pink=tertiary, peach=adjust)
- ML Kit: Object Detection, Text Recognition, Face Detection, Barcode Scanning
- Coil 2.7.0 for async image loading
- Android PdfDocument for PDF export
- minSdk 29, targetSdk 35, compileSdk 35

## Architecture
- `ScreenshotService` - Foreground service with ContentObserver on MediaStore. Detects screenshots, launches editor, shows notification with Edit/Share/Quick Crop actions. Falls back to notification if background activity launch fails (Android 12+).
- `MonitorTileService` - Quick Settings tile to toggle screenshot monitoring on/off.
- `CropActivity` - Loads bitmap, runs AutoCrop, hosts CropEditorScreen composable. Supports share/clipboard/save via FileProvider. Applies adjustments via ColorMatrixColorFilter on export.
- `CropEditorScreen` - Compose Canvas with draggable corner/edge handles, dim overlay, rule-of-thirds grid. 5 edit modes: CROP, PIXELATE, DRAW (9 tools), OCR, ADJUST. Pinch-to-zoom 1-5x, aspect ratio presets, undo/redo.
- `StitchActivity` - Combine 2+ images vertically or horizontally. Reorder with move up/down buttons.
- `AutoCrop` - Multi-strategy: (1) uniform-border scan, (2) system bar strip using exact device heights, (3) full image fallback. Edge case validation for scanTop >= scanBottom.
- `SystemBars` - Queries exact status_bar_height and navigation_bar_height from Android resources via `getIdentifier`.
- `SmartCropEngine` - ML Kit Object Detection wrapper for content-aware cropping. 2% padding, 10% significance threshold.
- `GalleryScreen` - Album grid, photo grid with pinch-to-zoom columns (2-6), fullscreen viewer (HorizontalPager), multi-select, favorites, sort (date/name/size), search, PDF export.
- `MainActivity` - Home screen with service toggle, permission management, manual pick, batch crop, stitch, recent crops gallery.
- `SettingsActivity` - Delete original, JPEG/PNG format, quality slider, EXIF strip, filename templates, auto-start, cache clear.

## Key Files
| File | Lines | Purpose |
|------|-------|---------|
| `CropEditorScreen.kt` | ~1300 | Crop UI, 10 draw tools, 5 edit modes, canvas rendering |
| `GalleryScreen.kt` | ~690 | Albums, photos, viewer, multi-select, favorites, PDF |
| `MainActivity.kt` | ~700 | Home screen, permissions, batch crop, stitch, PDF export |
| `CropActivity.kt` | ~550 | Save/share/delete, bitmap pipeline, adjustments |
| `ScreenshotService.kt` | ~300 | Screenshot detection, notification actions, quick save |
| `StitchActivity.kt` | ~260 | Image stitching with reorder |
| `AutoCrop.kt` | ~230 | Multi-strategy border detection |
| `SettingsActivity.kt` | ~230 | Settings with filename templates |
| `SmartCropEngine.kt` | ~85 | ML Kit object detection crop |
| `MonitorTileService.kt` | ~45 | Quick Settings tile |
| `FaceDetector.kt` | ~47 | ML Kit face detection for blur |
| `BarcodeScanner.kt` | ~41 | ML Kit barcode scanning |
| `TextExtractor.kt` | ~33 | ML Kit text recognition |

## Draw Tools (9)
1. **Pen** - Freehand with Catmull-Rom smoothing
2. **Arrow** - Line with arrowhead
3. **Rect** - Rectangle (optional fill)
4. **Circle** - Ellipse (optional fill)
5. **Text** - Tap to place, dialog input
6. **Highlight** - Semi-transparent wide stroke (40% alpha)
7. **Callout** - Auto-incrementing numbered circles
8. **Spotlight** - Dims everything outside selected rectangle
9. **Magnifier** - 2x zoomed circular inset with crosshair
10. **Emoji** - 20 common emojis, scrollable picker, tap to place

## Edit Modes (5)
1. **CROP** - Drag handles, aspect ratios, auto/AI crop, rotate/flip
2. **PIXELATE** - Draw rectangles to redact, one-tap face blur
3. **DRAW** - 9 tools above, 6 colors + eyedropper, stroke width slider
4. **OCR** - ML Kit text recognition + barcode scanning, tap to copy
5. **ADJUST** - Brightness (-100 to 100), contrast (0.5x-2x), saturation (0-2x)

## Build
```
./gradlew assembleDebug
./gradlew assembleRelease
```
Sign: `zipalign` + `apksigner` with `snapcrop.jks` (keystore in repo root, gitignored)

## Version
v5.6.1

## Version History
- v5.6.1: Bug audit + fixes — magnifier tool was drawing result bitmap into itself causing recursive rendering (now draws source bitmap), BootReceiver auto_start default was true (should be false, mismatched with all other code), adj FloatArray default expanded to 6 elements to prevent index bounds issues
- v5.6.0: Device frame mockup (5 frames: Pixel/iPhone/Samsung/Flat/White, bezel+notch+screen rendering), rounded rect crop (8% corner radius, PorterDuff masking), vignette effect (radial gradient, live preview + export), draw undo/redo improvement (proper redo stack, clear resets both)
- v5.5.0: Collage maker (8 grid layouts from 2x1 to 3x3, adjustable gap, center-crop cells), warmth adjustment slider (red/blue shift via ColorMatrix), batch resize from gallery multi-select (5 size presets with dialog), home screen layout improved (stitch+collage side by side)
- v5.4.0: Emoji overlay tool (20 common emojis, scrollable picker, tap to place, scales with stroke width), watermark overlay (repeating diagonal text, configurable in settings, 25% opacity), circle crop (PorterDuff masking, transparent BG, force PNG on save, visual circle preview on canvas)
- v5.3.0: Brightness/contrast/saturation adjustment sliders (ColorMatrix, live preview, applied to export), ADJUST edit mode
- v5.2.0: Magnifier/loupe tool, PDF export from gallery, custom filename templates, stitch reorder
- v5.1.0: Spotlight tool, image stitching, QS tile, notification actions, 6 bitmap leak fixes, SQL injection fix
- v5.0.0: Stroke smoothing (Catmull-Rom), video support in gallery
- v4.9.0: Crop presets (3:4, 9:16, 2:1), eyedropper, gallery pinch-zoom grid
- v4.8.0: EXIF strip on share, gallery sort
- v4.7.0: Favorites system
- v4.6.0: Highlighter, numbered callouts
- v4.5.0: Fix delete (scoped storage), viewer actions, album search
- v4.4.0: Pinch-to-zoom crop canvas, photo info panel
- v4.3.0: Face redaction, QR/barcode scanner
- v4.2.0: OCR text extraction
- v4.1.0: Fullscreen viewer, multi-select
- v4.0.0: Built-in gallery
- v3.x: Draw tools, pixelate, shapes, text, batch crop, icon
- v2.x: Settings, flip, preview, undo/redo, OOM fixes, aspect ratios, boot receiver
- v1.0.0: Initial release

## Competitor Research (2026-03-20)
Top competitors: ImageToolbox (12.1k stars), ScreenshotTile (1.9k), PhotoEditor (10k).

### Remaining high-value features:
- **Scrolling/long screenshot** — AccessibilityService auto-scroll + stitch
- **Background removal** — ML Kit subject segmentation
- **More shape crops** — Star, heart with transparent BG
- **Dashed/neon line styles** — Line style variants for shapes
- **Color palette extraction** — Extract dominant colors from image

## Gotchas
- `foregroundServiceType="specialUse"` required for Android 14+
- ContentObserver debounce needed — MediaStore fires multiple events per screenshot
- ML Kit runs via Google Play Services — no APK size cost, but requires Play Services
- FileProvider needed for share intent — cache dir `shared_crops/`
- SYSTEM_ALERT_WINDOW needed for background activity launch — notification actions serve as fallback
- Android 12+: status bar is transparent, pixel analysis CANNOT detect it. Use `resources.getIdentifier("status_bar_height", "dimen", "android")`
- ContentObserver fires TWICE per screenshot: IS_PENDING=1 then IS_PENDING=0. Validate by decoding stream.
- applyDraw() must ALWAYS copy bitmap — mutating original causes corruption
- Favorites SQL must use ? placeholders, not direct ID interpolation
- Bitmap.asImageBitmap() wraps same native bitmap — do NOT recycle source while ImageBitmap is in use
- ColorMatrix for adjustments: saturation first, then contrast (scale around mid + offset), then brightness (additive)
- PdfDocument pages must match bitmap dimensions for 1:1 pixel mapping
- Filename template: resolveFilename() expands %timestamp%, %date%, %time%, %counter% — counter persisted in SharedPreferences
