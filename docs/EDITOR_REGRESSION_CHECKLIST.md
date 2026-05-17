# Editor Regression Checklist

Use this checklist after editor refactors, gesture changes, rendering changes,
or save/export pipeline work. Record device/emulator, Android version, image
dimensions, and export format in the test notes.

## Build Gate

- Run `.\gradlew.bat :app:testDebugUnitTest`.
- Run `.\gradlew.bat :app:lintDebug :app:assembleDebug :app:assembleRelease`.
- When release dependency metadata matters, also run
  `.\gradlew.bat :app:cyclonedxDirectBom`.

## Crop And Gesture Basics

- Open a portrait screenshot and a landscape screenshot into the editor.
- Drag each corner handle and each edge handle.
- Drag the crop center.
- Verify crop bounds stay inside the bitmap and keep the minimum size.
- Toggle each aspect ratio, including Free, 1:1, 16:9, 9:16, and shape crops.
- Verify snap guides still apply at 0, 25, 33, 50, 67, 75, and 100 percent.
- Long-drag a handle and confirm precision drag still slows movement.
- Pinch zoom, pan while zoomed, and double-tap preview from crop mode.

## Draw And Layer Tools

- Create at least one layer with Pen, Arrow, Line, Rectangle, Circle, Text,
  Highlight, Callout, Emoji, Blur, Erase, Smart Erase, Fill, Spotlight,
  Magnifier, and Neon.
- Change stroke width and color before drawing.
- Use the eyedropper and recent color row.
- Toggle the layer panel with layers present and with no layers present.
- Move layers up and down; verify visual order matches export order.
- Hide a layer and verify it is skipped in canvas preview, raster export, and
  SVG export.
- Delete a layer and confirm undo restores it.

## Pixelate, OCR, And Adjust Modes

- Draw a pixelate rectangle and verify it appears in preview and saved output.
- Run Blur Faces and Auto Text on an image with detectable content.
- Run OCR, tap a text block, copy text, and open translation.
- Scan a barcode/QR screenshot and verify code actions still render.
- Change brightness, contrast, saturation, warmth, vignette, sharpen,
  highlights, shadows, tilt-shift, denoise, curves, filter, rotation, and
  gradient background.
- Reset adjustments and verify the output returns to the unadjusted state.

## Undo, Redo, And Project State

- Make one change in crop, pixelate, draw, OCR-derived crop, and adjust modes.
- Undo and redo across mode boundaries.
- Verify undo history labels and counts remain coherent.
- Save a `.snapcrop.json` sidecar, reopen it, modify the crop and layers, and
  export again.
- Confirm source-missing sidecars show the recoverable missing-source state.

## Save, Share, Clipboard, Delete

- Save using the default behavior and verify the label matches replacement or
  non-destructive mode.
- Save Copy and verify the source remains.
- Share and copy to clipboard from the editor.
- Delete with the confirmation dialog.
- Export PNG, JPEG, and WebP with metadata stripping on and off.
- Verify same-name SVG sidecars are written when visible vector annotations or
  redaction rectangles exist.

## Preview And Rendering

- Enter preview mode, drag the before/after divider, and double-tap to exit.
- Confirm grid overlay modes render correctly.
- Confirm crop shape masks and gradient backgrounds render in preview and
  saved output.
- Check a very small image and a very large screenshot for crashes, clipped
  handles, or invalid bitmap operations.
