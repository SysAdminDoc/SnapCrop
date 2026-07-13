package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropImageRendererPixelTest {
    @Test
    fun partialCropRetainsExactSourcePixels() {
        val source = coordinateBitmap(8, 6)

        val output = CropImageRenderer.render(
            source,
            Rect(2, 1, 7, 5),
            emptyList(),
            emptyList(),
            neutralAdjustments(),
        )

        assertEquals(5, output.width)
        assertEquals(4, output.height)
        for (y in 0 until output.height) {
            for (x in 0 until output.width) {
                assertEquals(source.getPixel(x + 2, y + 1), output.getPixel(x, y))
            }
        }
        output.recycle()
        source.recycle()
    }

    @Test
    fun noOpFullCropReturnsOwnedBitmapAndPreservesSource() {
        val pixels = IntArray(28 * 20) { index ->
            Color.rgb((index % 28) * 7, (index / 28) * 11, index % 251)
        }
        val source = Bitmap.createBitmap(pixels, 28, 20, Bitmap.Config.ARGB_8888)
        assertFalse(source.isMutable)

        val output = CropImageRenderer.render(
            source,
            Rect(0, 0, source.width, source.height),
            emptyList(),
            emptyList(),
            neutralAdjustments(),
        )

        assertNotSame(source, output)
        output.recycle()
        assertFalse(source.isRecycled)
        assertEquals(pixels[0], source.getPixel(0, 0))
        source.recycle()
    }

    @Test
    fun brightnessChangesStableInteriorChannels() {
        val source = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 80, 120))
        }
        val adjustments = neutralAdjustments().apply { this[0] = 20f }

        val output = CropImageRenderer.render(
            source,
            Rect(0, 0, 5, 5),
            emptyList(),
            emptyList(),
            adjustments,
        )

        assertColorNear(Color.rgb(60, 100, 140), output.getPixel(2, 2), tolerance = 1)
        output.recycle()
        source.recycle()
    }

    @Test
    fun annotationsRenderAndRedactionsWinFinalComposition() {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val annotation = DrawPath(
            points = listOf(PointF(4f, 4f), PointF(12f, 12f)),
            color = Color.RED,
            strokeWidth = 2f,
            shapeType = "rect",
            filled = true,
        )
        val redactions = listOf(
            redaction("blur", RedactionStyle.BLUR),
            redaction("solid", RedactionStyle.SOLID),
            redaction("pixelate", RedactionStyle.PIXELATE),
        )
        val adjustments = neutralAdjustments().apply { this[0] = 40f }

        val output = CropImageRenderer.render(
            source,
            Rect(0, 0, 16, 16),
            redactions,
            listOf(annotation),
            adjustments,
        )

        for (y in 6..9) {
            for (x in 6..9) assertEquals(Color.BLACK, output.getPixel(x, y))
        }
        assertColorNear(Color.WHITE, output.getPixel(2, 2), tolerance = 1)
        output.recycle()
        source.recycle()
    }

    @Test
    fun circleShapePreservesCenterAlphaAndClearsCornersWithoutRecyclingSource() {
        val source = Bitmap.createBitmap(
            IntArray(28 * 20) { Color.argb(96, 40, 80, 120) },
            28,
            20,
            Bitmap.Config.ARGB_8888,
        )
        val adjustments = neutralAdjustments().apply { this[3] = 1f }

        val output = CropImageRenderer.render(
            source,
            Rect(0, 0, 28, 20),
            emptyList(),
            emptyList(),
            adjustments,
        )

        assertEquals(20, output.width)
        assertEquals(20, output.height)
        assertEquals(0, Color.alpha(output.getPixel(0, 0)))
        assertColorNear(Color.argb(96, 40, 80, 120), output.getPixel(10, 10), tolerance = 1)
        assertFalse(source.isRecycled)
        output.recycle()
        source.recycle()
    }

    @Test
    fun cutoutRetainsExactPixelsAwayFromSeparatorSeams() {
        val source = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) setPixel(x, y, Color.rgb(x * 3, y * 4, (x + y) * 2))
            }
        }
        val cutout = CutoutEditState(
            bands = listOf(
                CutBand(CutAxis.HORIZONTAL, 20, 28),
                CutBand(CutAxis.VERTICAL, 28, 36),
            ),
        )

        val output = CropImageRenderer.render(
            source,
            Rect(4, 4, 60, 44),
            emptyList(),
            emptyList(),
            neutralAdjustments(),
            cutout,
        )

        assertEquals(48, output.width)
        assertEquals(32, output.height)
        assertEquals(source.getPixel(6, 6), output.getPixel(2, 2))
        assertEquals(source.getPixel(57, 6), output.getPixel(45, 2))
        assertEquals(source.getPixel(6, 41), output.getPixel(2, 29))
        assertEquals(source.getPixel(57, 41), output.getPixel(45, 29))
        output.recycle()
        source.recycle()
    }

    @Test
    fun perspectiveMapsSolidQuadrantsDeterministically() {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(
                        x,
                        y,
                        when {
                            x < 8 && y < 8 -> Color.RED
                            x >= 8 && y < 8 -> Color.GREEN
                            x < 8 -> Color.YELLOW
                            else -> Color.BLUE
                        },
                    )
                }
            }
        }
        val adjustments = neutralAdjustments().apply {
            floatArrayOf(2f, 2f, 13f, 3f, 12f, 14f, 3f, 13f).copyInto(this, 17)
        }

        val output = CropImageRenderer.render(
            source,
            Rect(0, 0, 16, 16),
            emptyList(),
            emptyList(),
            adjustments,
        )

        assertEquals(11, output.width)
        assertEquals(11, output.height)
        assertColorNear(Color.RED, output.getPixel(2, 2), 1)
        assertColorNear(Color.GREEN, output.getPixel(8, 2), 1)
        assertColorNear(Color.YELLOW, output.getPixel(2, 8), 1)
        assertColorNear(Color.BLUE, output.getPixel(8, 8), 1)
        output.recycle()
        source.recycle()
    }

    @Test
    fun rightAngleRotationMovesInteriorLandmarks() {
        val source = coordinateBitmap(9, 9)
        val adjustments = neutralAdjustments().apply { this[8] = 90f }

        val output = CropImageRenderer.render(
            source,
            Rect(0, 0, 9, 9),
            emptyList(),
            emptyList(),
            adjustments,
        )

        assertEquals(9, output.width)
        assertEquals(9, output.height)
        assertColorNear(source.getPixel(2, 1), output.getPixel(7, 2), 1)
        assertColorNear(source.getPixel(6, 6), output.getPixel(2, 6), 1)
        assertColorNear(source.getPixel(4, 4), output.getPixel(4, 4), 1)
        output.recycle()
        source.recycle()
    }

    @Test
    fun previewAndExportUseIdenticalComposedPixels() {
        val sourceForPreview = coordinateBitmap(20, 18)
        val sourceForExport = coordinateBitmap(20, 18)
        val rect = Rect(2, 2, 18, 16)
        val annotation = DrawPath(
            points = listOf(PointF(3f, 3f), PointF(8f, 8f)),
            color = Color.MAGENTA,
            strokeWidth = 2f,
            shapeType = "rect",
            filled = true,
        )
        val redactions = listOf(
            RedactionRegion(
                "preview-solid",
                Rect(10, 5, 15, 11),
                setOf(RedactionCategory.MANUAL),
                RedactionSource.MANUAL,
                RedactionStyle.SOLID,
            )
        )
        val adjustments = neutralAdjustments().apply { this[0] = 12f }

        val preview = renderEditorPreviewBitmap(
            sourceForPreview,
            rect,
            redactions,
            listOf(annotation),
            adjustments,
            CutoutEditState(),
        )
        val export = CropImageRenderer.render(
            sourceForExport,
            rect,
            redactions,
            listOf(annotation),
            adjustments,
        )

        assertEquals(export.width, preview.width)
        assertEquals(export.height, preview.height)
        assertEquals(export.pixelArray().toList(), preview.pixelArray().toList())
        preview.recycle()
        export.recycle()
        sourceForPreview.recycle()
        sourceForExport.recycle()
    }

    private fun neutralAdjustments(): FloatArray = FloatArray(25).apply {
        this[1] = 1f
        this[2] = 1f
    }

    private fun coordinateBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(x, y, Color.rgb(x * 20, y * 24, (x + y) * 10))
                }
            }
        }

    private fun redaction(id: String, style: RedactionStyle): RedactionRegion = RedactionRegion(
        id,
        Rect(4, 4, 12, 12),
        setOf(RedactionCategory.MANUAL),
        RedactionSource.MANUAL,
        style,
    )

    private fun Bitmap.pixelArray(): IntArray = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }

    private fun assertColorNear(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("alpha", kotlin.math.abs(Color.alpha(expected) - Color.alpha(actual)) <= tolerance)
        assertTrue("red", kotlin.math.abs(Color.red(expected) - Color.red(actual)) <= tolerance)
        assertTrue("green", kotlin.math.abs(Color.green(expected) - Color.green(actual)) <= tolerance)
        assertTrue("blue", kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) <= tolerance)
    }
}
