package com.sysadmindoc.snapcrop

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrBlocksTest {
    @Test
    fun textBlockDefensivelyCopiesConstructionReadsAndDeepCopies() {
        val sourceBounds = Rect(1, 2, 30, 40)
        val block = TextBlock("original", sourceBounds)

        sourceBounds.offset(100, 100)
        val exposedBounds = block.bounds
        exposedBounds.offset(50, 50)
        val copied = block.deepCopy()

        assertEquals(Rect(1, 2, 30, 40), block.bounds)
        assertEquals(block, copied)
        assertNotSame(block, copied)
        assertNotSame(block.bounds, copied.bounds)
    }

    @Test
    fun editChangesOnlySelectedTextAndReturnsDefensiveBlocks() {
        val input = blocks()

        val edited = OcrBlockEdits.edit(input, 1, "corrected second")

        assertEquals(listOf("first", "corrected second", "third"), edited.map { it.text })
        assertEquals(input[1].bounds, edited[1].bounds)
        input.indices.forEach { assertNotSame(input[it], edited[it]) }
    }

    @Test
    fun deleteRemovesOnlySelectedBlockAndCopiesSurvivors() {
        val input = blocks()

        val deleted = OcrBlockEdits.delete(input, 1)

        assertEquals(listOf("first", "third"), deleted.map { it.text })
        assertNotSame(input[0], deleted[0])
        assertNotSame(input[2], deleted[1])
    }

    @Test
    fun mergeUsesSourceOrderUnionBoundsAndFirstSelectedPosition() {
        val input = blocks()

        val merged = OcrBlockEdits.merge(input, setOf(2, 0))

        assertEquals(2, merged.size)
        assertEquals("first\nthird", merged[0].text)
        assertEquals(Rect(0, 0, 40, 50), merged[0].bounds)
        assertEquals("second", merged[1].text)
        assertEquals(listOf("first", "second", "third"), input.map { it.text })
        assertEquals(Rect(0, 0, 10, 10), input.first().bounds)
    }

    @Test
    fun operationsRejectInvalidEditsAndSelections() {
        val input = blocks()

        assertFails { OcrBlockEdits.edit(input, -1, "text") }
        assertFails { OcrBlockEdits.edit(input, 0, "   ") }
        assertFails { OcrBlockEdits.delete(input, 4) }
        assertFails { OcrBlockEdits.merge(input, setOf(1)) }
        assertFails { OcrBlockEdits.merge(input, setOf(0, 9)) }
    }

    private fun blocks() = listOf(
        TextBlock("first", Rect(0, 0, 10, 10)),
        TextBlock("second", Rect(12, 12, 20, 20)),
        TextBlock("third", Rect(30, 30, 40, 50))
    )

    private fun assertFails(action: () -> Unit) {
        val result = runCatching(action)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
