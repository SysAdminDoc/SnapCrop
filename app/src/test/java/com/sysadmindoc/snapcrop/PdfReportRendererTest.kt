package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfReportRendererTest {
    @Test
    fun rendererOwnsPdfDrawingWhileActivityOnlyCoordinatesExport() {
        val activity = File("src/main/java/com/sysadmindoc/snapcrop/MainActivity.kt").readText()
        val renderer = File("src/main/java/com/sysadmindoc/snapcrop/PdfReportRenderer.kt").readText()

        assertTrue(renderer.contains("doc.startPage("))
        assertTrue(renderer.contains("doc.finishPage(page)"))
        assertTrue(renderer.contains("fun drawCoverPage("))
        assertTrue(renderer.contains("fun drawImagePage("))
        assertTrue(renderer.contains("fun drawOcrAppendixPages("))
        assertFalse(activity.contains("PdfDocument.PageInfo.Builder("))
        assertFalse(activity.contains("private fun drawPdfFooter("))
    }

    @Test
    fun exportSizeFormattingIsStableAtUnitBoundaries() {
        assertEquals("999 B", formatExportSize(999))
        assertEquals("1.0 KB", formatExportSize(1_000))
        assertEquals("1.5 KB", formatExportSize(1_500))
        assertEquals("1.0 MB", formatExportSize(1_000_000))
    }
}
