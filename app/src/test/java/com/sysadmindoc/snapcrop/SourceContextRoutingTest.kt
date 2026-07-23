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
        assertTrue(crop.contains("sourceSnapshot.context"))
        assertTrue(crop.contains("explicitSourceContext.value"))
        assertTrue(project.contains("private const val VERSION = 5"))
        assertTrue(gallery.contains("observeSourceContexts()"))
        assertTrue(gallery.contains("GalleryFailureSource.SOURCE_CONTEXT_DATABASE"))
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

        val persist = crop.indexOf("putSourceContext(uri, checkNotNull(savedDateAdded), contextValue)")
        val replace = crop.indexOf("requestSourceMutation(SourceMutationPurpose.REPLACE_AFTER_SAVE")
        assertTrue(persist >= 0 && replace > persist)
        assertTrue(index.contains("tableName = \"media_source_context\""))
        assertFalse(hints.contains("ExplicitSourceContext"))
        assertFalse(journal.contains("sourceUrl"))
        assertFalse(journal.contains("sourceContext"))
    }

    @Test
    fun sourceRemovalUsesMediaStoreTrashRequest() {
        val crop = source("CropActivity.kt")

        // Replace-after-save and editor delete both route the original to the MediaStore
        // trash via createTrashRequest, the request API MANAGE_MEDIA authorizes prompt-free
        // for media the app does not own (the reason the old direct-update move never
        // removed system screenshots).
        assertTrue(crop.contains("requestSourceTrash()"))
        assertTrue(crop.contains("MediaStore.createTrashRequest(contentResolver, listOf(uri), true)"))
        assertFalse(crop.contains("moveSourceToArchive"))
        assertFalse(crop.contains("SourceArchiveManager.moveToArchive"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
