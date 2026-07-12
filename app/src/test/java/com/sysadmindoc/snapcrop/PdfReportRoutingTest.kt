package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfReportRoutingTest {
    @Test
    fun everyPdfPageUsesTheValidatedLayoutAndNoPixelSizedConstantsRemain() {
        val source = File("src/main/java/com/sysadmindoc/snapcrop/MainActivity.kt").readText()

        assertFalse(source.contains("PDF_WIDTH"))
        assertFalse(source.contains("PDF_HEIGHT"))
        assertFalse(source.contains("1240"))
        assertFalse(source.contains("1754"))
        assertTrue(source.contains("PageInfo.Builder(layout.widthPoints, layout.heightPoints"))
        assertTrue(source.contains("drawReportCoverPage("))
        assertTrue(source.contains("drawReportImagePage("))
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
