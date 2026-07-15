package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDiagnosticsRoutingTest {
    @Test
    fun settingsPreviewsBoundedReportAndDisclosesCategoriesBeforeSharing() {
        val settings = source("SettingsActivity.kt")
        val strings = File("src/main/res/values/strings.xml").readText()
        val preview = settings.indexOf("crashSharePreview = CrashSharePreview(file, report)")
        val share = settings.indexOf("shareCrashLog(preview.file)")

        assertTrue(preview >= 0 && share > preview)
        assertTrue(settings.contains("file?.let(CrashReporter::readReport)"))
        assertTrue(settings.contains("R.string.settings_crash_share_disclosure"))
        assertTrue(strings.contains("sanitized exception messages"))
        assertTrue(strings.contains("stack class/method names with line numbers"))
        assertTrue(strings.contains("Device manufacturer/model, paths, URIs, endpoints, query values"))
    }

    @Test
    fun clearRefreshesVisibleStateFromTypedDiskOutcome() {
        val settings = source("SettingsActivity.kt")
        val clearStart = settings.indexOf("val result = CrashReporter.clear(this@SettingsActivity)")
        val clearEnd = settings.indexOf("Toast.makeText(this@SettingsActivity, message", clearStart)
        val clearRoute = settings.substring(clearStart, clearEnd)

        assertTrue(clearRoute.contains("crashFiles = result.remaining"))
        assertTrue(clearRoute.contains("CrashClearStatus.COMPLETE"))
        assertTrue(clearRoute.contains("CrashClearStatus.PARTIAL"))
        assertTrue(clearRoute.contains("CrashClearStatus.FAILED"))
        assertFalse(clearRoute.contains("crashFiles = emptyList()"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
