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
- Quick Settings tiles for monitoring, long screenshot capture, bounded step-by-step guide capture, and rerunning the last Quick Crop action. Step Capture keeps at most 10 normalized 720 px frames in private temporary storage, stops after 10 minutes or 2 minutes idle, and exposes Stop in its ongoing notification.
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

**Draw — 17 Tools**
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
| Ruler | Measure pixel distance between two points with dimension label |

All tools support: custom colors (6 presets + full RGB picker + eyedropper), adjustable width, dashed lines, undo/redo.
Draw annotations also appear in a reorderable Layers panel with per-layer visibility and delete controls.
Annotated saves include a same-name SVG sidecar for visible vector layers and reversible concealment rectangles.
Editable project sidecars (`.snapcrop.json`) can also be saved next to exports,
then reopened later to restore the crop, concealment masks, adjustment state, and draw
layers without mutating the source screenshot.
Smart Erase remains local by default; experimental downloaded erase model packs
are blocked behind explicit opt-in and evaluation gates.

**Pixelate**
- Draw reversible pixelation rectangles for cosmetic concealment
- One-tap **face blur** powered by ML Kit (cosmetic; shows face count)
- Sensitive-text automation and scan-before-share use destructive opaque pixel replacement by default; Settings can explicitly select cosmetic pixelation instead
- One-tap **Auto Text** detects emails, phones, payment cards, IP/MAC addresses, and ML Kit entities

**OCR**
- Tap any text block to copy it or translate it on-device
- Translate recognized text with ML Kit language detection and downloadable offline translation models
- Translation shows model-download and retry guidance when Play Services needs
  Wi-Fi, storage, or an update
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
- ML-assisted tools surface Play Services/model retry guidance instead of
  silently looking like no-ops
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
- **Extras:** Export border, watermark overlay, EXIF stripping, and bounded
  editable project sidecars with source fingerprint verification and relinking

---

## Download

Grab the latest versioned APK from [**Releases**](https://github.com/SysAdminDoc/SnapCrop/releases/latest) and sideload it. Each release also includes a CycloneDX JSON SBOM and a provenance JSON file. Verify the downloaded APK's SHA-256 and signing-certificate SHA-256 against that manifest before installing.

**Requirements:**
- Android 10+ (API 29)
- Google Play Services (for ML Kit features)

---

## Privacy and Permissions

SnapCrop is local-first: no ads, no analytics SDKs, and no required network
export path. Optional upload targets are off by default and must be configured
in Settings. See [SECURITY.md](SECURITY.md) for the permission matrix and
release/security policy.

- Image, video, and notification grants are independent. Full image access alone
  enables automatic monitoring; selected media remains browsable, and video
  denial hides only library videos. Android pickers remain usable in every state.
- Notification denial hides detected-screenshot, countdown, Edit, Share, and
  Quick Crop drawer actions without blocking Android’s foreground-service Task
  Manager entry or image/video access.
- Display-over-apps is optional. Without it, screenshots still produce a
  notification that opens the editor.
- Long Screenshot and Step Capture use Accessibility only after the user starts
  the matching workflow. Home and each tile show a separate disclosure before
  opening Android settings; consent for one purpose never enables the other.
  Their Accessibility services keep temporary frames local and never upload data.
  Step Capture deletes its private cached frames after incremental assembly and
  enforces 12M-pixel, 48 MiB decoded, and 64 MiB cache budgets.
- SnapCrop does not request all-files access. On Android 11+, removals use
  Android's scoped Trash confirmation; the gallery updates only after approval.
  If Save & Replace cannot move the source, the copy remains saved and the
  original is explicitly retained.
- The screenshot intelligence index is opt-in, local-only, and can be rebuilt
  or purged from Settings.
- Network exports are opt-in. HTTP/WebDAV report uploads and Imgur image
  uploads run only after the user enables and configures a target. Credentials
  are stored in a versioned AES-256-GCM file whose key remains in Android
  Keystore; decryption failures disable uploads and expose a Settings reset.
  Uploads are HTTPS-only bounded streams with byte progress and cancellation:
  reports are capped at 64 MiB, Imgur images at 50,000,000 bytes each, and
  private temporary PDFs are deleted after success, failure, or cancellation.
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
| **Image Loading** | Coil 3.3 |
| **Min SDK** | 29 (Android 10) |
| **Target SDK** | 36 |
| **Compile SDK** | 36 |

---

## Build from Source

```bash
git clone https://github.com/SysAdminDoc/SnapCrop.git
cd SnapCrop
./gradlew clean :app:lintDebug :app:testDebugUnitTest --console=plain
./gradlew :app:generateReleaseProvenance --console=plain
./gradlew :app:verifyOfficialRelease --console=plain
```

Stable release artifacts are written to `app/build/outputs/provenance/` as
`SnapCrop-<version>.apk`, `SnapCrop-<version>-sbom.json`, and
`SnapCrop-<version>-provenance.json`. The manifest records the exact APK hash,
certificate fingerprint, version metadata, source commit/state, and build command.
Gradle also verifies the pinned wrapper distribution/JAR and all resolved plugin,
test, and release-runtime artifacts by SHA-256 against
`gradle/verification-metadata.xml`; PGP signer records are retained for audit.
The official-release task additionally requires the production keystore and
pinned certificate, a clean worktree, synchronized app/root/SBOM versions,
uncompressed ELF libraries, and successful 16 KB `zipalign` verification.

> Requires JDK 17 and the Android SDK. Official releases must show
> `"sourceState": "clean"` and use the published certificate fingerprint.

---

## License

[MIT](LICENSE) — use it, fork it, improve it.
