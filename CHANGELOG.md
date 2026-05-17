# Changelog

All notable changes to SnapCrop will be documented in this file.

## [Unreleased]

**Verification and release hardening.**

- Added CI lanes for lint, unit tests, debug assemble, release assemble,
  dependency review, and release SBOM artifacts.
- Added a starter JVM/Robolectric unit-test surface for auto-crop, app profile
  matching, sensitive text pattern detection, and Smart Erase behavior.
- Added a release checklist covering version sync, verification, signing,
  privacy/policy review, and artifact handling.
- Updated the Android build baseline to Gradle 9.4.1, AGP 9.2.1,
  Kotlin/Compose compiler 2.3.21, compileSdk 36, Compose BOM 2026.05.00,
  Material 3 1.4.0, Activity Compose 1.13.0, Lifecycle 2.10.0,
  Navigation Compose 2.9.8, and Core KTX 1.18.0.
- Migrated the app module to AGP 9 built-in Kotlin by removing the legacy
  `org.jetbrains.kotlin.android` plugin and using `kotlin.compilerOptions` for
  the JVM target.
- Added `SECURITY.md` and README privacy notes with a permission matrix,
  backup posture, release hygiene, and policy references.
- Removed the all-files access permission; Android 11+ deletions now use
  scoped-storage confirmation instead of `MANAGE_EXTERNAL_STORAGE`.
- Disabled Android app-data backup so local paths, favorites, automation
  toggles, and export preferences are not silently backed up.
- Made display-over-apps optional for screenshot monitoring by relying on the
  existing notification fallback when background editor launch is blocked.
- Added an in-app Accessibility disclosure before sending users to Android
  settings for Long Screenshot setup.
- Added editable `.snapcrop.json` project sidecars with a versioned schema for
  source URI/hash, crop rect, adjustments, pixelate rectangles, draw layers, and
  export settings.
- Added project-sidecar open support for JSON share/view intents and an
  `Editable project sidecars` setting. While sidecars are enabled, main Save
  keeps the source image so the project can be reopened.

## [v6.19.0] - 2026-05-14

**SVG annotation sidecars.**

- **Vector annotation export added.** Saving an annotated crop now writes a same-name `.svg` sidecar when visible draw layers or pixelate rectangles exist.
- **Layer order is preserved.** SVG output uses the same visible layer order as the editor/export pipeline and skips hidden layers.
- **Scoped-storage compatible.** SVG sidecars are written through MediaStore with `image/svg+xml` in the configured SnapCrop save location.

## [v6.18.0] - 2026-05-14

**Layered editing.**

- **Draw annotations now behave as layers.** Every committed stroke, shape, text label, emoji, callout, blur, erase, fill, spotlight, or magnifier remains individually represented in the editor stack.
- **Reorderable layer panel added.** Draw mode can show a compact Layers panel with top-to-bottom ordering, Up/Down controls, per-layer visibility, and per-layer delete.
- **Export respects layers.** Hidden layers are skipped in the canvas preview and raster export, while reordered layers render in the same order users see in the panel.

## [v6.17.0] - 2026-05-14

**Smart auto-albums.**

- **Gallery auto-tagging added.** Screenshot-like media is grouped into virtual smart albums for screenshots, chats, games, and sites without writing persistent tags or moving files.
- **Auto albums stay lightweight.** Grouping uses MediaStore filename/path metadata plus SnapCrop's existing screenshot-size heuristic, so the gallery avoids OCR or bitmap decoding during album browsing.
- **Album discovery improved.** Smart albums appear as labeled Auto cards, participate in album search, and open through the normal gallery grid/viewer/multi-select flow.

## [v6.16.0] - 2026-05-14

**AI Reframe.**

- **Content-aware aspect-ratio reframing added.** After choosing a crop ratio, the new Reframe chip shifts the crop around detected objects, OCR text, and faces.
- **Largest safe crop is preserved.** Reframe keeps the crop as large as possible for the selected ratio, then clamps it inside the image around the detected content center.
- **Detection stays local.** The feature reuses existing on-device ML Kit object, text, and face detection paths.

## [v6.15.0] - 2026-05-14

**Sensitive text auto-redaction.**

- **Shared sensitive-text detector added.** OCR text is now scanned for emails, phone numbers, credit-card candidates with Luhn validation, IP addresses, MAC addresses, and ML Kit entity matches.
- **Pixelate mode gained Auto Text.** The editor can add redaction rectangles for detected sensitive text with one action, alongside the existing Blur Faces action.
- **Quick Crop auto-actions use the same detector.** Conditional Reddit/X-Twitter redaction now benefits from the broader regex + ML Kit entity extraction path.

## [v6.14.0] - 2026-05-14

**Smart Erase.**

- **Heal was replaced with Smart Erase.** The draw toolbar now exposes a Smart object-removal brush instead of the old blemish-oriented Heal tool.
- **Mask-based inpainting added.** Smart Erase rasterizes the stroke into a removal mask, expands it to cover object edges, fills from surrounding safe pixels, and feathers the boundary for calmer results.
- **Preview semantics improved.** Smart Erase strokes now show a clear translucent removal mask on the canvas before export.

## [v6.13.0] - 2026-05-14

**OCR translate flow.**

- **On-device translation added to OCR.** Recognized text can now be translated from the OCR dialog using ML Kit language identification and translation.
- **Target-language chips added.** Common targets include English, Spanish, French, German, Portuguese, Japanese, Korean, and Chinese.
- **Translation feedback is explicit.** SnapCrop shows model preparation, Wi-Fi model-download guidance, source-to-target language labels, and copy support for the translated text.

## [v6.12.0] - 2026-05-14

**One-tap last action tile.**

- **Last action Quick Settings tile added.** A new tile reruns the saved Quick Crop action on the newest screenshot without opening the app.
- **Quick Crop now records itself as the last action.** Notification Quick Crop and the new tile share the same crop/profile/conditional-auto-action pipeline.
- **Foreground service cleanup is calmer.** Tile-triggered runs stop the service after completion when monitoring was not already active.

## [v6.11.0] - 2026-05-14

**Screen-recording frame and clip workflow.**

- **Video frame workflow added.** Home and Gallery can now open a screen recording in a dedicated video tool, scrub to a frame, save it using the configured image format, and open it directly in the editor.
- **Clip trimming added.** The video tool can save a selected time range as an MP4 into `Movies/SnapCrop` without transcoding.
- **Gallery video handling improved.** Tapping a video now opens SnapCrop's frame/trim workflow, with an option to hand off playback to the system player.

## [v6.10.0] - 2026-05-14

**Conditional Quick Crop auto-actions.**

- **Opt-in automation rules added.** Quick Crop can now recognize Reddit and X/Twitter screenshots, run OCR privacy redaction, and save into app-specific SnapCrop subalbums.
- **Sensitive text is redacted automatically.** Matching Quick Crop flows pixelate detected emails, phone numbers, IP addresses, and credit-card-like number blocks before saving.
- **Automation stays explicit.** The feature is controlled by a new **Quick Crop auto-actions** setting and leaves the normal editor save flow unchanged.

## [v6.9.0] - 2026-05-14

**App-specific auto-crop profiles.**

- **Profile-aware auto-crop added.** Auto-crop now applies conservative built-in visual templates for Reddit and X/Twitter chrome after the standard bar/border crop.
- **Source hints improve confidence.** SnapCrop reads MediaStore/share URI hints when available, so shared or tagged images can trigger the right profile without broad over-cropping.
- **Users stay in control.** Settings now includes an **App crop profiles** toggle, and the editor crop badge shows the matched profile when one is applied.

## [v6.8.0] - 2026-05-14

**Long screenshot capture.**

- **Scrolling capture added.** SnapCrop now includes an AccessibilityService that captures the current screen, scrolls forward, and stitches overlapping frames into one long screenshot.
- **Capture is reachable from real workflows.** The home screen explains setup and the app now ships a dedicated **Long screenshot** Quick Settings tile so captures can start from the app being captured.
- **Exports stay consistent.** Long screenshots save into the configured SnapCrop folder using the user's PNG/JPEG/WebP format preference, then open in the editor for cleanup.

## [v6.7.3] - 2026-05-13

**Home recent-crop trust polish.**

- **Recent crop removal is explicit.** The home screen now shows a visible delete affordance on each recent crop instead of hiding removal behind long-press.
- **Deletion now confirms intent.** Removing a recent crop opens a calm confirmation dialog that explains only the exported crop is removed, not the source screenshot.
- **Accessibility improved.** Recent crop thumbnails and delete actions now have clearer screen-reader labels and stable touch targets.

## [v6.7.2] - 2026-05-13

**Editor trust and save-state polish.**

- **Delete now asks first.** The editor trash action now opens a clear confirmation dialog before removing the source screenshot from the media library.
- **Primary save is explicit.** When the user's default Save behavior replaces the source screenshot, the editor now labels the main action **Save & Replace**; non-destructive setups still show **Crop & Save**.
- **Saving feedback is calmer.** The blocking save overlay now explains whether SnapCrop is replacing the source or writing a separate copy instead of showing a bare spinner.

## [v6.7.1] - 2026-05-13

**Premium UX polish pass across the main workflow, gallery, settings, and utility screens.**

- **Home is now workflow-led.** The main screen now presents clearer monitor status, calmer permission guidance, polished action tiles, and a proper batch-progress card with percentage and cancel affordance.
- **Settings is usable on real phones.** Settings now scrolls, surfaces export defaults at the top, explains JPEG/WebP-only target-size behavior, disables unavailable toggles instead of letting them silently do nothing, and clears temporary files from a coroutine with clearer feedback.
- **Gallery states feel finished.** Loading, empty, no-results, and selection states now include clearer hierarchy and copy; the screenshot-cleanup action is labeled instead of being a tiny icon/count pair.
- **Secondary tools got better trust states.** Stitch, Collage, and Device Mockup now have intentional empty states, rendering status text, and disabled save buttons that explain what is needed.
- **Visual consistency tightened.** UI backdrops now stay within the sharper 4-12dp radius system, and text annotation backdrops use a compact rounded rectangle instead of a fully rounded label shape.

## [v6.7.0] - 2026-05-13

**Gallery cleanup feature: one-tap select all screenshots.**

- **Screenshot detection by dimensions.** Every image in an album is now compared against the device's physical display resolution (with ~2% tolerance for status-bar / cutout trims). Matches are flagged as screenshots regardless of filename.
- **"Select N screenshots" action.** When viewing any album, a phone-icon button + count appears in the top bar if screenshot-sized images are present. Tap to enter selection mode with every detected screenshot pre-selected — then tap the trash icon to delete them all at once. Designed for cleaning up raw screenshots that piled up before you cropped them.
- **Visual indicator.** Detected screenshots get a small phone-icon badge in the top-left of their grid tile so you can review the auto-detection before bulk-deleting.
- **Browse-by-swipe** in the photo viewer continues to work via `HorizontalPager` — opening any photo lets you swipe left/right through the rest of the album.
- Loaders now query `WIDTH`/`HEIGHT` from MediaStore (1 extra cursor column, negligible overhead).

## [v6.6.1] - 2026-05-13

- **Default Save now replaces the original screenshot.** When you crop a screenshot and tap Save, the original full-size capture is removed automatically so you don't end up with both copies. Use **Save Copy** when you want to keep the original alongside the crop. The Settings toggle (renamed to "Replace original on Save") defaults to ON; turn it off to revert to non-destructive behavior.
- Existing users who never opened the old "Delete original after crop" toggle pick up the new default automatically. Users who explicitly turned it off keep their preference.
- Seamless silent delete requires **All files access** (MANAGE_EXTERNAL_STORAGE) — the home screen permission card prompts for it. Without that permission, Android 11+ shows a one-tap system confirmation per delete.

## [v6.6.0] - 2026-05-13

Sixth-pass deep audit — security + reliability.

- **Security**: keystore passwords moved out of committed `app/build.gradle.kts` into gitignored `keystore.properties` (with `SNAPCROP_KEYSTORE_PASSWORD` / `SNAPCROP_KEY_PASSWORD` env-var fallback for CI). Contributor builds without the keystore now fall back to debug signing instead of failing.
- **Fixed**: `MainActivity.loadRecentCrops` recycled native bitmaps that Compose was still rendering, risking "Cannot draw recycled bitmaps" crashes on rapid app resume. Thumbnails now rely on GC.
- **Fixed**: `ScreenshotService` ACTION_DELAYED_CAPTURE could start the service without promoting it to foreground, making it eligible for OS termination mid-countdown. Service now always promotes to foreground before dispatching any action.
- **Fixed**: Delayed-capture race where the MediaStore observer could consume the new screenshot before the countdown handler ran, leaving the user with no editor. Observer is suppressed during an active delayed capture; the post-countdown handler polls by `DATE_ADDED` baseline rather than a single `lastProcessedId`, and retries briefly for slow writes.
- **Fixed**: Delayed-capture countdown notification leaked if the service was stopped mid-countdown (toggle off, system kill). `onDestroy` now cancels it explicitly.
- **Fixed**: `PhotoViewer` zoom map was a plain `mutableMapOf`, so changes never recomposed the pager's `userScrollEnabled`. Swipes weren't actually locked while zoomed in. Now `mutableStateMapOf`.
- **Fixed**: `GalleryScreen.onDelete` left orphan IDs in the Favorites store after a delete from inside the viewer. Favorite is now toggled off in the same step.
- **Fixed**: `MainActivity.shareImages` hardcoded `image/*` for the share intent and always re-encoded as PNG when "Strip metadata" was on, ignoring the user's JPEG/WebP preference. Now respects format; mixed image+video selections use `*/*`; videos pass through unchanged (no transcode).
- **Fixed**: `CollageActivity` tap-to-add-image on an empty cell wiped *all* existing selections via the picker callback. Empty cells now append, with a `Replace` action in the top bar for an explicit start-over. Occupied cells gain a remove (×) button. Shrinking the layout drops overflow silently.
- **Fixed**: `CropActivity.applyTiltShift` allocated a `bitmap.copy()` that was immediately overwritten by `setPixels`; added 2×2 minimum-size guard.
- **Fixed**: `CropActivity.applySharpen` ran a 3×3 convolution on bitmaps too small to support it; added 3×3 minimum-size guard.
- **Version bump**: 6.5.6 → 6.6.0 (versionCode 49 → 50).

## [v6.5.5] - 2026-04-29

- Changed: Update README.md for user-facing appeal
- docs: add release signing notes to CLAUDE.md
- Added: Add release signing config for installable APKs
- Fixed: fix: move LaunchedEffect after variable declarations to fix compile error
- v6.5.5: Fifth-pass audit — 11 bug fixes across 5 files
- Changed: Update README for v6.5.0 — complete feature documentation
- v6.5.0: Curves, flood fill, heal brush, target size, delayed capture, 25 collage layouts
- v6.3.0: Line/eraser tools, selective color pop, tilt-shift
- v6.2.0: Blur brush, free rotation, export border, highlights/shadows
- v6.1.0: Audit — 8 bug fixes, sharpen slider, gallery viewer zoom
