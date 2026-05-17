# Security And Dependency Review

Research date: 2026-05-17

## Dependency Snapshot

| Area | Current | 2026-05-17 metadata/research observation | Recommendation |
|---|---|---|---|
| Android Gradle Plugin | 9.2.1 | Google Maven metadata showed latest stable 9.2.1 and latest alpha 9.3.0-alpha05. | Baseline upgraded in P0.2; monitor future stable releases. |
| Kotlin/Compose compiler plugin | 2.3.21 | Maven Central metadata showed latest stable 2.3.21 and RC 2.4.0-RC. | Baseline upgraded in P0.2; AGP built-in Kotlin replaces `kotlin-android`. |
| Compose BOM | 2026.05.00 | Google Maven metadata showed 2026.05.00. | Baseline upgraded in P0.2; keep visual/manual editor smoke checks. |
| core-ktx | 1.18.0 | Metadata showed 1.18.0. | Baseline upgraded in P0.2. |
| activity-compose | 1.13.0 | Metadata showed 1.13.0. | Baseline upgraded in P0.2; target-behavior audit remains separate. |
| navigation-compose | 2.9.8 | Metadata showed 2.9.8. | Baseline upgraded in P0.2. |
| lifecycle-runtime-ktx | 2.10.0 | Metadata showed 2.10.0. | Baseline upgraded in P0.2. |
| Coil Compose | 2.7.0 | Coil 2 metadata still showed 2.7.0; Coil 3 stable line exists at 3.4.0. | Stay on 2.7.0 unless a Coil 3 migration is planned/tested. |
| ML Kit object/text/face/barcode/translate/language-id | Current catalog versions | Metadata indicated current stable versions for inspected artifacts. | No urgent upgrade; focus on runtime error UX. |
| ML Kit subject segmentation/entity extraction | Beta artifacts | Metadata indicated current beta artifacts for inspected coordinates. | Treat as beta-risk features; add fallback/status UX. |

## CI/Supply Chain Gaps

P0.1 replaced the manual-only `assembleRelease` workflow with lint, tests,
debug assemble, release assemble, dependency review, and release SBOM artifact
generation.

Remaining recommendations:

- Release checklist that verifies APK signing, version sync, changelog,
  dependency snapshot, and smoke install.
- Clear handling of signing secrets in CI; do not rely on debug signing for
  public release artifacts.

## Manifest Risk Review

| Manifest item | Risk | Current rationale | Recommended action |
|---|---|---|---|
| `MANAGE_EXTERNAL_STORAGE` | High Play policy scrutiny | Removed in P0.3. Android 11+ deletion now uses scoped-storage confirmation. | Keep all-files access out unless a future feature truly qualifies under Play policy. |
| `SYSTEM_ALERT_WINDOW` | User trust and platform restriction risk | Optional instant editor launch after screenshot detection. | Notification fallback is now primary when background launch is blocked. |
| AccessibilityService | High user trust sensitivity | Long screenshot capture and scroll automation. | P0.3 added an in-app disclosure before opening Accessibility settings; keep scope limited to user-started long screenshots. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ declaration scrutiny | Screenshot monitoring service. | Subtype now names screenshot monitoring and persistent user controls. |
| `POST_NOTIFICATIONS` | Runtime permission and UX | Screenshot monitor/action notifications. | Keep degraded behavior documented when denied. |
| `android:allowBackup="false"` | User convenience tradeoff | App-data backup disabled in P0.3. | Keep disabled unless SnapCrop adds explicit export/import for non-sensitive preferences. |
| Exported `CropActivity` | External input surface | `ACTION_SEND image/*` editing entry point. | Validate incoming URIs/types and maintain decode failure handling. |
| Exported tile services | Binder-protected | Quick Settings tile services. | Acceptable with required permission; keep service actions narrow. |

## Privacy And Data Handling

README positions SnapCrop as no ads, no tracking, and no internet requirement.
Future network integrations must preserve that by being explicit, optional, and
off by default.

Recommended controls:

- Local-only default for OCR, entity extraction, translation, and profiles.
- Clear model-download messaging for ML Kit translation/subject segmentation.
- Index purge controls before adding persistent OCR/entity search.
- Permission matrix in README or `SECURITY.md`.
- Android app-data backup remains disabled because preferences can reveal local
  paths, favorites, automation toggles, and screenshot workflow habits.
- Sidecar privacy review before saving source URI/path, OCR text, or entity
  metadata.

## aCropalypse-Style Export Risk

Android CVE-2023-21036 showed why image editors must ensure cropped/redacted
exports do not retain stale original bytes. SnapCrop writes new MediaStore rows
and rasterizes edited bitmaps, but this should become a regression check.

Recommended test:

- Create a source image with recognizable secret bytes/content.
- Export cropped/redacted output.
- Verify output dimensions/content and ensure file bytes do not contain appended
  stale source data.

## ML Kit Runtime Risks

Potential runtime failure classes from ML Kit docs/issues:

- first-use model downloads,
- Play Services availability/version problems,
- beta subject segmentation behavior,
- emulator/device-specific initialization failures,
- translator model storage/network conditions.

Recommended controls:

- Centralized ML capability/status helper.
- User-visible first-run/download/progress/error states.
- Retry guidance for Play Services issues.
- Avoid silent "original bitmap returned" behavior where users expect a model
  transformation.

## Security Roadmap Items

1. Add export regression tests for stale-byte/redaction safety.
2. Review tracked `.idsig` artifacts and remove if not intentionally versioned.
3. Before implementing project sidecars or screenshot indexing, document
   source-URI/path, OCR text, and entity metadata privacy rules.
