package com.sysadmindoc.snapcrop

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrawPathPixelOperationTest {
    @Test
    fun transformedForPixelOperation_mapsPointsAndBrushWidthOnce() {
        val source = DrawPath(
            points = listOf(PointF(10f, 20f), PointF(30f, 20f)),
            color = 0,
            strokeWidth = 6f,
            shapeType = "blur",
            transOffsetX = 5f,
            transOffsetY = -3f,
            transScale = 2f,
            transRotation = 90f
        )

        val transformed = source.transformedForPixelOperation()

        assertEquals(25f, transformed.points[0].x, 0.01f)
        assertEquals(-3f, transformed.points[0].y, 0.01f)
        assertEquals(25f, transformed.points[1].x, 0.01f)
        assertEquals(37f, transformed.points[1].y, 0.01f)
        assertEquals(12f, transformed.strokeWidth, 0.01f)
        assertFalse(transformed.hasTransform)
    }

    @Test
    fun pixelOperationClassification_coversEveryBitmapMutatingTool() {
        fun layer(shape: String) = DrawPath(listOf(PointF(1f, 1f)), 0, 1f, shapeType = shape)

        assertTrue(layer("fill").isPixelOperation())
        assertTrue(layer("blur").isPixelOperation())
        assertTrue(layer("smart_erase").isPixelOperation())
        assertTrue(layer("heal").isPixelOperation())
        assertFalse(layer("pen").isPixelOperation())
    }
}
