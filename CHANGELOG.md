# Changelog

All notable changes to SnapCrop will be documented in this file.

## [Unreleased]

**Measured screenshot-similarity guardrail (v6.87.0).**

- A deterministic generated corpus now benchmarks 96 screenshot pairs across
  JPEG recompression, uniform resize, slight crop, color shift, one-message and
  sticky-header changes, unrelated same-layout content, and diagnostic theme
  changes without committing private screenshots or binary fixtures.
- The local benchmark separates production dimension/luma-gated dHash from raw
  dHash, pHash, and bounded SSIM, then reports calibration/validation precision,
  recall, F1, per-category outcomes, median/p95 time, and estimated scratch
  memory. A dedicated Gradle task regenerates and validates the JSON report.
- No candidate cleared the production-change gate. Validation measured pHash at
  1.0 precision/0.625 recall, SSIM at 0.947 precision/0.75 recall, and raw dHash
  below the precision floor, so exact matching, current dHash grouping, durable
  dismissals, complete-link grouping, and human review remain unchanged.

**Unfiled screenshot inbox (v6.86.0).**

- Gallery now derives a deterministic Unfiled inbox from current screenshot
  images that have not been filed, deferred by a future reminder, or explicitly
  reviewed. File, Remind me, Keep unfiled, and Android's recoverable Trash stay
  available as labeled 48 dp actions without moving or duplicating media.
- Exact URI/date identities prevent recycled MediaStore URIs from inheriting old
  decisions. Room migration 8 preserves durable inbox triage independently of
  index rebuilds, and collection filing replaces stale same-URI membership
  atomically.
- Recoverable Trash preserves app metadata until Android confirms the result;
  permanent pre-Android-11 deletion still cleans immediately. Generated corpus,
  migration, coordinator, host, lint, rendered emulator, accessibility, and the
  complete 26-test Android suite cover the new path.

**Testable state and I/O boundaries (v6.85.0).**

- Gallery album/photo loading now publishes typed snapshots from an independent
  coordinator, preserves partial image/video/index recovery, and propagates
  coroutine cancellation instead of exposing stale failure state.
- PDF, SVG, project, OCR-text, and image publication share one transactional
  pending-row writer with byte accounting and rollback across MediaStore
  collections. Batch crop/resize share cap, progress, outcome, and cancellation
  accounting; Settings navigation rejects stale delayed completions.
- Editor history now has a bounded tested controller for record, undo, redo, and
  jump transitions. Full host tests, lint, debug builds, and each clean five-APK
  signed release gate pass without changing public or visual behavior.

**Private two-screenshot comparison (v6.84.0).**

- Gallery selection now preserves selection order and enables Compare only for
  exactly two distinct images. The unexported secure workspace labels A/Before
  and B/After and creates no file, share, upload, or publication path.
- Swipe uses the fitted shared canvas rather than the viewport; Overlay exposes
  image-B opacity; Blink is paused by default; Difference reports deterministic
  changed analysis pixels and at most 32 grouped regions with overflow retained.
- Top-left, Center, and Bottom-left alignment never stretches mismatched images;
  unmatched edges count as changed. The shared bounded intake path enforces the
  existing 64 MiB/48 MP source and 12 MP/48 MiB working limits, then analysis uses
  one original-coordinate scale and a 1.5 MP cap.
- Pure fixtures cover threshold boundaries, transparent hidden RGB, independently
  sampled sources, union denominators, mismatches, fragmented-region overflow,
  selection order, routing, saved state, and decoder behavior. A headless Android
  17 run exercised all four modes, alignment, recreation, and accessibility checks.

**User-managed optional ML delivery (v6.83.0).**

- Latin OCR remains bundled and fail-closed privacy scans stay immediately
  available, while Chinese, Japanese, Korean, and Devanagari OCR now use thin
  Google Play services delivery. Missing requested scripts route to the exact
  Settings model control instead of producing an empty result.
- The searchable On-device models panel reports live OCR and translation model
  readiness, installed-size guidance, download progress where Play services
  exposes it, Wi-Fi-only download/retry, and removal. Translation readiness now
  comes from ML Kit's model inventory rather than stale preferences.
- Release provenance schema 3 records the v6.82 universal/per-ABI size baseline,
  reports each APK delta, rejects bundled optional-script assets, and enforces at
  least a 1.5 MB reduction. The thin build removes 2,330,760 bytes from every APK.
- Host contracts, accessibility routing, and error-path tests pass. The Android 17
  preview emulator's current Play services module installer returns
  `INTERNAL_ERROR`; that single live-download assertion is an explicit assumption
  skip while all app-side model lifecycle and packaging contracts remain strict.

**Searchable, anchored Settings and contextual routing (v6.82.0).**

- A localized static registry covers 32 exact Settings controls without reading
  current values or secrets. Search includes an accessible no-result/clear state;
  results transiently reveal conditional controls, bring the exact control into
  view, and highlight it without changing its saved toggle.
- Allowlisted section resets are atomic and exclude credentials, rules, presets,
  index data, journals, crash reports, and other user-owned content. Typed stable
  destinations now connect project/privacy Help, Gallery index management, Home,
  and report network recovery to the same control anchors.
- Host tests lock stable IDs, search normalization, secret exclusion, reset
  isolation, route coverage, and exact anchors. API-37 Compose tests exercise the
  no-result and local-network paths and pass accessibility checks.

**Incremental MediaStore index reconciliation (v6.81.0).**

- The opt-in screenshot index now keeps atomic per-volume MediaStore version and
  generation watermarks. Stable full-access scopes query only bounded added or
  modified metadata; Android 37.1/S-extension-23 deletion records remove missing
  items without walking the library, with an exact ID-only fallback elsewhere.
- Volume, version, generation rollback, permission kind, selected-media scope,
  screen, or favorite changes force a full-equivalent scan. Image/video grants
  are independent, trashed/pending rows are removed, and permission/version
  races never advance a watermark.
- Room v7 preserves collections, source context, notes/reminders, OCR payloads,
  and duplicate dismissals while invalidating only changed derived fingerprints.
  Immediate permission/toggle reconciliation is serialized with periodic/manual
  work. A 10,000-row corpus, host planner/replay tests, 6→7 migration tests, and
  real API-37 MediaStore add/modify/delete coverage verify convergence.

**Public trust and ML Kit disclosure contract (v6.80.0).**

- README privacy guidance now distinguishes SnapCrop's lack of an analytics
  backend from ML Kit's documented operational collection. It names on-device
  input handling, Google metrics/data categories, bundled versus downloaded
  models, and every app-controlled network path with primary-source links.
- The ignored security-policy link is replaced by an inline permission/support
  policy and GitHub's private advisory route. Stale editor/filter/SDK claims are
  synchronized with source, including six modes, 18 draw tools, 16 filter
  effects, and target SDK 37.
- Host checks now keep local README links, version history, feature counts,
  sensitive permissions, and network disclosures synchronized with source.
  Update settings state exactly what the disabled-by-default GitHub request does
  and does not send.

**Signed architecture-specific release APKs (v6.79.0).**

- Official distribution now publishes production-signed arm64-v8a,
  armeabi-v7a, x86, and x86_64 APKs beside the stable universal asset while
  ordinary debug builds keep their existing single-APK path.
- Provenance schema 2 binds all five APK hashes, sizes, versions, certificates,
  and ABIs to one hashed CycloneDX SBOM. The release gate rejects missing or
  stale assets, mixed manifests/certificates, incorrect native payloads,
  compressed ELF libraries, weak size reductions, or failed 16 KB alignment.
- The in-app updater remains pinned to the exact universal filename. Obtainium
  users can opt into documented exact per-ABI filters without x86/x86_64
  ambiguity.

**Renderer parity and side-effect regression gates (v6.78.0).**

- Before/after now renders asynchronously through the exact Save/Copy/Share
  composition path, including crop, rotation, perspective, Cut Out, adjustments,
  annotations, concealment, alpha shapes, and gradients. Renderer outputs are
  always caller-owned, source bitmaps survive no-op/shape exports, and opaque
  concealment wins final composition.
- Copy and Share now publish cache artifacts only after a successful non-empty
  encode and successful clipboard/chooser dispatch; partial failures are deleted.
  Generated native-graphics fixtures assert exact/tolerant pixels and preview/
  export identity, while injected MediaStore/cache/clipboard/share failures prove
  that each requested side effect fails independently.

**Accessible interaction semantics (v6.77.0).**

- Compact icon, swatch, layer, redaction, Gallery, stitch, collage, Home, and
  Settings actions now reserve 48 dp interaction space while retaining their
  existing visual density. Nested actions are siblings, and selected/toggle state
  is announced exactly once on the actionable node.
- Low-height landscape and tablet-wide editor layouts retain complete action
  targets. Production Home, Gallery empty/filter/viewer/permission/delete,
  project-error, Settings, and compact/wide editor surfaces now run through the
  Android Accessibility Test Framework on a headless API-37 emulator.

**Workflow restoration and hierarchical Back (v6.76.0).**

- Stitch, Collage, Device Mockup, video, and Web Capture now restore URI/order,
  layout/options, URL, and scrub/trim state across Activity recreation. Saved
  identity state is capped below 64 KiB and never contains bitmaps, jobs, or
  transient load failures.
- Document-based pickers retain read grants. Restored media is validated off the
  main thread; revoked items are pruned with a visible choose-again path, while
  invalid video sources retain Retry and Choose another after recreation.
- Gallery now restores bounded selection plus the exact viewer media identity,
  including the page reached by swiping. Reordered/deleted libraries resolve or
  fail closed without using a stale list index.
- Toolbar and system Back share one deepest-first reducer: dialog, viewer detail,
  viewer, selection, filter, album, then Gallery-to-Home. Robolectric round-trip,
  state-budget, reducer, routing, and headless API-37 rendered checks cover it.

**Gallery, index, and video recovery (v6.75.0).**

- Gallery now distinguishes loading, empty, partial-permission, failed, and ready
  content states while preserving the last good image, video, collection, note,
  or index result when another source fails.
- Added an in-Gallery local-index health card with eligible/indexed, pending,
  failure, and last-success data plus direct Retry and Rebuild recovery. Settings
  and scheduled rebuilds update the same state and fail visibly.
- Invalid video metadata and preview frames now have typed failure states with
  Retry and Choose another actions. Replaced previews are recycled safely.
- Gallery, index, and video failures emit content-free diagnostics. Focused unit,
  routing, and headless API-37 rendered failure-state checks cover recovery.

**Progressive permission recovery (v6.74.0).**

- Replaced the anonymous multi-permission callback and three unrelated pending
  flags with one typed initiating action. Monitor, delayed capture, Quick Crop,
  latest-image, Pin, Accessibility, and LAN-upload continuations resume at most
  once and only after their matching capability becomes available.
- First denial now keeps every unrelated editor/picker available and offers Try
  again; repeated denial routes to exact app, notification, overlay, or
  Accessibility settings instead of relaunching a dead prompt.
- Requests are purpose-timed: image access for monitoring/library actions, video
  access from Gallery, and notifications when capture/reminder behavior needs it.
  Gallery reflects runtime and app/channel notification blocking.
- Added policy, continuation-decision, settings-route, source-routing, and headless
  API-37 rendered tests for retryable and settings-required states.

**Enforced target-size exports (v6.73.0).**

- Replaced best-effort target compression with typed within-budget,
  cannot-meet, and encoder-failure outcomes. Save, Share, and Quick Crop publish
  only verified payloads and recheck the final size after metadata changes.
- Added an explicit resolution-reduction opt-in that preserves aspect ratio,
  never reduces either side below 320 px, and remains disabled by default.
- Bounded every compression attempt to the configured byte budget in memory.
  Native noisy JPEG/WebP, alpha PNG, impossible-budget, minimum-dimension,
  format-preservation, and failing/throwing encoder tests cover the contract.

**Bounded batch image intake (v6.72.0).**

- Routed Home/Gallery batch auto-crop and resize through one shared intake policy:
  50 items, 64 MiB encoded, 48 MP source, and 12 MP / 48 MiB decoded working caps.
- Every provider stream is byte-preflighted before bounds decode, reopened through
  a hard byte limiter, and power-of-two sampled before pixel allocation. Resize
  no-ops skip allocation after bounds; cancellation is rechecked before publication.
- Batch completion now distinguishes saved, skipped, oversized, unreadable, failed,
  and cancelled items. Native bitmap, huge-dimension, oversized generator, corrupt,
  throwing, stalled-stream, cancellation, and source-routing tests cover the path.

**Kotlin build-cache containment (v6.71.0).**

- Disabled reusable Gradle task-output, configuration, Kotlin build, and Kotlin
  incremental caches in trusted commands while the pinned stable Kotlin 2.3.21
  remains affected by CVE-2026-53914.
- Added a fail-early settings gate that rejects task-output, Kotlin build, and
  incremental cache opt-ins, rejects prerelease compiler versions, and permits cache
  opt-in only after a stable Kotlin 2.4.20+ is deliberately pinned.
- The official release path now depends on an auditable cache-security task. Safe
  and hostile command-line probes, source guards, full host tests, lint, and a
  clean signed release build cover the containment without disabling verified
  dependency downloads.

**Android 17 local-network compatibility (v6.70.0).**

- Raised compile/target SDK to API 37 and added Android 17's scoped Local network
  access permission for custom HTTP/WebDAV destinations only; public HTTPS and
  Imgur paths never prompt.
- Added strict endpoint/DNS/route classification, just-in-time permission handling,
  denial and revocation recovery, TLS/network/LAN failure typing, masked credentials,
  and Settings disclosure without weakening certificate validation.
- Kept local report saves available when LAN access is denied. Pure policy tests,
  source-routing guards, the full host suite/lint, and all 11 instrumentation tests
  pass on a headless API-37 emulator; Robolectric host emulation is pinned to API 36
  until it ships an API-37 runtime.

**Transactional image publication (v6.69.0).**

- Routed editor, Quick Crop, batch crop/resize, Stitch, Collage, device-frame,
  long/step capture, and video-frame saves through one typed MediaStore writer.
- Image publication now requires a row and stream, a successful encoder, non-empty
  bytes, and exactly one pending-row publish update; every post-insert failure
  attempts cleanup, including cancellation and fatal-error paths.
- Added a failure-injection matrix, readable bitmap round trip, and source-routing
  guard; video trimming now also rejects an unsuccessful publish update.

**Privacy-safe home widget (v6.68.0).**

- Added a resizable AMOLED home-screen widget with direct actions for the newest
  screenshot, the last Quick Crop, and Gallery. It never loads or displays media
  pixels, so Protect media previews cannot leak screenshot thumbnails.
- Shared the validated newest-screenshot resolver between the widget and Quick
  Crop, including readable-image checks and exclusion of SnapCrop/automated outputs.
- Extracted PDF report pagination/rendering from `MainActivity` and the raster
  crop/redaction/draw/filter pipeline from `CropActivity` into focused renderers,
  keeping activities responsible for workflow coordination and lifecycle state.
- Replaced duplicate review's all-pairs candidate scan with lossless logarithmic
  dimension buckets, luma buckets, and Hamming-safe hash-prefix bands. Complete-link
  group membership remains byte-for-byte compatible with the brute-force rules.

**Premium interface system and workflow-focused Home (v6.67.0).**

- Rebuilt Home around a calm monitoring hero, one dominant Edit image action,
  compact capture shortcuts, a structured tool grid, actionable Recent state,
  and a rectangular high-contrast bottom navigation surface. The generated
  visual direction is retained in `docs/mockups/snapcrop-home-v6.67.png`.
- Added complete light/dark Material color roles, semantic danger/success/favorite
  accents, a shared 4–12dp shape scale, and a readable typography hierarchy so
  default cards, dialogs, controls, and disabled states remain consistent.
- Reduced the compact editor app bar to close, undo, redo, preview, and More;
  history, transforms, and Help now live in an accessible overflow menu. Draw
  swatches have 44dp targets, explicit selected semantics, and check indicators.
- Settings section hierarchy and export summary are clearer, and each settings
  toggle is now a full-row Switch target. Gallery permission and empty states use
  consistent surfaces and offer direct clear-search/filter recovery actions.
- Separated destructive red from the pink favorite/mode accent across editor,
  Gallery, duplicate review, long-screenshot review, Stitch, and Settings.
- Localized editor mode guidance, allowed OCR status copy to wrap, and moved OCR
  actions into a scroll-safe 40dp action row instead of clipping on compact widths.
- Aligned the app and Room migration-test serialization runtime on 1.8.1, fixing
  the binary-incompatible 1.8.1 JSON / 1.7.3 core split found by connected tests.

**HDR-safe PNG export, per-image duplicate correction, and launcher shortcuts (v6.66.0).**

- On Android 16 the PNG codec carries HDR gain maps, so HDR screenshots saved,
  copied, or shared as PNG now stay PNG instead of being force-encoded to JPEG.
  Older releases still fall back to JPEG (the only pre-16 gain-map format). The
  save/clipboard/share export-format decision is now resolved in one place.
- Duplicate review's correction is now per-image: "Not a match" splits only the
  previewed screenshot out of its group and remembers it, keeping the remaining
  matches grouped instead of discarding the whole group.
- Added launcher long-press app shortcuts: Quick Crop on the newest screenshot,
  Long screenshot, toggle screenshot monitoring, and open Gallery.
- OCR detected-actions now also surface IBAN, IPv4, and MAC-address chips that copy
  the value on tap (alongside the existing call/email/open-link chips). Detection is
  fully on-device, and IPv4 addresses are no longer misread as phone numbers.

**Straighten export now matches the editor preview (v6.65.0).**

- The straighten angle is applied within the original image frame on export instead
  of expanding the canvas to the rotated bounding box. Exported straightened crops
  now land on the same pixels the preview showed, and a straighten-only save keeps
  the source dimensions rather than growing transparent corners.

**Reviewed near-duplicate screenshot cleanup (v6.65.0).**

- Gallery can analyze the complete screenshot library on demand using streamed
  SHA-256 plus bounded 9×8 perceptual hashes, then group exact or visually
  similar captures under strict, balanced, or loose thresholds.
- A source-resolution, one-image-at-a-time review surface supports keep-oldest,
  keep-newest, and safe manual removal. Nothing is selected or deleted
  automatically; confirmed removals use Android's existing recoverable trash.
  Trashing a group advances to the remaining groups instead of ending the
  review, and background analysis throttles its progress reporting so large
  libraries do not flood WorkManager with per-item writes.
- Room v6 retains resumable derived fingerprints across interrupted scans and
  content-keyed “not similar” corrections across copies or moves. Stale hashes
  prune only after a complete MediaStore pass, while index purge never removes
  the correction history.

**Physical PDF page layouts and deterministic pagination (v6.64.0).**

- Fixed the report renderer’s root geometry error: 1240×1754 values were being
  interpreted as PostScript points, yielding ~17×24-inch pages. Reports now use
  real A4 (595×842 pt) or US Letter (612×792 pt) dimensions.
- The report dialog adds A4, Letter, and validated 50–500 mm custom sizes,
  portrait/landscape orientation, 5–50 mm margins, and an exact aspect/margin
  preview. Invalid or collapsed content geometry disables creation.
- One immutable point-based layout now drives cover, image, searchable OCR,
  appendix, and measured footer placement. Cover notes paginate instead of
  clipping at 18 lines; over-wide tokens split; appendix headings keep their
  first line; bounded OCR truncation is labeled; metadata can continue before
  a full image page when necessary.
- The UI explicitly creates standard PDF only. PDF/A is not offered or claimed
  because Android’s renderer cannot control or validate the required XMP, ICC,
  and font-embedding conformance data.

**Local screenshot notes and one-time reminders (v6.63.0).**

- Gallery screenshots now support bounded multiline notes and one-time reminder
  presets or a custom date/time. Notes are searchable without enabling the
  derived screenshot index and are visibly marked in the grid and viewer.
- Room v5 stores notes/reminders by media URI plus original date in a user-owned
  table that survives index rebuild/purge and fail-closed migrations. Settings
  backups continue to exclude this local metadata by design.
- Token-checked unique WorkManager jobs survive reboot, replace on reschedule,
  cancel with media deletion, and use generic privacy-safe notifications. Taps
  reset Gallery filters and open only the exact original media identity; missing
  or URI-reused screenshots fail closed.
- Save-and-Replace moves notes and future reminders only after the new media row
  is durable, while denied trash requests leave metadata on the original.

**Versioned preference backup schema (v6.62.0).**

- Settings backups now export only registered, typed preferences with the app
  version, preference-schema version, and explicit legacy-key migration rules.
  Credentials, transient capture URIs, and unrecognized internal keys are never
  copied into the backup.
- Restore accepts v1 and v2 documents, migrates the former `secure_editor` key,
  reports restored/migrated/unknown/invalid counts, preserves unregistered local
  state, and atomically rejects malformed custom redaction patterns. Installed
  secure-preview preferences migrate at startup.

**Exact release-asset update metadata and Obtainium setup (v6.61.0).**

- The update checker now selects only the exact trusted
  `SnapCrop-<version>.apk` asset from GitHub's latest-release response and opens
  its `browser_download_url`; missing, renamed, untrusted, or non-uploaded assets
  fail safely to the release page.
- When GitHub supplies its `sha256:` asset digest, update dialogs display the
  validated 64-hex checksum. Malformed digests are ignored, and release notes
  remain bounded. Fixture tests cover exact selection and hostile URL/name data.
- README installation guidance documents the GitHub source, stable APK regex,
  prerelease posture, and provenance/checksum verification for Obtainium users.

**Non-destructive Cut Out / squeeze editing (v6.60.0).**

- The editor can remove up to 32 horizontal or vertical source bands, edit or
  remove them non-destructively, preview the compressed result, and render
  straight, dashed, or torn seams. Crop-aware retained-tile geometry handles
  multiple cuts on both axes without duplicating or inventing pixels.
- Save, Save Copy, clipboard, and share use one raster path that clips and shifts
  redactions and annotations with retained pixels. Project schema v5 restores
  bands and seam style; annotation SVG uses retained-tile clips/translations, and
  corrected OCR text sidecars omit blocks cropped out or intersecting a cut.
- Cut plans are bounded and fail closed for hostile geometry, incompatible
  perspective/free rotation, and all-content removal. Undo/redo, process-death
  drafts, compressed size estimates, searchable help, Ultra HDR-safe SDR fallback,
  and compact/wide editor entry points share the typed cut state.

**Searchable offline help and private recent workflows (v6.59.0).**

- Home, Settings, and the editor now open task-based offline help covering
  permission recovery, save/sidecar semantics, safe redaction, share privacy,
  Gallery organization, and delayed/long/step/static-web capture recovery.
  Search is deterministic and help routes users to the relevant Home, Gallery,
  Settings, picker, or editor control without executing an action automatically.
- Home can show up to six successful recent workflows. The dedicated local store
  persists only allowlisted workflow enum IDs, sanitizes corrupt/unknown values,
  clears immediately when disabled or after settings restore, and is excluded
  from settings exports; failed/cancelled pickers and captures are not recorded.

**Explicit source context through edit, projects, Gallery, and sharing (v6.58.0).**

- Whole-field shared/Web Capture URLs plus explicit referrer/package hints now use
  a bounded typed context model separate from filename, OCR, MediaStore, and
  Accessibility heuristics. Editor and Gallery users can add, edit, clear, or
  explicitly open a validated HTTP(S) source in an external browser.
- Room v4 stores user-owned context by media URI plus original date independently
  of the rebuildable screenshot index. Edited copies inherit context before any
  replace/trash request, confirmed app-mediated deletes clean it up, and project
  sidecar v4 round-trips it without changing source-image fingerprint behavior.
- The existing share privacy dialog can include the one common canonical source
  link, unchecked on every share. Crop, Gallery, notification, direct-share, and
  metadata/redaction branches add no link unless the user opts in.

**Structured gallery filters and stable media identity (v6.57.0).**

- Gallery filters now combine media type, creator/source folder, indexed category,
  relative date, orientation, minimum dimensions, URI-keyed favorite state, and
  canonical MIME format. Active counts, clear-all, stable sorting, process-restored
  state, and filtered-result collection seeding share the existing Gallery flow.
- The local index now uses URI primary identity so equal numeric image/video IDs
  cannot overwrite each other. Room v3 adds MIME, creator package, video dimensions,
  orientation, and facet indexes while preserving manual collections.
- Index rebuilds retain bounded replaceable OCR/category data, URI/date matching
  prevents recycled MediaStore entries from inheriting stale text, and reactive
  enrichment no longer reloads the album or clears valid selection.

**Precise multi-layer layout commands (v6.56.0).**

- The draw-layer panel now supports persistent multi-selection plus six crop-bound
  alignment commands, equal visual edge-gap distribution on either axis, and
  duplicate-with-offset. Hidden selected layers participate without changing
  visibility; distribute appears for three or more selections.
- Layout uses transformed source-coordinate visual bounds, including text, emoji,
  callout, magnifier, measure, arrowhead, neon, and pixel-operation geometry.
  Scale, rotation, points, styles, and control points remain unchanged.
- Each command is one undo snapshot. Duplicates are deep-copied immediately above
  their originals, selected as a group, and rejected atomically at project
  layer/point limits. Arrangement, pixel resolution, z-order, undo routing, raster/
  SVG routing, and project transform round trips have host coverage.

**Developer-secret and custom redaction patterns (v6.55.0).**

- Sensitive-text scans now recognize strict bearer/JWT, vendor token, private-key,
  credential URL, and high-signal secret-assignment shapes with entropy and
  placeholder guards. The release privacy corpus covers developer secrets across
  every OCR script and light/dark fixture.
- Settings can create, test, enable, edit, delete, copy-export, and import up to 20
  local custom patterns. RE2/J provides linear-time matching; expression/input,
  match-count, payload, and monotonic time limits are enforced, invalid restores
  are rejected atomically, and corrupt configured storage makes scans fail closed.
- Privacy OCR now maps matches to element geometry so multiple secrets in one code
  block remain separate editable regions. Direct shares expose numbered per-region
  checkboxes and a live preview without displaying matched values; editor, share,
  and Quick Crop routes all load the same local pattern set.

**Per-share metadata privacy preflight (v6.54.0).**

- Metadata-bearing image shares now show category names without values and offer
  Strip all, Keep safe technical fields, or Preserve detected supported fields.
  A choice is remembered only when explicitly requested, and source media is
  never mutated.
- Editor, Home/Gallery multi-share, and detected-screenshot notification actions
  use the same fail-closed policy. Sanitized images are freshly re-encoded; an
  inspection, encoding, or EXIF-copy failure aborts instead of leaking originals.
- Exact-tag tests cover all three policies and source immutability. Mixed video
  selections cannot claim unsupported sanitization, and notification sharing now
  opens the Activity directly instead of relying on a restricted trampoline.

**Reliable index scheduling and bounded background image cache (v6.53.0).**

- Upgraded WorkManager 2.10.1 to 2.11.2 for current blocked-network,
  SecurityException, and periodic-rescheduling fixes. Dependency verification
  metadata now covers the runtime and work-testing artifacts.
- Screenshot-index maintenance now uses one updateable periodic request, cancels
  immediately when indexing is disabled, removes the unrelated network constraint,
  completes permission loss safely, and limits other failures to two retries before
  a terminal failure.
- SnapCrop now owns Coil's singleton ImageLoader and trims its memory cache to 25%
  of the initial cap whenever the process is backgrounded. Work-testing and loader
  policy tests cover unique update/cancel, constraints, retry bounds, and cache setup.

**Single visible editor command path (v6.52.0).**

- Removed all editor key-event interception, including Ctrl+Z/Y/S, arrow-key crop
  nudges/zoom, and Enter preview toggling. Text fields and system focus navigation
  now receive keyboard input without a competing editor-level command path.
- Undo, redo, save, preview, crop nudge, and zoom remain available through visible
  controls and canvas accessibility custom actions. A source-policy regression test
  prevents shortcut handlers or key APIs from returning while asserting those actions.

**Consistent secure media previews (v6.51.0).**

- The existing preference is now named Protect media previews and applies to the
  editor, Gallery/home, stitch, collage, device-frame, video, long/web review,
  floating overlay, system Recents, and every other Activity that renders media.
- Activities reapply protection on resume. The floating overlay updates its live
  WindowManager flags when Settings changes, while an existing rich notification is
  rebuilt immediately without screenshot pixels and marked secret.
- The backward-compatible preference key/default remain unchanged. Policy and
  Robolectric Activity tests cover default, enabled/disabled window flags, overlay
  flags, and notification-thumbnail suppression.

**Bounded local operation journal (v6.50.0).**

- Settings now exposes a local-only diagnostic ring with enable/disable, view,
  copy, attach, and purge controls. Disabling purges immediately and prevents
  queued cross-process writes from recreating the journal.
- Up to 200 typed workflow results are retained for 14 days in an atomic 64 KiB
  store. The API cannot accept paths, URIs, image/OCR content, credentials,
  exception messages, or stack traces; only bounded timing, enums, and a sanitized
  exception class are persisted.
- Screenshot monitoring, Quick Crop, long/step capture, image export, media deletion,
  and background removal now emit one actionable terminal result. Storage failures
  are swallowed, UI writes use a bounded background queue, and corruption, retention,
  concurrency, privacy, clock, disable, and failure behavior have host tests.

**Isolated static web-page capture (v6.49.0).**

- Home and Android text sharing can open an explicit public HTTPS URL, fetch one
  bounded HTML document through public-IP-pinned TLS, and send a capped full-page
  PNG into the editor with the source URL retained as an explicit hint.
- Rendering runs in a dedicated process and offline WebView. Scripts, subresources,
  cookies, storage, file/content access, permissions, pop-ups, downloads, mixed
  content, authentication, and private/local destinations are blocked.
- Redirects, DNS, TLS, HTTP framing, body size, document type, render time,
  dimensions, cache retention, and stale jobs are bounded and covered by host tests;
  the Android instrumentation APK also assembles successfully.

**Named export presets (v6.48.0).**

- Settings can snapshot up to 20 named export configurations containing PNG/JPEG/WebP,
  quality or target KB, border, watermark, filename template, and MediaStore location;
  presets can be applied, updated, deleted, and assigned as editor or Quick Crop defaults.
- The editor exposes Current Settings plus every valid preset beside Save/Share. Save and
  Share resolve one immutable configuration at operation start; alpha crops and Ultra HDR
  compatibility continue to override incompatible format requests safely.
- Quick Crop honors its configured preset for decoration, compression budget, filename,
  and destination while app-rule format/album overrides retain precedence. Versioned,
  bounded JSON validation and rendering/compression tests cover corrupt or missing presets.

**Manual long-screenshot seam correction (v6.47.0).**

- Long-screenshot review now preserves the original lossless frames and a versioned
  stitch plan instead of only a flattened preview. Every detected join exposes the
  previous-frame trim and next-frame start, sliders, ±8 px nudges, and reset.
- Corrected plans are persisted atomically, rerender the overview without stale-job
  overwrites, and drive the final Gallery save; retry and discard clean the bounded
  cache session, while corrupt/missing sessions fail back to the last valid preview.
- Stitch rendering validates frame geometry, clamps edits to retain content, caps
  output pixels, supports mixed-width normalization, and has plan/store/cleanup tests.

**Editable OCR correction and export sidecars (v6.46.0).**

- OCR blocks can now be reviewed, corrected, deleted, and merged without changing
  image pixels. Corrections participate in undo/redo, process-death drafts, and v3
  editable project sidecars; older v1/v2 projects remain compatible.
- Searchable PDF reports require an explicit OCR review pass and use the reviewed
  blocks for both the invisible text layer and appendix instead of rerunning OCR.
- Added an opt-in UTF-8 `.txt` companion export with shared-storage privacy copy,
  bounded text/block validation, deliberate delete-all persistence, and stale-geometry
  invalidation after bitmap resize, rotate, flip, or background replacement.

**Ultra HDR edit/export preservation (v6.45.0).**

- Android 14+ gain-map inputs now retain HDR metadata through redaction, adjustments,
  annotations, perspective correction, crop/shape masks, gradients, borders, and
  watermarks. Gain-map geometry is transformed independently from the SDR base image,
  and metadata is cloned when Canvas-created bitmaps require explicit reattachment.
- Save, Share, and Clipboard choose JPEG for gain-map outputs so the HDR rendition is
  not silently flattened by a format without Ultra HDR gain-map encoding support.
- Added Android 14 Robolectric geometry/metadata coverage and a physical-codec JPEG
  round-trip instrumentation test. Device execution remains recorded as blocked while
  no ADB target is connected.

**Manual gallery collections (v6.44.0).**

- Added named manual collections alongside smart albums: create/rename/delete from the
  Gallery, add screenshots through URI-keyed multi-select, remove them without deleting
  media, and search/sort within a collection.
- Room schema v2 stores collections and URI-plus-original-date membership separately from
  the rebuildable screenshot index. The fail-closed v1→v2 migration preserves index rows,
  and index rebuild/purge operations cannot erase user collection membership.
- Collection creation with selected screenshots is transactional; normalized names are
  length/control-character checked and case-insensitively unique. Mixed selections report
  skipped videos/non-screenshots instead of silently accepting them.
- Gallery selection now uses canonical media URIs rather than numeric IDs, avoiding image
  and video namespace collisions. Added pure filtering/selection/name tests, migration DAO
  coverage, the exported v2 schema, and instrumentation-APK verification.

**Non-visual editor placement and redaction control (v6.43.0).**

- Canvas accessibility actions can now create a centered redaction or the current
  annotation without touch placement, including opening the text editor for Text.
- Selected redactions are announced with index, style, and enabled state and expose
  move, resize, Bar/Pixelate/Blur, toggle, and delete actions. Existing selected-layer
  move/resize/rotate controls remain available in the same canvas action flow.
- Localized the new redaction review panel labels and preserved deterministic category
  focus order. Physical TalkBack/Switch Access traversal QA remains recorded as a device
  blocker because no ADB target is connected.

**Editable redaction regions (v6.42.0).**

- Auto-text now preserves email, phone, payment-card, IP, address, MAC, and IBAN
  categories instead of flattening detections into anonymous rectangles; face and manual
  concealments use the same dedicated region model.
- Added per-category and per-region enable controls, Bar/Pixelate/Blur styles, bounded
  move/resize controls, explicit disabled-region previews, and deep undo/draft copies.
- Export validates enabled geometry, renders mixed styles once with opaque bars last, and
  conceals source pixels before rotation/perspective transforms to prevent mask drift.
- Project sidecars now write schema v2 typed redactions while safely migrating v1
  `pixelateRects`; IDs, bounds, categories, sources, styles, counts, and duplicates are
  validated on import. Annotation SVG companions carry enabled redaction metadata.

**Transform-safe pixel layers (v6.41.1).**

- Blur, fill, smart-erase, and heal layers now resolve their transformed image-space
  points before sampling or replacing pixels during export. Brush scaling follows the
  same transform as the editor preview, and transform controls are enabled for these
  layers instead of silently doing nothing.
- Added regression coverage for transformed points, scaled brush widths, single-use
  transforms, and every bitmap-mutating layer type.

**Safe inbound multi-image shares (v6.40.0).**

- Added `ACTION_SEND_MULTIPLE` plus stable-order extraction across stream extras,
  `ClipData`, and data URIs without silently truncating duplicates or overflow.
  Single images still open the editor; validated multi-image shares open an
  action chooser for batch crop, stitch, collage, or PDF report.
- Shared items are preflighted off the main thread for content URI access,
  declared/unknown-length 64 MiB limits, decodable image bounds, and a 48 MP
  pixel ceiling. Every rejected item remains visible by item number/reason; a
  lone valid survivor opens normally.
- Forwarded grants now include `ClipData`; Stitch/Collage reuse inbound images
  without launching a picker, Collage selects a fitting layout and explicitly
  disables sets above 25, and multi-item drag/drop uses the same router.
- Release provenance now declares the built APK and SBOM as task inputs, so a
  changed same-version artifact cannot reuse a stale stable copy.

**Theme-aware media surfaces (v6.39.0).**

- Editor, Gallery, crop recovery, stitch, collage, device-frame, and video roots
  now use the active Dark/Light/System background instead of hardcoded black.
  Image/video viewers retain a neutral black backdrop through dedicated media
  tokens with guaranteed contrasting placeholder text and controls.
- Light/System mode now updates status/navigation icon appearance at runtime and
  uses paired day/night launch resources. Adjusted light secondary/warning tokens
  meet WCAG normal-text contrast against their app surfaces.
- Added deterministic Dark/Light/System resolution, token-contrast, system-bar,
  and full-screen hardcoded-surface guards. Physical portrait/landscape,
  large-font, and live system-bar QA remains recorded as a device blocker.

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
- Added an opt-in "Protect media previews" setting that marks all media windows and
  floating previews `FLAG_SECURE`, hides them from Recents on Android 13+, and omits
  screenshot pixels from rich notifications.
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
  redaction, draw layers, and adjustments, with visible controls, accessibility
  actions, and mouse-wheel zoom support.
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
