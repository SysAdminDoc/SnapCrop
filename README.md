# SnapCrop

**The screenshot tool Android should have shipped with.**

Auto-detect, auto-crop, annotate, redact, and share screenshots — all in one tap. No ads, no tracking, no internet required.

[![Android](https://img.shields.io/badge/Android-10%2B-3ddc84?logo=android&logoColor=white)](https://github.com/SysAdminDoc/SnapCrop/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/SysAdminDoc/SnapCrop?color=purple)](https://github.com/SysAdminDoc/SnapCrop/releases/latest)

---

## Why SnapCrop?

Taking a screenshot on Android gives you a raw capture with status bars, navigation bars, and ugly borders. You open a separate editor, crop manually, then switch to *another* app to annotate. SnapCrop replaces that entire workflow:

1. **Take a screenshot** — SnapCrop detects it instantly
2. **Auto-crop** — Status bars, nav bars, and borders are stripped automatically
3. **Edit** — Annotate, redact, adjust, OCR, or just save
4. **Done** — One app, zero friction

---

## Features

### Instant Screenshot Detection
- Background service monitors for new screenshots and opens the editor automatically
- Rich notification with thumbnail preview + **Edit**, **Share**, and **Quick Crop** actions
- Quick Settings tile to toggle monitoring on/off
- Delayed capture mode (3 / 5 / 10 second countdown)
- Survives reboots — auto-starts with your device

### Smart Auto-Crop
- **System bar stripping** — Reads exact status/nav bar heights from Android (works on transparent bars too)
- **Border detection** — Removes uniform borders from any screenshot, light or dark mode
- **AI crop** — ML Kit object detection for content-aware cropping

### Powerful Editor — 5 Modes

**Crop**
- Drag corners/edges with edge magnetism (snaps to guides)
- Precision mode — hold 800ms for 4x slower, pixel-perfect dragging
- 18 aspect ratios including 7 shape crops (circle, star, heart, triangle, hexagon, diamond, rounded rect)
- Free rotation slider (-45 to +45 degrees)
- Gradient backgrounds for shape crops (6 presets)
- Grid overlays (rule of thirds / golden ratio)
- Tap dimensions for exact pixel input
- Before/after swipe comparison
- Live file size estimate

**Draw — 16 Tools**
| Tool | What it does |
|------|-------------|
| Pen | Smooth freehand with velocity-based stroke width |
| Arrow | Line with arrowhead |
| Line | Straight line between two points |
| Rectangle | Optional fill |
| Circle / Ellipse | Optional fill |
| Text | Tap to place, optional background pill |
| Highlight | Semi-transparent marker (40% alpha) |
| Callout | Auto-numbered circles (1, 2, 3...) |
| Spotlight | Dims everything *except* your selection |
| Magnifier | 2x zoomed inset with crosshair |
| Emoji | 20 common emojis, tap to place |
| Neon | Glowing 3-layer pen effect |
| Blur Brush | Gaussian blur along your stroke |
| Eraser | Erase to transparent |
| Flood Fill | Fill a region with your selected color |
| Heal | Content-aware inpainting (removes blemishes) |

All tools support: custom colors (6 presets + full RGB picker + eyedropper), adjustable width, dashed lines, undo/redo.

**Pixelate**
- Draw rectangles to mosaic-redact sensitive info
- One-tap **face blur** powered by ML Kit (shows face count)

**OCR**
- Tap any text block to copy it
- Scan QR codes and barcodes
- Double-tap a text block to crop directly to it

**Adjust — 13 Sliders + 17 Filters**
- Brightness, Contrast, Saturation, Warmth, Vignette, Sharpen, Highlights, Shadows, Tilt-Shift, Denoise
- Per-channel RGB curves
- 17 filters: Mono, Sepia, Cool, Warm, Vivid, Vintage, Noir, Fade, Invert, Polaroid, Grain, Red/Blue/Green Pop, Glitch + Auto-enhance

### Gallery
- Browse all photos and videos by album
- Pinch-to-zoom grid (2-6 columns)
- Fullscreen viewer with pinch zoom (up to 5x)
- Multi-select for batch delete, share, resize, or PDF export
- Favorites, search, and sort by date/name/size

### More Tools
- **Image Stitching** — Combine 2+ images vertically or horizontally with reorder controls
- **Collage Maker** — 25 grid layouts, adjustable gaps, multiple aspect ratios
- **Device Frame Mockup** — Wrap screenshots in Pixel, iPhone, Samsung, or flat device frames
- **Background Removal** — One-tap ML Kit subject segmentation
- **Color Palette** — Extract 6 dominant colors with hex codes and percentages

### Export Options
- **Formats:** PNG, JPEG, or WebP with quality slider
- **Target file size:** Set a KB budget, SnapCrop auto-adjusts quality to hit it
- **Custom filenames:** Templates with `%timestamp%`, `%date%`, `%time%`, `%counter%`
- **Save location:** Pictures, DCIM, or Downloads
- **Extras:** Export border, watermark overlay, EXIF stripping, delete-original toggle

---

## Download

Grab the latest APK from [**Releases**](https://github.com/SysAdminDoc/SnapCrop/releases/latest) and sideload it.

**Requirements:**
- Android 10+ (API 29)
- Google Play Services (for ML Kit features)

---

## Tech Stack

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Theme** | AMOLED black with Catppuccin Mocha accents |
| **ML** | ML Kit (Object Detection, Text Recognition, Face Detection, Barcode Scanning, Subject Segmentation) |
| **Image Loading** | Coil 2.7 |
| **Min SDK** | 29 (Android 10) |
| **Target SDK** | 35 |

---

## Build from Source

```bash
git clone https://github.com/SysAdminDoc/SnapCrop.git
cd SnapCrop
./gradlew assembleRelease
```

The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.

> Requires JDK 17 and the Android SDK.

---

## License

[MIT](LICENSE) — use it, fork it, improve it.
