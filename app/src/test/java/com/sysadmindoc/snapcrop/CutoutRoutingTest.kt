package com.sysadmindoc.snapcrop

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CutoutRoutingTest {
    @Test
    fun everyFlatteningAndRecoveryPathCarriesTypedCutoutState() {
        val editor = source("CropEditorScreen.kt")
        val activity = source("CropActivity.kt")
        val renderer = source("CropImageRenderer.kt")
        val project = source("SnapCropProjectSidecar.kt")
        val preview = source("EditorPreview.kt")

        assertTrue(editor.contains("CutoutEditState(cutBands, cutSeparatorStyle)"))
        assertTrue(activity.contains("cutout = d.cutout"))
        assertTrue(activity.contains("initialCutout.value = CutoutEditState(project.cutBands, project.cutSeparatorStyle)"))
        assertTrue(activity.contains("CropImageRenderer.render(bitmap, rect, redactions, drawPaths, adj, cutout)"))
        assertTrue(activity.contains("buildAnnotationSvg(rect, redactions, drawPaths, cutout)"))
        assertTrue(activity.contains("buildProjectSidecarJson(rect, redactions, drawPaths, adj, cutout"))
        assertTrue(project.contains("private const val VERSION = 5"))
        assertTrue(project.contains(".put(\"cutout\""))
        assertTrue(preview.contains("CutoutBitmapRenderer.render(cropped, plan)"))
    }

    @Test
    fun incompatibleTransformsFailClosedInsteadOfIgnoringCuts() {
        val renderer = source("CropImageRenderer.kt")
        val editor = source("CropEditorScreen.kt")

        assertTrue(renderer.contains("Cut Out cannot be combined with free rotation"))
        assertTrue(renderer.contains("Cut Out cannot be combined with perspective"))
        assertTrue(editor.contains("enabled = cutBands.isEmpty()"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
