package com.sysadmindoc.snapcrop

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.io.File

internal data class RestoredUriBatch(
    val uris: List<Uri>,
    val unavailableCount: Int,
)

internal enum class GalleryBackTarget {
    DELETE_DIALOG,
    COLLECTION_EDITOR,
    COLLECTION_PICKER,
    SOURCE_EDITOR,
    NOTE_EDITOR,
    DUPLICATE_REVIEW,
    DUPLICATE_SCAN,
    VIEWER,
    SELECTION,
    FILTERS,
    ALBUM,
    ROOT,
}

internal data class GalleryBackState(
    val deleteDialog: Boolean = false,
    val collectionEditor: Boolean = false,
    val collectionPicker: Boolean = false,
    val sourceEditor: Boolean = false,
    val noteEditor: Boolean = false,
    val duplicateReview: Boolean = false,
    val duplicateScan: Boolean = false,
    val viewer: Boolean = false,
    val selection: Boolean = false,
    val filters: Boolean = false,
    val album: Boolean = false,
)

internal data class RestoredVideoTimeline(
    val frameMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
)

internal object WorkflowStateRestoration {
    const val MAX_SAVED_URIS = 50
    const val MAX_URI_CHARS = 2_048
    const val MAX_URI_STATE_CHARS = 32_768

    fun boundedUriStrings(values: Iterable<String>): ArrayList<String> {
        val result = ArrayList<String>()
        var totalChars = 0
        for (value in values) {
            if (result.size >= MAX_SAVED_URIS) break
            if (value.length > MAX_URI_CHARS) continue
            val normalized = value
            if (normalized.isBlank() || normalized in result) continue
            if (totalChars + normalized.length > MAX_URI_STATE_CHARS) break
            result += normalized
            totalChars += normalized.length
        }
        return result
    }

    fun putUris(outState: Bundle, key: String, uris: Iterable<Uri>) {
        outState.putStringArrayList(key, boundedUriStrings(uris.map(Uri::toString)))
    }

    fun restoreUris(state: Bundle?, key: String): List<Uri> {
        val raw = state?.getStringArrayList(key).orEmpty()
        return boundedUriStrings(raw).mapNotNull { value ->
            runCatching { Uri.parse(value) }.getOrNull()
        }
    }

    fun validateReadableUris(context: Context, uris: List<Uri>): RestoredUriBatch {
        val readable = uris.filter { isReadable(context, it) }
        return RestoredUriBatch(readable, uris.size - readable.size)
    }

    fun persistReadGrant(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun deepestGalleryBackTarget(state: GalleryBackState): GalleryBackTarget = when {
        state.deleteDialog -> GalleryBackTarget.DELETE_DIALOG
        state.collectionEditor -> GalleryBackTarget.COLLECTION_EDITOR
        state.collectionPicker -> GalleryBackTarget.COLLECTION_PICKER
        state.sourceEditor -> GalleryBackTarget.SOURCE_EDITOR
        state.noteEditor -> GalleryBackTarget.NOTE_EDITOR
        state.duplicateReview -> GalleryBackTarget.DUPLICATE_REVIEW
        state.duplicateScan -> GalleryBackTarget.DUPLICATE_SCAN
        state.viewer -> GalleryBackTarget.VIEWER
        state.selection -> GalleryBackTarget.SELECTION
        state.filters -> GalleryBackTarget.FILTERS
        state.album -> GalleryBackTarget.ALBUM
        else -> GalleryBackTarget.ROOT
    }

    fun restoreVideoTimeline(
        durationMs: Long,
        frameMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
    ): RestoredVideoTimeline {
        val duration = durationMs.coerceAtLeast(0L)
        val frame = frameMs.coerceIn(0L, duration)
        val start = trimStartMs.coerceIn(0L, duration)
        val end = trimEndMs.coerceIn(0L, duration)
        return if (end > start) {
            RestoredVideoTimeline(frame, start, end)
        } else {
            RestoredVideoTimeline(frame, 0L, duration)
        }
    }

    private fun isReadable(context: Context, uri: Uri): Boolean = when (uri.scheme?.lowercase()) {
        "content" -> runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        "file" -> runCatching { File(checkNotNull(uri.path)).isFile }.getOrDefault(false)
        "android.resource" -> true
        else -> false
    }
}
