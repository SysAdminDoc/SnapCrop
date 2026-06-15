package com.sysadmindoc.snapcrop

import android.graphics.PointF
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapCropProjectSidecarTest {
    @Test
    fun encodeDecodePreservesEditableProjectState() {
        val adjustments = floatArrayOf(
            10f, 1.2f, 0.8f, 3f, -4f, 0.25f, 2f, 0.5f, 1.5f,
            -8f, 6f, 0.3f, 0.2f, 4f, 5f, -6f, 7f,
            100f, 200f, 500f, 200f, 500f, 800f, 100f, 800f
        )
        val project = SnapCropProject(
            sourceUri = "content://media/external/images/media/42",
            sourceSha256 = "abc123",
            sourceWidth = 1440,
            sourceHeight = 3120,
            cropRect = Rect(10, 20, 500, 900),
            adjustments = adjustments,
            pixelateRects = listOf(Rect(50, 60, 120, 140)),
            drawLayers = listOf(
                DrawPath(
                    points = listOf(PointF(1f, 2f), PointF(3f, 4f)),
                    color = 0xFFFF0000.toInt(),
                    strokeWidth = 7f,
                    isArrow = true,
                    shapeType = "line",
                    text = null,
                    filled = false,
                    dashed = true,
                    visible = false
                )
            ),
            exportFormat = "png",
            exportMimeType = "image/png",
            exportQuality = 100,
            exportSavePath = "Pictures/SnapCrop",
            deleteOriginal = true
        )

        val json = SnapCropProjectSidecar.encode(project)
        val decoded = SnapCropProjectSidecar.decode(json)

        assertEquals(project.sourceUri, decoded.sourceUri)
        assertEquals(project.sourceSha256, decoded.sourceSha256)
        assertRectEquals(project.cropRect, decoded.cropRect)
        assertTrue(project.adjustments.contentEquals(decoded.adjustments))
        assertEquals(1, decoded.pixelateRects.size)
        assertRectEquals(project.pixelateRects.first(), decoded.pixelateRects.first())
        assertEquals(1, decoded.drawLayers.size)
        val layer = decoded.drawLayers.first()
        assertEquals(2, layer.points.size)
        assertEquals(1f, layer.points[0].x, 0.0001f)
        assertEquals(2f, layer.points[0].y, 0.0001f)
        assertEquals(3f, layer.points[1].x, 0.0001f)
        assertEquals(4f, layer.points[1].y, 0.0001f)
        assertEquals(0xFFFF0000.toInt(), layer.color)
        assertEquals(7f, layer.strokeWidth, 0.0001f)
        assertTrue(layer.isArrow)
        assertEquals("line", layer.shapeType)
        assertFalse(layer.filled)
        assertTrue(layer.dashed)
        assertFalse(layer.visible)
        assertEquals(project.exportFormat, decoded.exportFormat)
        assertEquals(project.exportMimeType, decoded.exportMimeType)
        assertEquals(project.exportQuality, decoded.exportQuality)
        assertEquals(project.exportSavePath, decoded.exportSavePath)
        assertTrue(decoded.deleteOriginal)
    }

    @Test
    fun encodeDecodePreservesLayerTransform() {
        val project = SnapCropProject(
            sourceUri = "content://media/external/images/media/7",
            sourceSha256 = "deadbeef",
            sourceWidth = 1080,
            sourceHeight = 1920,
            cropRect = Rect(0, 0, 1080, 1920),
            adjustments = floatArrayOf(0f, 1f, 1f),
            pixelateRects = emptyList(),
            drawLayers = listOf(
                DrawPath(
                    points = listOf(PointF(10f, 10f), PointF(40f, 40f)),
                    color = 0xFF00FF00.toInt(),
                    strokeWidth = 5f,
                    shapeType = "rect",
                    transOffsetX = 12.5f,
                    transOffsetY = -8.25f,
                    transScale = 1.75f,
                    transRotation = 30f
                )
            ),
            exportFormat = "png",
            exportMimeType = "image/png",
            exportQuality = 100,
            exportSavePath = "Pictures/SnapCrop",
            deleteOriginal = false
        )

        val decoded = SnapCropProjectSidecar.decode(SnapCropProjectSidecar.encode(project))
        val layer = decoded.drawLayers.single()

        assertTrue(layer.hasTransform)
        assertEquals(12.5f, layer.transOffsetX, 0.0001f)
        assertEquals(-8.25f, layer.transOffsetY, 0.0001f)
        assertEquals(1.75f, layer.transScale, 0.0001f)
        assertEquals(30f, layer.transRotation, 0.0001f)
    }

    @Test
    fun untransformedLayerHasIdentityDefaults() {
        val layer = DrawPath(
            points = listOf(PointF(1f, 1f)),
            color = 0xFFFFFFFF.toInt(),
            strokeWidth = 4f
        )
        assertFalse(layer.hasTransform)
        assertEquals(1f, layer.transScale, 0.0001f)
        assertEquals(0f, layer.transRotation, 0.0001f)
    }

    @Test
    fun projectDetectionAcceptsCustomAndJsonTypes() {
        assertTrue(SnapCropProjectSidecar.looksLikeProject(SnapCropProjectSidecar.MIME_TYPE, null))
        assertTrue(SnapCropProjectSidecar.looksLikeProject("application/json", "edit.snapcrop.json"))
        assertTrue(SnapCropProjectSidecar.looksLikeProject(null, "edit.snapcrop.json"))
        assertFalse(SnapCropProjectSidecar.looksLikeProject("image/png", "edit.png"))
    }

    @Test
    fun missingOptionalFieldsDecodeToSafeDefaults() {
        val decoded = SnapCropProjectSidecar.decode(
            """
            {
              "schema": "com.sysadmindoc.snapcrop.project",
              "version": 1,
              "source": {},
              "crop": { "left": 1, "top": 2, "right": 3, "bottom": 4 }
            }
            """.trimIndent()
        )

        assertRectEquals(Rect(1, 2, 3, 4), decoded.cropRect)
        assertNotNull(decoded.adjustments)
        assertEquals(25, decoded.adjustments.size)
        assertEquals(1f, decoded.adjustments[1], 0.0001f)
        assertEquals(1f, decoded.adjustments[2], 0.0001f)
        assertEquals(0f, decoded.adjustments[17], 0.0001f)
        assertTrue(decoded.pixelateRects.isEmpty())
        assertTrue(decoded.drawLayers.isEmpty())
        assertFalse(decoded.deleteOriginal)
    }

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left)
        assertEquals(expected.top, actual.top)
        assertEquals(expected.right, actual.right)
        assertEquals(expected.bottom, actual.bottom)
    }
}
