package com.sysadmindoc.snapcrop

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal enum class DiagnosticOperation {
    SCREENSHOT_MONITOR,
    QUICK_CROP,
    LONG_SCREENSHOT,
    STEP_CAPTURE,
    EXPORT,
    ARCHIVE,
    DELETE,
    MODEL,
    WEB_CAPTURE,
    GALLERY,
    INDEX,
    VIDEO
}

internal enum class DiagnosticStage {
    START,
    OBSERVE,
    CAPTURE,
    PROCESS,
    STITCH,
    ASSEMBLE,
    SAVE,
    SHARE,
    OPEN_EDITOR,
    COMPLETE
}

internal enum class DiagnosticResult { SUCCESS, PARTIAL, CANCELLED, BLOCKED, RETRY, FAILED }

internal enum class DiagnosticSidecar { SVG, PROJECT, OCR_TEXT }

internal sealed interface DiagnosticPartial {
    data class Counts(
        val requested: Int,
        val succeeded: Int,
        val retained: Int,
    ) : DiagnosticPartial {
        init {
            require(requested > 0)
            require(succeeded >= 0)
            require(retained >= 0)
            require(succeeded + retained == requested)
        }
    }

    data class Sidecars(val omitted: Set<DiagnosticSidecar>) : DiagnosticPartial {
        init {
            require(omitted.isNotEmpty())
        }
    }
}

internal enum class DiagnosticCode {
    NONE,
    PERMISSION_DENIED,
    START_NOT_ALLOWED,
    NO_SOURCE,
    SECURE_WINDOW,
    INVALID_WINDOW,
    WINDOW_LOST,
    WINDOW_UNAVAILABLE,
    THROTTLED,
    ACCESS_REVOKED,
    INVALID_DISPLAY,
    FRAME_LIMIT,
    PIXEL_LIMIT,
    MEMORY_LIMIT,
    CACHE_LIMIT,
    TIME_LIMIT,
    INACTIVITY_TIMEOUT,
    STORAGE_FAILURE,
    DECODE_FAILURE,
    ENCODE_FAILURE,
    PUBLISH_FAILURE,
    LAUNCH_FAILURE,
    USER_CANCELLED,
    INTERNAL
}

internal data class DiagnosticEvent(
    val timestampMs: Long,
    val operation: DiagnosticOperation,
    val stage: DiagnosticStage,
    val durationMs: Long,
    val result: DiagnosticResult,
    val code: DiagnosticCode,
    val errorClass: String?,
    val partial: DiagnosticPartial? = null,
) {
    init {
        require((result == DiagnosticResult.PARTIAL) == (partial != null))
    }
}

/**
 * A local-only, content-free diagnostic ring. The API deliberately accepts enums and an exception
 * class only: paths, URIs, OCR text, credentials, exception messages, and stack traces cannot enter
 * the journal through normal call sites. Every public operation fails harmlessly if storage fails.
 */
internal object OperationJournal {
    const val PREF_ENABLED = "operation_journal_enabled"
    internal const val MAX_EVENTS = 200
    internal const val MAX_BYTES = 64 * 1024
    private const val RETENTION_MS = 14L * 24 * 60 * 60 * 1000
    private const val DIR = "diagnostics"
    private const val FILE = "operation_journal.jsonl"
    private const val LOCK_FILE = "operation_journal.lock"
    private val writer = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(32),
        { runnable -> Thread(runnable, "snapcrop-operation-journal").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy()
    )

    fun start(): Long = SystemClock.elapsedRealtime()

    fun isEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, true)
    }.getOrDefault(false)

    fun setEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
            val persisted = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, enabled).commit()
            if (!enabled) clear(context)
            persisted && isEnabled(context) == enabled
        }.getOrDefault(false)

    fun record(
        context: Context,
        operation: DiagnosticOperation,
        stage: DiagnosticStage,
        result: DiagnosticResult,
        startedAtElapsedMs: Long? = null,
        code: DiagnosticCode = DiagnosticCode.NONE,
        error: Throwable? = null,
        partial: DiagnosticPartial? = null,
    ): Boolean = runCatching {
        if (!isEnabled(context)) return false
        val nowElapsed = SystemClock.elapsedRealtime()
        val duration = startedAtElapsedMs?.let { started ->
            val difference = nowElapsed - started
            when {
                started > nowElapsed -> 0L
                difference < 0L -> 24 * 60 * 60 * 1000L
                else -> difference.coerceAtMost(24 * 60 * 60 * 1000L)
            }
        } ?: 0L
        append(
            context,
            DiagnosticEvent(
                timestampMs = System.currentTimeMillis(),
                operation = operation,
                stage = stage,
                durationMs = duration,
                result = result,
                code = code,
                errorClass = error?.javaClass?.simpleName?.take(80)?.takeIf { it.matches(ERROR_CLASS) },
                partial = partial,
            )
        )
    }.getOrDefault(false)

    fun enqueue(
        context: Context,
        operation: DiagnosticOperation,
        stage: DiagnosticStage,
        result: DiagnosticResult,
        startedAtElapsedMs: Long? = null,
        code: DiagnosticCode = DiagnosticCode.NONE,
        error: Throwable? = null,
        partial: DiagnosticPartial? = null,
    ) {
        val app = context.applicationContext
        runCatching {
            writer.execute { record(app, operation, stage, result, startedAtElapsedMs, code, error, partial) }
        }
    }

    @Synchronized
    fun events(context: Context): List<DiagnosticEvent> = runCatching {
        withLock(context) { readBounded(context, System.currentTimeMillis()) }
    }.getOrDefault(emptyList())

    fun formatted(context: Context): String = format(events(context))

    fun snapshotFile(context: Context): File? = runCatching {
        val text = formatted(context)
        if (text.isBlank()) return null
        val dir = File(context.cacheDir, "$DIR/journal").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.name.startsWith("SnapCrop_Operations_")) it.delete() }
        File(dir, "SnapCrop_Operations_${System.currentTimeMillis()}.txt").apply { writeText(text) }
    }.getOrNull()

    @Synchronized
    fun clear(context: Context) {
        runCatching {
            withLock(context) {
                journalFile(context).delete()
                File(journalFile(context).parentFile, "$FILE.tmp").delete()
                File(context.cacheDir, "$DIR/journal").deleteRecursively()
            }
        }
    }

    internal fun format(events: List<DiagnosticEvent>): String {
        if (events.isEmpty()) return ""
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return buildString {
            append("SnapCrop local operation journal\n")
            append("Content, paths, URIs, OCR text, credentials, messages, and stack traces are excluded.\n\n")
            events.forEach { event ->
                append(formatter.format(Date(event.timestampMs))).append(' ')
                append(event.operation.name).append('/').append(event.stage.name).append(' ')
                append(event.result.name).append(" durationMs=").append(event.durationMs)
                if (event.code != DiagnosticCode.NONE) append(" code=").append(event.code.name)
                when (val partial = event.partial) {
                    is DiagnosticPartial.Counts -> {
                        append(" requested=").append(partial.requested)
                        append(" succeeded=").append(partial.succeeded)
                        append(" retained=").append(partial.retained)
                    }
                    is DiagnosticPartial.Sidecars -> append(" omittedSidecars=").append(
                        partial.omitted.sortedBy(DiagnosticSidecar::name).joinToString(",", transform = DiagnosticSidecar::name)
                    )
                    null -> Unit
                }
                event.errorClass?.let { append(" errorClass=").append(it) }
                append('\n')
            }
        }
    }

    @Synchronized
    private fun append(context: Context, event: DiagnosticEvent): Boolean =
        withLock(context) {
            if (!isEnabled(context)) return@withLock false
            val now = System.currentTimeMillis()
            val retained = readBounded(context, now).toMutableList().apply { add(event) }
            while (retained.size > MAX_EVENTS) retained.removeAt(0)
            var encoded = encode(retained)
            while (encoded.size > MAX_BYTES && retained.isNotEmpty()) {
                retained.removeAt(0)
                encoded = encode(retained)
            }
            val target = journalFile(context)
            val temporary = File(target.parentFile, "$FILE.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(encoded)
                    output.fd.sync()
                }
                try {
                    Files.move(
                        temporary.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                temporary.delete()
            }
            true
        }

    private fun readBounded(context: Context, nowMs: Long): List<DiagnosticEvent> {
        val file = journalFile(context)
        if (!file.isFile || file.length() !in 1..MAX_BYTES.toLong()) return emptyList()
        val cutoff = nowMs - RETENTION_MS
        return file.bufferedReader().useLines { lines ->
            lines.take(MAX_EVENTS + 1)
                .mapNotNull(::decode)
                .filter { it.timestampMs in cutoff..(nowMs + 60_000L) }
                .toList()
                .takeLast(MAX_EVENTS)
        }
    }

    private fun encode(events: List<DiagnosticEvent>): ByteArray = events.joinToString(
        separator = "\n",
        postfix = if (events.isEmpty()) "" else "\n"
    ) { event ->
        JSONObject()
            .put("t", event.timestampMs)
            .put("o", event.operation.name)
            .put("s", event.stage.name)
            .put("d", event.durationMs)
            .put("r", event.result.name)
            .put("c", event.code.name)
            .apply {
                event.errorClass?.let { put("e", it) }
                when (val partial = event.partial) {
                    is DiagnosticPartial.Counts -> {
                        put("q", partial.requested)
                        put("u", partial.succeeded)
                        put("n", partial.retained)
                    }
                    is DiagnosticPartial.Sidecars -> put(
                        "x",
                        JSONArray(partial.omitted.sortedBy(DiagnosticSidecar::name).map(DiagnosticSidecar::name)),
                    )
                    null -> Unit
                }
            }
            .toString()
    }.toByteArray(Charsets.UTF_8)

    private fun decode(line: String): DiagnosticEvent? = runCatching {
        if (line.length > 512) return null
        val json = JSONObject(line)
        val errorClass = json.optString("e", "").orEmpty().take(80)
            .takeIf { it.isNotBlank() && it.matches(ERROR_CLASS) }
        val result = DiagnosticResult.valueOf(json.getString("r"))
        val hasCounts = json.has("q") || json.has("u") || json.has("n")
        val hasSidecars = json.has("x")
        require(!(hasCounts && hasSidecars))
        val partial = when {
            result != DiagnosticResult.PARTIAL -> null
            hasCounts -> DiagnosticPartial.Counts(
                requested = json.getInt("q"),
                succeeded = json.getInt("u"),
                retained = json.getInt("n"),
            )
            hasSidecars -> {
                val encoded = json.getJSONArray("x")
                require(encoded.length() in 1..DiagnosticSidecar.entries.size)
                DiagnosticPartial.Sidecars(
                    buildSet {
                        repeat(encoded.length()) { add(DiagnosticSidecar.valueOf(encoded.getString(it))) }
                    }
                )
            }
            else -> null
        }
        DiagnosticEvent(
            timestampMs = json.getLong("t"),
            operation = DiagnosticOperation.valueOf(json.getString("o")),
            stage = DiagnosticStage.valueOf(json.getString("s")),
            durationMs = json.getLong("d").coerceIn(0, 24 * 60 * 60 * 1000L),
            result = result,
            code = DiagnosticCode.valueOf(json.optString("c", DiagnosticCode.NONE.name)),
            errorClass = errorClass,
            partial = partial,
        )
    }.getOrNull()

    private fun journalFile(context: Context): File = File(
        File(context.filesDir, DIR).apply { mkdirs() },
        FILE
    )

    private fun <T> withLock(context: Context, block: () -> T): T {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        return RandomAccessFile(File(dir, LOCK_FILE), "rw").use { randomAccess ->
            randomAccess.channel.use { channel -> channel.lock().use { block() } }
        }
    }

    private val ERROR_CLASS = Regex("[A-Za-z0-9_.$-]{1,80}")
}
