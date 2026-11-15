package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorViewportTest {
    // A 1080x2400 portrait screenshot fitted into a 1080x1800 canvas: 0.75x, centred horizontally.
    private val portrait = EditorViewportGeometry(
        canvasWidth = 1080f, canvasHeight = 1800f,
        imageLeft = 135f, imageTop = 0f, imageWidth = 810f, imageHeight = 1800f,
    )
    private val fitted = EditorViewportState(1f, 0f, 0f)

    private fun screenX(state: EditorViewportState, unzoomed: Float) = state.zoom * unzoomed + state.panX
    private fun screenY(state: EditorViewportState, unzoomed: Float) = state.zoom * unzoomed + state.panY

    @Test
    fun pinchKeepsTheContentUnderTheFingers() {
        val focusX = 540f; val focusY = 900f
        val zoomed = EditorViewport.zoom(fitted, 2f, focusX, focusY, focusX, focusY, portrait)

        assertEquals(2f, zoomed.zoom, 0f)
        assertEquals(focusX, screenX(zoomed, focusX), 0.01f)
        assertEquals(focusY, screenY(zoomed, focusY), 0.01f)

        // A second step from an already zoomed, panned state still pins the focal point.
        val unzoomedX = (600f - zoomed.panX) / zoomed.zoom
        val unzoomedY = (1000f - zoomed.panY) / zoomed.zoom
        val again = EditorViewport.zoom(zoomed, 1.25f, 600f, 1000f, 600f, 1000f, portrait)
        assertEquals(2.5f, again.zoom, 0.0001f)
        assertEquals(600f, screenX(again, unzoomedX), 0.01f)
        assertEquals(1000f, screenY(again, unzoomedY), 0.01f)
    }

    @Test
    fun movingFingersPanWhileZooming() {
        val zoomed = EditorViewport.zoom(fitted, 2f, 540f, 900f, 540f, 900f, portrait)
        val panned = EditorViewport.zoom(zoomed, 1f, 540f, 900f, 500f, 850f, portrait)
        assertEquals(zoomed.panX - 40f, panned.panX, 0.01f)
        assertEquals(zoomed.panY - 50f, panned.panY, 0.01f)
    }

    @Test
    fun zoomingNearACornerNeverPushesTheImageOffScreen() {
        // The issue #3 recording: zooming a centred portrait screenshot threw it out of view.
        var state = fitted
        repeat(12) { step ->
            val fx = if (step % 2 == 0) 30f else 1050f
            val fy = if (step % 3 == 0) 40f else 1760f
            state = EditorViewport.zoom(state, 1.35f, fx, fy, fx, fy, portrait)
            assertImageFillsOrCentres(state)
        }
        assertEquals(EditorViewport.MAX_ZOOM, state.zoom, 0f)
    }

    @Test
    fun narrowAxisStaysCentred() {
        // At 1.2x the 810 px wide image is still narrower than the 1080 px canvas.
        val state = EditorViewport.zoom(fitted, 1.2f, 0f, 0f, 0f, 0f, portrait)
        val left = screenX(state, portrait.imageLeft)
        val right = screenX(state, portrait.imageLeft + portrait.imageWidth)
        assertEquals(1080f - right, left, 0.01f)
    }

    @Test
    fun zoomingBackToFitResetsThePan() {
        val zoomed = EditorViewport.zoom(fitted, 3f, 100f, 200f, 100f, 200f, portrait)
        val back = EditorViewport.zoom(zoomed, 1f / 3f * 1.005f, 700f, 1500f, 700f, 1500f, portrait)
        assertEquals(EditorViewportState(1f, 0f, 0f), back)
    }

    @Test
    fun zoomIsCappedAtTheMaximum() {
        val state = EditorViewport.zoom(fitted, 100f, 540f, 900f, 540f, 900f, portrait)
        assertEquals(EditorViewport.MAX_ZOOM, state.zoom, 0f)
        assertImageFillsOrCentres(state)
    }

    @Test
    fun panStopsAtTheImageEdges() {
        val zoomed = EditorViewport.zoom(fitted, 2f, 540f, 900f, 540f, 900f, portrait)
        val farRight = EditorViewport.pan(zoomed, 10_000f, 10_000f, portrait)
        assertEquals(0f, screenX(farRight, portrait.imageLeft), 0.01f)
        assertEquals(0f, screenY(farRight, portrait.imageTop), 0.01f)

        val farLeft = EditorViewport.pan(zoomed, -10_000f, -10_000f, portrait)
        assertEquals(1080f, screenX(farLeft, portrait.imageLeft + portrait.imageWidth), 0.01f)
        assertEquals(1800f, screenY(farLeft, portrait.imageTop + portrait.imageHeight), 0.01f)
    }

    @Test
    fun panAtFitDoesNothing() {
        assertEquals(fitted, EditorViewport.pan(fitted, 300f, -200f, portrait))
    }

    @Test
    fun invalidFactorsLeaveTheViewAlone() {
        val zoomed = EditorViewport.zoom(fitted, 2f, 540f, 900f, 540f, 900f, portrait)
        assertEquals(zoomed, EditorViewport.zoom(zoomed, Float.NaN, 0f, 0f, 0f, 0f, portrait))
        assertEquals(zoomed, EditorViewport.zoom(zoomed, 0f, 0f, 0f, 0f, 0f, portrait))
        assertEquals(zoomed, EditorViewport.zoom(zoomed, Float.POSITIVE_INFINITY, 0f, 0f, 0f, 0f, portrait))
    }

    @Test
    fun unmeasuredCanvasDoesNotCrash() {
        val empty = EditorViewportGeometry(0f, 0f, 0f, 0f, 0f, 0f)
        val state = EditorViewport.zoom(fitted, 2f, 10f, 10f, 10f, 10f, empty)
        assertEquals(2f, state.zoom, 0f)
        assertTrue(state.panX.isFinite() && state.panY.isFinite())
    }

    private fun assertImageFillsOrCentres(state: EditorViewportState) {
        val left = screenX(state, portrait.imageLeft)
        val right = screenX(state, portrait.imageLeft + portrait.imageWidth)
        val top = screenY(state, portrait.imageTop)
        val bottom = screenY(state, portrait.imageTop + portrait.imageHeight)
        if (right - left <= portrait.canvasWidth) {
            assertEquals("centred horizontally at ${state.zoom}x", portrait.canvasWidth - right, left, 0.01f)
        } else {
            assertTrue("left edge inside at ${state.zoom}x: $left", left <= 0.01f)
            assertTrue("right edge inside at ${state.zoom}x: $right", right >= portrait.canvasWidth - 0.01f)
        }
        if (bottom - top <= portrait.canvasHeight) {
            assertEquals("centred vertically at ${state.zoom}x", portrait.canvasHeight - bottom, top, 0.01f)
        } else {
            assertTrue("top edge inside at ${state.zoom}x: $top", top <= 0.01f)
            assertTrue("bottom edge inside at ${state.zoom}x: $bottom", bottom >= portrait.canvasHeight - 0.01f)
        }
    }
}
