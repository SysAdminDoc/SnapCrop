# Changelog

All notable changes to SnapCrop will be documented in this file.

## [Unreleased]

**Measured sensitive-text redaction gates (v6.38.0).**

- Sensitive-text detections now retain category, source, and OCR-box geometry
  across email, phone, payment card, IPv4, IPv6, MAC, IBAN, and postal-address
  paths. Phone matching no longer absorbs date/invalid-IP lookalikes, and IPv4
  octets are validated.
- Privacy-sensitive Quick Crop and share scans now honor the selected Latin,
  Chinese, Japanese, Korean, or Devanagari OCR model and distinguish OCR failure
  from a clean screenshot instead of silently publishing an unscanned result.
- Added an 80-case synthetic light/dark × OCR-script × category corpus with only
  reserved/example values. The local release gate enforces 100% known-secret
  recall, zero rendered leaks, at least 99% box coverage, category/macro
  precision floors, and bounded over-redaction before producing an official APK.

**Fail-closed asynchronous screenshot index (v6.37.0).**

- Converted every one-shot Room DAO/index operation to a suspend API, moved
  MediaStore rescans onto the IO dispatcher inside the store, and removed the
  Settings composition-time count query. Room main-thread access is no longer
  enabled.
- Removed database-wide destructive migration fallback so future user-owned
  collections, notes, or source metadata cannot be silently erased by a missing
  migration.
- Enabled and tracked Room v1 schema export. Added a Room 2.8.4 migration-test
  harness that opens the exported v1 schema with representative data and tests
  suspend replace/purge behavior; debug instrumentation uses a separate package
  so it cannot replace an installed production build.

**Active-window Accessibility capture (v6.36.0).**

- Android 14 and later Long Screenshot and Step Capture sessions now target the
  current active application window with `takeScreenshotOfWindow`, excluding
  Accessibility overlays and system UI. Android 11–13 retain the visible-display
  capture fallback with system-bar cropping.
- Long Screenshot locks capture and scroll actions to the initial app window;
  switching or closing it stops safely instead of mixing frames. Step tap markers
  now translate screen coordinates into window-local coordinates.
- Added distinct recovery messages for unavailable, changed, invalid, secure,
  throttled, revoked-access, and invalid-display failures, plus policy tests for
  active-window selection, non-zero-origin taps, and overlay contamination.

**Bounded streaming network exports (v6.35.0).**

- PDF reports now serialize into a bounded private temp file, stream-copy to
  MediaStore, reuse that file for HTTP/WebDAV upload, and delete it on every
  completion path. Cancel stops the report job, disconnects the active request,
  and prevents partial report publication.
- HTTP/WebDAV/Imgur uploads now use 64 KiB streaming buffers, declared and actual
  byte limits, byte progress, response caps, request/batch deadlines, HTTPS-only
  validated endpoints, sanitized headers, and disabled redirects.
- Added a 100-image report limit plus Imgur’s 50,000,000-byte image,
  50-file, and 256 MiB batch guards. Large generated-stream tests verify memory
  use does not scale with payload size; stale and over-limit temp files are tested.

**Independent media capability gates (v6.34.0).**

- Split image, video, and notification state across Android 29–36. Automatic
  monitoring now depends only on full image access; video and notification
  denial no longer block it, boot resume, or unrelated picker workflows.
- Android 14 selected image/video access now includes the reselection permission,
  queries only authorized Gallery collections, and displays exact Manage/Allow
  recovery cards. SAF image, batch, and video pickers remain available in every
  denied or partial state.
- Added defensive image-access checks to delayed capture, boot, monitor and
  last-action tiles, and MediaStore observer registration. Notification denial
  preserves the foreground service’s Task Manager visibility while explaining
  that drawer actions are unavailable.

**Bounded Step Capture pipeline (v6.33.0).**

- Step Capture now normalizes frames to at most 720 px, stores at most 10
  lossless frames in a private session directory, and enforces 12M-pixel,
  48 MiB decoded, 64 MiB cache, 10-minute, and 2-minute inactivity limits.
- Added automatic limit finalization, stale/late-frame cleanup, explicit
  capture/finalizing state, an ongoing Stop notification, and finalization state
  in the Quick Settings tile. Notifications-disabled sessions retain the tile as
  the guaranteed stop control.
- Guide assembly preflights dimensions and decodes one cached frame at a time;
  1080p/1440p fixture tests keep the estimated bitmap peak below 80 MiB.

**Keystore-backed network credentials (v6.32.0).**

- Replaced active `EncryptedSharedPreferences` storage with a bounded,
  versioned AES-256-GCM credential file under no-backup storage and an Android
  Keystore key. Existing encrypted and legacy plaintext credentials migrate
  once, after which the old stores are removed.
- Credential corruption, key invalidation, and encryption failures now disable
  network exports without retaining plaintext. Settings explains the failure
  and provides an immediate credential reset path.

**Fail-closed official release gate (v6.31.2).**

- Added `:app:verifyOfficialRelease`, which refuses contributor/debug signing,
  verifies the pinned production certificate, rejects dirty source state, and
  checks that root, app, provenance, and SBOM versions agree.
- Official APK verification now checks every packaged native library is an
  uncompressed ELF and runs SDK `zipalign -c -P 16 -v 4` before publication.

**Pinned build inputs (v6.31.1).**

- Pinned the Gradle 9.4.1 distribution SHA-256 and added a local task that
  verifies the wrapper JAR against Gradle's published checksum.
- Added strict Gradle SHA-256 dependency verification for plugins plus
  debug-test and release runtime artifacts. PGP signer metadata remains in the
  catalog for audit, while unavailable public keys fall back to pinned hashes.
  Release provenance now depends on wrapper verification.

**Destructive redaction by default (v6.31.0).**

- Sensitive-text automation and scan-before-share now replace every selected
  raster pixel with a fully opaque fill by default. PNG round-trip tests verify
  that original region pixels do not survive the export.
- Added an explicit Sensitive-text replacement setting: Opaque fill (safe) or
  Pixelate (cosmetic). Manual pixelation and face blur are now labeled cosmetic
  concealment, while editable project masks are labeled reversible.
- Automated redaction and scan-before-share now fail closed when recognition
  fails instead of silently saving or sharing an unscanned original.

**Bounded, verified project imports (v6.30.0).**

- External `.snapcrop.json` imports now enforce an 8 MB UTF-8 input ceiling,
  exact schema/version, source dimensions/fingerprint, bounded rectangles,
  layers, points, text, transforms, adjustment ranges, and export metadata.
- Project source images are hashed before decode and their decoded working
  dimensions are checked before any bitmap or editor collections are published.
  Missing, inaccessible, untrusted-provider, hash-mismatched, and
  dimension-mismatched sources now offer a verified OpenDocument relink flow.
- Process-death drafts retain a separate trusted policy that permits their
  intentionally omitted hash while still requiring exact dimensions. Exported
  projects are capped to the same limits the importer accepts.

**Purpose-specific Accessibility consent (v6.29.0).**

- Long Screenshot and Step Capture now have separate, versioned consent records
  and prominent disclosures describing the exact events, window data, visible
  screenshots, gestures, temporary frames, local retention, and no-upload
  behavior used by each workflow.
- Home and both Quick Settings tiles now route first use through the matching
  disclosure instead of opening Android Accessibility settings directly. Cancel
  records nothing; an active Step Capture session can still be stopped at once.
- Accessibility service metadata now identifies both services as non-tool
  workflows, links their tiles on supported Android versions, and uses
  purpose-specific labels/descriptions.

**Verifiable local release artifacts (v6.28.1).**

- Added `:app:generateReleaseProvenance`, which produces a stable versioned APK,
  versioned CycloneDX JSON SBOM, and JSON provenance manifest containing the
  APK SHA-256, signing-certificate SHA-256 fingerprint, version code/name,
  source commit/state, and exact local build command.

**Scoped, recoverable media mutations (v6.28.0).**

- Removed the broad all-files permission and its Home prompt. Android 11+
  cleanup now uses recoverable MediaStore Trash requests; Android 10 clearly
  labels its permanent-delete fallback.
- Save & Replace now publishes the image and requested sidecars before asking
  to remove the source. Denial or failure reports "Copy saved; original
  retained" instead of closing as if replacement succeeded.
- Gallery, recent-crop, editor, and long-screenshot cleanup now wait for the
  Activity Result before changing UI/favorites. Batch cleanup includes an
  item-count confirmation, supports Android's 2,000-item request limit, and
  preserves pending state across activity recreation.
- Quick Crop no longer silently removes its source on Android 10, and verifies
  that its MediaStore row was encoded and published before success feedback.

**Trust, distribution, and resilience (v6.27.0).**

- Quick Crop now copies the saved result to the system clipboard alongside
  saving, so the image is ready to paste immediately. The home-screen recent
  crops also gained a one-tap clipboard copy button.
- After saving a long/scrolling screenshot, SnapCrop now offers to delete the
  short seed screenshot that triggered the capture, avoiding a duplicate gallery
  entry. Uses scoped-storage delete confirmation on Android 11+.
- The Draw-mode eyedropper now doubles as a color sampler: tapping a pixel opens
  a dialog with HEX, RGB, and HSL codes (tap to copy). Sample a second pixel to
  see WCAG 2.x contrast ratio (AA/AAA pass/fail) and APCA Lc value.
- PDF export with OCR enabled now embeds an invisible text layer aligned to the
  image, making the PDF searchable and text-selectable in any viewer.
- Added a deferred screenshot index worker: a WorkManager job constrained to
  charging + idle + unmetered network rebuilds the screenshot intelligence index
  every 6 hours, avoiding battery/thermal cost from reactive ML processing.
- Added selectable OCR script: Settings now has a Chinese/Japanese/Korean/Devanagari
  picker alongside the default Latin recognizer. The selected script flows through
  all OCR paths (editor, search, smart albums, PDF export, Quick Crop).
- The `strip_exif` setting now actually works: editor saves copy EXIF metadata
  (orientation, capture time, color space, exposure) from the source screenshot
  to the export. With strip ON, GPS, device make/model, and serial numbers are
  removed while safe photographic metadata is preserved. Added `ExifTransfer`
  utility and the `androidx.exifinterface` dependency.
- Edge-to-edge hardening pass: editor, home, settings, stitch, collage, device
  frame, video frame, long-screenshot review, and gallery viewer overlays now
  use safe-drawing/display-cutout aware padding plus IME padding where relevant.
- Added TalkBack-facing editor canvas semantics: the canvas announces current
  mode, crop geometry, redaction count, layer count, and selected layer state,
  with custom actions for crop nudging, zoom/preview, redaction removal, and
  selected-layer move/resize/rotate/delete.
- Custom Quick Crop app-rule deletion now asks for confirmation before removing
  the rule.
- Second polish pass: color swatches, palette chips, border/background color
  pickers, and stitch remove buttons now keep at least 36dp touch targets while
  preserving compact visuals; color sampler values use copy buttons instead of
  tiny clickable text.
- Gallery now loads the existing Room-backed screenshot index on open instead
  of rebuilding it during composition; manual rebuild and the deferred
  WorkManager job remain the refresh paths.
- Home workflow tile titles can wrap to two lines, with explicit ellipsis as a
  last-resort guard for small screens and large font scales.
- Stricter polish pass: Gallery album loads now assign Compose state on the
  main dispatcher, album/photo grids use stable lazy-list keys, remaining dense
  editor/collage controls meet the 36dp project touch-target floor, and
  hardcoded editor/video/collage accessibility strings were moved to resources.
  The half-width Batch tile copy was shortened to avoid awkward ellipsizing on
  small screens and larger font scales.
- Release-candidate polish pass: localized the remaining Stitch/Collage/Device
  Mockup option semantics plus editor utility labels, added stable lazy-list
  keys to Stitch and Device Mockup pickers, and hardened precise crop input for
  very small images.
- Extra release polish pass: the Home monitor switch now exposes its own
  stateful accessibility label, and Stitch output dimensions are calculated from
  a stable URI snapshot on the IO dispatcher so reordered or appended images do
  not leave stale size estimates or block composition.

- Added settings/preset backup & restore: export all preferences, presets, and
  app-crop profiles to a JSON file and restore them after a reinstall (the app
  keeps `allowBackup=false`). Network credentials are excluded.
- The editor now checkpoints an in-progress edit (source, crop, redactions, draw
  layers, adjustments) so a low-memory process death restores your work instead
  of losing it.
- Declared `READ_MEDIA_VISUAL_USER_SELECTED` and detect the Android 14+ "Select
  photos" partial-access state: the home card now explains the screenshot monitor
  needs full media access instead of silently doing nothing.

- **Crop editor crash on small images**: `coerceIn(0, cropRight - 50)` threw
  `IllegalArgumentException` when the crop rect or bitmap was smaller than 50px,
  creating an invalid range. Now clamps the upper bound to at least 0.
- **Service crash on notification dismiss from background**: `ScreenshotService`
  `onStartCommand` called `startForeground()` unconditionally, but Android 12+
  blocks foreground promotion from background PendingIntent re-delivery. Now
  catches `ForegroundServiceStartNotAllowedException`, dismisses the notification,
  and stops cleanly instead of crash-looping.
- **WebDAV upload fix**: `appendWebDavFileName` now appends the filename when the
  endpoint URL omits a trailing slash instead of silently uploading to the base URL.
- **Bitmap leak fix**: `createCroppedBitmap` intermediate cleanup no longer leaks the
  rotated bitmap when adjustments are a no-op (rotation + identity adjust + pixelate).
- **Color picker accuracy**: RGB slider hex/color conversion uses proper rounding instead
  of truncation, eliminating off-by-one color errors at intermediate slider positions.
- **Curves LUT accuracy**: per-channel gamma curve table values now round instead of
  truncating, matching the visual preview more faithfully.
- **Perspective sidecar data loss**: project sidecars now persist the quad-corner
  perspective warp (adj indices 17-24); previously reopening a `.snapcrop.json`
  project silently dropped the perspective transform.
- **Localization**: before/after preview labels extracted from hardcoded strings to
  localizable string resources.

- Added local crash diagnostics: an `UncaughtExceptionHandler` writes a stacktrace
  plus app/OS/device info to the app-private directory (last 5 retained), viewable,
  shareable, and clearable from Settings → About. Nothing is sent anywhere unless
  the user shares it. Closes the missing crash-log file for an off-Play, sideloaded app.
- Added an opt-in in-app update check: a "Check for updates" button (and an
  auto-check-on-launch toggle) does a single anonymous query to the GitHub Releases
  API and offers the download link when a newer version exists. No account, no
  tracking — sideload builds otherwise have no update path.
- Added an opt-in "Protect the editor screen" setting that marks the editor window
  `FLAG_SECURE` (and hides it from Recents on Android 13+), so other apps can't
  screenshot/screen-record the un-redacted image.
- Hardened the network-credential store: `EncryptedSharedPreferences` (deprecated)
  now self-heals on a corrupted keyset (wipe + recreate) instead of crashing on launch.
- Enabled per-app language support (`generateLocaleConfig`); SnapCrop will appear under
  system per-app language settings as soon as a translation is added.
- CI now signs the release APK with the release key (from repository secrets) and
  attaches it to the published GitHub Release, instead of producing a debug-signed
  artifact that can't update an existing install. (Requires the `SNAPCROP_KEYSTORE_*`
  secrets to be configured; without them it falls back to the prior debug-signed artifact.)

## [6.26.0] - 2026-06-14

**Deep audit — correctness, theming, and accessibility hardening (v6.26.0).**

- Fixed a fullscreen-viewer crash: the bottom-bar actions (favorite, share, pin,
  describe, edit, delete) indexed `photos[currentPage]` directly, which threw
  IndexOutOfBounds when the list shrank after a delete. All accesses are now
  null-safe.
- Hardened the magnifier/loupe export: it drew the result bitmap onto its own
  backing canvas (undefined behavior → blank/corrupt loupe). It now reads from a
  snapshot copy.
- Made the editor adapt to light theme: the OCR/Adjust mode accents, the Palette
  button, the curve/color RGB channel indicators, and the layer-row backgrounds
  were hardcoded dark-theme colors that rendered low-contrast or wrong in light
  mode. They now use semantic, theme-aware tokens (OcrAccent, AdjustAccent,
  ChannelRed/Green/Blue, OnSurface tints).
- Share and Copy now apply the configured export border and watermark (previously
  only Save did), and wrap bitmap creation in try/catch so an OOM surfaces a
  toast instead of crashing. Fixed a preview-bitmap leak in the redaction dialog.
- Made the project sidecar tolerant of a missing `crop` object instead of
  throwing on decode.
- Internationalized and made accessible the editor Layers panel: replaced
  hardcoded English with string resources, bumped icon touch targets to 36dp,
  and wired proper content descriptions.
- Committed draw strokes now integrate with the global undo stack (consistent
  with pixelate and tap-placed tools), and the layer-transform selection resets
  on delete/reorder so controls can't target the wrong layer.
- Removed a per-pixel `List<Pair>` allocation from the flood-fill BFS hot loop.
- ScreenshotService now runs quick-save / last-action work on a lifecycle-bound
  coroutine scope that is cancelled in onDestroy, instead of leaking a fresh
  scope per call that kept touching the service after it stopped.
- Step capture no longer orphans a screenshot captured after the session was
  stopped, and recycles the accessibility node it inspects.
- The floating pin overlay decodes the screenshot downsampled to its display
  size (instead of full resolution), recycles it on teardown, and fails cleanly
  if overlay permission is missing instead of crashing the service.
- Network export refuses to send credentials over a non-HTTPS endpoint with a
  clear message, and `usesCleartextTraffic="false"` is now explicit.
- Stitch and Collage stream sources one at a time (bounds pre-pass + per-image
  downsampling) instead of decoding every full-resolution image at once, fixing
  out-of-memory crashes on large multi-image jobs. Collage also only decodes the
  cells it uses and reports failure instead of producing a degenerate output.
- The gallery no longer writes the photo grid from two effects at once (a race
  that could show the wrong album); a single effect owns photo loading.
- Batch crop/resize delete the pending MediaStore row on any write failure
  instead of leaving an invisible orphaned entry.

**Verification and release hardening.**

- Added a step-capture workflow. A new Step capture Quick Settings tile starts a
  session backed by `StepCaptureService` (a dedicated AccessibilityService); each
  tap captures the current screen and records where you tapped. Tapping the tile
  again assembles the frames into a single numbered guide image — each step gets
  a numbered badge at the tap location — saved to the SnapCrop folder and opened
  in the editor for further annotation. Privacy: nothing is captured unless a
  session is explicitly started, the service description states this, and the
  tile routes to Accessibility settings when not yet enabled. (Capture-on-tap is
  device-runtime behavior; verified by build, pending on-device validation.)
- Added annotation layer transforms (move, resize, rotate). Committed draw
  layers were previously static; now tapping a layer in the Layers panel enters
  transform mode with move (nudge in four directions), resize (grow/shrink), and
  rotate (±15°) controls plus Reset. Transforms are pivoted on the layer
  centroid and applied consistently on the editor canvas, the raster export, and
  the SVG sidecar, and persist in `.snapcrop.json` projects. Layers with no
  transform render exactly as before (identity guard), so existing annotations
  are unchanged.
- Migrated the screenshot intelligence index from raw SQLite to Room. The store
  now uses `@Entity`/`@Dao`/`@Database` with compile-time-verified queries and
  exposes a reactive `Flow`; the gallery observes it so smart-album membership
  and indexed search update live when screenshots are indexed, OCR tokens are
  captured, or the index is rebuilt/purged — no manual refresh. The public store
  API (`rebuildFromMediaStore`, `loadEntryMap`, `updateRecognizedText`, `count`,
  `purge`) is unchanged, so all call sites are source-compatible. The index is a
  rebuildable cache, so a schema change falls back to a clean destructive rebuild
  and the obsolete pre-Room database file is deleted on first launch. Adds the
  KSP Gradle plugin and AndroidX Room (runtime/ktx/compiler) dependencies.
- Added a Measurement/Ruler draw tool. A new "Ruler" tool in Draw mode lets you
  drag a line whose length is reported in source-image pixels, rendered with
  perpendicular end ticks and a labeled distance badge. The measurement renders
  identically on canvas, on PNG/JPEG/WebP export, and in the SVG annotation
  sidecar, and persists in `.snapcrop.json` projects via the generic shapeType
  field. Targets designers/developers who need pixel dimensions from screenshots.
- Added auto-redact on share. Tapping Share now scans the cropped image for
  sensitive text (emails, phone numbers, IPs, MAC addresses, payment cards via
  Luhn, and ML Kit entities) using the existing `SensitiveTextDetector`. When
  matches are found, a dialog previews the redacted image and offers "Share
  redacted" (pixelates the detected areas before the share intent), "Share
  original", or cancel. A new Settings → "Scan before sharing" toggle (default
  ON) controls the scan; turning it off restores instant sharing.
- Internationalized all user-facing strings: extracted ~660 string resources to
  `values/strings.xml` and wired `stringResource()` / `getString()` across all
  17+ Activity, Screen, Service, and helper files. A translator can now add
  `values-es/strings.xml` (or any locale) and see a fully localized UI.
- Added screenshot explanation and accessibility summaries. New Describe button
  in gallery fullscreen viewer runs on-device OCR, entity extraction, barcode
  scanning, and color analysis to generate a structured natural-language summary.
  Summary includes word count, text preview, detected entities (emails, phones,
  URLs), barcodes, and dominant colors. Results shown in a dialog with copy
  support for use as alt-text or accessibility descriptions. All processing is
  local with no remote AI dependency.
- Added dataset-backed evaluation harness. Settings → About section has a
  "Run evaluation harness" button that executes synthetic fixture suites for
  AutoCrop border detection (white/black/dark-mode/system-bars/edge-cases),
  SensitiveTextPatterns regex matching, Luhn card validation, and AppCropProfiles
  hint-based matching. Results show per-suite pass/fail counts with IoU metrics
  for crop accuracy. All fixtures are programmatically generated with no external
  dataset dependencies.
- Added cross-app drag-and-drop for multi-window mode. Long-press an image in the
  fullscreen gallery viewer to start a drag that other apps can accept in
  split-screen. Dragging an image from Files or another app onto SnapCrop opens
  the editor. Drop target uses requestDragAndDropPermissions for cross-process
  URI access; drag source sets DRAG_FLAG_GLOBAL | DRAG_FLAG_GLOBAL_URI_READ.
- Added floating screenshot overlay. A new Pin button in the gallery fullscreen
  viewer launches a draggable overlay on top of other apps via WindowManager.
  Requires SYSTEM_ALERT_WINDOW permission (already declared). Tap to dismiss,
  drag to reposition. Close button in top-right corner.
- Added stylus palm rejection in the editor. When the primary pointer is a
  stylus or eraser, touch-type events are filtered and consumed so palm
  contact does not interfere with drawing or crop gestures.
- Added light theme option with Catppuccin Latte-inspired palette. Settings
  toggle offers Dark, Light, and System (follows Android dark mode). All
  surfaces, text colors, primary accents, and button content colors adapt.
  Editor/gallery canvases remain dark regardless of theme. OnPrimary semantic
  color ensures text on primary buttons has correct contrast in both modes.
- Added entity action chips to the OCR text dialog. When recognized text contains
  phone numbers, email addresses, or URLs, tappable chips appear above the
  translation controls. Phone chips open the dialer, email chips open a compose
  intent, and URL chips open the browser. Entity extraction uses local regex
  matching with no network dependency.
- Added perspective/quad crop with warp transform. A Perspective chip in crop
  mode toggles 4 independent corner handles. Dragging any corner reshapes the
  selection quad without affecting the others. On export, `Matrix.setPolyToPoly`
  warps the quadrilateral to a rectangle. Output dimensions are derived from the
  longest opposing edges. Grid lines interpolate across the quad. Useful for
  photographed screens, whiteboards, tilted documents, and perspective correction.
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
- Split reusable editor model/state, canvas helper, layer panel, and
  before/after preview code out of `CropEditorScreen.kt`.
- Added an editor regression checklist and a small model-helper regression test
  for extracted adjustment, aspect-ratio, filter, and path behavior.
- Upgraded Long Screenshot capture to a ten-frame/time-guarded flow with
  repeated-frame stop detection, sticky-header/footer aware stitching, and a
  review screen with Save & Edit, Retry, and Discard before gallery save.
- Added an opt-in local screenshot intelligence index with rebuild/purge
  controls, category-backed smart albums, indexed gallery search, OCR/barcode
  token capture from the editor OCR flow, and non-favorite screenshot cleanup
  selection.
- Added a visible app rules system: built-in Reddit/X profiles now appear in
  Settings with confidence/preview/test support, and users can create/import/
  export custom source/OCR-based crop profiles with album, redaction, and export
  format actions for Quick Crop.
- Added an adaptive editor layout for tablet/foldable/desktop-width windows:
  wide layouts use a persistent right inspector for modes, crop controls,
  redaction, draw layers, and adjustments, with keyboard shortcuts and
  mouse-wheel zoom support.
- Upgraded gallery PDF export into a local incident report builder with title,
  notes, timestamps, image metadata, source hints, dimensions, and optional OCR
  appendix pages.
- Added gallery batch rename templates for `%app%`, `%date%`, `%time%`,
  `%timestamp%`, `%counter%`, and `%profile%`, with filename sanitization.
- Added opt-in network export targets for PDF reports/images: self-hosted HTTP
  multipart upload, WebDAV/Nextcloud PUT, and Imgur anonymous image upload.
- Added share-sheet destination shortcuts that remember chosen Android share
  components and surface the most-used targets first.
- Added centralized ML Kit status/retry guidance, translation model-download
  progress, cached language-pair readiness, and visible subject-segmentation
  failure messages for background removal.
- Added an opt-in advanced erase backend registry, Settings visibility, and
  evaluation gates so downloaded inpainting model packs cannot activate until
  license, size, latency, battery, and benchmark criteria pass.
- Added ProGuard/R8 keep rules for ML Kit Play Services internals (subject
  segmentation, translation, language ID, entity extraction), the Tasks API,
  and dynamite module loading to prevent ClassNotFoundException in release
  builds.
- Verified aCropalypse-safe save pipeline: all six save paths (CropActivity,
  StitchActivity, CollageActivity, DeviceFrameActivity, ScreenshotService,
  LongScreenshotStore) use MediaStore insert, never overwrite-in-place.
- Migrated to targetSdk 36 with `enableOnBackInvokedCallback` for predictive
  back gesture support and 16KB NDK page alignment for Google Play compliance.
- Added unsaved-changes confirmation dialog when closing the editor via back
  gesture or close button with pending edits.
- Unlocked CropActivity landscape orientation. The editor now supports rotation
  on tablets, foldables, and desktop mode. Edit state is preserved across
  configuration changes via `configChanges` handling.
- Network export credentials (HTTP auth headers and Imgur client IDs) now use
  EncryptedSharedPreferences backed by Android Keystore. Existing plain-text
  credentials are migrated transparently on first launch after update.
- Added curved arrow draw tool (17th tool): drag to trace a curve, rendered as
  a quadratic bezier with arrowhead. Control point derived from the midpoint of
  the drag path. SVG sidecar exports as `Q` bezier paths. Persists in project
  sidecars via the new `controlPoint` field.
- Added annotation style presets: save current draw tool, color, stroke width,
  and dash style as named presets. Quick-select row in draw mode applies presets
  instantly. Settings page manages presets with default selection and deletion.
- Migrated from Coil 2.7 to Coil 3.3 for faster image loading and reduced
  allocations during gallery scrolling. Artifact group changed from
  `io.coil-kt` to `io.coil-kt.coil3`; all imports updated to `coil3.*`.
- Verified AVIF (Android 12+) and HEIC (Android 10+) read support works
  natively via BitmapFactory — no code changes needed since SnapCrop accepts
  `image/*` MIME types and has no format filtering.
- Added TalkBack accessibility labels across all screens: semantics on Switch
  toggles, Slider controls, clickable Cards/tiles, color swatches, emoji
  pickers, gallery photo items, collage cells, layer controls, stitch reorder
  buttons, and video trim sliders. Covers CropEditorScreen, MainActivity,
  GalleryScreen, SettingsActivity, StitchActivity, CollageActivity,
  DeviceFrameActivity, VideoClipActivity, and EditorLayers.

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
