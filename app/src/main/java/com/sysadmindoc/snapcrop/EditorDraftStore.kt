package com.sysadmindoc.snapcrop

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

internal enum class EditorDraftQuarantineReason {
    INTERRUPTED_WRITE,
    TOO_LARGE,
    MALFORMED,
    UNSUPPORTED_SCHEMA,
    UNSUPPORTED_VERSION,
    MISSING_FINGERPRINT,
    INVALID_FIELD,
    IO_FAILURE,
}

internal sealed interface EditorDraftReadResult {
    data object None : EditorDraftReadResult
    data class Ready(val project: SnapCropProject) : EditorDraftReadResult
    data class Quarantined(val reason: EditorDraftQuarantineReason) : EditorDraftReadResult
    data class Failed(val reason: EditorDraftQuarantineReason) : EditorDraftReadResult
}

internal sealed interface EditorDraftWriteResult {
    data class Success(val bytes: Int) : EditorDraftWriteResult
    data class Rejected(val reason: EditorDraftQuarantineReason) : EditorDraftWriteResult
    data object Failed : EditorDraftWriteResult
}

internal data class EditorDraftDiscardResult(
    val draftRemoved: Boolean,
    val stagingRemoved: Boolean,
) {
    val complete: Boolean get() = draftRemoved && stagingRemoved
}

/**
 * Owns the single recoverable editor checkpoint. All operations serialize process-wide so an
 * activity recreation cannot observe another activity's half-finished promotion.
 */
internal class EditorDraftStore(
    private val directory: File,
    private val maxBytes: Int = MAX_DRAFT_BYTES,
    private val promote: (File, File) -> Unit = ::promoteAtomically,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val draftFile get() = File(directory, DRAFT_FILE_NAME)
    private val stagingFile get() = File(directory, STAGING_FILE_NAME)
    private val quarantineDirectory get() = File(directory, QUARANTINE_DIRECTORY_NAME)

    fun write(json: String): EditorDraftWriteResult = synchronized(PROCESS_LOCK) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) {
            return@synchronized EditorDraftWriteResult.Rejected(EditorDraftQuarantineReason.TOO_LARGE)
        }
        val validated = SnapCropProjectSidecar.decode(
            ByteArrayInputStream(bytes),
            ProjectImportOrigin.INTERNAL_DRAFT,
            SnapCropProjectSidecar.DEFAULT_LIMITS.copy(maxJsonBytes = maxBytes),
        )
        if (validated is ProjectDecodeResult.Rejected) {
            return@synchronized EditorDraftWriteResult.Rejected(validated.reason.toQuarantineReason())
        }
        if (!directory.exists() && !directory.mkdirs()) return@synchronized EditorDraftWriteResult.Failed

        try {
            FileOutputStream(stagingFile, false).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            promote(stagingFile, draftFile)
            EditorDraftWriteResult.Success(bytes.size)
        } catch (_: Exception) {
            quarantine(stagingFile, EditorDraftQuarantineReason.INTERRUPTED_WRITE)
            EditorDraftWriteResult.Failed
        }
    }

    fun readValidated(): EditorDraftReadResult = synchronized(PROCESS_LOCK) {
        val interrupted = stagingFile.exists()
        if (interrupted && quarantine(stagingFile, EditorDraftQuarantineReason.INTERRUPTED_WRITE) == null) {
            return@synchronized EditorDraftReadResult.Failed(EditorDraftQuarantineReason.IO_FAILURE)
        }
        if (!draftFile.exists()) {
            return@synchronized if (interrupted) {
                EditorDraftReadResult.Quarantined(EditorDraftQuarantineReason.INTERRUPTED_WRITE)
            } else {
                EditorDraftReadResult.None
            }
        }
        if (!draftFile.isFile || draftFile.length() > maxBytes) {
            val reason = if (draftFile.length() > maxBytes) {
                EditorDraftQuarantineReason.TOO_LARGE
            } else {
                EditorDraftQuarantineReason.IO_FAILURE
            }
            return@synchronized quarantineResult(draftFile, reason)
        }

        val decoded = try {
            FileInputStream(draftFile).use { input ->
                SnapCropProjectSidecar.decode(
                    input,
                    ProjectImportOrigin.INTERNAL_DRAFT,
                    SnapCropProjectSidecar.DEFAULT_LIMITS.copy(maxJsonBytes = maxBytes),
                )
            }
        } catch (_: Exception) {
            return@synchronized quarantineResult(draftFile, EditorDraftQuarantineReason.IO_FAILURE)
        }
        when (decoded) {
            is ProjectDecodeResult.Success -> EditorDraftReadResult.Ready(decoded.project)
            is ProjectDecodeResult.Rejected -> quarantineResult(
                draftFile,
                decoded.reason.toQuarantineReason(),
            )
        }
    }

    fun discard(): EditorDraftDiscardResult = synchronized(PROCESS_LOCK) {
        EditorDraftDiscardResult(
            draftRemoved = removeIfPresent(draftFile),
            stagingRemoved = removeIfPresent(stagingFile),
        )
    }

    fun hasCheckpoint(): Boolean = synchronized(PROCESS_LOCK) {
        draftFile.exists() || stagingFile.exists()
    }

    private fun quarantineResult(
        file: File,
        reason: EditorDraftQuarantineReason,
    ): EditorDraftReadResult = if (quarantine(file, reason) != null) {
        EditorDraftReadResult.Quarantined(reason)
    } else {
        EditorDraftReadResult.Failed(EditorDraftQuarantineReason.IO_FAILURE)
    }

    private fun quarantine(file: File, reason: EditorDraftQuarantineReason): File? {
        if (!file.exists()) return null
        if (!quarantineDirectory.exists() && !quarantineDirectory.mkdirs()) return null
        val reasonLabel = reason.name.lowercase(Locale.ROOT)
        val baseName = "editor_draft_${clockMillis()}_$reasonLabel"
        var target = File(quarantineDirectory, "$baseName.json")
        var suffix = 1
        while (target.exists()) {
            target = File(quarantineDirectory, "${baseName}_${suffix++}.json")
        }
        return try {
            try {
                Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(file.toPath(), target.toPath())
            }
            target
        } catch (_: Exception) {
            null
        }
    }

    private fun removeIfPresent(file: File): Boolean = !file.exists() || file.delete()

    private fun ProjectRejectReason.toQuarantineReason(): EditorDraftQuarantineReason = when (this) {
        ProjectRejectReason.TOO_LARGE -> EditorDraftQuarantineReason.TOO_LARGE
        ProjectRejectReason.MALFORMED -> EditorDraftQuarantineReason.MALFORMED
        ProjectRejectReason.UNSUPPORTED_SCHEMA -> EditorDraftQuarantineReason.UNSUPPORTED_SCHEMA
        ProjectRejectReason.UNSUPPORTED_VERSION -> EditorDraftQuarantineReason.UNSUPPORTED_VERSION
        ProjectRejectReason.MISSING_FINGERPRINT -> EditorDraftQuarantineReason.MISSING_FINGERPRINT
        ProjectRejectReason.INVALID_FIELD -> EditorDraftQuarantineReason.INVALID_FIELD
    }

    companion object {
        const val MAX_DRAFT_BYTES = 8 * 1024 * 1024
        const val DRAFT_FILE_NAME = "editor_draft.json"
        const val STAGING_FILE_NAME = "editor_draft.json.tmp"
        const val QUARANTINE_DIRECTORY_NAME = "editor_draft_quarantine"

        private val PROCESS_LOCK = Any()

        private fun promoteAtomically(source: File, target: File) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
