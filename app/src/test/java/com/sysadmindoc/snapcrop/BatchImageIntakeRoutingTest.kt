package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchImageIntakeRoutingTest {
    @Test
    fun cropAndResizeUseBoundedIntakeBeforeTransactionalPublication() {
        val main = sourceFile("app/src/main/java/com/sysadmindoc/snapcrop/MainActivity.kt")
        val crop = between(main, "private fun batchAutocrop", "override fun onCreate")
        val resize = between(main, "private fun batchResize", "private fun batchRename")

        listOf(crop, resize).forEach { workflow ->
            val intake = workflow.indexOf("BatchImageIntake.decode(")
            val publication = workflow.indexOf("MediaStoreImageWriter.write(")
            assertTrue(intake >= 0)
            assertTrue(publication > intake)
            assertTrue(workflow.contains("BatchImageIntake.MAX_ITEMS"))
            assertTrue(workflow.contains("BatchImageIntakeResult.Oversized"))
            assertTrue(workflow.contains("BatchImageIntakeResult.Unreadable"))
            assertTrue(workflow.contains("BatchImageIntakeResult.Cancelled"))
            assertTrue(workflow.contains("if (batchCancelled.value) continue"))
            assertFalse(workflow.contains("BitmapFactory.decodeStream"))
        }
    }

    @Test
    fun summaryKeepsEveryTerminalOutcomeDistinct() {
        val strings = sourceFile("app/src/main/res/values/strings.xml")
        assertTrue(strings.contains("%1\$d saved · %2\$d skipped · %3\$d oversized"))
        assertTrue(strings.contains("%4\$d unreadable · %5\$d failed · %6\$d cancelled"))
        assertTrue(strings.contains("12 MP / 48 MiB working bitmap"))
    }

    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        val until = source.indexOf(end, from + start.length)
        require(from >= 0 && until > from)
        return source.substring(from, until)
    }

    private fun sourceFile(path: String): String {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate $path")
    }
}
