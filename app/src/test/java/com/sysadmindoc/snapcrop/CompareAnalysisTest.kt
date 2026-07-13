package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompareAnalysisTest {
    @Test
    fun identicalThresholdBoundaryAndHiddenTransparentRgbAreStable() {
        val black = Color.rgb(0, 0, 0)
        analysis(intArrayOf(black), intArrayOf(black)).useResult { result ->
            assertEquals(0, result.changedPixels)
            assertTrue(result.regions.isEmpty())
        }
        analysis(intArrayOf(black), intArrayOf(Color.rgb(12, 0, 0))).useResult { result ->
            assertEquals(0, result.changedPixels)
        }
        analysis(intArrayOf(black), intArrayOf(Color.rgb(13, 0, 0))).useResult { result ->
            assertEquals(1, result.changedPixels)
            assertEquals(100.0, result.changedPercent, 0.0)
        }
        analysis(
            intArrayOf(Color.argb(0, 255, 0, 0)),
            intArrayOf(Color.argb(0, 0, 255, 0)),
        ).useResult { result -> assertEquals(0, result.changedPixels) }
    }

    @Test
    fun topLeftMismatchCountsOnlyUnionAndUnmatchedEdge() {
        val before = ComparePixels(4, 3, IntArray(12) { Color.BLACK })
        val after = ComparePixels(2, 3, IntArray(6) { Color.BLACK })
        CompareAnalyzer.analyze(before, after, CompareAlignment.TOP_LEFT).useResult { result ->
            assertEquals(12, result.totalPixels)
            assertEquals(6, result.changedPixels)
            assertEquals(50.0, result.changedPercent, 0.0)
            assertEquals(1, result.totalRegionCount)
        }
    }

    @Test
    fun centeredCrossAspectExcludesNeverOccupiedCorners() {
        val before = ComparePixels(4, 2, IntArray(8) { Color.BLACK })
        val after = ComparePixels(2, 4, IntArray(8) { Color.BLACK })
        CompareAnalyzer.analyze(before, after, CompareAlignment.CENTER).useResult { result ->
            assertEquals(12, result.totalPixels)
            assertEquals(8, result.changedPixels)
        }
    }

    @Test
    fun independentDecoderSamplingUsesOneOriginalCoordinateScale() {
        val beforeBitmap = Bitmap.createBitmap(540, 1_200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        val afterBitmap = Bitmap.createBitmap(1_080, 1_800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        try {
            CompareAnalyzer.analyze(
                CompareSource(beforeBitmap, 1_080, 2_400),
                CompareSource(afterBitmap, 1_080, 1_800),
                CompareAlignment.TOP_LEFT,
            ).useResult { result ->
                assertEquals(540, result.width)
                assertEquals(1_200, result.height)
                assertEquals(25.0, result.changedPercent, 0.0)
            }
        } finally {
            beforeBitmap.recycle()
            afterBitmap.recycle()
        }
    }

    @Test
    fun fragmentedChangesAreBoundedButEveryChangedPixelRemainsRepresented() {
        val width = 400
        val height = 400
        val before = IntArray(width * height) { Color.BLACK }
        val after = before.copyOf()
        for (y in 4 until height step 20) {
            for (x in 4 until width step 20) after[y * width + x] = Color.WHITE
        }
        CompareAnalyzer.analyze(
            ComparePixels(width, height, before),
            ComparePixels(width, height, after),
            CompareAlignment.TOP_LEFT,
        ).useResult { result ->
            assertTrue(result.totalRegionCount > CompareAnalyzer.MAX_REGIONS)
            assertEquals(CompareAnalyzer.MAX_REGIONS, result.regions.size)
            assertEquals(result.changedPixels, result.regions.sumOf(CompareRegion::changedPixels))
        }
    }

    @Test
    fun analysisAndSwipeGeometryStayBounded() {
        val dimensions = CompareAnalyzer.analysisDimensions(8_000, 6_000, 4_000, 3_000)
        assertTrue(dimensions.width.toLong() * dimensions.height <= CompareAnalyzer.MAX_ANALYSIS_PIXELS)
        assertEquals(0f, compareSwipeFraction(30f, 30f, 200f), 0f)
        assertEquals(0.5f, compareSwipeFraction(130f, 30f, 200f), 0f)
        assertEquals(1f, compareSwipeFraction(260f, 30f, 200f), 0f)
    }

    private fun analysis(before: IntArray, after: IntArray): CompareAnalysisResult =
        CompareAnalyzer.analyze(
            ComparePixels(before.size, 1, before),
            ComparePixels(after.size, 1, after),
            CompareAlignment.TOP_LEFT,
        )

    private inline fun CompareAnalysisResult.useResult(block: (CompareAnalysisResult) -> Unit) {
        try {
            block(this)
        } finally {
            differenceBitmap.recycle()
        }
    }
}
