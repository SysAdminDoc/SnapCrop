# Competitor Matrix

Research date: 2026-05-17

GitHub metadata is a point-in-time snapshot from `gh repo view` on
2026-05-17. Treat stars/releases/activity as freshness indicators, not product
truth.

## Direct And Adjacent Products

| Product | Type | 2026-05-17 activity signal | Notable features | SnapCrop lesson |
|---|---|---:|---|---|
| ImageToolbox | Android OSS app | 12,871 stars, latest release 3.9.0, updated 2026-05-17 | Broad image manipulation, crop, draw, filters, OCR, conversion, batch tools | Do not compete by generic breadth alone. Win on screenshot automation, trust, and Android system workflow. |
| ImageToolboxLite | Android OSS app | 36 stars, updated 2026-03-05 | Lightweight variant | Confirms appetite for smaller focused tools. Preserve SnapCrop's local-first focused identity. |
| ScreenshotTile | Android OSS app | 356 stars, latest release v2.19.0, updated 2026-05-15 | Quick Settings screenshot capture, AccessibilityService patterns | Useful reference for capture permission education and service reliability. |
| PhotoEditor | Android library | 4,465 stars, latest release v3.1.0, updated 2026-05-17 | Drawing, text, stickers, filters as reusable library | Editor extraction could make SnapCrop easier to maintain. |
| Satty | Desktop screenshot annotation | 2,125 stars, latest release v0.20.1, updated 2026-05-16 | Modern annotation, focused tool UI, fullscreen crop/markup | Cascading/contextual tool panels and crisp annotation UX are relevant to SnapCrop's dense editor. |
| ksnip | Desktop screenshot annotation | 3,211 stars, latest release v1.10.1, updated 2026-05-16 | Capture, annotate, obfuscate, upload, plugin-like annotation engine | Reinforces re-editable annotation model and upload/report workflows. |
| kImageAnnotator | Desktop annotation library | 92 stars, latest release v0.7.2, updated 2026-05-04 | Extracted annotation engine used by host apps | Strong architectural precedent for splitting editor model/rendering from UI. |
| Flameshot | Desktop screenshot annotation | 29,911 stars, latest release v13.3.0, updated 2026-05-17 | Fast capture toolbar, annotation, upload, config | Highlights the value of immediate post-capture tool access and concise annotation controls. |
| ShareX | Desktop capture automation | 37,560 stars, latest release v20.1.0, updated 2026-05-17 | Capture -> action -> upload workflows, OCR, GIF, destinations | SnapCrop's Quick Crop/last-action features should evolve into user-visible recipes. |
| Shottr | macOS commercial | Active product site | OCR, scrolling screenshots, precise annotation, pixel tools | Screenshot-first precision and OCR/search are important differentiators. |
| CleanShot X | macOS commercial | Active product site | Capture, annotate, recording, cloud/share links | Shows demand for polished export/share/report flows. SnapCrop must keep network sharing optional. |
| Greenshot | Desktop OSS/free | Active product site | Capture, annotation, obfuscation, export destinations | Validates redaction-first annotation and export destinations. |
| Pixel Screenshots | OEM/Google app | Official support source | Screenshot library/search concepts | Local screenshot intelligence/indexing is a product direction to watch. |
| Samsung screenshot tools | OEM feature | Official support source | Scroll capture, toolbar, edit/share | Long screenshot and immediate edit/share are baseline OEM expectations. |

## Feature Pattern Harvest

| Pattern | Sources | Current SnapCrop state | Opportunity |
|---|---|---|---|
| Capture -> workflow recipes | ShareX, CleanShot X, ScreenshotTile | Quick Crop, last action, conditional auto-actions exist | Generalize to user-visible recipes with safe previews. |
| Re-editable annotation model | kImageAnnotator, ksnip, PhotoEditor | SVG sidecar export exists; no project sidecar | Add `.snapcrop.json` sidecars and reopen support. |
| Fast contextual annotation UI | Flameshot, Satty, Shottr | Dense two-row editor and many tools | Extract panels and adapt layout for wide screens. |
| Upload/share destinations | ShareX, ksnip, Greenshot, CleanShot X | Android share and PDF export exist | Add opt-in Imgur/WebDAV/self-hosted workflows while preserving local-first default. |
| Screenshot library/search | Pixel Screenshots, ImageToolbox, OEM galleries | Heuristic smart albums only | Build local OCR/entity/app index and saved searches. |
| Long/scroll capture | Samsung, Shottr, ScreenshotTile | Implemented, basic stitcher | Improve overlap matching, sticky-header handling, retry/preview. |
| Obfuscation/redaction | Greenshot, ksnip, Flameshot | Strong redaction and sensitive text detection | Add evaluation harness and confidence/reporting UX. |
| Report/package export | CleanShot X, ShareX | PDF export exists | Build incident/bug report bundles with annotations and OCR appendix. |

## Positioning Conclusion

SnapCrop should not attempt to become ImageToolbox. It already has enough broad
editing surface. The defensible product lane is Android screenshot operations:
capture, crop, redact, annotate, organize, recover, and export with clear local
privacy guarantees.
