package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal data class LibraryMetadataSelection(
    val collections: Boolean = true,
    val notesAndReminders: Boolean = true,
    val inboxDecisions: Boolean = true,
) {
    val isEmpty: Boolean get() = !collections && !notesAndReminders && !inboxDecisions
}

internal data class LibraryMediaIdentity(
    val uri: String,
    val dateAdded: Long,
    val name: String,
    val albumPath: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val exactSha256: String? = null,
)

internal data class LibraryMetadataMedia(
    val id: String,
    val uriHint: String,
    val dateAdded: Long,
    val name: String,
    val albumPath: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val exactSha256: String?,
)

internal data class LibraryMetadataCollection(
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mediaIds: List<String>,
)

internal data class LibraryMetadataNote(
    val mediaId: String,
    val note: String,
    val reminderAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class LibraryMetadataTriage(
    val mediaId: String,
    val triagedAt: Long,
)

internal data class LibraryMetadataDocument(
    val version: Int = LibraryMetadataCodec.VERSION,
    val exportedAt: Long,
    val selection: LibraryMetadataSelection,
    val media: List<LibraryMetadataMedia>,
    val collections: List<LibraryMetadataCollection>,
    val notes: List<LibraryMetadataNote>,
    val triage: List<LibraryMetadataTriage>,
)

internal data class LibraryMetadataLocalSnapshot(
    val media: List<LibraryMediaIdentity>,
    val collections: List<ManualCollectionRow>,
    val collectionItems: List<ManualCollectionItemRow>,
    val notes: List<ScreenshotNoteReminderRow>,
    val triage: List<MediaTriageRow>,
    val exactSha256ByMedia: Map<Pair<String, Long>, String>,
)

internal data class ResolvedLibraryMedia(val uri: String, val dateAdded: Long)

internal data class LibraryCollectionChange(
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<ResolvedLibraryMedia>,
)

internal data class LibraryNoteChange(
    val media: ResolvedLibraryMedia,
    val note: String,
    val reminderAt: Long?,
    val createdAt: Long,
)

internal data class LibraryTriageChange(
    val media: ResolvedLibraryMedia,
    val triagedAt: Long,
)

internal data class LibraryMetadataImportReport(
    val matched: Int,
    val ambiguous: Int,
    val missing: Int,
    val conflicting: Int,
    val collectionsCreated: Int,
    val membershipsAdded: Int,
    val notesAdded: Int,
    val remindersAdded: Int,
    val inboxDecisionsAdded: Int,
    val reminderSchedulingFailed: Int = 0,
) {
    val changes: Int
        get() = collectionsCreated + membershipsAdded + notesAdded + remindersAdded + inboxDecisionsAdded
}

internal data class LibraryMetadataImportPlan(
    val report: LibraryMetadataImportReport,
    val collectionChanges: List<LibraryCollectionChange>,
    val noteChanges: List<LibraryNoteChange>,
    val triageChanges: List<LibraryTriageChange>,
)

internal data class PreparedLibraryMetadataImport(
    val document: LibraryMetadataDocument,
    val report: LibraryMetadataImportReport,
)

internal data class LibraryMetadataCommitResult(
    val report: LibraryMetadataImportReport,
    val remindersToSchedule: List<ScreenshotNoteReminder>,
)

internal data class LibraryMetadataExportReport(
    val media: Int,
    val collections: Int,
    val notes: Int,
    val reminders: Int,
    val inboxDecisions: Int,
)

internal enum class LibraryMetadataFormatReason { INVALID, TOO_LARGE, LEGACY_VERSION, FUTURE_VERSION }

internal class LibraryMetadataFormatException(
    val reason: LibraryMetadataFormatReason,
) : IllegalArgumentException(reason.name)

internal object LibraryMetadataCodec {
    const val VERSION = 1
    const val MIME_TYPE = "application/json"
    const val MAX_DOCUMENT_BYTES = 8 * 1024 * 1024
    const val MAX_MEDIA = 3_000
    const val MAX_COLLECTIONS = 500
    const val MAX_COLLECTION_LINKS = 10_000
    const val MAX_NOTES = 3_000
    const val MAX_TRIAGE = 3_000
    private const val SCHEMA = "com.sysadmindoc.snapcrop.library-metadata"

    fun encode(document: LibraryMetadataDocument): ByteArray {
        validate(document)
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("version", document.version)
            .put("exportedAt", document.exportedAt)
            .put("included", JSONArray().apply {
                if (document.selection.collections) put("collections")
                if (document.selection.notesAndReminders) put("notesAndReminders")
                if (document.selection.inboxDecisions) put("inboxDecisions")
            })
            .put("media", JSONArray().apply {
                document.media.forEach { media ->
                    put(JSONObject()
                        .put("id", media.id)
                        .put("uriHint", media.uriHint)
                        .put("dateAdded", media.dateAdded)
                        .put("name", media.name)
                        .put("albumPath", media.albumPath)
                        .put("size", media.size)
                        .put("width", media.width)
                        .put("height", media.height)
                        .put("mimeType", media.mimeType)
                        .apply { media.exactSha256?.let { put("sha256", it) } })
                }
            })
            .put("collections", JSONArray().apply {
                document.collections.forEach { collection ->
                    put(JSONObject()
                        .put("name", collection.name)
                        .put("createdAt", collection.createdAt)
                        .put("updatedAt", collection.updatedAt)
                        .put("mediaIds", JSONArray(collection.mediaIds)))
                }
            })
            .put("notes", JSONArray().apply {
                document.notes.forEach { note ->
                    put(JSONObject()
                        .put("mediaId", note.mediaId)
                        .put("note", note.note)
                        .apply { note.reminderAt?.let { put("reminderAt", it) } }
                        .put("createdAt", note.createdAt)
                        .put("updatedAt", note.updatedAt))
                }
            })
            .put("inboxDecisions", JSONArray().apply {
                document.triage.forEach { row ->
                    put(JSONObject().put("mediaId", row.mediaId).put("triagedAt", row.triagedAt))
                }
            })
        return root.toString(2).toByteArray(Charsets.UTF_8).also {
            if (it.size > MAX_DOCUMENT_BYTES) throw LibraryMetadataFormatException(LibraryMetadataFormatReason.TOO_LARGE)
        }
    }

    fun decode(input: InputStream): LibraryMetadataDocument {
        val bytes = readBounded(input)
        try {
            val text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            if (StrictJsonValidator.validate(text) != null) invalid()
            val root = JSONObject(text)
            if (root.optString("schema") != SCHEMA) invalid()
            val version = root.optInt("version", -1)
            when {
                version < VERSION -> throw LibraryMetadataFormatException(LibraryMetadataFormatReason.LEGACY_VERSION)
                version > VERSION -> throw LibraryMetadataFormatException(LibraryMetadataFormatReason.FUTURE_VERSION)
            }
            val included = root.requiredArray("included").strings().toSet()
            val selection = LibraryMetadataSelection(
                collections = "collections" in included,
                notesAndReminders = "notesAndReminders" in included,
                inboxDecisions = "inboxDecisions" in included,
            )
            if (selection.isEmpty) invalid()
            val media = root.requiredArray("media").objects(MAX_MEDIA).map { value ->
                LibraryMetadataMedia(
                    id = value.requiredString("id", 80),
                    uriHint = value.requiredString("uriHint", 8_192),
                    dateAdded = value.requiredNonNegativeLong("dateAdded"),
                    name = value.requiredString("name", 512),
                    albumPath = value.requiredString("albumPath", 2_048),
                    size = value.requiredNonNegativeLong("size"),
                    width = value.requiredNonNegativeInt("width"),
                    height = value.requiredNonNegativeInt("height"),
                    mimeType = value.requiredString("mimeType", 160),
                    exactSha256 = value.optString("sha256").takeIf(String::isNotEmpty)?.also {
                        if (!it.matches(Regex("[0-9a-fA-F]{64}"))) invalid()
                    }?.lowercase(Locale.ROOT),
                )
            }
            if (media.map { it.id }.toSet().size != media.size) invalid()
            val mediaIds = media.mapTo(hashSetOf()) { it.id }
            var linkCount = 0
            val collections = root.requiredArray("collections").objects(MAX_COLLECTIONS).map { value ->
                val ids = value.requiredArray("mediaIds").strings().also {
                    linkCount += it.size
                    if (linkCount > MAX_COLLECTION_LINKS || it.any { id -> id !in mediaIds }) invalid()
                }
                LibraryMetadataCollection(
                    name = ManualCollectionNames.normalize(value.requiredString("name", 80)).display,
                    createdAt = value.requiredNonNegativeLong("createdAt"),
                    updatedAt = value.requiredNonNegativeLong("updatedAt"),
                    mediaIds = ids.distinct(),
                )
            }
            if (collections.map { ManualCollectionNames.normalize(it.name).key }.toSet().size != collections.size) invalid()
            val notes = root.requiredArray("notes").objects(MAX_NOTES).map { value ->
                val mediaId = value.requiredString("mediaId", 80).also { if (it !in mediaIds) invalid() }
                LibraryMetadataNote(
                    mediaId = mediaId,
                    note = ScreenshotNoteText.normalize(value.requiredText("note", ScreenshotNoteText.MAX_CHARS)),
                    reminderAt = value.optionalNonNegativeLong("reminderAt"),
                    createdAt = value.requiredNonNegativeLong("createdAt"),
                    updatedAt = value.requiredNonNegativeLong("updatedAt"),
                )
            }
            if (notes.map { it.mediaId }.toSet().size != notes.size) invalid()
            val triage = root.requiredArray("inboxDecisions").objects(MAX_TRIAGE).map { value ->
                LibraryMetadataTriage(
                    mediaId = value.requiredString("mediaId", 80).also { if (it !in mediaIds) invalid() },
                    triagedAt = value.requiredNonNegativeLong("triagedAt"),
                )
            }
            if (triage.map { it.mediaId }.toSet().size != triage.size) invalid()
            return LibraryMetadataDocument(
                version = version,
                exportedAt = root.requiredNonNegativeLong("exportedAt"),
                selection = selection,
                media = media,
                collections = collections,
                notes = notes,
                triage = triage,
            ).also(::validate)
        } catch (error: LibraryMetadataFormatException) {
            throw error
        } catch (_: JSONException) {
            throw LibraryMetadataFormatException(LibraryMetadataFormatReason.INVALID)
        } catch (_: IllegalArgumentException) {
            throw LibraryMetadataFormatException(LibraryMetadataFormatReason.INVALID)
        } catch (_: Exception) {
            throw LibraryMetadataFormatException(LibraryMetadataFormatReason.INVALID)
        }
    }

    private fun validate(document: LibraryMetadataDocument) {
        if (document.version != VERSION || document.selection.isEmpty) invalid()
        if (document.media.size > MAX_MEDIA || document.collections.size > MAX_COLLECTIONS ||
            document.notes.size > MAX_NOTES || document.triage.size > MAX_TRIAGE ||
            document.collections.sumOf { it.mediaIds.size } > MAX_COLLECTION_LINKS
        ) throw LibraryMetadataFormatException(LibraryMetadataFormatReason.TOO_LARGE)
        val ids = document.media.map { it.id }
        if (ids.toSet().size != ids.size) invalid()
        val idSet = ids.toSet()
        if (document.collections.flatMap { it.mediaIds }.any { it !in idSet } ||
            document.notes.any { it.mediaId !in idSet } || document.triage.any { it.mediaId !in idSet }
        ) invalid()
        if ((!document.selection.collections && document.collections.isNotEmpty()) ||
            (!document.selection.notesAndReminders && document.notes.isNotEmpty()) ||
            (!document.selection.inboxDecisions && document.triage.isNotEmpty())
        ) invalid()
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_DOCUMENT_BYTES) throw LibraryMetadataFormatException(LibraryMetadataFormatReason.TOO_LARGE)
            output.write(buffer, 0, read)
        }
        if (total == 0) invalid()
        return output.toByteArray()
    }

    private fun JSONObject.requiredArray(name: String): JSONArray =
        if (has(name) && !isNull(name)) getJSONArray(name) else invalid()

    private fun JSONObject.requiredString(name: String, max: Int): String {
        if (!has(name) || isNull(name)) invalid()
        return getString(name).also { if (it.length > max || it.any(Char::isISOControl)) invalid() }
    }

    private fun JSONObject.requiredText(name: String, max: Int): String {
        if (!has(name) || isNull(name)) invalid()
        return getString(name).also { value ->
            if (value.length > max || value.any { it.isISOControl() && it != '\n' && it != '\t' }) invalid()
        }
    }

    private fun JSONObject.requiredNonNegativeLong(name: String): Long =
        getLong(name).also { if (it < 0) invalid() }

    private fun JSONObject.requiredNonNegativeInt(name: String): Int =
        getInt(name).also { if (it < 0) invalid() }

    private fun JSONObject.optionalNonNegativeLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else requiredNonNegativeLong(name)

    private fun JSONArray.objects(max: Int): List<JSONObject> {
        if (length() > max) throw LibraryMetadataFormatException(LibraryMetadataFormatReason.TOO_LARGE)
        return List(length()) { getJSONObject(it) }
    }

    private fun JSONArray.strings(): List<String> = List(length()) { index ->
        getString(index).also { if (it.length > 80 || it.any(Char::isISOControl)) invalid() }
    }

    private fun invalid(): Nothing = throw LibraryMetadataFormatException(LibraryMetadataFormatReason.INVALID)
}

internal object LibraryMetadataDocumentBuilder {
    fun build(
        snapshot: LibraryMetadataLocalSnapshot,
        resolvedMedia: List<LibraryMediaIdentity>,
        selection: LibraryMetadataSelection,
        exportedAt: Long,
    ): LibraryMetadataDocument {
        require(!selection.isEmpty)
        val selectedItems = if (selection.collections) snapshot.collectionItems else emptyList()
        val selectedNotes = if (selection.notesAndReminders) snapshot.notes else emptyList()
        val selectedTriage = if (selection.inboxDecisions) snapshot.triage else emptyList()
        val referenced = buildSet {
            selectedItems.forEach { add(it.mediaUri to it.mediaDateAdded) }
            selectedNotes.forEach { add(it.mediaUri to it.mediaDateAdded) }
            selectedTriage.forEach { add(it.mediaUri to it.mediaDateAdded) }
        }.sortedWith(compareBy<Pair<String, Long>>({ it.first }, { it.second }))
        if (referenced.size > LibraryMetadataCodec.MAX_MEDIA) {
            throw LibraryMetadataFormatException(LibraryMetadataFormatReason.TOO_LARGE)
        }
        val mediaByKey = (snapshot.media + resolvedMedia)
            .associateBy { it.uri to it.dateAdded }
        val idByKey = referenced.mapIndexed { index, key -> key to "m${index + 1}" }.toMap()
        val media = referenced.map { key ->
            val value = mediaByKey[key] ?: LibraryMediaIdentity(
                uri = key.first,
                dateAdded = key.second,
                name = "",
                albumPath = "",
                size = 0,
                width = 0,
                height = 0,
                mimeType = "",
            )
            LibraryMetadataMedia(
                id = idByKey.getValue(key),
                uriHint = safeIdentityText(value.uri, 8_192),
                dateAdded = value.dateAdded.coerceAtLeast(0),
                name = safeIdentityText(value.name, 512),
                albumPath = safeIdentityText(value.albumPath, 2_048),
                size = value.size.coerceAtLeast(0),
                width = value.width.coerceAtLeast(0),
                height = value.height.coerceAtLeast(0),
                mimeType = safeIdentityText(value.mimeType, 160),
                exactSha256 = (value.exactSha256 ?: snapshot.exactSha256ByMedia[key])
                    ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                    ?.lowercase(Locale.ROOT),
            )
        }
        val itemsByCollection = selectedItems.groupBy { it.collectionId }
        val collections = if (selection.collections) snapshot.collections.map { collection ->
            LibraryMetadataCollection(
                name = collection.name,
                createdAt = collection.createdAt,
                updatedAt = collection.updatedAt,
                mediaIds = itemsByCollection[collection.id].orEmpty().mapNotNull {
                    idByKey[it.mediaUri to it.mediaDateAdded]
                }.distinct(),
            )
        } else emptyList()
        return LibraryMetadataDocument(
            exportedAt = exportedAt,
            selection = selection,
            media = media,
            collections = collections,
            notes = selectedNotes.mapNotNull { row ->
                idByKey[row.mediaUri to row.mediaDateAdded]?.let { id ->
                    LibraryMetadataNote(id, row.note, row.reminderAt, row.createdAt, row.updatedAt)
                }
            },
            triage = selectedTriage.mapNotNull { row ->
                idByKey[row.mediaUri to row.mediaDateAdded]?.let { id ->
                    LibraryMetadataTriage(id, row.triagedAt)
                }
            },
        )
    }

    private fun safeIdentityText(value: String, maxLength: Int): String = value
        .map { if (it.isISOControl()) ' ' else it }
        .joinToString("")
        .take(maxLength)
}

internal object LibraryMetadataPlanner {
    fun plan(
        document: LibraryMetadataDocument,
        local: LibraryMetadataLocalSnapshot,
        now: Long,
    ): LibraryMetadataImportPlan {
        val resolutions = document.media.associate { media -> media.id to resolve(media, local.media) }
        val matched = resolutions.values.count { it is Resolution.Match }
        val ambiguous = resolutions.values.count { it is Resolution.Ambiguous }
        val missing = resolutions.values.count { it is Resolution.Missing }
        fun resolved(id: String): ResolvedLibraryMedia? =
            (resolutions[id] as? Resolution.Match)?.media?.let { ResolvedLibraryMedia(it.uri, it.dateAdded) }

        val currentCollections = local.collections.associateBy { it.normalizedName }
        val currentItems = local.collectionItems.groupBy { it.collectionId }
        var collectionsCreated = 0
        var membershipsAdded = 0
        val collectionChanges = document.collections.mapNotNull { imported ->
            val normalized = ManualCollectionNames.normalize(imported.name)
            val existing = currentCollections[normalized.key]
            val existingItems = existing?.let { currentItems[it.id] }.orEmpty()
                .mapTo(hashSetOf()) { it.mediaUri to it.mediaDateAdded }
            val additions = imported.mediaIds.mapNotNull(::resolved).distinct()
                .filter { (it.uri to it.dateAdded) !in existingItems }
            if (existing == null) collectionsCreated++
            membershipsAdded += additions.size
            if (existing != null && additions.isEmpty()) null else LibraryCollectionChange(
                imported.name, imported.createdAt, imported.updatedAt, additions,
            )
        }

        val currentNotes = local.notes.associateBy { it.mediaUri to it.mediaDateAdded }
        var conflicting = 0
        var notesAdded = 0
        var remindersAdded = 0
        val noteChanges = document.notes.mapNotNull { imported ->
            val media = resolved(imported.mediaId) ?: return@mapNotNull null
            val reminderAt = imported.reminderAt?.takeIf { it > now }
            val existing = currentNotes[media.uri to media.dateAdded]
            if (existing != null) {
                if (existing.note != imported.note || existing.reminderAt != reminderAt) conflicting++
                return@mapNotNull null
            }
            if (imported.note.isNotEmpty()) notesAdded++
            if (reminderAt != null) remindersAdded++
            LibraryNoteChange(media, imported.note, reminderAt, imported.createdAt)
                .takeIf { it.note.isNotEmpty() || it.reminderAt != null }
        }

        val currentTriage = local.triage.mapTo(hashSetOf()) { it.mediaUri to it.mediaDateAdded }
        val triageChanges = document.triage.mapNotNull { imported ->
            val media = resolved(imported.mediaId) ?: return@mapNotNull null
            LibraryTriageChange(media, imported.triagedAt)
                .takeIf { (media.uri to media.dateAdded) !in currentTriage }
        }.distinctBy { it.media.uri to it.media.dateAdded }

        return LibraryMetadataImportPlan(
            report = LibraryMetadataImportReport(
                matched = matched,
                ambiguous = ambiguous,
                missing = missing,
                conflicting = conflicting,
                collectionsCreated = collectionsCreated,
                membershipsAdded = membershipsAdded,
                notesAdded = notesAdded,
                remindersAdded = remindersAdded,
                inboxDecisionsAdded = triageChanges.size,
            ),
            collectionChanges = collectionChanges,
            noteChanges = noteChanges,
            triageChanges = triageChanges,
        )
    }

    private fun resolve(imported: LibraryMetadataMedia, local: List<LibraryMediaIdentity>): Resolution {
        val candidates = imported.exactSha256?.let { digest ->
            val exact = local.filter {
                it.exactSha256.equals(digest, ignoreCase = true) && it.size == imported.size
            }
            if (exact.size == 1) return Resolution.Match(exact.single())
            if (exact.isNotEmpty()) exact else local.filter { it.exactSha256 == null }
        } ?: local
        val sameUri = candidates.filter { it.uri == imported.uriHint && it.dateAdded == imported.dateAdded }
        if (sameUri.size == 1) return Resolution.Match(sameUri.single())
        if (sameUri.size > 1) return Resolution.Ambiguous

        val scored = candidates.mapNotNull { candidate ->
            val score = when {
                imported.name.isNotEmpty() && candidate.name.equals(imported.name, true) &&
                    candidate.size == imported.size && candidate.dateAdded == imported.dateAdded -> 90
                imported.name.isNotEmpty() && candidate.name.equals(imported.name, true) &&
                    candidate.size == imported.size && imported.width > 0 && imported.height > 0 &&
                    candidate.width == imported.width && candidate.height == imported.height &&
                    candidate.mimeType.equals(imported.mimeType, true) -> 80
                imported.albumPath.isNotEmpty() && candidate.albumPath.equals(imported.albumPath, true) &&
                    candidate.name.equals(imported.name, true) && candidate.size == imported.size -> 70
                else -> 0
            }
            candidate.takeIf { score >= 70 }?.let { it to score }
        }
        val bestScore = scored.maxOfOrNull { it.second } ?: return Resolution.Missing
        val best = scored.filter { it.second == bestScore }.map { it.first }
        return if (best.size == 1) Resolution.Match(best.single()) else Resolution.Ambiguous
    }

    private sealed interface Resolution {
        data class Match(val media: LibraryMediaIdentity) : Resolution
        data object Ambiguous : Resolution
        data object Missing : Resolution
    }
}

internal class LibraryMetadataTransfer(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val store = ScreenshotIndexStore(appContext)

    suspend fun export(
        destination: Uri,
        selection: LibraryMetadataSelection,
        now: Long = System.currentTimeMillis(),
    ): LibraryMetadataExportReport = withContext(Dispatchers.IO) {
        val snapshot = store.libraryMetadataSnapshot()
        val references = selectedReferences(snapshot, selection)
        val media = MediaStoreMetadataResolver(appContext).loadReferences(references)
            .mergeFallback(snapshot.media)
            .withCachedHashes(snapshot.exactSha256ByMedia)
            .withComputedHashes(resolver, references)
        val document = LibraryMetadataDocumentBuilder.build(snapshot, media, selection, now)
        val payload = LibraryMetadataCodec.encode(document)
        resolver.openOutputStream(destination, "wt")?.use { it.write(payload) }
            ?: throw IllegalStateException("metadata output unavailable")
        LibraryMetadataExportReport(
            media = document.media.size,
            collections = document.collections.size,
            notes = document.notes.count { it.note.isNotEmpty() },
            reminders = document.notes.count { it.reminderAt != null },
            inboxDecisions = document.triage.size,
        )
    }

    suspend fun prepareImport(source: Uri, now: Long = System.currentTimeMillis()): PreparedLibraryMetadataImport =
        withContext(Dispatchers.IO) {
            val document = resolver.openInputStream(source)?.use(LibraryMetadataCodec::decode)
                ?: throw IllegalStateException("metadata input unavailable")
            val snapshot = store.libraryMetadataSnapshot()
            val localMedia = loadImportMedia(document, snapshot)
            val report = LibraryMetadataPlanner.plan(
                document,
                snapshot.copy(media = localMedia),
                now,
            ).report
            PreparedLibraryMetadataImport(document, report)
        }

    suspend fun commitImport(
        prepared: PreparedLibraryMetadataImport,
        now: Long = System.currentTimeMillis(),
    ): LibraryMetadataImportReport = withContext(Dispatchers.IO) {
        val snapshot = store.libraryMetadataSnapshot()
        val localMedia = loadImportMedia(prepared.document, snapshot)
        val committed = store.commitLibraryMetadata(prepared.document, localMedia, now)
        val schedulingFailures = committed.remindersToSchedule.count { reminder ->
            runCatching { ScreenshotReminderScheduler.schedule(appContext, reminder) }.isFailure
        }
        committed.report.copy(reminderSchedulingFailed = schedulingFailures)
    }

    private fun loadImportMedia(
        document: LibraryMetadataDocument,
        snapshot: LibraryMetadataLocalSnapshot,
    ): List<LibraryMediaIdentity> = MediaStoreMetadataResolver(appContext).loadAll()
        .withCachedHashes(snapshot.exactSha256ByMedia)
        .withCandidateHashes(resolver, document.media)

    private fun selectedReferences(
        snapshot: LibraryMetadataLocalSnapshot,
        selection: LibraryMetadataSelection,
    ): Set<Pair<String, Long>> = buildSet {
        if (selection.collections) snapshot.collectionItems.forEach { add(it.mediaUri to it.mediaDateAdded) }
        if (selection.notesAndReminders) snapshot.notes.forEach { add(it.mediaUri to it.mediaDateAdded) }
        if (selection.inboxDecisions) snapshot.triage.forEach { add(it.mediaUri to it.mediaDateAdded) }
    }
}

private class MediaStoreMetadataResolver(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val projection = BASE_PROJECTION.toMutableList().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.IS_TRASHED)
    }.toTypedArray()

    fun loadReferences(references: Set<Pair<String, Long>>): List<LibraryMediaIdentity> = references.mapNotNull { key ->
        query(Uri.parse(key.first))?.takeIf { it.dateAdded == key.second }
    }

    fun loadAll(): List<LibraryMediaIdentity> {
        val capabilities = MediaCapabilityResolver.current(appContext)
        val values = ArrayList<LibraryMediaIdentity>()
        if (capabilities.canQueryImages) queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (capabilities.canQueryVideos) queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        return values
    }

    private fun query(uri: Uri): LibraryMediaIdentity? = runCatching {
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.toVisibleIdentity(uri) else null
        }
    }.getOrNull()

    private fun queryCollection(uri: Uri, output: MutableList<LibraryMediaIdentity>) {
        runCatching {
            resolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    if (output.size >= MAX_LOCAL_MEDIA) {
                        throw LibraryMetadataFormatException(LibraryMetadataFormatReason.TOO_LARGE)
                    }
                    cursor.toVisibleIdentity(Uri.withAppendedPath(uri, cursor.getLong(id).toString()))
                        ?.let(output::add)
                }
            } ?: throw IllegalStateException("MediaStore query unavailable")
        }.getOrElse { throw it }
    }

    private fun android.database.Cursor.toVisibleIdentity(uri: Uri): LibraryMediaIdentity? {
        if (getInt(getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)) != 0) return null
        val trashed = getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
        if (trashed >= 0 && getInt(trashed) != 0) return null
        return LibraryMediaIdentity(
        uri = uri.toString(),
        dateAdded = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)),
        name = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)).orEmpty(),
        albumPath = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)).orEmpty(),
        size = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
        width = getInt(getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)),
        height = getInt(getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)),
        mimeType = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)).orEmpty(),
        )
    }

    companion object {
        private const val MAX_LOCAL_MEDIA = 100_000
        private val BASE_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.IS_PENDING,
        )
    }
}

private fun List<LibraryMediaIdentity>.mergeFallback(
    fallback: List<LibraryMediaIdentity>,
): List<LibraryMediaIdentity> = (this + fallback).distinctBy { it.uri to it.dateAdded }

private fun List<LibraryMediaIdentity>.withCachedHashes(
    hashes: Map<Pair<String, Long>, String>,
): List<LibraryMediaIdentity> = map { media ->
    media.copy(exactSha256 = hashes[media.uri to media.dateAdded]?.lowercase(Locale.ROOT) ?: media.exactSha256)
}

private fun List<LibraryMediaIdentity>.withComputedHashes(
    resolver: ContentResolver,
    references: Set<Pair<String, Long>>,
): List<LibraryMediaIdentity> {
    val budget = HashBudget()
    return map { media ->
        if ((media.uri to media.dateAdded) in references && media.exactSha256 == null) {
            media.copy(exactSha256 = hashMedia(resolver, media, budget))
        } else media
    }
}

private fun List<LibraryMediaIdentity>.withCandidateHashes(
    resolver: ContentResolver,
    imported: List<LibraryMetadataMedia>,
): List<LibraryMediaIdentity> {
    val needed = imported.filter { it.exactSha256 != null }
    if (needed.isEmpty()) return this
    val budget = HashBudget()
    return map { local ->
        if (local.exactSha256 != null || needed.none { candidate ->
                candidate.size == local.size && (
                    candidate.name.equals(local.name, true) ||
                        (candidate.width > 0 && candidate.height > 0 &&
                            candidate.width == local.width && candidate.height == local.height &&
                            candidate.mimeType.equals(local.mimeType, true))
                    )
            }) local else local.copy(exactSha256 = hashMedia(resolver, local, budget))
    }
}

private class HashBudget(
    var remainingBytes: Long = 512L * 1024 * 1024,
    var remainingFiles: Int = 512,
)

private fun hashMedia(
    resolver: ContentResolver,
    media: LibraryMediaIdentity,
    budget: HashBudget,
): String? {
    if (media.size <= 0 || media.size > 64L * 1024 * 1024 ||
        media.size > budget.remainingBytes || budget.remainingFiles <= 0
    ) return null
    budget.remainingFiles--
    budget.remainingBytes -= media.size
    return runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(Uri.parse(media.uri))?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var actualBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                actualBytes += read
                if (actualBytes > media.size || actualBytes > 64L * 1024 * 1024) {
                    return@runCatching null
                }
                digest.update(buffer, 0, read)
            }
            if (actualBytes != media.size) return@runCatching null
        } ?: return@runCatching null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

internal fun ScreenshotIndexRow.toLibraryMediaIdentity(): LibraryMediaIdentity = LibraryMediaIdentity(
    uri = uri,
    dateAdded = dateAdded,
    name = name,
    albumPath = albumPath,
    size = size,
    width = width,
    height = height,
    mimeType = mimeType,
)
