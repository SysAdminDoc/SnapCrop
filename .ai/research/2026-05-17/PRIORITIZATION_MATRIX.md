# Prioritization Matrix

Research date: 2026-05-17

Scoring: 1 low, 5 high. Priority is based on impact, confidence, risk
reduction, fit with SnapCrop's philosophy, and implementation leverage.

Continuation status: roadmap items 1 through 7 were implemented on
2026-05-17. The remaining highest-priority open item is User-visible app
profile/rules system.

| Candidate | Impact | Confidence | Risk reduction | Fit | Effort | Tier | Rationale |
|---|---:|---:|---:|---:|---:|---|---|
| CI lint/test/build lane | 5 | 5 | 5 | 5 | 3 | P0 | The repo has broad surface and no discovered tests. This reduces regression risk before more feature work. |
| Dependency baseline refresh | 4 | 4 | 4 | 4 | 3 | P0 | Android/Kotlin/Compose baseline is behind 2026-05-17 stable metadata. Needs controlled upgrades. |
| Permission/privacy/policy hardening | 5 | 5 | 5 | 5 | 3 | P0 | Sensitive Android permissions and Play policy exposure are central to trust and distribution. |
| Non-destructive project sidecars | 5 | 4 | 4 | 5 | 4 | P0 | SVG sidecars are now present but not re-editable. This is a core screenshot-editor differentiator. |
| Editor modularization | 4 | 5 | 4 | 5 | 4 | P0 | `CropEditorScreen.kt` is too large to keep expanding safely. |
| Long screenshot stitcher v2 | 4 | 4 | 3 | 5 | 4 | P1 | Capture reliability is a screenshot-specific differentiator and current stitcher is basic. |
| Persistent OCR/entity/source index | 5 | 4 | 3 | 5 | 4 | P1 | Builds on existing ML and gallery features; enables high-value screenshot library workflows. |
| User-visible app profile/rules system | 4 | 4 | 3 | 5 | 4 | P1 | Existing Reddit/X automation should become configurable rather than code-only. |
| Tablet/foldable/DeX layout | 3 | 4 | 2 | 4 | 3 | P1 | Improves dense editor ergonomics and precision work. |
| PDF report/export workflow | 4 | 4 | 2 | 4 | 3 | P1 | Uses existing PDF, annotations, OCR, and gallery capabilities to create a clear workflow product. |
| ML capability/status UX | 3 | 4 | 4 | 5 | 3 | P2 | Prevents silent ML failures and supports subject segmentation/translation reliability. |
| Optional advanced erase model | 3 | 3 | 2 | 3 | 5 | P2 | Potential quality improvement, but large models conflict with app-size/local-default constraints. |
| Dataset-backed evaluation harness | 3 | 4 | 3 | 4 | 3 | P2 | Useful after test foundation; public dataset license review needed. |
| Screenshot explanation/local LLM | 3 | 2 | 1 | 3 | 5 | P2 | Interesting but premature without index/eval/privacy foundation. |
| Upload integrations | 3 | 4 | 2 | 3 | 3 | P1 | Valuable but must remain opt-in because README positions SnapCrop as no-internet/local-first. |
| GIF/animated export | 2 | 3 | 1 | 3 | 3 | P2 | Nice workflow extension but below trust/reliability work. |
| Watermark presets | 2 | 4 | 1 | 3 | 2 | P2 | Easy but not strategic compared with sidecars/index/CI. |
| Speech-bubble tool | 2 | 4 | 1 | 3 | 2 | P2 | Useful annotation polish, but editor should be modularized first. |
| Smart duplicate/similar finder | 3 | 3 | 2 | 4 | 3 | P2 | Useful gallery cleanup feature after local index exists. |

## Tier Definitions

- P0: Do before or alongside the next major feature. These reduce future
  regression, privacy, distribution, or architecture risk.
- P1: Next product differentiators once the foundation is safer.
- P2: Research, polish, or optional power-user work.

## Selected Roadmap

The root `ROADMAP.md` uses this scoring to select:

1. CI/release-quality verification.
2. Dependency upgrades.
3. Permission/privacy/policy hardening.
4. Non-destructive project sidecars.
5. Editor modularization.
6. Long screenshot stitcher v2.
7. Persistent screenshot intelligence index.
8. User-visible profiles/rules.
9. Adaptive editor layout.
10. Export/report workflows.
11. ML model-state robustness.
12. Optional advanced erase backend.
13. Dataset-backed evaluation.
14. Screenshot summaries/local explanation research.

Implementation status as of the 2026-05-17 autonomous continuation: items 1
through 8 are implemented and verified at least through focused Gradle
compile/unit-test gates, with full-gate results recorded per batch in
`CHANGESET_SUMMARY.md`.
