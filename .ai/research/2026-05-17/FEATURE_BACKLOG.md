# Feature Backlog

Research date: 2026-05-17

This is the raw harvested backlog before final prioritization. The scored
version is in `PRIORITIZATION_MATRIX.md`; the selected roadmap is in
`ROADMAP.md`.

## Foundation And Quality

- Add CI lint/test/build lanes.
- Add PR dependency review.
- Generate CycloneDX SBOM on release.
- Add release checklist and signed APK verification.
- Add unit tests for AutoCrop.
- Add unit tests for sensitive text regex/Luhn behavior.
- Add unit tests for filename templates and save-format helpers.
- Add unit tests for app-profile scoring thresholds.
- Add focused engine tests for SmartErase mask behavior.
- Create a small synthetic screenshot fixture set.
- Remove tracked `.idsig` artifacts if not intentionally preserved.
- Add `SECURITY.md`.
- Add `CONTRIBUTING.md`.
- Add privacy/permission matrix to README.
- Refresh dependencies in controlled groups.
- Add Renovate/Dependabot if desired after CI baseline exists.

## Security, Privacy, And Policy

- Audit `android:allowBackup="true"` and exclude sensitive preference keys if
  appropriate.
- Document all-files access and Play Console justification.
- Document foreground-service special-use reason.
- Add user-facing education for AccessibilityService long screenshot capture.
- Add fallback UX for no overlay/background launch permission.
- Add backup/export/import of user settings with redaction of sensitive paths.
- Add local index purge controls.
- Add explicit "network features disabled by default" policy if upload targets
  are introduced.
- Add safer delete/replace audit trail in recent crops.
- Add aCropalypse-style export regression check: edited exports must not append
  stale source bytes.

## Editor And Annotation

- Define `.snapcrop.json` sidecar schema.
- Reopen sidecar into editor.
- Version sidecar schema and migrate old sidecars.
- Extract editor state/model from `CropEditorScreen.kt`.
- Extract layer panel.
- Extract tool option panels.
- Extract canvas renderer.
- Extract export preview.
- Snap-to-grid and snap-to-element alignment.
- Text style presets.
- Speech-bubble/callout variants.
- Pattern fills for shape crops.
- Better draggable watermark editor.
- Annotation templates for bug report/tutorial/redaction.
- Keyboard shortcuts for wide screens/DeX.
- Mouse wheel zoom and pan controls.
- Tablet/foldable side-panel layout.
- Layer thumbnails and lock controls.
- Multi-select layers and group/ungroup.
- Reorderable layer drag handles.
- SVG import preview for SnapCrop-generated sidecars.

## Capture And Automation

- Long screenshot stitcher v2 with better overlap matching.
- Sticky header/footer reduction in long screenshots.
- More than five frames behind time/battery guard.
- Long screenshot preview before save.
- Better stuck-scroll detection.
- User-visible workflow recipes.
- User-trained app crop profiles.
- Profile import/export.
- Profile test-image debugger.
- Conditional action preview and audit note.
- Saved Quick Crop variants, not just last action.
- Optional Tasker/Automate style intents or deep links.
- Screen recording to GIF from selected frames.
- Timed screenshot sequence -> GIF.

## Gallery And Organization

- Persistent local OCR/entity/source index.
- Smart albums by OCR keyword/entity/app/source.
- Saved searches.
- Sensitive screenshot review album.
- Storage health cleanup for old non-favorite screenshots.
- Duplicate/similar screenshot finder.
- Reindex and purge controls.
- Favorite/tag backup and restore.
- Per-album cover/theme.
- Bulk rename by template with `%app%`.
- Sidecar-aware grouping: raster, SVG, project JSON stay together.

## ML And Intelligence

- Central ML capability/status layer.
- First-run ML model download progress.
- ML Kit Play Services error retry guidance.
- Sensitive text detection evaluation harness.
- Profile matching evaluation harness.
- Background removal model-state UX.
- OCR translation model manager.
- Offline OCR language pack status.
- Optional advanced erase backend prototype.
- OCR/entity summaries without LLM.
- Local screenshot explanation prototype after foundation work.
- Dataset license review for RICO, Screen2Words, Android in the Wild, UICrit.

## Export And Integrations

- PDF report builder with title, notes, timestamps, dimensions, OCR appendix.
- Share shortcuts for frequent destinations.
- Self-hosted HTTP upload endpoint.
- WebDAV/Nextcloud upload.
- Imgur anonymous upload.
- Export package ZIP with image, SVG, JSON sidecar, and metadata.
- Watermark presets per destination.
- Batch rename with profile/app/source tokens.
- Direct "copy redacted version" action.
- Export before/after comparison image.

## Documentation

- Keep `PROJECT_CONTEXT.md` current.
- Keep `CLAUDE.md` gotchas current after architecture changes.
- Move completed roadmap items to changelog/history.
- Add architecture diagram for capture/editor/export pipeline.
- Add permission decision tree.
- Add release process docs.
- Add ML model behavior docs.
- Add sidecar schema docs after implementation.
