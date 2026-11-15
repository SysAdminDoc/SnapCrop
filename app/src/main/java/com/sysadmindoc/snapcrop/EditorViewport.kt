package com.sysadmindoc.snapcrop

/**
 * Zoom and pan of the editor canvas.
 *
 * A canvas point is drawn at `zoom * unzoomed + pan`, where `unzoomed` is its position in the
 * fitted (zoom 1) layout. [zoom] keeps whatever sits under the gesture's focal point under it, and
 * [clamp] keeps the fitted image on screen, centring an axis that's narrower than the canvas.
 */
internal data class EditorViewportState(val zoom: Float, val panX: Float, val panY: Float)

/** Canvas size and the fitted image rect inside it at zoom 1, all in canvas pixels. */
internal data class EditorViewportGeometry(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val imageLeft: Float,
    val imageTop: Float,
    val imageWidth: Float,
    val imageHeight: Float,
)

internal object EditorViewport {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 5f

    /** Zoom levels this close to 1 snap back to the fitted layout. */
    private const val SNAP_TO_FIT = 1.01f

    /**
     * Scales by [factor] and moves the content point that was under [prevFocusX]/[prevFocusY] to
     * [focusX]/[focusY]. Passing the same point twice zooms in place; a moving focus pans as well.
     */
    fun zoom(
        state: EditorViewportState,
        factor: Float,
        prevFocusX: Float,
        prevFocusY: Float,
        focusX: Float,
        focusY: Float,
        geometry: EditorViewportGeometry,
    ): EditorViewportState {
        if (!factor.isFinite() || factor <= 0f || state.zoom <= 0f) return clamp(state, geometry)
        var next = (state.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (next <= SNAP_TO_FIT) next = MIN_ZOOM
        val ratio = next / state.zoom
        return clamp(
            EditorViewportState(
                zoom = next,
                panX = focusX - ratio * (prevFocusX - state.panX),
                panY = focusY - ratio * (prevFocusY - state.panY),
            ),
            geometry,
        )
    }

    /** Moves the content by a drag delta, staying inside the bounds [clamp] allows. */
    fun pan(state: EditorViewportState, dx: Float, dy: Float, geometry: EditorViewportGeometry): EditorViewportState =
        clamp(state.copy(panX = state.panX + dx, panY = state.panY + dy), geometry)

    fun clamp(state: EditorViewportState, geometry: EditorViewportGeometry): EditorViewportState {
        val zoom = state.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (zoom <= MIN_ZOOM) return EditorViewportState(MIN_ZOOM, 0f, 0f)
        return EditorViewportState(
            zoom = zoom,
            panX = clampAxis(state.panX, zoom, geometry.canvasWidth, geometry.imageLeft, geometry.imageWidth),
            panY = clampAxis(state.panY, zoom, geometry.canvasHeight, geometry.imageTop, geometry.imageHeight),
        )
    }

    private fun clampAxis(pan: Float, zoom: Float, canvas: Float, start: Float, extent: Float): Float {
        if (canvas <= 0f || extent <= 0f || !pan.isFinite()) return if (pan.isFinite()) pan else 0f
        val scaledExtent = extent * zoom
        val scaledStart = start * zoom
        return if (scaledExtent <= canvas) {
            (canvas - scaledExtent) / 2f - scaledStart
        } else {
            pan.coerceIn(canvas - scaledExtent - scaledStart, -scaledStart)
        }
    }
}
