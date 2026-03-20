# SnapCrop

Auto-crop screenshots instantly. Detects when you take a screenshot, opens it in an editor with intelligent edge detection, and lets you crop and save in one tap.

## Features

- **Automatic Screenshot Detection** — Background service monitors for new screenshots and opens the editor immediately
- **Smart Autocrop** — Multi-strategy detection: uniform border removal, status/nav bar stripping, and ML Kit AI object detection fallback
- **Notification Quick Actions** — Quick Save (autocrop + save without opening editor), Edit, or Dismiss right from the notification
- **Manual Crop Adjustment** — Draggable corner and edge handles with rule-of-thirds grid overlay
- **Preview Mode** — Toggle to see what the final cropped image will look like before saving
- **Instant Share** — Share cropped images directly from the editor without saving first
- **AI Crop** — ML Kit Object Detection finds the dominant content when border detection isn't enough
- **Crop Method Indicator** — Shows whether the crop was detected via border scan, system bars, or AI
- **Manual Mode** — Pick any image from gallery to crop
- **AMOLED Dark Theme** — Pure black with Catppuccin accent colors

## How It Works

1. Toggle on the Screenshot Monitor from the home screen
2. Take a screenshot using your device's normal screenshot method
3. SnapCrop automatically opens with the screenshot and detected crop bounds
4. A notification also appears with **Quick Save** (one-tap autocrop) or **Edit** options
5. In the editor: adjust handles, tap **Auto** for border detection, or **AI** for ML Kit smart crop
6. Tap **Crop & Save** or **Share** — done

## Crop Strategies

| Strategy | How It Works | Best For |
|---|---|---|
| **Border Scan** | Scans edges inward for uniform-color strips | Memes with white/black borders, solid margins |
| **System Bar Strip** | Detects status bar (24dp) and nav bar (48dp) by density | Screenshots with captured system UI |
| **AI (ML Kit)** | Object detection finds dominant content bounding box | Complex screenshots where border scan finds nothing |

## Build

```bash
./gradlew assembleDebug
# or
./gradlew assembleRelease
```

Requires JDK 17 and Android SDK with API 35. ML Kit runs via Google Play Services (no additional setup needed).

## Requirements

- Android 10+ (API 29)
- Google Play Services (for ML Kit AI crop)
- Storage permission (to read screenshots)
- Notification permission (Android 13+, for the monitoring service)
