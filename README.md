# SnapCrop

Auto-crop screenshots instantly on Android. Detects screenshots in real-time, strips system bars using exact device dimensions, and shows a floating thumbnail for quick editing.

## Features

- **Floating thumbnail overlay** — When a screenshot is taken, a preview slides in at the bottom-left. Tap to edit, swipe to dismiss, or quick-save with one tap
- **Smart autocrop** — Multi-strategy: uniform border removal, system bar stripping (exact device pixel heights), and ML Kit AI fallback
- **Crop editor** — Draggable corner/edge handles with rule-of-thirds grid and dim overlay
- **Aspect ratio presets** — Free, 1:1, 4:3, 16:9 with constrained handle dragging
- **Rotate 90°** — Rotate in the editor before cropping
- **Preview toggle** — See the final cropped result before saving
- **Save Copy** — Save cropped version while keeping the original, or replace it
- **Instant share** — Share cropped screenshots directly to any app
- **AI crop** — ML Kit object detection for content-aware cropping
- **Boot auto-start** — Service resumes after device reboot
- **Haptic feedback** — Subtle vibration on screenshot detection
- **Manual mode** — Pick any image from gallery to crop
- **AMOLED dark theme** — Pure black with Catppuccin accent colors

## How It Works

1. Toggle on the Screenshot Monitor from the home screen
2. Take a screenshot — a floating thumbnail appears in the bottom-left corner
3. **Tap** the thumbnail to open the crop editor with auto-detected bounds
4. **Swipe** the thumbnail to dismiss, or tap the crop icon to quick-save
5. In the editor: adjust handles, choose aspect ratio, rotate, use Auto/AI crop
6. **Crop & Save** (replaces original) or **Copy** (keeps original) — done

The autocrop engine queries exact system bar heights from Android resources (`status_bar_height`, `navigation_bar_height`), so it works on all Android versions including 12+ where status bars are transparent.

## Crop Strategies

| Strategy | How It Works | Best For |
|---|---|---|
| **Border Scan** | Scans edges for uniform-color strips | Memes with borders, solid margins |
| **System Bar Strip** | Uses exact device bar pixel heights | Screenshots with status/nav bar |
| **AI (ML Kit)** | Object detection finds content bounding box | Complex layouts where borders aren't obvious |

## Requirements

- Android 10+ (API 29)
- Google Play Services (for ML Kit)
- Permissions: Media access, Overlay (for thumbnail), Notifications (for service)

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## License

MIT
