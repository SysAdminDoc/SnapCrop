# SnapCrop Release Checklist

Use this checklist before publishing a tagged SnapCrop build or attaching APKs
to a GitHub release.

## Version And Notes

- Confirm `versionName` and `versionCode` in `app/build.gradle.kts`.
- Add a matching `CHANGELOG.md` entry.
- Confirm `README.md`, `PROJECT_CONTEXT.md`, and `ROADMAP.md` do not describe
  stale feature or release state.

## Local Verification

- Run `.\gradlew.bat :app:lintDebug :app:testDebugUnitTest`.
- Run `.\gradlew.bat :app:assembleDebug :app:assembleRelease`.
- Run `.\gradlew.bat :app:cyclonedxDirectBom` and retain the generated SBOM
  from `app/build/reports/cyclonedx-direct/`.
- Install the release APK on a physical device when changing runtime behavior.
- Smoke test screenshot monitoring, manual pick, crop/save, Save Copy,
  share/export, and any feature changed in the release.

## Signing

- Ensure release credentials come from `keystore.properties` or the
  `SNAPCROP_KEYSTORE_*` environment variables.
- Verify the APK is signed with the intended release key before public release.
- Do not publish a debug-signed contributor fallback APK as an official release.

## Privacy, Permissions, And Policy

- Re-check user-facing justification for sensitive permissions changed in the
  release: All files access, overlay/background launch, AccessibilityService,
  notification permission, and foreground-service special use.
- Confirm the app still works when optional permissions are denied.
- Confirm no network integration is enabled by default.
- If Play Console declarations changed, record the exact use-case wording in the
  release notes or private release record.

## Artifacts

- Attach the signed release APK.
- Attach the CycloneDX SBOM JSON/XML.
- Attach or link release notes.
- Keep generated APKs, signing byproducts, and `.idsig` files out of source
  control unless there is an explicit archival reason.
