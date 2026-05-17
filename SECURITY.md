# Security and Privacy

Updated: 2026-05-17

SnapCrop is local-first. Core screenshot detection, cropping, annotation,
redaction, gallery, stitch, collage, video-frame extraction, and export
workflows run on device. The app does not include ads, analytics SDKs, or a
required network export path. Optional network exports are off by default and
must be explicitly configured in Settings before a report dialog can upload.

## Reporting Security Issues

Please report security issues through GitHub private vulnerability reporting or
by opening a minimal issue that avoids public exploit details. Include the app
version, Android version, device model, and steps to reproduce.

## Permissions and Platform Access

| Permission or access | User-visible feature | Behavior without it | Policy note |
|---|---|---|---|
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` on Android 12 and lower | Find screenshots, open media, show gallery/video workflows, batch process user media. | Manual picker flows still work where Android grants a URI; background screenshot monitoring and gallery features are limited. | Media access is limited to local user media through Android media APIs. |
| `POST_NOTIFICATIONS` | Foreground screenshot monitor notification plus Edit, Share, and Quick Crop actions. | Monitoring cannot provide the same persistent status/actions and may be blocked by the platform. | Runtime permission on modern Android. |
| `SYSTEM_ALERT_WINDOW` | Optional instant editor launch after a screenshot on Android 12+. | SnapCrop still detects screenshots and shows notification actions; users can tap the notification to edit. | Optional special access; not required for core editing. |
| `INTERNET` | Optional user-configured network exports to HTTP, WebDAV/Nextcloud, or Imgur. | Local save, edit, report, and share workflows still work. | Network targets are disabled by default and require explicit user configuration before upload. |
| AccessibilityService | User-started Long Screenshot capture: screen capture, scroll gesture, and stitch while the user is preparing a long capture. | Long Screenshot is unavailable; manual crop, stitch, gallery, and editor workflows still work. | SnapCrop is not an accessibility tool. The app shows an in-app disclosure before opening Accessibility settings. |
| Foreground service `specialUse` | Persistent screenshot monitor that watches for new screenshots and exposes user controls. | Automatic detection is off; users can still manually pick or share images into SnapCrop. | Manifest subtype explains screenshot monitoring for Play review. |
| Quick Settings tile binding | Monitor, Long Screenshot, and Last Action tiles. | Tiles are unavailable; in-app buttons remain available. | Tile services are protected by Android's tile binding permission. |

SnapCrop does not request `MANAGE_EXTERNAL_STORAGE`. Source screenshot deletion
uses Android scoped-storage delete confirmation on Android 11+ instead of
all-files access.

## Backup Posture

`android:allowBackup` is disabled. SnapCrop stores settings such as export
format, save location, favorites, automation toggles, and last-action state in
local app preferences. Those values can reveal screenshot habits or local
storage paths, so the app does not allow Android Auto Backup or device-transfer
extraction of app data.

Exported screenshots, SVG sidecars, PDFs, and videos are written to shared media
locations chosen by the user. Android media backup behavior is controlled by the
user's device and gallery/cloud-photo apps, not by SnapCrop app-data backup.

## Local ML and Model Downloads

ML Kit features run through Google Play services. Translation and segmentation
models may be downloaded by Play services when first used, but SnapCrop does not
upload screenshots to its own servers. Features should show user-facing fallback
messages when model download, Play services, or device capability checks fail.

## Release Hygiene

- Release APKs must be signed from local/CI signing secrets, never with secrets
  committed to the repository.
- CI should run lint, unit tests, debug assemble, release assemble, dependency
  review, and CycloneDX SBOM generation.
- Public releases should include a changelog entry and a smoke install of the
  signed artifact.

## Source References

- Google Play all-files access policy:
  https://support.google.com/googleplay/android-developer/answer/10467955
- Google Play AccessibilityService policy:
  https://support.google.com/googleplay/android-developer/answer/10964491
- Android foreground service type guidance:
  https://developer.android.com/develop/background-work/services/fgs/service-types
- Android app backup attributes:
  https://developer.android.com/guide/topics/manifest/application-element
- Android Auto Backup rules:
  https://developer.android.com/identity/data/autobackup
