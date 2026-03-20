# SnapCrop

Auto-crop, annotate, and redact screenshots instantly on Android.

## Features

### Screenshot Detection
- **Automatic capture** — Background service detects screenshots via MediaStore ContentObserver and opens the editor immediately with autocrop applied
- **White flash + haptic** — Visual and tactile confirmation when a screenshot is detected
- **Boot auto-start** — Service resumes after device reboot

### Smart Autocrop
- **System bar stripping** — Uses exact device status/nav bar pixel heights (from Android resources). Works on all Android versions including 12+ where bars are transparent
- **Border detection** — Scans for uniform-color borders (black bars, meme margins) and removes them
- **AI crop** — ML Kit object detection for content-aware cropping when borders aren't obvious

### Crop Editor
- **Draggable handles** — Corner and edge handles with midpoint dots and rule-of-thirds grid
- **Aspect ratio presets** — Free, 1:1, 4:3, 16:9 with constrained dragging
- **Rotate 90° / Flip H** — Transform the image before cropping
- **Undo/Redo** — Full crop history stack (30 levels)
- **Double-tap preview** — Quick toggle to see the final result
- **Crop percentage** — Shows how much area is being removed

### Annotation Tools
- **Freehand draw** — Smooth anti-aliased strokes in 6 colors with adjustable width (2-20px)
- **Arrow tool** — Draw arrows with arrowheads for pointing at things
- **Pixelate/Redact** — Draw rectangles to mosaic regions, hiding sensitive info before sharing
- **Undo per tool** — Undo last stroke/region or clear all

### Save & Share
- **Crop & Save** — Replace the original screenshot with the cropped version
- **Save Copy** — Keep the original, save cropped version alongside
- **Share** — Send to any app directly from the editor
- **Copy to clipboard** — Paste into any app that accepts images
- **Delete** — Trash the original from the editor
- **JPEG/PNG** — Configurable save format with quality slider

### Home Screen
- **Recent crops gallery** — Horizontal scrollable thumbnails. Tap to re-edit, long-press to delete
- **Crop count** — Shows total screenshots cropped
- **Pick from gallery** — Manually select any image to crop/annotate
- **Share-to-SnapCrop** — Other apps can share images to SnapCrop via the system share menu

### Settings
- Delete original toggle
- Save format (PNG/JPEG) with quality slider
- Auto-start on boot
- Clear cache

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- AMOLED black theme (Catppuccin accent colors)
- ML Kit Object Detection (via Google Play Services)
- minSdk 29 (Android 10), targetSdk 35

## Requirements

- Android 10+
- Google Play Services (for ML Kit)
- Permissions: Media access, Display over apps (Android 12+), Notifications

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## License

MIT
