# SnapCrop

**The screenshot tool Android should have shipped with.**

Auto-detect, auto-crop, annotate, redact, and share screenshots — all in one tap. Core editing works offline, with no ads or tracking.

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
- Rich notification with thumbnail preview + **Edit**, **Share**, and **Quick Crop** actions; Protect media previews omits screenshot pixels and marks the notification secret
- Quick Settings tiles for monitoring, long screenshot capture, bounded step-by-step guide capture, and rerunning the last Quick Crop action. On Android 14+, Long Screenshot and Step Capture target only the active app window so overlays and system UI do not contaminate captures; Android 11–13 use a visible-display fallback. Step Capture keeps at most 10 normalized 720 px frames in private temporary storage, stops after 10 minutes or 2 minutes idle, and exposes Stop in its ongoing notification.
- A resizable home-screen widget opens the newest screenshot, reruns Quick Crop,
  or opens Gallery without ever loading or displaying a private thumbnail.
- Dark, Light, and System themes cover the editor, Gallery, crop, stitch, collage,
  device-frame, video, launch, and system-bar surfaces; media previews retain a
  neutral black canvas with dedicated high-contrast controls.
- The status-led Home keeps monitoring state, the primary Edit image action,
  capture shortcuts, secondary tools, and recent exports visually distinct,
  with consistent accessible targets in both light and dark themes.
- Share one image into SnapCrop to edit it, or share multiple images to choose
  Batch auto-crop, Stitch, Collage, or PDF report. Each item is validated and
  failures are reported individually instead of dropping unsupported inputs.
- Stitch, Collage, Device Mockup, video trimming, Web Capture, and Gallery
  navigation restore identity-and-option state after recreation without storing
  pixels in Android saved state. Revoked media returns to a picker/retry path,
  and Back closes dialogs, viewer, selection, and album layers before leaving
  Gallery for Home.
- Delayed capture mode (3 / 5 / 10 second countdown)
- Searchable offline Help & Tips opens the relevant workflow controls, and an
  optional Home row remembers only the IDs of up to six successfully started
  workflows—never media, filenames, paths, links, or recognized text.
- Long screenshot capture via Accessibility: start from the dedicated Quick Settings tile, auto-scroll, review, retry if needed, save, then continue editing
- Optional static web-page capture accepts an entered or shared public HTTPS URL. SnapCrop fetches only the bounded main HTML document over public-IP-pinned TLS, renders it in an isolated offline WebView with scripts and external resources blocked, and sends the full-page image to the editor without uploading page content.
- Optional Quick Crop auto-actions can redact sensitive text from recognized app screenshots and save them into app-specific albums
- User app rules can route Quick Crop by source app/package hints or OCR keywords, with per-rule crop bands, album, redaction, and export format
- Survives reboots — auto-starts with your device

### Smart Auto-Crop
- **Cut Out / squeeze** — Remove one or more irrelevant horizontal or vertical
  bands from long screenshots, edit the cuts non-destructively, and preview
  straight, dashed, or torn seams before save/share/project export.
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
- Multi-select annotation layers to align against crop edges/centers, distribute
  equal gaps horizontally or vertically, and duplicate with a visible offset
- Tap dimensions for exact pixel input
- Before/after swipe comparison uses the same composed renderer as Save, Copy,
  and Share, including adjustments, annotations, concealment, shapes, perspective,
  and Cut Out.
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
Named export presets capture format, quality or target size, border, watermark, filename,
and destination; choose one in the editor for Save/Share or assign one to Quick Crop in Settings.
Cache-backed Copy and Share artifacts publish only after a non-empty encode and successful
clipboard/chooser dispatch; failed artifacts are removed instead of being exposed as success.
Auto-detected and manual redactions remain editable until export: review individual
email/phone/card/IP/face regions, toggle whole categories, choose safe opaque Bar or
cosmetic Pixelate/Blur, and move or resize each region before it is flattened once.
TalkBack and Switch Access users can place the current annotation or a redaction at the
crop center and adjust selected layers/redactions through canvas accessibility actions.
Compact icon and color controls reserve 48 dp interaction space, expose one localized
action, and announce selection/toggle state without duplicate nested targets.
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
- Review OCR blocks before export: correct text, merge related blocks, or delete
  false detections without changing the screenshot pixels
- Searchable PDF reports use explicitly reviewed text for the PDF layer and appendix;
  Settings can also save an opt-in UTF-8 `.txt` companion (which may contain sensitive text)
- Translate recognized text with ML Kit language detection and downloadable offline translation models
- Translation shows model-download and retry guidance when Play Services needs
  Wi-Fi, storage, or an update
- Scan QR codes and barcodes
- Double-tap a text block to crop directly to it

**Adjust — 13 Sliders + 17 Filters**
- Android 14+ Ultra HDR screenshots retain their gain map through editing and export
  as JPEG, including crop, perspective, redaction, annotations, borders, and watermarks.
- Brightness, Contrast, Saturation, Warmth, Vignette, Sharpen, Highlights, Shadows, Tilt-Shift, Denoise
- Per-channel RGB curves
- 17 filters: Mono, Sepia, Cool, Warm, Vivid, Vintage, Noir, Fade, Invert, Polaroid, Grain, Red/Blue/Green Pop, Glitch + Auto-enhance

### Gallery
- Review duplicate screenshots locally with exact/perceptual matching, strict,
  balanced, or loose sensitivity, full-resolution candidate previews, and
  keep-oldest/newest/manual choices. Nothing is auto-deleted; confirmed files
  move through Android's recoverable trash, and false matches stay dismissed.
- Browse all photos and videos by album
- Optional local intelligence index powers smart auto-albums for Screenshots, Chats, Games, Sites, Documents, Codes, and Payments without moving files
- Gallery distinguishes loading, empty, partial-access, and failed states. Its
  local index health card shows eligible/indexed work, pending or failed scans,
  the last successful scan, and direct Retry/Rebuild actions; filename/date
  browsing remains available when indexed metadata cannot be read.
- Combine structured chips for media type, creator/source folder, indexed category,
  date, orientation/minimum dimensions, favorite state, and MIME format; filter
  state survives recreation and matching screenshots can seed a manual collection
- Create named manual collections, add screenshots with multi-select, and search/sort them without moving or duplicating media; memberships survive index rebuilds
- Add local multiline notes and schedule, change, or cancel one-time reminders;
  note text participates in Gallery search, reminder notifications reveal no
  screenshot content, and taps reopen only the exact original media identity
- Retain an explicit page/app source URL or label through edited copies and project
  reopen; add/edit/open it from the editor or Gallery, and choose per share whether
  the canonical link is included (off by default)
- Search screenshot albums by filename, source hints, indexed categories, dimensions, and OCR/barcode text after using OCR in the editor
- Pinch-to-zoom grid (2-6 columns)
- Fullscreen viewer with pinch zoom (up to 5x)
- Multi-select for collections, batch delete, share, resize, batch rename, or PDF report export
- Favorites, search, and sort by date/name/size
- Rebuild or purge the local screenshot index from Settings
- Home recent crops include explicit delete confirmation for safe cleanup

### More Tools
- **Long Screenshot** — Accessibility-powered scroll capture with sticky-header aware stitching, editable frame joins (trim/start sliders, precise nudges, reset), review/retry, and editor handoff
- **Screen Recording Tools** — Trim recordings and grab editable frames from video;
  unreadable metadata or preview frames offer Retry and Choose another instead
  of appearing as a valid zero-duration recording
- **Image Stitching** — Combine 2+ images vertically or horizontally with reorder controls
- **Collage Maker** — 25 grid layouts, adjustable gaps, multiple aspect ratios
- **Device Frame Mockup** — Wrap screenshots in Pixel, iPhone, Samsung, or flat device frames
- **Background Removal** — One-tap ML Kit subject segmentation
- ML-assisted tools surface Play Services/model retry guidance instead of
  silently looking like no-ops
- **Color Palette** — Extract 6 dominant colors with hex codes and percentages

### Export Options
- Image saves use one transactional MediaStore path: an output is published only
  after its encoder writes a non-empty file and Android confirms the pending row;
  failed rows are removed instead of being reported as successful.
- **Formats:** PNG, JPEG, or WebP with quality slider
- **Enforced target file size:** JPEG/WebP exports adjust quality to meet the KB
  budget or fail without publishing an oversized file. Resolution reduction is a
  separate opt-in, preserves aspect ratio, and stops at 320 px per side; metadata
  growth is checked before Save or Share completes.
- **Custom filenames:** Templates with `%timestamp%`, `%date%`, `%time%`, `%counter%`
- **Batch rename:** Gallery-selected screenshots can use `%app%`, `%date%`,
  `%time%`, `%timestamp%`, `%counter%`, and `%profile%` templates
- **Bounded batch crop/resize:** Up to 50 selected images are byte-preflighted,
  bounds-decoded, and sampled before allocation. Sources are limited to 64 MiB /
  48 MP and working bitmaps to 12 MP / 48 MiB; results report saved, skipped,
  oversized, unreadable, failed, and cancelled counts separately
- **PDF reports:** Gallery selections can be bundled with title, notes,
  timestamps, source/dimension metadata, and an optional OCR appendix; choose
  physical A4, US Letter, or validated custom millimetre dimensions,
  portrait/landscape, and margins with a proportional preview. Output is
  standard PDF; PDF/A archival conformance is not generated or claimed
- **Opt-in network exports:** Settings can enable explicit HTTP, WebDAV/
  Nextcloud, or Imgur upload targets; uploads are never used by default. On
  Android 17, public HTTPS and Imgur remain permission-free while LAN,
  link-local, and `.local` destinations request Local network access only when used
- **Share shortcuts:** The Android share sheet can surface the destinations you
  choose most often
- **Metadata privacy preflight:** Metadata-bearing image shares list only present
  categories (never values) and offer Strip all, Keep safe technical fields, or
  Preserve detected supported fields without modifying the source
- **Save location:** Pictures, DCIM, or Downloads
- **Extras:** Export border, watermark overlay, EXIF stripping, and bounded
  editable project sidecars with source fingerprint verification and relinking

---

## Download

Grab the latest universal `SnapCrop-<version>.apk` from [**Releases**](https://github.com/SysAdminDoc/SnapCrop/releases/latest) and sideload it. Smaller production-signed APKs are also published for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; choose one only when the device ABI is known. Each release includes a shared CycloneDX JSON SBOM and a provenance JSON file covering every APK. Verify the downloaded APK's SHA-256 and signing-certificate SHA-256 against that manifest before installing.

### Automatic sideload updates with Obtainium

1. In Obtainium, add `https://github.com/SysAdminDoc/SnapCrop` as a GitHub source.
2. Keep prereleases disabled. If an APK filter is requested, use
   `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+\.apk$` so SBOM/provenance files and any
   future alternate artifacts cannot be selected accidentally.
3. Before installing, compare Obtainium/GitHub's SHA-256 with the digest shown by
   SnapCrop's update dialog or `SnapCrop-<version>-provenance.json`.

Official release assets always use `SnapCrop-<version>.apk`; the in-app update
checker opens that exact asset when GitHub publishes it and falls back to the
release page if the expected name or trusted URL is absent.

To let Obtainium download a smaller architecture-specific APK, replace the
universal filter with exactly one of these patterns:

- arm64-v8a: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+-arm64-v8a\.apk$`
- armeabi-v7a: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+-armeabi-v7a\.apk$`
- x86_64: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+-x86_64\.apk$`
- x86: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+-x86\.apk$`

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
- A first denial explains the reduced behavior and offers Try again; repeated
  denial switches to the exact Android app, notification, overlay, or Accessibility
  settings page. Returning resumes only the initiating monitor, capture, latest,
  Pin, Accessibility, or LAN-upload action when its capability is now available.
  Photo access is requested when monitoring/library work needs it, video access
  only while browsing Gallery videos, and notifications only for capture/reminders.
- Notification denial hides detected-screenshot, countdown, Edit, Share, and
  Quick Crop drawer actions without blocking Android’s foreground-service Task
  Manager entry or image/video access.
- Display-over-apps is optional. Without it, screenshots still produce a
  notification that opens the editor.
- Long Screenshot and Step Capture use Accessibility only after the user starts
  the matching workflow. Home and each tile show a separate disclosure before
  opening Android settings; consent for one purpose never enables the other.
  Their Accessibility services keep temporary frames local and never upload data.
  Android 14+ captures only the active application window; Android 11–13 captures
  the visible display and removes system bars before processing.
  Step Capture deletes its private cached frames after incremental assembly and
  enforces 12M-pixel, 48 MiB decoded, and 64 MiB cache budgets.
- Sensitive-text Quick Crop/share scans use the OCR script selected in Settings,
  detect strict developer-secret shapes, support bounded local custom RE2 patterns,
  expose each region for review, and fail closed when recognition or configured
  pattern validation fails. Official local releases run a fixed
  synthetic light/dark corpus across every supported OCR script and sensitive
  category; fixture values are reserved examples, never real personal data.
- SnapCrop does not request all-files access. On Android 11+, removals use
  Android's scoped Trash confirmation; the gallery updates only after approval.
  If Save & Replace cannot move the source, the copy remains saved and the
  original is explicitly retained.
- The screenshot intelligence index is opt-in, local-only, and can be rebuilt
  or purged from Settings.
- The bounded local operation journal keeps only typed workflow stage/result,
  duration, and sanitized error-class data (up to 200 events for 14 days). It
  never stores paths, URIs, image/OCR content, credentials, messages, or stack
  traces; Settings can disable and purge it or explicitly view, copy, or attach
  the redacted snapshot.
- Protect media previews applies secure window/Recents policy to Gallery, the
  editor, stitch/collage/frame/video tools, long/web review, and the floating
  preview. It also removes screenshot pixels from detected notifications, including
  a notification already visible when the preference is enabled.
- Network exports are opt-in. HTTP/WebDAV report uploads and Imgur image
  uploads run only after the user enables and configures a target. Credentials
  are stored in a versioned AES-256-GCM file whose key remains in Android
  Keystore; decryption failures disable uploads and expose a Settings reset.
  Uploads are HTTPS-only bounded streams with byte progress and cancellation:
  reports are capped at 64 MiB, Imgur images at 50,000,000 bytes each, and
  private temporary PDFs are deleted after success, failure, or cancellation.
  Android 17 Local network access is requested only for LAN, link-local, or
  `.local` custom endpoints; denial skips that upload while preserving the local
  report and all public export paths, with retry and app-settings recovery.
- Android app-data backup is disabled so local paths, favorites, automation
  toggles, and export preferences are not silently backed up by SnapCrop. The
  manual Settings backup is a versioned, typed allowlist: it excludes network
  credentials, transient capture state, screenshot notes, and reminders;
  migrates supported legacy keys; and reports unknown or invalid entries.

---

## Tech Stack

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Theme** | AMOLED black with Catppuccin Mocha accents |
| **ML** | ML Kit (Object Detection, Text Recognition, Face Detection, Barcode Scanning, Subject Segmentation) |
| **Image Loading** | Coil 3.3 with a 25% background memory-cache cap |
| **Min SDK** | 29 (Android 10) |
| **Target SDK** | 36 |
| **Compile / target SDK** | 37 |

---

## Build from Source

```bash
git clone https://github.com/SysAdminDoc/SnapCrop.git
cd SnapCrop
./gradlew --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false clean :app:lintDebug :app:testDebugUnitTest --console=plain
./gradlew --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false :app:generateReleaseProvenance --console=plain
./gradlew --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false :app:verifyOfficialRelease --console=plain
```

Stable release artifacts are written to `app/build/outputs/provenance/` as
the universal `SnapCrop-<version>.apk`, four `SnapCrop-<version>-<abi>.apk`
assets, `SnapCrop-<version>-sbom.json`, and
`SnapCrop-<version>-provenance.json`. The schema-2 manifest records every APK's
ABI, byte size, hash, certificate fingerprint, synchronized version, shared SBOM
hash, source commit/state, and build command.
Gradle also verifies the pinned wrapper distribution/JAR and all resolved plugin,
test, and release-runtime artifacts by SHA-256 against
`gradle/verification-metadata.xml`; PGP signer records are retained for audit.
Until stable Kotlin 2.4.20 or newer is reviewed and pinned, settings evaluation
rejects Gradle/Kotlin build-cache or incremental-cache opt-ins for
CVE-2026-53914. Trusted commands state the safe cache flags explicitly; normal
dependency downloads remain cached and checksum-verified.
The official-release task additionally requires the production keystore and
pinned certificate, the exact five-APK asset set, a clean worktree, synchronized
app/root/manifest/SBOM versions, correct native ABI contents, materially smaller
split assets, uncompressed ELF libraries, and successful 16 KB `zipalign`
verification for every APK.

> Requires JDK 17 and the Android SDK. Official releases must show
> `"sourceState": "clean"` and use the published certificate fingerprint.

---

## License

[MIT](LICENSE) — use it, fork it, improve it.
