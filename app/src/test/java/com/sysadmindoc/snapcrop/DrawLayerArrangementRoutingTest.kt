package com.sysadmindoc.snapcrop

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DrawLayerArrangementRoutingTest {
    @Test
    fun layoutCommandsShareUndoPreviewRasterSvgAndProjectTransformPaths() {
        val editor = source("CropEditorScreen.kt")
        val export = source("CropActivity.kt")
        val sidecar = source("SnapCropProjectSidecar.kt")

        assertTrue(editor.contains("fun arrangeDrawLayers"))
        assertTrue(editor.contains("DrawLayerArrangement.align"))
        assertTrue(editor.contains("DrawLayerArrangement.distribute"))
        assertTrue(editor.contains("DrawLayerArrangement.duplicate"))
        assertTrue(editor.substringAfter("fun arrangeDrawLayers").substringBefore("fun alignSelectedLayers").contains("pushUndo()"))
        assertTrue(export.contains("dp.transformMatrix()"))
        assertTrue(export.contains("<g transform=\\\"translate("))
        assertTrue(sidecar.contains("put(\"transOffsetX\""))
        assertTrue(sidecar.contains("transOffsetX = optDouble"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
