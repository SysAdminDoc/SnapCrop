package com.sysadmindoc.snapcrop

import android.graphics.Rect
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorDraftStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validatedCheckpointSurvivesReadUntilExplicitDiscard() {
        val root = temporaryFolder.newFolder("valid")
        val store = EditorDraftStore(root)
        val project = project()

        val written = store.write(SnapCropProjectSidecar.encode(project))
        val read = store.readValidated()

        assertTrue(written is EditorDraftWriteResult.Success)
        assertEquals(project, (read as EditorDraftReadResult.Ready).project)
        assertTrue(File(root, EditorDraftStore.DRAFT_FILE_NAME).isFile)
        assertTrue(store.discard().complete)
        assertFalse(File(root, EditorDraftStore.DRAFT_FILE_NAME).exists())
    }

    @Test
    fun interruptedPromotionPreservesLastCommittedCheckpoint() {
        val root = temporaryFolder.newFolder("promotion")
        val original = project(crop = Rect(0, 0, 90, 90))
        val replacement = project(crop = Rect(5, 5, 80, 80))
        assertTrue(EditorDraftStore(root).write(SnapCropProjectSidecar.encode(original)) is EditorDraftWriteResult.Success)
        val failingStore = EditorDraftStore(root, promote = { _, _ -> error("interrupted") })

        assertEquals(
            EditorDraftWriteResult.Failed,
            failingStore.write(SnapCropProjectSidecar.encode(replacement)),
        )

        val recovered = EditorDraftStore(root).readValidated() as EditorDraftReadResult.Ready
        assertEquals(original, recovered.project)
        assertTrue(quarantines(root).any { it.name.contains("interrupted_write") })
    }

    @Test
    fun abandonedStagingFileIsQuarantinedWithoutReplacingCommittedDraft() {
        val root = temporaryFolder.newFolder("staging")
        val committed = project(crop = Rect(0, 0, 90, 90))
        val abandoned = project(crop = Rect(5, 5, 80, 80))
        val store = EditorDraftStore(root)
        store.write(SnapCropProjectSidecar.encode(committed))
        File(root, EditorDraftStore.STAGING_FILE_NAME)
            .writeText(SnapCropProjectSidecar.encode(abandoned))

        val recovered = store.readValidated() as EditorDraftReadResult.Ready

        assertEquals(committed, recovered.project)
        assertFalse(File(root, EditorDraftStore.STAGING_FILE_NAME).exists())
        assertTrue(quarantines(root).any { it.name.contains("interrupted_write") })
    }

    @Test
    fun oversizedCheckpointIsQuarantinedBeforeParsing() {
        val root = temporaryFolder.newFolder("oversize")
        File(root, EditorDraftStore.DRAFT_FILE_NAME).writeText("x".repeat(257))

        val result = EditorDraftStore(root, maxBytes = 256).readValidated()

        assertEquals(
            EditorDraftQuarantineReason.TOO_LARGE,
            (result as EditorDraftReadResult.Quarantined).reason,
        )
        assertFalse(File(root, EditorDraftStore.DRAFT_FILE_NAME).exists())
    }

    @Test
    fun truncatedCheckpointIsQuarantined() {
        val root = temporaryFolder.newFolder("truncated")
        File(root, EditorDraftStore.DRAFT_FILE_NAME).writeText("{\"schema\":")

        val result = EditorDraftStore(root).readValidated()

        assertEquals(
            EditorDraftQuarantineReason.MALFORMED,
            (result as EditorDraftReadResult.Quarantined).reason,
        )
    }

    @Test
    fun schemaMismatchIsQuarantinedWithExactReason() {
        val root = temporaryFolder.newFolder("schema")
        val incompatible = SnapCropProjectSidecar.encode(project())
            .replace("com.sysadmindoc.snapcrop.project", "example.incompatible.project")
        File(root, EditorDraftStore.DRAFT_FILE_NAME).writeText(incompatible)

        val result = EditorDraftStore(root).readValidated()

        assertEquals(
            EditorDraftQuarantineReason.UNSUPPORTED_SCHEMA,
            (result as EditorDraftReadResult.Quarantined).reason,
        )
    }

    @Test
    fun missingSourceRemainsAValidRelinkableDraft() {
        val root = temporaryFolder.newFolder("missing-source")
        val project = project(sourceUri = "content://missing.provider/image/42")
        val store = EditorDraftStore(root)
        store.write(SnapCropProjectSidecar.encode(project))

        val result = store.readValidated()

        assertEquals(project, (result as EditorDraftReadResult.Ready).project)
        assertTrue(File(root, EditorDraftStore.DRAFT_FILE_NAME).exists())
    }

    private fun quarantines(root: File): List<File> =
        File(root, EditorDraftStore.QUARANTINE_DIRECTORY_NAME).listFiles()?.toList().orEmpty()

    private fun project(
        sourceUri: String = "content://media/external/images/media/42",
        crop: Rect = Rect(0, 0, 100, 100),
    ) = SnapCropProject(
        sourceUri = sourceUri,
        sourceSha256 = null,
        sourceWidth = 100,
        sourceHeight = 100,
        cropRect = crop,
        adjustments = floatArrayOf(
            0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
        ),
        drawLayers = emptyList(),
        exportFormat = "png",
        exportMimeType = "image/png",
        exportQuality = 100,
        exportSavePath = "Pictures/SnapCrop",
        deleteOriginal = false,
    )
}
