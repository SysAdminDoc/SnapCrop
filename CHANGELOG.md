# Changelog

All notable changes to SnapCrop will be documented in this file.

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
