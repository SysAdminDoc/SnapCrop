package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrBackfillRoutingTest {
    @Test
    fun settingsAndLibraryExposeOptInStatusCancellationAndClear() {
        val settings = source("SettingsActivity.kt")
        val gallery = source("GalleryScreen.kt")
        val app = source("SnapCropApp.kt")

        assertTrue(settings.contains("prefs.getBoolean(OcrBackfillWorker.PREF_ENABLED, false)"))
        assertTrue(settings.contains("OcrBackfillWorker.sync(this@SettingsActivity, enabled)"))
        assertTrue(settings.contains("ocrBackfillStatus.queued"))
        assertTrue(settings.contains("ocrBackfillStatus.indexed"))
        assertTrue(settings.contains("ocrBackfillStatus.skipped"))
        assertTrue(settings.contains("ocrBackfillStatus.failed"))
        assertTrue(settings.contains("OcrBackfillWorker.clearIndex"))
        assertTrue(gallery.contains("OcrBackfillWorker.cancelCurrent(context)"))
        assertTrue(gallery.contains("gallery_ocr_backfill_counts"))
        assertTrue(app.contains("OcrBackfillWorker.schedule(this)"))
    }

    @Test
    fun workerIsReadOnlyAndCheckpointMarkerNeverBecomesAVisibleCategory() {
        val worker = source("OcrBackfillWorker.kt")
        val store = source("ScreenshotIndexStore.kt")

        assertTrue(worker.contains("openInputStream"))
        assertTrue(worker.contains("openAssetFileDescriptor"))
        assertFalse(worker.contains("openOutputStream"))
        assertFalse(worker.contains("createWriteRequest"))
        assertFalse(worker.contains("createTrashRequest"))
        assertFalse(worker.contains("RELATIVE_PATH"))
        assertTrue(store.contains("it != OCR_CHECKPOINT_MARKER"))
        assertTrue(store.contains("recognized_text = ''"))
    }

    @Test
    fun disclosureNamesSensitiveLocalTextAndNoNewPermissionIsAdded() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(strings.contains("Search text may be sensitive"))
        assertTrue(strings.contains("charging and idle"))
        assertFalse(manifest.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
