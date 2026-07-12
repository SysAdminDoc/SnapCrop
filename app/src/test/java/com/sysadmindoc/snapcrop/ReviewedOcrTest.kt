package com.sysadmindoc.snapcrop

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewedOcrTest {
    @Test
    fun sanitizeDropsDeletedTextTrimsCorrectionsAndCopiesGeometry() {
        val source = listOf(
            TextBlock("  corrected text  ", Rect(1, 2, 30, 40)),
            TextBlock("   ", Rect(40, 2, 60, 40)),
            TextBlock("日本語", Rect(1, 50, 30, 80))
        )

        val sanitized = ReviewedOcr.sanitize(source)

        assertEquals(listOf("corrected text", "日本語"), sanitized.map { it.text })
        assertEquals("corrected text\n日本語", ReviewedOcr.plainText(source))
        assertNotSame(source.first(), sanitized.first())
        assertNotSame(source.first().bounds, sanitized.first().bounds)
    }

    @Test
    fun plainTextIsBounded() {
        val longText = "x".repeat(ReviewedOcr.MAX_PLAIN_TEXT_CHARS + 20)

        assertEquals(
            ReviewedOcr.MAX_PLAIN_TEXT_CHARS,
            ReviewedOcr.plainText(listOf(TextBlock(longText, Rect(0, 0, 10, 10)))).length
        )
    }
}
