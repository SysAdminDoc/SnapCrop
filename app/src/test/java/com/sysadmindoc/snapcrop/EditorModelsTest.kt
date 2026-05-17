package com.sysadmindoc.snapcrop

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorModelsTest {
    @Test
    fun adjustmentValuesUseDefaultsWhenMissing() {
        val adjustments = floatArrayOf(10f, 1.2f)

        assertEquals(10f, adjustments.adjustValue(0, 0f), 0.0001f)
        assertEquals(1.2f, adjustments.adjustValue(1, 1f), 0.0001f)
        assertEquals(0.75f, adjustments.adjustValue(2, 0.75f), 0.0001f)
        assertEquals(0.5f, (null as FloatArray?).adjustValue(4, 0.5f), 0.0001f)
    }

    @Test
    fun shapeCropValuesMapToExpectedAspectRatios() {
        assertEquals(AspectRatio.FREE, aspectRatioForShapeCrop(0f))
        assertEquals(AspectRatio.CIRCLE, aspectRatioForShapeCrop(1f))
        assertEquals(AspectRatio.HEART, aspectRatioForShapeCrop(4f))
        assertEquals(AspectRatio.DIAMOND, aspectRatioForShapeCrop(7f))
        assertEquals(AspectRatio.FREE, aspectRatioForShapeCrop(99f))
    }

    @Test
    fun filterOrdinalFallsBackSafely() {
        assertEquals(ImageFilter.NONE, filterFromOrdinal(0f))
        assertEquals(ImageFilter.WARM, filterFromOrdinal(4f))
        assertEquals(ImageFilter.NONE, filterFromOrdinal(999f))
    }

    @Test
    fun extractedFilterMatricesPreserveRenderableFilters() {
        assertNull(getFilterMatrix(ImageFilter.NONE))
        assertNotNull(getFilterMatrix(ImageFilter.MONO))
        assertNotNull(getFilterMatrix(ImageFilter.WARM))
        assertNotNull(getFilterMatrix(ImageFilter.POLAROID))
        assertNull(getFilterMatrix(ImageFilter.GLITCH))
    }

    @Test
    fun smoothPathKeepsShortPathsUnchanged() {
        val points = listOf(PointF(1f, 2f), PointF(2f, 3f), PointF(3f, 4f))

        assertEquals(points, smoothPath(points))
    }

    @Test
    fun adaptiveLayoutClassKeepsPhonesOnCompactPath() {
        assertEquals(EditorLayoutClass.Phone, editorLayoutClass(393f, 852f))
        assertEquals(EditorLayoutClass.Phone, editorLayoutClass(820f, 1180f))
    }

    @Test
    fun adaptiveLayoutClassUsesWideInspectorForLargeWindows() {
        assertEquals(EditorLayoutClass.Wide, editorLayoutClass(900f, 600f))
        assertEquals(312f, editorSidePanelWidthDp(900f), 0.0001f)
        assertEquals(360f, editorSidePanelWidthDp(1280f), 0.0001f)
    }
}
