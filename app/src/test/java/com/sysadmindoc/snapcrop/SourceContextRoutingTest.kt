package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceContextRoutingTest {
    @Test
    fun cropProjectGalleryAndShareUseTypedContextRoutes() {
        val crop = source("CropActivity.kt")
        val gallery = source("GalleryScreen.kt")
        val main = source("MainActivity.kt")
        val project = source("SnapCropProjectSidecar.kt")

        assertTrue(crop.contains("ExplicitSourceContext.fromIntent(incomingIntent, referrer)"))
        assertTrue(crop.contains("sourceContext = explicitSourceContext.value"))
        assertTrue(project.contains("private const val VERSION = 5"))
        assertTrue(gallery.contains("observeSourceContexts().collect"))
        assertTrue(gallery.contains("Intent.CATEGORY_BROWSABLE"))
        assertTrue(main.contains("commonSourceUrl.takeIf { options.includeSourceLink }"))
        assertTrue(crop.contains("shareOptions.includeSourceLink"))
    }

    @Test
    fun contextPersistsBeforeReplaceAndNeverEntersHeuristicOrJournalData() {
        val crop = source("CropActivity.kt")
        val index = source("ScreenshotIndexStore.kt")
        val hints = source("CropSourceHints.kt")
        val journal = source("OperationJournal.kt")

        val persist = crop.indexOf("putSourceContext(uri, dateAdded, contextValue)")
        val replace = crop.indexOf("requestSourceTrash(SourceMutationPurpose.REPLACE_AFTER_SAVE")
        assertTrue(persist >= 0 && replace > persist)
        assertTrue(index.contains("tableName = \"media_source_context\""))
        assertFalse(hints.contains("ExplicitSourceContext"))
        assertFalse(journal.contains("sourceUrl"))
        assertFalse(journal.contains("sourceContext"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
