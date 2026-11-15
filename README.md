# SnapCrop

**The screenshot tool Android should have shipped with.**

SnapCrop notices every screenshot you take, strips the status and nav bars, and hands you an editor. Scrolling capture and automatic redaction of sensitive text are free here, while the tools people usually compare it with charge for both. Core editing works offline, and there are no ads, no account and no SnapCrop-run analytics service.

[![Android](https://img.shields.io/badge/Android-10%2B-3ddc84?logo=android&logoColor=white)](https://github.com/SysAdminDoc/SnapCrop/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/SysAdminDoc/SnapCrop?color=purple)](https://github.com/SysAdminDoc/SnapCrop/releases/latest)

<p align="center">
  <a href="https://ko-fi.com/X8K126YVER">
    <img height="42" src="https://storage.ko-fi.com/cdn/kofi2.png?v=3" alt="Buy me a coffee on Ko-fi" />
  </a>
</p>

<p align="center">
  <sub><em>If this project helps you, a coffee helps me keep working on it.</em></sub>
</p>

---

## Why SnapCrop?

A stock Android screenshot keeps the status bar, the navigation bar and sometimes a border. Cleaning it up usually means cropping in one app and annotating in another. With SnapCrop you take the screenshot the way you always do. It spots it right away, crops off the bars and borders, and opens an editor where you can annotate, redact, adjust, pull out the text or just save.

---

## Features

### Screenshot detection
- A background service watches for new screenshots and opens the editor on its own
- The notification shows a thumbnail with **Edit**, **Share** and **Quick Crop** actions. With Protect media previews on, it leaves out the screenshot pixels and is marked secret
- Quick Settings tiles for monitoring, long screenshots, step-by-step guide capture and rerunning the last Quick Crop. On Android 14+, Long Screenshot and Step Capture record only the active app's window, so overlays and system UI stay out of the picture. Android 11 to 13 capture the visible display instead. Step Capture keeps at most 10 frames (scaled to 720 px) in private temporary storage, stops after 10 minutes or 2 idle minutes, and has a Stop button in its notification.
- A resizable home-screen widget opens the newest screenshot, reruns Quick Crop or opens Gallery. It never loads or shows a private thumbnail.
- Dark, Light and System themes apply to the editor, Gallery, the crop, stitch, collage, device-frame and video tools, the launch screen and the system bars. Media previews stay on a neutral black background with their own high-contrast controls.
- Home shows whether monitoring is on at the top, with Edit image, capture shortcuts, other tools and recent exports below it
- Share one image into SnapCrop to edit it, or share several and pick Batch auto-crop, Stitch, Collage or PDF report. Each image is checked separately, and any that can't be used are reported one by one instead of being dropped.
- Stitch, Collage, Device Mockup, video trimming, Web Capture and Gallery pick up where you left off after Android recreates the screen, without keeping image pixels in Android's saved state. If access to a picture was revoked, you get the picker or a retry. Back closes dialogs, the viewer, selection and albums before Gallery returns to Home.
- Delayed capture with a 3, 5 or 10 second countdown
- Searchable offline Help & Tips that opens the controls for each workflow. An optional Home row remembers up to six workflows you started, by ID only. It never stores media, file names, paths, links or recognized text.
- Settings search works from the localized control names and fixed keywords, so it never indexes file names, endpoints, credentials, watermark text, presets or other values you've entered. A result scrolls to the exact setting and highlights it, shows hidden conditional controls without switching them on, and some sections offer a reset.
- Long screenshots through Accessibility. Start from the Quick Settings tile, let it scroll, review, retry if needed, save and keep editing.
- Optional static web-page capture takes a public HTTPS address you type or share. SnapCrop fetches only the page's main HTML document (size-limited, over TLS pinned to a public IP), renders it in an isolated offline WebView with scripts and outside resources blocked, and sends the full-page image to the editor. Nothing from the page gets uploaded.
- Optional Quick Crop auto-actions can redact sensitive text in screenshots of recognized apps and file them into per-app albums
- Your own app rules can route Quick Crop by source app or package hints or by OCR keywords, each with its own crop bands, album, redaction and export format
- Starts again by itself after a reboot

### Smart auto-crop
- Strips the status and nav bars using the exact heights Android reports, transparent bars included
- Removes plain borders from any screenshot in light or dark mode
- Optional app crop profiles (built in, or your own) trim an app's toolbars when source hints, OCR keywords or visual templates match
- AI crop uses ML Kit object detection to crop around the content

### Editor (6 modes)

**Crop**
- Drag corners and edges, with magnetic snapping to guides
- Hold for 800 ms to switch to precision mode, which drags 4x slower for pixel-exact edges
- 18 aspect ratios, including 7 shape crops (circle, star, heart, triangle, hexagon, diamond, rounded rect)
- AI Reframe shifts some aspect ratios around detected objects, text and faces
- Free rotation slider (-45 to +45 degrees), plus a button that turns the image a quarter turn per tap
- Gradient backgrounds for shape crops (6 presets)
- Grid overlays (rule of thirds, golden ratio)
- Select several annotation layers to line them up against crop edges or centers, space them evenly across or down, or duplicate them with a visible offset
- Tap the dimensions to type exact pixel sizes
- The before and after swipe uses the same renderer as Save, Copy and Share, so adjustments, annotations, concealment, shapes, perspective and Cut Out look the way they'll be saved
- Live file size estimate
- On tablets, foldables and desktop-mode windows, a wide layout keeps the controls in a side panel
- Separate **Save & Replace** and **Save Copy** buttons. Replace saves the crop first and only then moves the original into `Pictures/trashed/`. On Android 12+ you can grant Media management access once instead of confirming every move.

**Cut Out**
- Remove horizontal or vertical bands you don't need from long screenshots, change the cuts later, and pick straight, dashed or torn seams before you save, share or export a project

**Draw (18 tools)**

| Tool | What it does |
|------|-------------|
| Pen | Smooth freehand with velocity-based stroke width |
| Arrow | Line with arrowhead |
| Curved Arrow | Curved line with arrowhead |
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

Every tool takes custom colors (6 presets, a full RGB picker and an eyedropper), adjustable width, dashed lines, and undo and redo. Drawn annotations also show up in a Layers panel where you can reorder, hide or delete each one.

Named export presets store the format, quality or target size, border, watermark, file name and destination. Pick one in the editor for Save or Share, or assign one to Quick Crop in Settings.

Redactions, found automatically or drawn by hand, stay editable until you export. Review each email, phone, card, IP and face region, switch whole categories on or off, choose a solid Bar (the safe option) or a cosmetic Pixelate or Blur, and move or resize any region. They're flattened once, at export.

TalkBack and Switch Access users can place the current annotation or a redaction at the crop center and adjust selected layers and redactions through the canvas's accessibility actions. Small icon and color buttons get 48 dp touch targets with one localized action each, and they announce whether they're selected or on.

Annotated saves come with a same-name SVG sidecar holding the visible vector layers and reversible concealment rectangles. Editable project files (`.snapcrop.json`) can also be saved next to exports and reopened later to bring back the crop, concealment masks, adjustments and drawing layers, without touching the original screenshot.

Smart Erase runs on the phone by default. Experimental downloadable erase models stay off unless you opt in, and only after they pass evaluation.

**Pixelate**
- Draw reversible pixelation boxes for cosmetic concealment
- One-tap **face blur** with ML Kit (cosmetic, and it shows how many faces it found)
- Automatic sensitive-text redaction and scan-before-share replace the pixels with solid color by default. Settings can switch them to cosmetic pixelation.
- One-tap **Auto Text** finds emails, phone numbers, payment cards, IP and MAC addresses, and ML Kit entities

**OCR**
- Tap any text block to copy it or translate it on the phone
- Long-press in the text window to pick out a word or a few, and Copy and Translate use just that
- Links show up as chips you can tap, including bare ones like `github.com/user/repo`
- Review OCR blocks before export. Fix the text, merge blocks that belong together, or delete false detections, all without changing the screenshot's pixels.
- Searchable PDF reports use only text you reviewed for the PDF's text layer and appendix. Settings can also save an opt-in UTF-8 `.txt` copy next to it (which may contain sensitive text).
- Translate recognized text into any of the 59 languages ML Kit can translate offline, with language detection and downloadable models
- When a translation model needs Wi-Fi or more storage, translation tells you what to download or retry
- Scan QR codes and barcodes
- Double-tap a text block to crop straight to it

**Adjust (13 sliders, 16 filters)**
- Ultra HDR screenshots on Android 14+ keep their gain map through editing and JPEG export, with crop, perspective, redaction, annotations, borders and watermarks applied
- Brightness, Contrast, Saturation, Warmth, Vignette, Sharpen, Highlights, Shadows, Tilt-Shift and Denoise
- Per-channel RGB curves
- Every slider and filter previews live on the photo, and on a phone the sliders scroll in a short panel so the photo stays in view
- 16 filters (Mono, Sepia, Cool, Warm, Vivid, Muted, Vintage, Noir, Fade, Invert, Polaroid, Grain, Red/Blue/Green Pop and Glitch). Auto-enhance is a separate button that analyzes the image.

### Gallery
- Find duplicate screenshots on the phone with exact or perceptual matching at strict, balanced or loose sensitivity, then compare full-size previews and keep the oldest, the newest or your own pick. Nothing is deleted automatically. Files you confirm go to Android's recoverable trash, and matches you dismiss stay dismissed.
- Browse all photos and videos by album
- An optional local index builds smart albums for Screenshots, Chats, Games, Sites, Documents, Codes and Payments without moving files
- Gallery tells loading, empty, partial access and failed states apart. The index health card shows how much is indexed, any pending or failed scans, the last good scan, and Retry and Rebuild buttons. Browsing by file name and date still works when the index can't be read.
- An Unfiled inbox (shown even when it's empty) collects current screenshots you haven't filed, snoozed with a reminder or marked as reviewed. File, Remind me and Keep unfiled never move the file or change its MediaStore owner, and Trash only does once Android confirms it.
- The index updates in small steps as screenshots come and go, and a full rescan never deletes your collections, notes or reminders
- Filter with chips for media type, creator or source folder, indexed category, date, orientation and minimum size, favorites and file format. Filters survive screen recreation, and the matching screenshots can start a manual collection.
- Make named collections, add screenshots with multi-select, and search and sort them without moving or copying the media. Collections survive index rebuilds.
- Add multi-line notes and set, change or cancel one-time reminders. Gallery search covers note text, reminder notifications show no screenshot content, and tapping one opens exactly the original image.
- Export collections, notes, upcoming reminders and Unfiled decisions from Settings as a versioned JSON file. Import does a dry run first, relinking by SHA-256 and media hints and listing what matched and what's ambiguous, missing or in conflict. Confirming merges it all in one step and never overwrites a local note or reminder that conflicts. Media, OCR text, source URLs, credentials and reminder tokens are never included.
- Keep a page or app source link or label with a screenshot through edited copies and project reopen. Add, edit or open it from the editor or Gallery, and choose for each share whether the link goes along (off by default).
- Search screenshot albums by file name, source hints, indexed categories, dimensions, and OCR or barcode text once you've used OCR in the editor
- Optionally fill in searchable OCR text for older screenshots, only while the phone is charging and idle. It's a separate opt-in, off by default, and it never moves media. Each run has limits on item count, file size, decoded pixels and time, saves results as it goes, and reports queued, indexed, skipped and failed counts in Settings and Library. You can cancel it or clear all cached OCR and barcode text.
- Pinch to change the grid (2 to 6 columns)
- On tablets, foldables and desktop-mode windows, a navigation rail replaces the bottom bar and the Library grid and image viewer stay side by side. Smaller windows keep the full-screen viewer. Selection, filters, scroll position and the open image survive resizing and Back.
- Wide Settings windows add a category pane that jumps straight to each setting
- Full-screen viewer with pinch zoom (up to 5x)
- Multi-select to add to collections, delete, share, resize, rename in bulk or export a PDF report
- Pick exactly two images to compare them privately on the phone with a fitted swipe, an opacity overlay, a paused blink or a difference-only view. Original sizes stay visible, the alignment options (top left, center, bottom left) never stretch images of different sizes, and closing the comparison saves nothing.
- Favorites, search, and sort by date, name or size
- Rebuild or clear the local screenshot index from Settings
- Recent crops on Home ask before deleting

### More tools
- **Long Screenshot** scrolls and captures through Accessibility, stitches around sticky headers, and lets you adjust each join (trim and start sliders, fine nudges, reset), review and retry before it hands the result to the editor
- **Screen recording tools** trim recordings and grab editable frames from video. If a recording's details or preview frames can't be read, you get Retry and Choose another instead of a recording that claims to be zero seconds long.
- **Image stitching** joins 2 or more images vertically or horizontally, with reorder controls
- **Collage maker** with 25 grid layouts, adjustable gaps and several aspect ratios
- **Device frame mockup** wraps screenshots in Pixel, iPhone, Samsung or flat device frames
- **Background removal** in one tap with ML Kit subject segmentation
- ML tools tell you when Play Services or a model needs a retry, instead of looking like the button did nothing
- **Color palette** pulls out the 6 main colors with hex codes and percentages
- **Search image** in the editor's ⋮ menu looks up the edited picture on Google Lens (through the Google app), Bing or Yandex

### Export
- A save only shows up in your gallery once the file is fully written, and a failed save is cleaned up instead of reported as done
- PNG, JPEG or WebP, with a quality slider
- Target file size for JPEG and WebP. SnapCrop lowers the quality until the file fits the KB budget, or stops without saving an oversized file. Shrinking the resolution is a separate opt-in that keeps the aspect ratio and stops at 320 px per side. Metadata growth is checked before Save or Share finishes.
- File name templates with `%timestamp%`, `%date%`, `%time%` and `%counter%`
- Batch rename for screenshots picked in Gallery, with `%app%`, `%date%`, `%time%`, `%timestamp%`, `%counter%` and `%profile%`
- Batch crop and resize for up to 50 images. Each file's size and dimensions are checked before anything is decoded. Sources are limited to 64 MiB or 48 MP and working images to 12 MP or 48 MiB, and the result lists saved, skipped, oversized, unreadable, failed and cancelled counts separately.
- PDF reports bundle Gallery picks with a title, notes, timestamps, source and size details, and an optional OCR appendix. Choose A4, US Letter or a custom page size, portrait or landscape, and margins, with a preview drawn to scale. The output is standard PDF, not PDF/A.
- Opt-in network exports. Settings can turn on HTTP, WebDAV or Nextcloud, or Imgur upload targets, and nothing uploads by default. On Android 17, public HTTPS and Imgur need no extra permission, while LAN, link-local and `.local` destinations ask for Local network access only when used.
- The Android share sheet can show the destinations you pick most often
- Before you share an image that carries metadata, SnapCrop lists which kinds are present (never the values) and offers Strip all, Keep safe technical fields, or Preserve detected supported fields. The original file is never changed.
- Save to Pictures, DCIM or Downloads
- Export border, watermark, EXIF stripping, and editable project files that check the source's fingerprint and can relink it

---

## Download

Grab `SnapCrop-<version>-arm64-v8a.apk` from [**Releases**](https://github.com/SysAdminDoc/SnapCrop/releases/latest) and sideload it. Nearly every phone sold since 2017 is `arm64-v8a`, and that build is about a third the size of the universal one. If you're not sure what your phone runs, or you need `armeabi-v7a`, `x86` or `x86_64`, get the universal `SnapCrop-<version>.apk`. It has every ABI and works anywhere.

Each release also has a CycloneDX JSON SBOM and a provenance JSON file covering every APK. Check the downloaded APK's SHA-256 and signing certificate SHA-256 against that file before installing.

### Automatic updates with Obtainium

1. In Obtainium, add `https://github.com/SysAdminDoc/SnapCrop` as a GitHub source.
2. Leave prereleases off. If it asks for an APK filter, use one of the patterns below so it can't pick the SBOM, the provenance file or some other asset by mistake.
3. Before installing, compare the SHA-256 that Obtainium or GitHub shows with the one in SnapCrop's update dialog or in `SnapCrop-<version>-provenance.json`.

APK filters, one per build:

- arm64-v8a: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+-arm64-v8a\.apk$`
- armeabi-v7a: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+-armeabi-v7a\.apk$`
- x86_64: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+-x86_64\.apk$`
- x86: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+-x86\.apk$`
- universal: `^SnapCrop-[0-9]+\.[0-9]+\.[0-9]+\.apk$`

The in-app update check opens the exact `SnapCrop-<version>.apk` asset when GitHub has it, and falls back to the release page if that name or a trusted URL is missing.

**Requirements**
- Android 10+ (API 29)
- Google Play services for background removal and for Chinese, Japanese, Korean and Devanagari OCR. On a phone without it, like CalyxOS or GrapheneOS without sandboxed Play, SnapCrop hides background removal and marks those OCR scripts as needing Play services instead of asking you to update it. Everything else works, translation, Latin OCR, barcodes, face blur and AI crop included, and the bundled editing and OCR features work offline.

---

## Privacy and Permissions

SnapCrop keeps your screenshots on the phone and runs no analytics or telemetry server of its own. Core editing and the bundled recognition work without a network. It does include Google's ML Kit SDKs, and how they handle data is described below. Optional exports and the automatic update check are off by default.

- Image, video and notification permissions are separate. Full image access alone turns on automatic monitoring. With only selected photos you can still browse those, denying video only hides library videos, and Android's pickers work either way.
- After a first denial, SnapCrop explains what's limited and offers Try again. After the next one it opens the exact Android settings page (app, notification, overlay or Accessibility). When you come back, only the thing you started (monitoring, a capture, latest, Pin, Accessibility or a LAN upload) picks up again, and only if it can run now. Photo access is asked for when monitoring or the library needs it, video access only while you browse Gallery videos, and notifications only for captures and reminders.
- Without notification access, the detected-screenshot, countdown, Edit, Share and Quick Crop notifications are hidden. Android's foreground-service entry in Task Manager and image and video access keep working.
- Overlay access is optional. Without it, a screenshot still gets a notification that opens the editor.
- Long Screenshot and Step Capture use Accessibility only after you start that workflow. Home and each tile show their own disclosure before opening Android settings, and agreeing to one never turns on the other. Their Accessibility services keep frames on the phone and never upload anything. Android 14+ captures only the active app's window. Android 11 to 13 capture the visible display and remove the system bars before processing. Step Capture deletes its cached frames as it puts them together and stays within 12 million pixels, 48 MiB decoded and a 64 MiB cache.
- Sensitive-text scans for Quick Crop and sharing use the OCR script picked in Settings, catch strict developer-secret formats, support your own RE2 patterns (with limits), and show each region for review. If recognition or a pattern check fails, the scan stops the action instead of letting it through. Latin OCR works right away, and picking another script without its model opens that model's download control instead of skipping the scan.
- SnapCrop doesn't ask for all-files access. Deleting something by hand goes through Android's Trash confirmation. Save & Replace instead moves the original into the visible `Pictures/trashed/` album after the crop is safely saved. On Android 12+ you can grant optional Media management and photo-location access once to skip the per-image confirmation. SnapCrop doesn't index, show, upload or otherwise use location data in your photos. If access is denied or a move fails, the copy stays saved and the original stays put.
- The screenshot index is opt-in, stays on the phone and can be rebuilt or cleared from Settings.
- A small local operation log keeps only each workflow's step and result, how long it took and a cleaned-up error type (up to 200 events for 14 days). It never stores paths, URIs, image or OCR content, credentials, messages or stack traces. Settings can turn it off and clear it, or show, copy or attach the redacted log.
- Protect media previews turns on secure-window and Recents protection for Gallery, the editor, the stitch, collage, frame and video tools, long and web capture review, and the floating preview. It also takes the screenshot pixels out of detection notifications, including one that's already showing when you turn it on.
- Network exports are opt-in. HTTP and WebDAV report uploads and Imgur image uploads only run after you turn on and set up a target. Credentials sit in a versioned AES-256-GCM file whose key stays in the Android Keystore, and if decryption fails, uploads are turned off and Settings offers a reset. Uploads are HTTPS only and size-limited, with progress and a cancel button. Reports are capped at 64 MiB and Imgur images at 50,000,000 bytes each, and temporary PDFs are deleted afterward whether the upload worked, failed or was cancelled. Android 17 Local network access is only asked for with LAN, link-local or `.local` custom endpoints. Denying it skips that upload but keeps the local report and every public export path, with retry and app settings on offer.
- Android app-data backup is off, so Android doesn't copy SnapCrop's local paths, favorites, automation switches or export preferences anywhere on its own. The manual backup in Settings is versioned and only includes settings it knows. It leaves out network credentials, temporary capture state and Library organization, carries over supported older keys, and reports anything unknown or invalid. Library organization has its own export and import, with a read-only relink preview and a commit that keeps your side of any conflict.
- Background OCR search needs both the local index and its own opt-in. Recognized text can contain sensitive content. It stays in the local Room index, is left out of metadata and settings exports, and can be cleared separately.

### Network and ML Kit disclosure

- ML Kit processes input images, recognized text, and inference results on the device, and Google states that those inputs and outputs aren't sent to its servers. ML Kit may contact Google for bug fixes, model updates and hardware accelerator compatibility data. Its Android SDKs also collect operational device and app information, per-installation identifiers, performance/utilization metrics, and feature settings such as identified or configured languages, for diagnostics and usage analytics. Google states this data is encrypted in transit and isn't passed to third parties. See [ML Kit Terms & Privacy](https://developers.google.com/ml-kit/terms) and the [ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure).
- Object detection, Latin OCR, face detection, barcode scanning and language identification models come bundled with the app. Chinese, Japanese, Korean and Devanagari OCR are optional Google Play services modules, and Subject segmentation also comes through Google Play services. Translation and entity-extraction models download when first needed. Settings → On-device models shows whether each one is ready and lets you download, retry or remove optional OCR and translation models over Wi-Fi. Removing an OCR model is best effort when Play services shares it with another app. Entity extraction may use whatever network is active the first time. Model storage and updates follow ML Kit's documented [installation paths](https://developers.google.com/ml-kit/tips/installation-paths).
- A manual update check, or the launch check (off by default), asks `api.github.com` only for public release information, with a fixed app user agent. The version comparison happens on the phone, and no screenshot or OCR content is sent.
- Static Web Capture fetches only the main document of the public HTTPS page you chose, with a size limit. The isolated renderer blocks scripts, subresources, cookies, storage, downloads, private and local addresses, and navigation. SnapCrop doesn't upload page content.
- Network exports run only after you turn on a target and start an upload. Custom HTTP and WebDAV destinations must use HTTPS, Imgur receives the image you picked, and Android 17 asks for Local network access only for a configured LAN, link-local or `.local` endpoint.
- Search image sends nothing until you pick an engine. Picking Google Lens hands the image to the Google app on the phone. For Bing or Yandex, SnapCrop uploads a JPEG of the edited image (at most 1600 px on the long side, no EXIF or location) straight to that engine over HTTPS and opens the results page in your browser. That engine's own privacy policy covers what happens after.

### Permission and security policy

- Photos/images access turns on screenshot monitoring and Gallery. Video is asked for only when Gallery video browsing needs it, and Android's selected-photo picker works without broad library access. Media management and photo-location access are optional. You grant them yourself, and they're used only to move a replaced original into `Pictures/trashed/` without repeated confirmations.
- Notifications cover monitoring, countdowns, reminders and recovery actions. The display-over-apps permission is optional, and Accessibility is only turned on in Android settings after the separate Long Screenshot or Step Capture disclosure.
- Internet and network-state permissions are used only for the paths listed above. Android 17 Local network access is asked for when it's needed, for eligible custom endpoints. Foreground-service, boot and vibration permissions support local capture monitoring. SnapCrop never asks for all-files access.
- Only the latest production release gets fixes. Report vulnerabilities privately through a [GitHub security advisory](https://github.com/SysAdminDoc/SnapCrop/security/advisories/new), and please don't attach real screenshots, credentials or recognized secrets to a public issue. Official builds are production-signed and pass the local provenance, SBOM, manifest, redaction-quality and 16 KB alignment checks.

---

## Tech Stack

| | |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Theme** | Near-black dark theme and a light theme, with a coral accent |
| **ML** | ML Kit (Object Detection, Text Recognition, Face Detection, Barcode Scanning, Subject Segmentation, Language ID, Translation, Entity Extraction) |
| **Image Loading** | Coil 3.3 with a 25% background memory-cache cap |
| **Min SDK** | 29 (Android 10) |
| **Compile / target SDK** | 37.1 / 37 |

---

## Build from Source

```bash
git clone https://github.com/SysAdminDoc/SnapCrop.git
cd SnapCrop
./gradlew --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false clean :app:lintDebug :app:testDebugUnitTest --console=plain
./gradlew --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false :app:benchmarkScreenshotSimilarity --console=plain
./gradlew --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false :app:verifyReleaseSizeBudget --console=plain
./gradlew --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false :app:generateReleaseProvenance --console=plain
./gradlew --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false :app:verifyOfficialRelease --console=plain
```

The cache flags are on purpose. Until a stable Kotlin 2.4.20 or newer is reviewed and pinned, the build refuses Gradle and Kotlin build-cache or incremental-cache opt-ins because of CVE-2026-53914. Dependency downloads are still cached and checked against `gradle/verification-metadata.xml` by SHA-256.

Release builds land in `app/build/outputs/provenance/`. That's the universal `SnapCrop-<version>.apk`, four `SnapCrop-<version>-<abi>.apk` files, `SnapCrop-<version>-sbom.json` and `SnapCrop-<version>-provenance.json`, which records each APK's ABI, sizes, hash and certificate fingerprint along with the version, source commit and build command.

The similarity benchmark runs on a synthetic screenshot set and writes its numbers to `app/build/reports/screenshot-similarity/benchmark.json`. It never reads your library. The size gate compares every APK, dependency and native library against `gradle/release-size-baseline.json`, and a deliberate change needs a baseline refresh with a reason in `--project-prop=snapcrop.sizeBaselineReason="..."`. `verifyOfficialRelease` also needs the production keystore and a clean worktree, and it checks matching versions, native library layout, the size budget and 16 KB `zipalign` on every APK.

> Needs JDK 17 and the Android SDK. Official releases show `"sourceState": "clean"` and use the published certificate fingerprint.

---

## License

[MIT](LICENSE)
