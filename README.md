# SnapCrop

Auto-crop, annotate, and redact screenshots instantly on Android.

![SnapCrop Screenshot](screenshot.png)

## Features

### Screenshot Detection & Capture
- **Automatic detection** — Background service monitors MediaStore for new screenshots, opens editor with autocrop applied
- **Rich notification** — Thumbnail preview with Edit, Share, and Quick Crop actions
- **Delayed capture** — 3/5/10 second countdown timer, detects new screenshot after delay
- **Quick Settings tile** — Toggle monitoring from the notification shade
- **Boot auto-start** — Service resumes after device reboot

### Smart Autocrop
- **System bar stripping** — Exact device status/nav bar pixel heights from Android resources (works on Android 12+ transparent bars)
- **Border detection** — Corner-sampled border color reference removes uniform borders (dark mode aware)
- **AI crop** — ML Kit Object Detection for content-aware cropping with 2% padding

### Crop Editor (5 Edit Modes)

**Crop Mode**
- Draggable corner/edge handles with edge magnetism (snap to 0/25/33/50/67/75/100% guides)
- Precision drag mode (4x slower after 800ms hold)
- 18 aspect ratio presets including 7 shape crops (circle, rounded rect, star, heart, triangle, hexagon, diamond)
- Grid overlay toggle (rule-of-thirds / golden ratio / off)
- Free rotation/straighten slider (-45 to +45 degrees)
- Gradient background fill for shape crops (6 presets: Sunset, Ocean, Purple, Dark, Mint, Fire)
- Rotate 90 / Flip H / Flip V
- Resize (5 presets)
- Before/after swipe comparison in preview mode
- Tap dimension display for precise pixel input (X/Y/W/H)
- Estimated file size on save button

**Pixelate Mode**
- Draw rectangles to mosaic regions
- One-tap face blur with ML Kit Face Detection (count badge)

**Draw Mode — 16 Tools**
1. **Pen** — Catmull-Rom smoothing + velocity-based stroke width
2. **Arrow** — Line with arrowhead
3. **Line** — Straight line between two points
4. **Rect** — Rectangle (optional fill)
5. **Circle** — Ellipse (optional fill)
6. **Text** — Tap to place with optional background pill
7. **Highlight** — Semi-transparent wide stroke (40% alpha)
8. **Callout** — Auto-incrementing numbered circles
9. **Spotlight** — Dims everything outside selected rectangle
10. **Magnifier** — 2x zoomed circular inset with crosshair
11. **Emoji** — 20 common emojis, scrollable picker
12. **Neon** — 3-layer glow pen (blur outer + colored mid + white core)
13. **Blur** — Gaussian blur brush along stroke path
14. **Eraser** — Erase to transparent
15. **Fill** — Flood fill contiguous region at tap point
16. **Heal** — Content-aware inpainting brush (samples surrounding pixels)

All tools support: 6 preset colors + RGB color picker + eyedropper + recent colors, adjustable stroke width, dashed line toggle, undo/redo

**OCR Mode**
- ML Kit Text Recognition — tap to copy single block, "Copy All" button
- ML Kit Barcode Scanning — tap to copy QR/barcode value
- Double-tap text block to crop to it

**Adjust Mode — 13 Sliders + 17 Filters**
- Brightness, Contrast, Saturation, Warmth, Vignette, Sharpen, Highlights, Shadows, Tilt-Shift, Denoise
- Curves (per-channel Red/Green/Blue gamma adjustment)
- 17 image filters: Mono, Sepia, Cool, Warm, Vivid, Muted, Vintage, Noir, Fade, Invert, Polaroid, Grain, Red Pop, Blue Pop, Green Pop, Glitch + Auto-enhance (histogram-based)

### Undo System
- Unified undo/redo across all modes (crop + adjust + pixelate + draw)
- 30-level history stack
- Collapsible undo history panel with step labels

### Gallery
- Album grid with photo counts
- Photo grid with pinch-to-zoom columns (2-6)
- Fullscreen viewer with pinch-to-zoom (1-5x) and HorizontalPager
- Photo info panel (dimensions, size, date, path)
- Multi-select with batch delete, share, resize, PDF export
- Favorites system
- Sort (date/name/size) and search
- Date section headers

### Image Stitching
- Combine 2+ images vertically or horizontally
- Reorder with move up/down buttons
- Output dimensions preview

### Collage Maker
- 25 grid layouts (2x1 through 6x3)
- Adjustable gap (0-20px)
- 6 background colors
- Cell aspect ratio selector (4:3, 1:1, 16:9, 3:4)

### Device Frame Mockup
- 5 frames: Pixel, iPhone, Samsung, Flat, White
- Bezel + notch + screen rendering

### ML Kit Integration (5 Engines)
- Object Detection — Smart crop
- Text Recognition — OCR
- Face Detection — Face blur
- Barcode Scanning — QR/barcode copy
- Subject Segmentation — One-tap background removal

### Export & Save
- PNG / JPEG / WebP format with quality slider
- Target file size compression (set KB budget, auto-adjusts quality)
- Custom filename templates (%timestamp%, %date%, %time%, %counter%)
- Save location picker (Pictures/DCIM/Downloads)
- Export border (0-100px, 6 colors)
- Watermark overlay (repeating diagonal text)
- EXIF strip on share
- Delete original after crop (prompt-free with MANAGE_EXTERNAL_STORAGE)
- PDF export from gallery

### Color Tools
- 6-color dominant palette extraction with hex codes + percentages
- RGB color picker with hex display
- Eyedropper (sample color from image)
- Recent custom colors memory (last 4)

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- AMOLED black theme (Catppuccin Mocha accent colors)
- ML Kit via Google Play Services (no APK size cost)
- Coil 2.7.0 for async image loading
- minSdk 29 (Android 10), targetSdk 35, compileSdk 35

## Requirements

- Android 10+
- Google Play Services (for ML Kit)
- Permissions: Media access, Notifications, Display over apps (Android 12+), File management (optional, for prompt-free deletion)

## Build

```bash
export JAVA_HOME="/path/to/jdk-17"
./gradlew assembleDebug
./gradlew assembleRelease
```

Sign release APK:
```bash
zipalign -v -p 4 app-release-unsigned.apk app-release-aligned.apk
apksigner sign --ks snapcrop.jks --out SnapCrop.apk app-release-aligned.apk
```

## License

MIT
