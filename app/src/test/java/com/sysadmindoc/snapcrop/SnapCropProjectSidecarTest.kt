package com.sysadmindoc.snapcrop

import android.graphics.PointF
import android.graphics.Rect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapCropProjectSidecarTest {
    private val validHash = "a".repeat(64)

    @Test
    fun encodeDecodePreservesEditableProjectState() {
        val adjustments = floatArrayOf(
            10f, 1.2f, 0.8f, 3f, -4f, 0.25f, 2f, 0.5f, 1.5f,
            -8f, 6f, 0.3f, 0.2f, 4f, 5f, -6f, 7f,
            100f, 200f, 500f, 200f, 500f, 800f, 100f, 800f
        )
        val project = SnapCropProject(
            sourceUri = "content://media/external/images/media/42",
            sourceSha256 = "a".repeat(64),
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
        assertEquals(1, decoded.redactions.size)
        assertRectEquals(project.pixelateRects.first(), decoded.redactions.first().bounds)
        assertEquals(RedactionStyle.PIXELATE, decoded.redactions.first().style)
        assertEquals(setOf(RedactionCategory.MANUAL), decoded.redactions.first().categories)
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
            sourceSha256 = "d".repeat(64),
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
              "source": { "width": 100, "height": 100 },
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

    @Test
    fun versionOnePixelateRectsMigrateToEditableRegions() {
        val decoded = SnapCropProjectSidecar.decode(
            """
            {"schema":"com.sysadmindoc.snapcrop.project","version":1,
             "source":{"width":100,"height":100},
             "crop":{"left":0,"top":0,"right":100,"bottom":100},
             "pixelateRects":[{"left":10,"top":12,"right":40,"bottom":44}]}
            """.trimIndent()
        )

        val region = decoded.redactions.single()
        assertRectEquals(Rect(10, 12, 40, 44), region.bounds)
        assertEquals(RedactionStyle.PIXELATE, region.style)
        assertEquals(setOf(RedactionCategory.MANUAL), region.categories)
        assertTrue(region.enabled)
    }

    @Test
    fun versionTwoPreservesTypedDisabledRedaction() {
        val source = basicProject().copy(
            redactions = listOf(
                RedactionRegion(
                    id = "red_test",
                    bounds = Rect(10, 12, 40, 44),
                    categories = setOf(RedactionCategory.EMAIL, RedactionCategory.PHONE),
                    source = RedactionSource.OCR_REGEX,
                    style = RedactionStyle.BLUR,
                    enabled = false
                )
            )
        )

        val region = SnapCropProjectSidecar.decode(SnapCropProjectSidecar.encode(source)).redactions.single()

        assertEquals("red_test", region.id)
        assertEquals(setOf(RedactionCategory.EMAIL, RedactionCategory.PHONE), region.categories)
        assertEquals(RedactionSource.OCR_REGEX, region.source)
        assertEquals(RedactionStyle.BLUR, region.style)
        assertFalse(region.enabled)
    }

    @Test
    fun duplicateRedactionIdsAreRejected() {
        val region = RedactionRegions.manual(Rect(10, 10, 30, 30))
        val json = JSONObject(SnapCropProjectSidecar.encode(basicProject().copy(redactions = listOf(region, region.copy()))))

        val result = SnapCropProjectSidecar.decode(
            json.toString().byteInputStream(),
            ProjectImportOrigin.EXTERNAL
        )

        assertEquals("redactions.id", (result as ProjectDecodeResult.Rejected).field)
    }

    @Test
    fun unknownRedactionStyleIsRejected() {
        val region = RedactionRegions.manual(Rect(10, 10, 30, 30))
        val json = JSONObject(SnapCropProjectSidecar.encode(basicProject().copy(redactions = listOf(region))))
        json.getJSONArray("redactions").getJSONObject(0).put("style", "TRANSPARENT")

        val result = SnapCropProjectSidecar.decode(
            json.toString().byteInputStream(),
            ProjectImportOrigin.EXTERNAL
        )

        assertTrue(result is ProjectDecodeResult.Rejected)
    }

    @Test
    fun externalProjectRequiresSourceFingerprint() {
        val json = """
            {"schema":"com.sysadmindoc.snapcrop.project","version":1,
             "source":{"width":100,"height":100},
             "crop":{"left":0,"top":0,"right":100,"bottom":100}}
        """.trimIndent()

        val result = SnapCropProjectSidecar.decode(
            json.byteInputStream(),
            ProjectImportOrigin.EXTERNAL
        )

        assertEquals(ProjectRejectReason.MISSING_FINGERPRINT, (result as ProjectDecodeResult.Rejected).reason)
    }

    @Test
    fun inputByteLimitRejectsBeforeJsonParsing() {
        val result = SnapCropProjectSidecar.decode(
            "123456".byteInputStream(),
            ProjectImportOrigin.EXTERNAL,
            ProjectImportLimits(maxJsonBytes = 5)
        )

        assertEquals(ProjectRejectReason.TOO_LARGE, (result as ProjectDecodeResult.Rejected).reason)
    }

    @Test
    fun collectionLimitsRejectExcessRedactions() {
        val project = basicProject().copy(
            redactions = listOf(
                RedactionRegions.manual(Rect(1, 1, 5, 5)),
                RedactionRegions.manual(Rect(6, 6, 10, 10))
            )
        )

        val result = SnapCropProjectSidecar.decode(
            SnapCropProjectSidecar.encode(project).byteInputStream(),
            ProjectImportOrigin.EXTERNAL,
            ProjectImportLimits(maxRedactions = 1)
        )

        assertEquals(ProjectRejectReason.INVALID_FIELD, (result as ProjectDecodeResult.Rejected).reason)
        assertEquals("redactions", result.field)
    }

    @Test
    fun sourceComparatorDistinguishesHashDimensionsAndDraftPolicy() {
        val project = basicProject()

        assertEquals(
            SourceMatch.MATCH,
            SnapCropProjectSidecar.compareSource(project, SourceIdentity(validHash, 100, 100), SourceVerificationPolicy.REQUIRE_FINGERPRINT)
        )
        assertEquals(
            SourceMatch.HASH_MISMATCH,
            SnapCropProjectSidecar.compareSource(project, SourceIdentity("b".repeat(64), 100, 100), SourceVerificationPolicy.REQUIRE_FINGERPRINT)
        )
        assertEquals(
            SourceMatch.DIMENSION_MISMATCH,
            SnapCropProjectSidecar.compareSource(project, SourceIdentity(validHash, 99, 100), SourceVerificationPolicy.REQUIRE_FINGERPRINT)
        )
        assertEquals(
            SourceMatch.MISSING_FINGERPRINT,
            SnapCropProjectSidecar.compareSource(project.copy(sourceSha256 = null), SourceIdentity(null, 100, 100), SourceVerificationPolicy.REQUIRE_FINGERPRINT)
        )
        assertEquals(
            SourceMatch.MATCH,
            SnapCropProjectSidecar.compareSource(project.copy(sourceSha256 = null), SourceIdentity(null, 100, 100), SourceVerificationPolicy.ALLOW_MISSING_FINGERPRINT)
        )
    }

    private fun basicProject() = SnapCropProject(
        sourceUri = "content://media/external/images/media/42",
        sourceSha256 = validHash,
        sourceWidth = 100,
        sourceHeight = 100,
        cropRect = Rect(0, 0, 100, 100),
        adjustments = floatArrayOf(0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        pixelateRects = emptyList(),
        drawLayers = emptyList(),
        exportFormat = "png",
        exportMimeType = "image/png",
        exportQuality = 100,
        exportSavePath = "Pictures/SnapCrop",
        deleteOriginal = false
    )

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left)
        assertEquals(expected.top, actual.top)
        assertEquals(expected.right, actual.right)
        assertEquals(expected.bottom, actual.bottom)
    }
}
