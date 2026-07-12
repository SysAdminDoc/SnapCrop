package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CutoutBitmapRendererTest {
    @Test
    fun horizontalAndVerticalCutsRetainExactCartesianPixels() {
        val source = coordinateBitmap(4, 4)
        val plan = CutoutSqueeze.create(
            4,
            4,
            listOf(
                CutBand(CutAxis.HORIZONTAL, 1, 2),
                CutBand(CutAxis.VERTICAL, 2, 3),
            ),
        )

        val output = CutoutBitmapRenderer.render(source, plan, drawSeparators = false)

        assertEquals(3, output.width)
        assertEquals(3, output.height)
        val retainedX = listOf(0, 1, 3)
        val retainedY = listOf(0, 2, 3)
        retainedY.forEachIndexed { outputY, sourceY ->
            retainedX.forEachIndexed { outputX, sourceX ->
                assertEquals(source.getPixel(sourceX, sourceY), output.getPixel(outputX, outputY))
            }
        }
    }

    @Test
    fun cropLocalPlanIgnoresBandsOutsideCropAndTranslatesIntersectingBand() {
        val plan = CutoutSqueeze.createForCrop(
            sourceWidth = 100,
            sourceHeight = 200,
            cropLeft = 10,
            cropTop = 50,
            cropRight = 90,
            cropBottom = 150,
            bands = listOf(
                CutBand(CutAxis.HORIZONTAL, 0, 20),
                CutBand(CutAxis.HORIZONTAL, 70, 90),
                CutBand(CutAxis.VERTICAL, 80, 100),
            ),
        )

        assertEquals(70, plan.outputWidth)
        assertEquals(80, plan.outputHeight)
        assertEquals(listOf(CutBand(CutAxis.HORIZONTAL, 20, 40)), plan.horizontalBands)
        assertEquals(listOf(CutBand(CutAxis.VERTICAL, 70, 80)), plan.verticalBands)
    }

    @Test
    fun separatorStylesProduceVisibleDeterministicSeams() {
        CutSeparatorStyle.entries.forEach { style ->
            val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
            }
            val plan = CutoutSqueeze.create(
                64,
                64,
                listOf(CutBand(CutAxis.HORIZONTAL, 24, 40)),
                style,
            )

            val output = CutoutBitmapRenderer.render(source, plan)

            assertEquals(48, output.height)
            assertTrue((0 until output.width).any { x -> output.getPixel(x, 24) != Color.RED })
        }
    }

    @Test
    fun edgeCutsDoNotInventASeparatorAndNoBandsPreserveIdentity() {
        val source = coordinateBitmap(8, 8)
        val edgePlan = CutoutSqueeze.create(
            8,
            8,
            listOf(CutBand(CutAxis.HORIZONTAL, 0, 2)),
        )

        assertTrue(edgePlan.separators.isEmpty())
        assertSame(source, CutoutBitmapRenderer.render(source, CutoutSqueeze.create(8, 8, emptyList())))
    }

    private fun coordinateBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(x, y, Color.rgb(x * 30, y * 30, x + y))
                }
            }
        }
}
