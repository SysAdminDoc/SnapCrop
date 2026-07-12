package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfReportRoutingTest {
    @Test
    fun everyPdfPageUsesTheValidatedLayoutAndNoPixelSizedConstantsRemain() {
        val activity = File("src/main/java/com/sysadmindoc/snapcrop/MainActivity.kt").readText()
        val renderer = File("src/main/java/com/sysadmindoc/snapcrop/PdfReportRenderer.kt").readText()
        val source = activity + renderer

        assertFalse(source.contains("PDF_WIDTH"))
        assertFalse(source.contains("PDF_HEIGHT"))
        assertFalse(source.contains("1240"))
        assertFalse(source.contains("1754"))
        assertTrue(source.contains("PageInfo.Builder(layout.widthPoints, layout.heightPoints"))
        assertTrue(activity.contains("pdfReportRenderer.drawCoverPage("))
        assertTrue(activity.contains("pdfReportRenderer.drawImagePage("))
        assertTrue(activity.contains("pdfReportRenderer.drawOcrAppendixPages("))
        assertTrue(renderer.contains("fun drawCoverPage("))
        assertTrue(renderer.contains("fun drawImagePage("))
        assertTrue(source.contains("drawOcrAppendixPages("))
        assertTrue(source.contains("layout.fitImage(bitmap.width, bitmap.height"))
        assertTrue(source.contains("paginatePdfLines("))
    }

    @Test
    fun uiStatesStandardPdfWithoutClaimingPdfAOutput() {
        val source = File("src/main/java/com/sysadmindoc/snapcrop/MainActivity.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(source.contains("R.string.report_pdfa_unsupported"))
        assertTrue(strings.contains("PDF/A is not offered"))
        assertFalse(source.contains("ReportConformance.PDF_A"))
    }
}
