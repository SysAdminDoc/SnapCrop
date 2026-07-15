package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialOperationRoutingTest {
    @Test
    fun sidecarOmissionsUseTypedPartialJournalDetails() {
        val crop = source("CropActivity.kt")

        assertTrue(crop.contains("add(DiagnosticSidecar.SVG)"))
        assertTrue(crop.contains("add(DiagnosticSidecar.PROJECT)"))
        assertTrue(crop.contains("add(DiagnosticSidecar.OCR_TEXT)"))
        assertTrue(crop.contains("DiagnosticResult.SUCCESS else DiagnosticResult.PARTIAL"))
        assertTrue(crop.contains("DiagnosticPartial::Sidecars"))
    }

    @Test
    fun completedPartialMediaMutationNeverClaimsRetry() {
        val main = source("MainActivity.kt")
        val start = main.indexOf("private fun completeMediaMutation()")
        val end = main.indexOf("private fun toggleService()", start)
        val completion = main.substring(start, end)

        assertTrue(completion.contains("outcome.diagnosticResult"))
        assertTrue(completion.contains("partial = outcome.diagnosticPartial"))
        assertFalse(completion.contains("DiagnosticResult.RETRY"))

        val strings = File("src/main/res/values/strings.xml").readText()
        val partialCopy = listOf("toast_trashed_partial", "toast_deleted_partial").map { key ->
            Regex("""<string name="$key">([^<]+)</string>""").find(strings)?.groupValues?.get(1).orEmpty()
        }
        assertTrue(partialCopy.all(String::isNotBlank))
        assertFalse(partialCopy.any { it.contains("retry", ignoreCase = true) })
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
