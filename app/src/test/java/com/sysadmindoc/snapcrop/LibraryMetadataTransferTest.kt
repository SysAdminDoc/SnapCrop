package com.sysadmindoc.snapcrop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryMetadataTransferTest {
    @Test
    fun codecRoundTripKeepsSelectedMetadataAndExcludesPrivateDerivedFields() {
        val document = sampleDocument()

        val payload = LibraryMetadataCodec.encode(document)
        val decoded = LibraryMetadataCodec.decode(ByteArrayInputStream(payload))
        val text = payload.toString(Charsets.UTF_8)

        assertEquals(document, decoded)
        assertTrue(text.contains("line one\\nline two"))
        assertFalse(text.contains("recognizedText"))
        assertFalse(text.contains("reminderToken"))
        assertFalse(text.contains("sourceUrl"))
        assertFalse(text.contains("credential"))
    }

    @Test
    fun codecRejectsLegacyAndFutureSchemasWithoutApplyingAnything() {
        val encoded = LibraryMetadataCodec.encode(sampleDocument()).toString(Charsets.UTF_8)

        val legacy = JSONObject(encoded).put("version", 0).toString().byteInputStream()
        val legacyError = assertThrows(LibraryMetadataFormatException::class.java) {
            LibraryMetadataCodec.decode(legacy)
        }
        assertEquals(LibraryMetadataFormatReason.LEGACY_VERSION, legacyError.reason)

        val future = JSONObject(encoded).put("version", 2).toString().byteInputStream()
        val futureError = assertThrows(LibraryMetadataFormatException::class.java) {
            LibraryMetadataCodec.decode(future)
        }
        assertEquals(LibraryMetadataFormatReason.FUTURE_VERSION, futureError.reason)
    }

    @Test
    fun codecRejectsDuplicateKeysAndMalformedUtf8() {
        val duplicate = """{"schema":"first","schema":"second","version":1}""".byteInputStream()
        val duplicateError = assertThrows(LibraryMetadataFormatException::class.java) {
            LibraryMetadataCodec.decode(duplicate)
        }
        assertEquals(LibraryMetadataFormatReason.INVALID, duplicateError.reason)

        val malformed = ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))
        val malformedError = assertThrows(LibraryMetadataFormatException::class.java) {
            LibraryMetadataCodec.decode(malformed)
        }
        assertEquals(LibraryMetadataFormatReason.INVALID, malformedError.reason)
    }

    @Test
    fun codecRejectsOversizedEntrySets() {
        val tooMany = sampleDocument().copy(
            media = List(LibraryMetadataCodec.MAX_MEDIA + 1) { index ->
                sampleDocument().media.single().copy(id = "m$index", uriHint = "content://media/$index")
            },
            collections = emptyList(),
            notes = emptyList(),
            triage = emptyList(),
        )

        val error = assertThrows(LibraryMetadataFormatException::class.java) {
            LibraryMetadataCodec.encode(tooMany)
        }

        assertEquals(LibraryMetadataFormatReason.TOO_LARGE, error.reason)
    }

    @Test
    fun movedMediaRelinksFromStableHintsWhenOnlyAlbumAndUriChanged() {
        val localMedia = identity(
            uri = "content://media/external/images/media/99",
            album = "Pictures/Archive/",
        )
        val document = sampleDocument().copy(
            media = listOf(sampleDocument().media.single().copy(exactSha256 = null)),
        )

        val plan = LibraryMetadataPlanner.plan(document, emptySnapshot(listOf(localMedia)), NOW)

        assertEquals(1, plan.report.matched)
        assertEquals(0, plan.report.ambiguous)
        assertEquals(0, plan.report.missing)
        assertEquals(1, plan.report.collectionsCreated)
        assertEquals(localMedia.uri, plan.collectionChanges.single().items.single().uri)
    }

    @Test
    fun exactFingerprintRelinksRenamedMediaAheadOfHints() {
        val digest = "ab".repeat(32)
        val document = sampleDocument().copy(
            media = listOf(sampleDocument().media.single().copy(exactSha256 = digest)),
        )
        val local = identity(
            uri = "content://media/external/images/media/500",
            name = "renamed.png",
            album = "DCIM/",
            exactSha256 = digest,
        )

        val plan = LibraryMetadataPlanner.plan(document, emptySnapshot(listOf(local)), NOW)

        assertEquals(1, plan.report.matched)
        assertEquals(local.uri, plan.noteChanges.single().media.uri)
    }

    @Test
    fun knownFingerprintMismatchNeverFallsBackToAReusedUri() {
        val imported = sampleDocument()
        val reusedUri = identity(exactSha256 = "ff".repeat(32))

        val plan = LibraryMetadataPlanner.plan(imported, emptySnapshot(listOf(reusedUri)), NOW)

        assertEquals(0, plan.report.matched)
        assertEquals(1, plan.report.missing)
        assertEquals(0, plan.report.membershipsAdded)
    }

    @Test
    fun duplicateBestHintsAreReportedAmbiguousAndNeverLinked() {
        val document = sampleDocument().copy(
            media = listOf(sampleDocument().media.single().copy(exactSha256 = null)),
        )
        val local = listOf(
            identity("content://media/external/images/media/2", "Pictures/A/"),
            identity("content://media/external/images/media/3", "Pictures/B/"),
        )

        val plan = LibraryMetadataPlanner.plan(document, emptySnapshot(local), NOW)

        assertEquals(0, plan.report.matched)
        assertEquals(1, plan.report.ambiguous)
        assertEquals(1, plan.report.collectionsCreated)
        assertEquals(0, plan.report.membershipsAdded)
        assertTrue(plan.collectionChanges.single().items.isEmpty())
    }

    @Test
    fun missingMediaAndExistingNoteConflictsAreReportedPrecisely() {
        val missingDocument = sampleDocument().copy(
            media = listOf(sampleDocument().media.single().copy(
                uriHint = "content://missing",
                name = "missing.png",
                exactSha256 = null,
            )),
        )
        val missing = LibraryMetadataPlanner.plan(missingDocument, emptySnapshot(emptyList()), NOW)
        assertEquals(1, missing.report.missing)
        assertEquals(1, missing.report.collectionsCreated)
        assertEquals(0, missing.report.membershipsAdded)
        assertTrue(missing.noteChanges.isEmpty())
        assertTrue(missing.triageChanges.isEmpty())

        val media = identity()
        val conflictSnapshot = emptySnapshot(listOf(media)).copy(
            notes = listOf(
                ScreenshotNoteReminderRow(
                    media.uri, media.dateAdded, "local note", null, null, 1, 2,
                )
            ),
        )
        val conflict = LibraryMetadataPlanner.plan(
            sampleDocument().copy(media = listOf(sampleDocument().media.single().copy(exactSha256 = null))),
            conflictSnapshot,
            NOW,
        )
        assertEquals(1, conflict.report.conflicting)
        assertTrue(conflict.noteChanges.isEmpty())
    }

    @Test
    fun planningIsIdempotentAfterEquivalentMetadataExists() {
        val media = identity()
        val document = sampleDocument().copy(
            media = listOf(sampleDocument().media.single().copy(exactSha256 = null)),
        )
        val collection = ManualCollectionRow(7, "Research", "research", 1, 2)
        val alreadyApplied = emptySnapshot(listOf(media)).copy(
            collections = listOf(collection),
            collectionItems = listOf(ManualCollectionItemRow(7, media.uri, media.dateAdded, 3)),
            notes = listOf(
                ScreenshotNoteReminderRow(
                    media.uri,
                    media.dateAdded,
                    "line one\nline two",
                    FUTURE_REMINDER,
                    "local-token",
                    4,
                    5,
                )
            ),
            triage = listOf(MediaTriageRow(media.uri, media.dateAdded, 6)),
        )

        val secondPlan = LibraryMetadataPlanner.plan(document, alreadyApplied, NOW)

        assertEquals(1, secondPlan.report.matched)
        assertEquals(0, secondPlan.report.conflicting)
        assertEquals(0, secondPlan.report.changes)
        assertTrue(secondPlan.collectionChanges.isEmpty())
        assertTrue(secondPlan.noteChanges.isEmpty())
        assertTrue(secondPlan.triageChanges.isEmpty())
    }

    private fun sampleDocument() = LibraryMetadataDocument(
        exportedAt = NOW,
        selection = LibraryMetadataSelection(),
        media = listOf(
            LibraryMetadataMedia(
                id = "m1",
                uriHint = "content://media/external/images/media/1",
                dateAdded = 100,
                name = "Screenshot_1.png",
                albumPath = "Pictures/Screenshots/",
                size = 1234,
                width = 1080,
                height = 2400,
                mimeType = "image/png",
                exactSha256 = "12".repeat(32),
            )
        ),
        collections = listOf(LibraryMetadataCollection("Research", 1, 2, listOf("m1"))),
        notes = listOf(LibraryMetadataNote("m1", "line one\nline two", FUTURE_REMINDER, 4, 5)),
        triage = listOf(LibraryMetadataTriage("m1", 6)),
    )

    private fun identity(
        uri: String = "content://media/external/images/media/1",
        album: String = "Pictures/Screenshots/",
        name: String = "Screenshot_1.png",
        exactSha256: String? = null,
    ) = LibraryMediaIdentity(
        uri = uri,
        dateAdded = 100,
        name = name,
        albumPath = album,
        size = 1234,
        width = 1080,
        height = 2400,
        mimeType = "image/png",
        exactSha256 = exactSha256,
    )

    private fun emptySnapshot(media: List<LibraryMediaIdentity>) = LibraryMetadataLocalSnapshot(
        media = media,
        collections = emptyList(),
        collectionItems = emptyList(),
        notes = emptyList(),
        triage = emptyList(),
        exactSha256ByMedia = emptyMap(),
    )

    companion object {
        private const val NOW = 1_700_000_000_000L
        private const val FUTURE_REMINDER = NOW + 86_400_000L
    }
}
