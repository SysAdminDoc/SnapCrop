package com.sysadmindoc.snapcrop

import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfReportLayoutTest {
    @Test
    fun standardPageSizesUsePdfPointsAndSwapForLandscape() {
        val a4 = ReportPageSettings().layoutOrNull()!!
        assertEquals(595, a4.widthPoints)
        assertEquals(842, a4.heightPoints)

        val letter = ReportPageSettings(
            preset = ReportPagePreset.LETTER,
            orientation = ReportPageOrientation.LANDSCAPE
        ).layoutOrNull()!!
        assertEquals(792, letter.widthPoints)
        assertEquals(612, letter.heightPoints)
    }

    @Test
    fun customDimensionsAndMarginsRejectUnprintableGeometry() {
        assertNull(ReportPageSettings(
            preset = ReportPagePreset.CUSTOM,
            customWidthMm = 49f,
            customHeightMm = 200f
        ).layoutOrNull())
        assertNull(ReportPageSettings(
            preset = ReportPagePreset.CUSTOM,
            customWidthMm = 100f,
            customHeightMm = 100f,
            marginMm = 40f
        ).layoutOrNull())
        assertNull(ReportPageSettings(marginMm = 51f).layoutOrNull())

        val valid = ReportPageSettings(
            preset = ReportPagePreset.CUSTOM,
            orientation = ReportPageOrientation.PORTRAIT,
            customWidthMm = 300f,
            customHeightMm = 180f,
            marginMm = 12f
        ).layoutOrNull()!!
        assertEquals(180f, valid.widthMm, 0.001f)
        assertEquals(300f, valid.heightMm, 0.001f)
    }

    @Test
    fun imageFitIsCenteredBoundedAndAspectPreserving() {
        val layout = ReportPageSettings().layoutOrNull()!!
        val rect = layout.fitImage(1080, 2400, top = 180f)!!
        assertTrue(rect.left >= layout.marginPoints)
        assertTrue(rect.right <= layout.widthPoints - layout.marginPoints + 0.01f)
        assertTrue(rect.bottom <= layout.contentBottom + 0.01f)
        assertEquals(1080f / 2400f, rect.width / rect.height, 0.001f)
        assertNull(layout.fitImage(0, 100, 180f))
    }

    @Test
    fun paginationCapacityIsDeterministicAcrossPageShapes() {
        val portrait = ReportPageSettings().layoutOrNull()!!
        val landscape = ReportPageSettings(orientation = ReportPageOrientation.LANDSCAPE).layoutOrNull()!!
        assertEquals(portrait.linesAvailable(100f, 22f), portrait.linesAvailable(100f, 22f))
        assertTrue(portrait.linesAvailable(100f, 22f) > landscape.linesAvailable(100f, 22f))
        assertEquals(0, portrait.linesAvailable(portrait.contentBottom + 1f, 22f))
    }

    @Test
    fun paginationUsesInclusiveBottomBoundaryWithoutDroppingLines() {
        val exact = paginatePdfLines(listOf(20f, 20f), startY = 0f, bottom = 40f)
        assertEquals(listOf(0..1), exact)

        val overflow = paginatePdfLines(listOf(20f, 20.01f), startY = 0f, bottom = 40f)
        assertEquals(listOf(0..0, 1..1), overflow)
        assertEquals(overflow, paginatePdfLines(listOf(20f, 20.01f), 0f, 40f))
    }

    @Test
    fun wrappingSplitsLongTokensAndKeepsBlankParagraphs() {
        val paint = Paint().apply { textSize = 12f }
        val maxWidth = paint.measureText("abcde")
        val lines = wrapPdfTextLines("aa bb abcdefghijkl\n\nend", paint, maxWidth)

        assertTrue(lines.all { paint.measureText(it) <= maxWidth + 0.01f })
        assertTrue(lines.contains(""))
        assertEquals("aa bb", lines.first())
        assertEquals("end", lines.last())
    }
}
