# SnapCrop

## Overview
Android screenshot autocrop editor with full annotation toolkit, image adjustments, gallery, stitching, collage, device mockup, and ML Kit integration. Detects screenshots via foreground service, auto-crops system bars and borders (including dark mode), provides 11 draw tools + 5 edit modes + 13 image filters.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- AMOLED black theme (Catppuccin accent colors: blue=primary, green=secondary, pink=tertiary, peach=adjust, lavender=OCR)
- ML Kit: Object Detection, Text Recognition, Face Detection, Barcode Scanning, Subject Segmentation
- Coil 2.7.0 for async image loading
- Android PdfDocument for PDF export
- minSdk 29, targetSdk 35, compileSdk 35

## Architecture
- `ScreenshotService` - Foreground service with ContentObserver on MediaStore. Detects screenshots, launches editor, shows rich notification with thumbnail preview + Edit/Share/Quick Crop actions. Falls back to notification if background activity launch fails (Android 12+).
- `MonitorTileService` - Quick Settings tile to toggle screenshot monitoring on/off.
- `CropActivity` - Loads bitmap, runs AutoCrop, hosts CropEditorScreen composable. Supports share/clipboard/save via FileProvider. Applies adjustments + 13 image filters via ColorMatrixColorFilter on export. Saves as PNG/JPEG/WebP per user setting.
- `CropEditorScreen` - Compose Canvas with unified gesture handler (single `awaitEachGesture`). Two-row top bar: navigation (close/undo/redo/rotate/flip/preview) + scrollable mode tab chips (Crop/Pixelate/Draw/OCR/Adjust). Draggable corner/edge handles with edge magnetism (snap to 0/25/33/50/67/75/100% guides) + precision drag mode (4x slower after 800ms). Grid overlay toggles between rule-of-thirds, golden ratio (φ), and off. Before/after swipe comparison in preview mode. Unified undo/redo across all modes capturing full editor state. Tap dimension display for precise pixel input (X/Y/W/H dialog with numeric keyboard).
- `StitchActivity` - Combine 2+ images vertically or horizontally. Reorder with move up/down buttons. Shows output dimensions preview.
- `CollageActivity` - 8 grid layouts (2x1 to 3x3) with adjustable gap, 6 background colors, configurable cell aspect ratio (4:3/1:1/16:9/3:4).
- `AutoCrop` - Multi-strategy: (1) corner-sampled border color reference + uniform-border scan (works for both light and dark mode screenshots), (2) system bar strip using exact device heights, (3) full image fallback. Edge case validation for scanTop >= scanBottom.
- `SystemBars` - Queries exact status_bar_height and navigation_bar_height from Android resources via `getIdentifier`.
- `SmartCropEngine` - ML Kit Object Detection wrapper for content-aware cropping. 2% padding, 10% significance threshold.
- `GalleryScreen` - Album grid, photo grid with pinch-to-zoom columns (2-6), fullscreen viewer (HorizontalPager) with photo info panel, multi-select, favorites, sort (date/name/size), search, PDF export.
- `MainActivity` - Home screen with service toggle, permission management, manual pick, batch crop/resize with cancel + progress bar + format-aware export, stitch, collage, device mockup, recent crops gallery.
- `SettingsActivity` - Delete original, PNG/JPEG/WebP format selector with quality slider, EXIF strip, filename templates, watermark, save location picker, auto-start, cache clear.

## Key Files
| File | Lines | Purpose |
|------|-------|---------|
| `CropEditorScreen.kt` | ~2050 | Crop UI, 11 draw tools, 5 edit modes, 13 filters, unified gesture + undo |
| `GalleryScreen.kt` | ~690 | Albums, photos, viewer, multi-select, favorites, PDF |
| `MainActivity.kt` | ~850 | Home screen, permissions, batch crop/resize, format-aware export |
| `CropActivity.kt` | ~830 | Save/share/delete, bitmap pipeline, 13 filter matrices, adjustments |
| `ScreenshotService.kt` | ~330 | Screenshot detection, thumbnail notification, quick save |
| `StitchActivity.kt` | ~360 | Image stitching with reorder + output size preview |
| `CollageActivity.kt` | ~380 | Collage builder with cell aspect ratio selector |
| `AutoCrop.kt` | ~230 | Multi-strategy border detection with dark mode support |
| `SettingsActivity.kt` | ~240 | Settings with format selector + filename templates |
| `DeviceFrameActivity.kt` | ~260 | Device frame mockup (5 frames) |
| `SmartCropEngine.kt` | ~85 | ML Kit object detection crop |
| `MonitorTileService.kt` | ~45 | Quick Settings tile |
| `FaceDetector.kt` | ~47 | ML Kit face detection for blur |
| `BarcodeScanner.kt` | ~41 | ML Kit barcode scanning |
| `TextExtractor.kt` | ~33 | ML Kit text recognition |
| `ColorPaletteExtractor.kt` | ~50 | Dominant color extraction |

## Draw Tools (11)
1. **Pen** - Freehand with Catmull-Rom smoothing + velocity-based stroke width + optional dashed
2. **Arrow** - Line with arrowhead + optional dashed
3. **Rect** - Rectangle (optional fill, optional dashed)
4. **Circle** - Ellipse (optional fill, optional dashed)
5. **Text** - Tap to place, dialog input with optional background pill
6. **Highlight** - Semi-transparent wide stroke (40% alpha)
7. **Callout** - Auto-incrementing numbered circles
8. **Spotlight** - Dims everything outside selected rectangle
9. **Magnifier** - 2x zoomed circular inset with crosshair
10. **Emoji** - 20 common emojis, scrollable picker, tap to place
11. **Neon** - 3-layer glow pen (BlurMaskFilter outer + colored mid + white core) + velocity stroke

## Image Filters (13)
Mono, Sepia, Cool, Warm, Vivid, Muted, Vintage, Noir, Fade, Invert, Polaroid, Grain + Auto-enhance (histogram-based)

## Edit Modes (5)
1. **CROP** - Drag handles with edge magnetism + precision mode, 15 aspect ratios with lock indicator, grid overlay (thirds/golden ratio/off), auto/AI crop, rotate/flip H/flip V, resize, tap dimensions for exact pixel input, before/after swipe comparison in preview, estimated file size on save button
2. **PIXELATE** - Draw rectangles to redact, one-tap face blur with count badge, haptic on commit
3. **DRAW** - 11 tools, 6 preset colors + RGB color picker dialog + eyedropper + recent colors (last 4), velocity-based stroke width for pen/neon, stroke width slider, dashed toggle, text background pill option, haptic on commit
4. **OCR** - ML Kit text recognition + barcode scanning, tap to copy single block, "Copy All" button, double-tap text block to crop to it
5. **ADJUST** - 13 image filter presets + auto-enhance (histogram-based one-tap), brightness, contrast, saturation, warmth, vignette sliders

## Build
```
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot"
./gradlew assembleDebug
./gradlew assembleRelease
```
Sign: `zipalign` + `apksigner` with `snapcrop.jks` (keystore in repo root, gitignored)

## Version
v6.1.0

## Version History
- v6.1.0: **Audit fixes**: `delete_original` default changed from `true` to `false` (non-destructive default), `loadRecentCrops` now respects custom save path setting (was hardcoded to Pictures/SnapCrop), `quickSave` in ScreenshotService now respects format preference (was hardcoded PNG), `shareCropped` respects format preference (was always PNG), `copyToClipboard` moved to IO dispatcher (was blocking main thread), Stitch/Collage save now respect format preference (were always PNG), `loadAllPhotos` now includes videos (was images-only unlike album view), version string sync across all files. **New**: Sharpen slider in ADJUST mode (3x3 unsharp mask convolution kernel, 0-2x range), pinch-to-zoom in PhotoViewer (1-5x, resets on page change, disables pager swipe when zoomed), tap to reset zoom in viewer. **6 edit modes** (Crop/Pixelate/Draw/OCR/Adjust with sharpen).
- v6.0.0: **Crop**: unified gesture handler (fixed drag conflict with pinch-zoom), precise pixel input dialog, 4 new aspect ratios (4:5/5:4/3:1/21:9), edge magnetism snap guides with dashed guide line visualization, precision drag mode (4x slower after 800ms hold), golden ratio grid toggle (thirds/φ/off), flip vertical button, before/after swipe comparison in preview, file size estimate on save button. **Filters**: 13 presets (Mono/Sepia/Cool/Warm/Vivid/Muted/Vintage/Noir/Fade/Invert/Polaroid/Grain) + auto-enhance (histogram analysis). **Draw**: velocity-based stroke width modulation (slow=thicker, fast=thinner for PEN/NEON), RGB color picker dialog with live preview + hex display, recent custom colors memory (last 4), text annotation background pill option, haptic on commit. **OCR**: copy-all button in mode banner, double-tap text block to crop to it, face count badge on Blur Faces button. **Export**: WebP format option (API 29 compat fallback), batch ops respect format setting, save_path setting respected by all 6 activities. **Undo**: unified across all modes (crop+adjust+pixelate+draw in single EditorSnapshot). **Batch**: cancel button with determinate progress bar (X/N percentage), error counting with summary toast. **Stitch**: output dimensions preview, enlarged touch targets (28→36dp). **Collage**: cell aspect ratio selector (4:3/1:1/16:9/3:4). **Notifications**: screenshot thumbnail preview (BigPictureStyle). **UI overhaul**: Top bar split into two rows (navigation + scrollable mode tab chips) to prevent overcrowding. Mode switching via labeled FilterChip tabs instead of icon-only buttons. Standardized all vertical padding to 4dp minimum. Settings horizontal padding 20→16dp. Consistent 12dp horizontal padding across all tool rows. Aspect ratio lock indicator (tap 🔒 to unlock). **AutoCrop**: dark mode border detection improvement (corner-based border color reference prevents false positives on dark content). **Bugs fixed**: strip_exif toggle state tracking, EditorSnapshot moved to top-level (Kotlin forbids local data classes), WebP WEBP_LOSSY API 30 fallback to deprecated WEBP on API 29, WebP ext/mime detection for both constants, version string sync to 6.0.0, DrawScope color property shadowing in Paint.apply blocks.
- v5.9.0: Neon glow pen (11th draw tool, BlurMaskFilter 3-layer glow), image resize in editor (5 presets, dialog), save location picker (Pictures/DCIM/Downloads), gallery photo count in album view
- v5.8.0: Dashed line style toggle for pen/arrow/rect/circle (DashPathEffect on canvas + export), star + heart shape crops (5-point star path, cubic bezier heart, PorterDuff masking), 4 shape crop options total (circle/rounded/star/heart)
- v5.7.0: Background removal (ML Kit Subject Segmentation via Play Services, one-tap BG remove in editor), color palette extraction (6 dominant colors with hex codes + percentages, tap to copy), gallery date section headers (grouped by date in All Photos view), collage background color picker (6 color options), 5th ML Kit engine
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
- **Gradient background fill** — Replace transparent areas with gradient
- **Layer system** — Independent movable/resizable annotation layers
- **Undo history panel** — Visual timeline of all edits
- **Perspective/quad crop** — 4 independent corner points with warp transform
- **Non-destructive editing** — Save edit state as JSON sidecar, re-open later
- **Tablet/foldable adaptive layout** — WindowSizeClass for side-panel tools on wide screens

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
- ColorMatrix for adjustments: filter preset first, then saturation, then contrast (scale around mid + offset), then brightness (additive), then warmth
- Gesture handling uses single `awaitEachGesture` — do NOT add separate `detectDragGestures`/`detectTransformGestures` modifiers as they consume events and conflict
- adj FloatArray layout: [0]=brightness, [1]=contrast, [2]=saturation, [3]=shapeCrop, [4]=warmth, [5]=vignette, [6]=filterIndex, [7]=sharpen. CropActivity.getFilterColorMatrix() must match ImageFilter enum ordinal order exactly
- EditorSnapshot data class must be at file top-level (not inside composable) — Kotlin forbids local data classes in functions
- WebP uses `WEBP_LOSSY` on API 30+ and deprecated `WEBP` on API 29. saveToGallery ext/mime detection must handle all three constants (WEBP_LOSSY, WEBP_LOSSLESS, WEBP)
- Inside Compose `Canvas { }` (DrawScope), `color` is a DrawScope property — use `paint.color = x` outside `apply {}` blocks or assign to a `val` first to avoid shadowing
- PdfDocument pages must match bitmap dimensions for 1:1 pixel mapping
- Filename template: resolveFilename() expands %timestamp%, %date%, %time%, %counter% — counter persisted in SharedPreferences
- AutoCrop dark mode: border detection samples corner pixels to identify border color. Uniform rows that don't match border color are treated as content, not border.
- Batch operations in MainActivity use their own `getSaveFormat()` helper — must stay in sync with CropActivity's version
- All 6 activities (CropActivity, MainActivity, StitchActivity, CollageActivity, DeviceFrameActivity, ScreenshotService) must respect the save format and save path preferences
- `delete_original` defaults to `false` — non-destructive by default

## UI Design Rules
- **Padding rhythm**: 4dp, 8dp, 12dp, 16dp only. Minimum vertical padding 4dp.
- **Font scale**: 28sp (app title), 22sp (screen title), 13sp (body/buttons), 11sp (labels/chips), 9-10sp (captions)
- **Corner radii**: FilterChip = 8dp, FilledTonalButton = 12dp, OutlinedButton = 16dp, Dialog = Material default
- **Icon sizes**: Toolbar icons = 20dp in 40dp buttons. Button inline icons = 16dp with 4dp spacer. Action bar icons = default (24dp)
- **Touch targets**: All interactive elements ≥ 36dp (Material minimum = 48dp, acceptable minimum = 36dp for dense toolbars)
- **Top bar**: Two rows — Row 1 (navigation + info), Row 2 (scrollable mode tabs). Never >6 elements per row.
