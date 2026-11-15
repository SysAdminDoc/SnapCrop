package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Issue #4 follow-up: a draft saved after a turn, mirror or resize is replayed on the untouched
 * source, so the steps it stores have to rebuild exactly the picture the editor showed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GeometryStepsTest {
    private val r = GeometryStep.RotateClockwise
    private val fh = GeometryStep.Flip(horizontal = true)
    private val fv = GeometryStep.Flip(horizontal = false)

    private fun appendAll(vararg steps: GeometryStep): List<GeometryStep> =
        steps.fold(emptyList()) { acc, step -> GeometrySteps.append(acc, step) }

    @Test
    fun turnsAndMirrorsFoldIntoTheShortestRun() {
        assertEquals(emptyList<GeometryStep>(), appendAll(r, r, r, r))
        assertEquals(emptyList<GeometryStep>(), appendAll(fh, fh))
        assertEquals(emptyList<GeometryStep>(), appendAll(r, fh, r, fh))
        assertEquals(2, appendAll(fh, fv).size)
        assertEquals(1, appendAll(r, r, r, fh, r).size)
        assertEquals(listOf(r, GeometryStep.Resize(10, 20), fh), appendAll(r, GeometryStep.Resize(10, 20), fh))
    }

    @Test
    fun aLongSessionStaysUnderTheProjectLimit() {
        val random = Random(4)
        var steps = emptyList<GeometryStep>()
        repeat(500) { steps = GeometrySteps.append(steps, listOf(r, fh, fv)[random.nextInt(3)]) }
        assertTrue(steps.size <= 3)
    }

    @Test
    fun finalSizeFollowsTurnsAndRefusesToEnlarge() {
        assertEquals(40 to 100, GeometrySteps.finalSize(100, 40, listOf(r, fh)))
        assertEquals(20 to 50, GeometrySteps.finalSize(100, 40, listOf(r, GeometryStep.Resize(20, 50))))
        assertNull(GeometrySteps.finalSize(100, 40, listOf(GeometryStep.Resize(20, 50))))
    }

    @Test
    fun replayingTheStoredStepsRebuildsTheEditedPixels() {
        val random = Random(7)
        repeat(12) { round ->
            val source = noise(9, 6, random)
            var live = source.copy(Bitmap.Config.ARGB_8888, false)
            var stored = emptyList<GeometryStep>()
            repeat(6) {
                val change = when (random.nextInt(4)) {
                    0 -> ImageGeometryChange.RotateClockwise(live.width, live.height)
                    1 -> ImageGeometryChange.Flip(live.width, live.height, horizontal = true)
                    2 -> ImageGeometryChange.Flip(live.width, live.height, horizontal = false)
                    else -> ImageGeometryChange.resizeFor(live.width, live.height, maxOf(live.width, live.height) - 1)
                        ?: ImageGeometryChange.RotateClockwise(live.width, live.height)
                }
                live = change.apply(live)
                stored = GeometrySteps.append(stored, change.step)
            }

            var replayed = source
            for (step in stored) replayed = step.changeFor(replayed.width, replayed.height).apply(replayed)

            assertEquals("round $round size", live.width to live.height, replayed.width to replayed.height)
            assertEquals(live.width to live.height, GeometrySteps.finalSize(source.width, source.height, stored))
            assertArrayEquals("round $round pixels $stored", pixels(live), pixels(replayed))
        }
    }

    private fun noise(width: Int, height: Int, random: Random): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(IntArray(width * height) { 0xFF000000.toInt() or random.nextInt(0x1000000) }, 0, width, 0, 0, width, height)
        }

    private fun pixels(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
}
