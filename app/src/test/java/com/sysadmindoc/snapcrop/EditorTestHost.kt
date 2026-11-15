package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme

/** White portrait test image, the shape of a phone screenshot. */
internal fun whiteTestBitmap(width: Int = 400, height: Int = 800): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }

/** Hosts [CropEditorScreen] with no-op callbacks except the ones a test passes in. */
internal fun ComposeContentTestRule.hostEditor(
    bitmap: Bitmap = whiteTestBitmap(),
    initialOcrBlocks: List<TextBlock> = emptyList(),
    onRotate: () -> Unit = {},
    onRootView: (android.view.View) -> Unit = {},
    reverseSearchEngines: List<ReverseSearchEngine> = emptyList(),
    onReverseSearch: (ReverseSearchEngine, Rect) -> Unit = { _, _ -> },
) {
    setContent {
        onRootView(LocalView.current)
        SnapCropTheme {
            CropEditorScreen(
                bitmap = bitmap,
                initialCropRect = Rect(0, 0, bitmap.width, bitmap.height),
                cropMethod = "",
                initialOcrBlocks = initialOcrBlocks,
                onSave = { _, _, _, _, _ -> },
                onSaveCopy = { _, _, _, _, _ -> },
                onShare = { _, _, _, _, _ -> },
                onCopyClipboard = { _, _, _, _, _ -> },
                onDiscard = {},
                onDelete = {},
                onAutoCrop = { Rect(0, 0, bitmap.width, bitmap.height) },
                onSmartCrop = {},
                onRemoveBg = { callback -> callback(null) },
                onResize = {},
                onRotate = onRotate,
                onFlipH = {},
                onFlipV = {},
                reverseSearchEngines = reverseSearchEngines,
                onReverseSearch = { engine, rect, _, _, _, _ -> onReverseSearch(engine, rect) },
                replaceOriginalOnSave = false,
            )
        }
    }
}
