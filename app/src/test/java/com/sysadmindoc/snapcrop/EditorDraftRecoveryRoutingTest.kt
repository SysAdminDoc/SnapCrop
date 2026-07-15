package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDraftRecoveryRoutingTest {
    @Test
    fun coldLaunchChecksValidatedStoreWithoutSavedInstanceStateGate() {
        val activity = source("CropActivity.kt")
        val launchRoute = activity.substring(
            activity.indexOf("private fun recoverDraftOnLaunch"),
            activity.indexOf("private fun restoreDraft"),
        )

        assertTrue(activity.contains("recoverDraftOnLaunch(savedInstanceState?.getBoolean(KEY_HAS_DRAFT) == true)"))
        assertTrue(launchRoute.contains("draftStore.readValidated()"))
        assertTrue(launchRoute.contains("pendingDraftRecovery.value = result.project"))
        assertFalse(launchRoute.contains("readText()"))
    }

    @Test
    fun draftRestoreKeepsCheckpointThroughMissingSourceAndRelink() {
        val activity = source("CropActivity.kt")
        val decodeRoute = activity.substring(
            activity.indexOf("private fun handleProjectDecode"),
            activity.indexOf("override fun onSaveInstanceState"),
        )
        val loadRoute = activity.substring(
            activity.indexOf("private fun loadBitmap"),
            activity.indexOf("private fun Rect.coerceInside"),
        )

        assertTrue(decodeRoute.contains("pendingRelinkProject = project"))
        assertTrue(decodeRoute.contains("showProjectError(getString(R.string.crop_project_choose_source), canRelink = true)"))
        assertTrue(activity.contains("crop_project_source_unavailable), canRelink = true"))
        assertFalse(decodeRoute.contains("draftStore.discard()"))
        assertTrue(loadRoute.contains("if (restoringDraft)"))
        assertTrue(loadRoute.contains("val discarded = draftStore.discard()"))
    }

    @Test
    fun editorChangesDebounceOffMainAndFinishingAloneDoesNotDeleteDraft() {
        val activity = source("CropActivity.kt")
        val editor = source("CropEditorScreen.kt")
        val destroyRoute = activity.substring(activity.indexOf("override fun onDestroy()"))

        assertTrue(editor.contains("snapshotFlow { currentEditorDraft() to replaceOriginalOnSave }"))
        assertTrue(editor.contains(".drop(1)"))
        assertTrue(activity.contains("CoroutineScope(SupervisorJob() + Dispatchers.IO)"))
        assertTrue(activity.contains("scheduleDraftCheckpoint(DRAFT_DEBOUNCE_MILLIS)"))
        assertFalse(destroyRoute.contains("draftStore.discard()"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
