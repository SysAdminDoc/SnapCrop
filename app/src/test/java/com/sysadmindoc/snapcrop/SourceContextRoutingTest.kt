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
    fun promptFreeArchiveBypassesPerFileWriteRequestOnAndroidTwelveAndLater() {
        val crop = source("CropActivity.kt")
        val routeStart = crop.indexOf("private fun continueSourceArchiveAccess()")
        val routeEnd = crop.indexOf("private fun launchSourceArchiveWriteRequest()")
        val route = crop.substring(routeStart, routeEnd)

        assertTrue(route.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.S"))
        assertTrue(route.contains("moveSourceToArchive(mayRequestLegacyAccess = false)"))
        assertTrue(route.contains("else {\n            launchSourceArchiveWriteRequest()"))
        assertFalse(route.contains("MediaStore.createWriteRequest"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
