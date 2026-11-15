package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme

/** White portrait test image, the shape of a phone screenshot. */
internal fun whiteTestBitmap(width: Int = 400, height: Int = 800): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }

/**
 * Hosts [CropEditorScreen] with no-op callbacks except the ones a test passes in. A test that swaps
 * the image the way the activity does passes [shownBitmap] and [cropRect] states and updates them
 * from the geometry callbacks.
 */
internal fun ComposeContentTestRule.hostEditor(
    bitmap: Bitmap = whiteTestBitmap(),
    shownBitmap: State<Bitmap>? = null,
    cropRect: State<Rect>? = null,
    initialRedactions: List<RedactionRegion> = emptyList(),
    initialDrawPaths: List<DrawPath> = emptyList(),
    initialOcrBlocks: List<TextBlock> = emptyList(),
    initialOcrBlocksState: State<List<TextBlock>>? = null,
    onRotate: (Rect) -> Unit = {},
    onFlipH: (Rect) -> Unit = {},
    onFlipV: (Rect) -> Unit = {},
    onResize: (Int, Rect) -> Unit = { _, _ -> },
    registerStateProvider: ((() -> EditorDraft)?) -> Unit = {},
    onRootView: (android.view.View) -> Unit = {},
    reverseSearchEngines: List<ReverseSearchEngine> = emptyList(),
    onReverseSearch: (ReverseSearchEngine, Rect) -> Unit = { _, _ -> },
) {
    setContent {
        onRootView(LocalView.current)
        val current = shownBitmap?.value ?: bitmap
        SnapCropTheme {
            CropEditorScreen(
                bitmap = current,
                initialCropRect = cropRect?.value ?: Rect(0, 0, current.width, current.height),
                cropMethod = "",
                initialRedactions = initialRedactions,
                initialDrawPaths = initialDrawPaths,
                initialOcrBlocks = initialOcrBlocksState?.value ?: initialOcrBlocks,
                onSave = { _, _, _, _, _ -> },
                onSaveCopy = { _, _, _, _, _ -> },
                onShare = { _, _, _, _, _ -> },
                onCopyClipboard = { _, _, _, _, _ -> },
                onDiscard = {},
                onDelete = {},
                onAutoCrop = { Rect(0, 0, current.width, current.height) },
                onSmartCrop = {},
                onRemoveBg = { callback -> callback(null) },
                onResize = onResize,
                onRotate = onRotate,
                onFlipH = onFlipH,
                onFlipV = onFlipV,
                reverseSearchEngines = reverseSearchEngines,
                onReverseSearch = { engine, rect, _, _, _, _ -> onReverseSearch(engine, rect) },
                registerStateProvider = registerStateProvider,
                replaceOriginalOnSave = false,
            )
        }
    }
}
