package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScrollStitcherTest {
    @Test
    fun stitchSkipsStickyChromeAndOverlappedContent() {
        val frames = listOf(
            scrollFrame(contentStart = 0),
            scrollFrame(contentStart = 40),
            scrollFrame(contentStart = 80)
        )

        val stitched = ScrollStitcher.stitch(frames)

        try {
            assertTrue(stitched.height < frames.sumOf { it.height } - 120)
            assertTrue(stitched.height > frames.first().height)
            assertTrue(countSolidRows(stitched, HEADER_COLOR) <= HEADER_HEIGHT + 2)
            assertTrue(countSolidRows(stitched, FOOTER_COLOR) <= FOOTER_HEIGHT + 2)
        } finally {
            stitched.recycle()
            frames.forEach { it.recycle() }
        }
    }

    @Test
    fun looksSameDetectsStuckScrollFrames() {
        val first = scrollFrame(contentStart = 0)
        val same = scrollFrame(contentStart = 0)
        val different = scrollFrame(contentStart = 80)

        try {
            assertTrue(ScrollStitcher.looksSame(first, same))
            assertFalse(ScrollStitcher.looksSame(first, different))
        } finally {
            first.recycle()
            same.recycle()
            different.recycle()
        }
    }

    @Test
    fun manualJoinEditsChangeHeightExactlyAndResetToDetection() {
        val frames = listOf(
            scrollFrame(contentStart = 0),
            scrollFrame(contentStart = 40),
            scrollFrame(contentStart = 80)
        )
        val automatic = ScrollStitcher.createPlan(frames)
        val automaticBitmap = ScrollStitcher.stitch(frames, automatic)
        val next = automatic.frames[1]
        val adjustedTop = automatic.withCropTop(1, next.cropTop + 8)
        val previous = adjustedTop.frames[0]
        val adjusted = adjustedTop.withBottomTrim(0, previous.bottomTrim + 4)
        val adjustedBitmap = ScrollStitcher.stitch(frames, adjusted)

        try {
            val removedRows = (adjusted.frames[1].cropTop - automatic.frames[1].cropTop) +
                    (adjusted.frames[0].bottomTrim - automatic.frames[0].bottomTrim)
            assertEquals(automaticBitmap.height - removedRows, adjustedBitmap.height)
            assertEquals(automatic, adjusted.resetJoin(1))
            assertEquals(2, automatic.seamCount)
            assertTrue(automatic.outputJoinY(1) > 0)
        } finally {
            automaticBitmap.recycle()
            adjustedBitmap.recycle()
            frames.forEach(Bitmap::recycle)
        }
    }

    @Test
    fun manualJoinEditsClampAndMixedWidthsNormalize() {
        val frames = listOf(
            scrollFrame(contentStart = 0, width = 96),
            scrollFrame(contentStart = 40, width = 120)
        )
        val plan = ScrollStitcher.createPlan(frames)
        val clamped = plan.withCropTop(1, Int.MAX_VALUE).withBottomTrim(0, Int.MAX_VALUE)
        val stitched = ScrollStitcher.stitch(frames, clamped)

        try {
            assertEquals(96, plan.width)
            assertTrue(clamped.frames[1].cropTop + clamped.frames[1].bottomTrim < clamped.frames[1].frameHeight)
            assertTrue(clamped.frames[0].cropTop + clamped.frames[0].bottomTrim < clamped.frames[0].frameHeight)
            assertEquals(96, stitched.width)
            assertTrue(stitched.height >= 2)
        } finally {
            stitched.recycle()
            frames.forEach(Bitmap::recycle)
        }
    }

    private fun scrollFrame(
        contentStart: Int,
        width: Int = 96,
        viewportHeight: Int = 104
    ): Bitmap {
        val height = HEADER_HEIGHT + viewportHeight + FOOTER_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = when {
                    y < HEADER_HEIGHT -> HEADER_COLOR
                    y >= height - FOOTER_HEIGHT -> FOOTER_COLOR
                    else -> contentColor(contentStart + y - HEADER_HEIGHT, x)
                }
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    private fun contentColor(contentY: Int, x: Int): Int =
        Color.rgb(
            64 + ((contentY * 3 + x) % 120),
            64 + ((contentY * 5 + x * 2) % 120),
            64 + ((contentY * 7 + x * 3) % 120)
        )

    private fun countSolidRows(bitmap: Bitmap, color: Int): Int {
        var rows = 0
        for (y in 0 until bitmap.height) {
            var solid = true
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) != color) {
                    solid = false
                    break
                }
            }
            if (solid) rows++
        }
        return rows
    }

    private companion object {
        const val HEADER_HEIGHT = 16
        const val FOOTER_HEIGHT = 10
        val HEADER_COLOR: Int = Color.rgb(12, 18, 24)
        val FOOTER_COLOR: Int = Color.rgb(3, 3, 3)
    }
}
