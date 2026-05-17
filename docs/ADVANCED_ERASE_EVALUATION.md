# Advanced Erase Backend Evaluation

Updated: 2026-05-17

SnapCrop's default Smart Erase backend remains the local mask-fill engine in
`SmartEraseEngine.kt`. No neural inpainting model is bundled in the APK.

## Activation Gates

An optional downloaded erase backend may be activated only after all gates pass:

- License is verified for mobile redistribution and end-user download.
- Model pack is downloaded only after explicit user opt-in.
- APK size does not increase from the model pack.
- Downloaded model pack is 120 MB or smaller unless a later product decision
  explicitly accepts the size.
- Median erase latency is 2.5 seconds or less on representative mid-range
  Android hardware.
- Battery and thermal behavior are acceptable during repeated screenshot edits.
- Private screenshot mask benchmark shows at least 10 percent quality lift over
  local Smart Erase.
- Failure always falls back to local Smart Erase without losing the edit.

## Candidate Status

| Candidate | Status | Notes |
|---|---|---|
| Local Smart Erase | Default | Local Kotlin bitmap mask fill; no model, no download, no network. |
| ONNX Runtime Android plus LaMa-style model | Research only | Runtime and model-pack shape are plausible, but license, model size, latency, battery, and benchmark lift are not verified. |

## Private Benchmark Plan

Use only synthetic fixtures or private screenshots approved for local testing.
Do not commit real private screenshots.

Metrics:

- Mask edge continuity around text, icons, and UI chrome.
- Visible blur/smear artifacts inside erased area.
- Preservation of nearby straight UI lines.
- Median and p95 latency by image size.
- Peak memory during erase.
- Failure rate and fallback correctness.

Fixture classes:

- Flat UI cards with text labels.
- Chat/message bubbles.
- Browser or app chrome with straight lines.
- Mixed light/dark UI backgrounds.
- Small icon/object removal on gradients.

The registry in `AdvancedEraseBackend.kt` keeps the research backend inactive
until these gates are satisfied.
