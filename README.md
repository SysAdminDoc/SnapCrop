# SnapCrop

Auto-crop screenshots instantly. Detects when you take a screenshot, opens it in an editor with intelligent edge detection, and lets you crop and save in one tap.

## Features

- **Automatic Screenshot Detection** — Background service monitors for new screenshots and opens the editor immediately
- **Smart Autocrop** — Detects and removes uniform-color borders (white, black, or any solid color margins)
- **Manual Crop Adjustment** — Draggable corner and edge handles with rule-of-thirds grid overlay
- **Quick Save** — Cropped images saved to `Pictures/SnapCrop/`, original screenshot cleaned up
- **Manual Mode** — Pick any image from gallery to crop
- **AMOLED Dark Theme** — Pure black with Catppuccin accent colors

## How It Works

1. Toggle on the Screenshot Monitor from the home screen
2. Take a screenshot using your device's normal screenshot method
3. SnapCrop automatically opens with the screenshot and detected crop bounds
4. Adjust the crop handles if needed, or tap **Auto** to re-detect
5. Tap **Crop & Save** — done

## Build

```bash
./gradlew assembleDebug
# or
./gradlew assembleRelease
```

Requires JDK 17 and Android SDK with API 35.

## Requirements

- Android 10+ (API 29)
- Storage permission (to read screenshots)
- Notification permission (Android 13+, for the monitoring service)
