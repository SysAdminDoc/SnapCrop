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
- Quick Settings tiles for monitoring, long screenshot capture, and rerunning the last Quick Crop action
- Delayed capture mode (3 / 5 / 10 second countdown)
- Long screenshot capture via Accessibility: start from the dedicated Quick Settings tile, auto-scroll, review, retry if needed, save, then continue editing
- Optional Quick Crop auto-actions can redact sensitive text from recognized app screenshots and save them into app-specific albums
- User app rules can route Quick Crop by source app/package hints or OCR keywords, with per-rule crop bands, album, redaction, and export format
- Survives reboots — auto-starts with your device

### Smart Auto-Crop
- **System bar stripping** — Reads exact status/nav bar heights from Android (works on transparent bars too)
- **Border detection** — Removes uniform borders from any screenshot, light or dark mode
- **App crop profiles** — Optional built-in and user-created rules strip app chrome when source hints, OCR keywords, or visual templates match
- **AI crop** — ML Kit object detection for content-aware cropping

### Powerful Editor — 5 Modes

**Crop**
- Drag corners/edges with edge magnetism (snaps to guides)
- Precision mode — hold 800ms for 4x slower, pixel-perfect dragging
- 18 aspect ratios including 7 shape crops (circle, star, heart, triangle, hexagon, diamond, rounded rect)
- AI Reframe shifts selected aspect ratios around detected objects, text, and faces
- Free rotation slider (-45 to +45 degrees)
- Gradient backgrounds for shape crops (6 presets)
- Grid overlays (rule of thirds / golden ratio)
- Tap dimensions for exact pixel input
- Before/after swipe comparison
- Live file size estimate
- Adaptive wide editor layout with persistent side controls for tablets, foldables, and desktop-mode windows
- Explicit **Save & Replace** / **Save Copy** actions, with confirmation before deleting the original screenshot

**Draw — 16 Tools**
| Tool | What it does |
|------|-------------|
| Pen | Smooth freehand with velocity-based stroke width |
| Arrow | Line with arrowhead |
| Line | Straight line between two points |
| Rectangle | Optional fill |
| Circle / Ellipse | Optional fill |
| Text | Tap to place, optional readable backdrop |
| Highlight | Semi-transparent marker (40% alpha) |
| Callout | Auto-numbered circles (1, 2, 3...) |
| Spotlight | Dims everything *except* your selection |
| Magnifier | 2x zoomed inset with crosshair |
| Emoji | 20 common emojis, tap to place |
| Neon | Glowing 3-layer pen effect |
| Blur Brush | Gaussian blur along your stroke |
| Eraser | Erase to transparent |
| Flood Fill | Fill a region with your selected color |
| Smart Erase | Mask-based object removal with edge-aware inpainting |

All tools support: custom colors (6 presets + full RGB picker + eyedropper), adjustable width, dashed lines, undo/redo.
Draw annotations also appear in a reorderable Layers panel with per-layer visibility and delete controls.
Annotated saves include a same-name SVG sidecar for visible vector layers and redaction rectangles.
Editable project sidecars (`.snapcrop.json`) can also be saved next to exports,
then reopened later to restore the crop, redactions, adjustment state, and draw
layers without mutating the source screenshot.

**Pixelate**
- Draw rectangles to mosaic-redact sensitive info
- One-tap **face blur** powered by ML Kit (shows face count)
- One-tap **Auto Text** detects emails, phones, payment cards, IP/MAC addresses, and ML Kit entities

**OCR**
- Tap any text block to copy it or translate it on-device
- Translate recognized text with ML Kit language detection and downloadable offline translation models
- Scan QR codes and barcodes
- Double-tap a text block to crop directly to it

**Adjust — 13 Sliders + 17 Filters**
- Brightness, Contrast, Saturation, Warmth, Vignette, Sharpen, Highlights, Shadows, Tilt-Shift, Denoise
- Per-channel RGB curves
- 17 filters: Mono, Sepia, Cool, Warm, Vivid, Vintage, Noir, Fade, Invert, Polaroid, Grain, Red/Blue/Green Pop, Glitch + Auto-enhance

### Gallery
- Browse all photos and videos by album
- Optional local intelligence index powers smart auto-albums for Screenshots, Chats, Games, Sites, Documents, Codes, and Payments without moving files
- Search screenshot albums by filename, source hints, indexed categories, dimensions, and OCR/barcode text after using OCR in the editor
- Pinch-to-zoom grid (2-6 columns)
- Fullscreen viewer with pinch zoom (up to 5x)
- Multi-select for batch delete, share, resize, batch rename, or PDF report export
- Favorites, search, and sort by date/name/size
- Rebuild or purge the local screenshot index from Settings
- Home recent crops include explicit delete confirmation for safe cleanup

### More Tools
- **Long Screenshot** — Accessibility-powered scroll capture with sticky-header aware stitching, review/retry, and editor handoff
- **Screen Recording Tools** — Trim recordings and grab editable frames from video
- **Image Stitching** — Combine 2+ images vertically or horizontally with reorder controls
- **Collage Maker** — 25 grid layouts, adjustable gaps, multiple aspect ratios
- **Device Frame Mockup** — Wrap screenshots in Pixel, iPhone, Samsung, or flat device frames
- **Background Removal** — One-tap ML Kit subject segmentation
- **Color Palette** — Extract 6 dominant colors with hex codes and percentages

### Export Options
- **Formats:** PNG, JPEG, or WebP with quality slider
- **Target file size:** Set a KB budget, SnapCrop auto-adjusts quality to hit it
- **Custom filenames:** Templates with `%timestamp%`, `%date%`, `%time%`, `%counter%`
- **Batch rename:** Gallery-selected screenshots can use `%app%`, `%date%`,
  `%time%`, `%timestamp%`, `%counter%`, and `%profile%` templates
- **PDF reports:** Gallery selections can be bundled with title, notes,
  timestamps, source/dimension metadata, and an optional OCR appendix
- **Opt-in network exports:** Settings can enable explicit HTTP, WebDAV/
  Nextcloud, or Imgur upload targets; uploads are never used by default
- **Share shortcuts:** The Android share sheet can surface the destinations you
  choose most often
- **Save location:** Pictures, DCIM, or Downloads
- **Extras:** Export border, watermark overlay, EXIF stripping,
  editable project sidecars, delete-original toggle

---

## Download

Grab the latest APK from [**Releases**](https://github.com/SysAdminDoc/SnapCrop/releases/latest) and sideload it.

**Requirements:**
- Android 10+ (API 29)
- Google Play Services (for ML Kit features)

---

## Privacy and Permissions

SnapCrop is local-first: no ads, no analytics SDKs, and no required network
export path. Optional upload targets are off by default and must be configured
in Settings. See [SECURITY.md](SECURITY.md) for the permission matrix and
release/security policy.

- Media permissions let SnapCrop find screenshots, show the gallery, and edit
  user-selected images and videos.
- Notifications keep the screenshot monitor visible and expose Edit, Share, and
  Quick Crop actions.
- Display-over-apps is optional. Without it, screenshots still produce a
  notification that opens the editor.
- Long Screenshot uses Accessibility only after the user starts that workflow;
  SnapCrop shows an in-app disclosure before opening Android Accessibility
  settings.
- SnapCrop does not request all-files access. On Android 11+, source screenshot
  cleanup uses Android's scoped delete confirmation.
- The screenshot intelligence index is opt-in, local-only, and can be rebuilt
  or purged from Settings.
- Network exports are opt-in. HTTP/WebDAV report uploads and Imgur image
  uploads run only after the user enables and configures a target.
- Android app-data backup is disabled so local paths, favorites, automation
  toggles, and export preferences are not silently backed up by SnapCrop.

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
| **Compile SDK** | 36 |

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
