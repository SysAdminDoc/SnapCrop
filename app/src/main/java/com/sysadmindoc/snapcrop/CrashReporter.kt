package com.sysadmindoc.snapcrop

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale

internal enum class CrashClearStatus { COMPLETE, PARTIAL, FAILED }

internal data class CrashClearResult(
    val status: CrashClearStatus,
    val requested: Int,
    val deleted: Int,
    val retained: Int,
    val remaining: List<File>,
)

/**
 * Privacy-first local crash diagnostics. Reports are bounded, atomically committed, and redact
 * location- or account-like values from exception messages. Stack class/method names and line
 * numbers remain available for debugging; reports never include device manufacturer/model data.
 */
object CrashReporter {
    private const val DIR = "crash"
    private const val PREFIX = "crash_"
    internal const val MAX_LOGS = 5
    internal const val MAX_REPORT_BYTES = 48 * 1024
    internal const val MAX_TOTAL_BYTES = 128 * 1024
    internal const val RETENTION_MS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_MESSAGE_CHARS = 2_048
    private const val MAX_CAUSES = 8
    private const val MAX_STACK_FRAMES = 192
    private const val MAX_IDENTIFIER_CHARS = 160
    private const val TRUNCATED = "\n[report truncated at the local safety limit]\n"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let crash logging itself crash the crash path.
            try {
                record(app, thread, throwable)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Writes one sanitized crash report and enforces every retention ceiling. */
    @Synchronized
    internal fun record(context: Context, thread: Thread, throwable: Throwable): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        prune(dir, now)
        val report = buildReport(thread, throwable, now)
        val bytes = truncateUtf8(report, MAX_REPORT_BYTES).toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_REPORT_BYTES)
        val target = uniqueReportFile(dir, now)
        val temporary = File(dir, ".${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
        target.setLastModified(now)
        prune(dir, now)
        return target
    }

    @Synchronized
    fun crashLogs(context: Context): List<File> {
        val dir = File(context.filesDir, DIR)
        prune(dir, System.currentTimeMillis())
        return listCrashFiles(dir)
    }

    fun latestCrash(context: Context): File? = crashLogs(context).firstOrNull()

    internal fun readReport(file: File): String? = runCatching {
        if (!file.isFile || file.length() !in 1..MAX_REPORT_BYTES.toLong()) return null
        file.readBytes().toString(Charsets.UTF_8)
    }.getOrNull()

    @Synchronized
    internal fun clear(
        context: Context,
        delete: (File) -> Boolean = { it.delete() },
    ): CrashClearResult {
        val dir = File(context.filesDir, DIR)
        val requestedFiles = listCrashFiles(dir)
        requestedFiles.forEach { file -> runCatching { delete(file) } }
        val requestedPaths = requestedFiles.mapTo(hashSetOf(), File::getAbsolutePath)
        val remaining = listCrashFiles(dir)
        val retained = remaining.count { it.absolutePath in requestedPaths }
        val deleted = requestedFiles.size - retained
        val status = when {
            retained == 0 -> CrashClearStatus.COMPLETE
            deleted > 0 -> CrashClearStatus.PARTIAL
            else -> CrashClearStatus.FAILED
        }
        return CrashClearResult(status, requestedFiles.size, deleted, retained, remaining)
    }

    internal fun sanitizeMessage(message: String?): String? {
        if (message.isNullOrBlank()) return null
        var sanitized = message
            .replace(CONTROL_CHARACTERS, " ")
            .replace(WINDOWS_PATH, "[redacted-path]")
            .replace(UNC_PATH, "[redacted-path]")
            .replace(URI_VALUE, "[redacted-uri]")
            .replace(UNIX_PATH, "[redacted-path]")
            .replace(EMAIL_VALUE, "[redacted-endpoint]")
            .replace(ENDPOINT_VALUE, "[redacted-endpoint]")
            .replace(SENSITIVE_QUERY_VALUE) { match ->
                "${match.groupValues[1]}=[redacted-query-value]"
            }
            .replace(QUERY_FRAGMENT, "[redacted-query]")
            .replace(WHITESPACE, " ")
            .trim()
        if (sanitized.length > MAX_MESSAGE_CHARS) {
            sanitized = sanitized.take(MAX_MESSAGE_CHARS) + "…"
        }
        return sanitized.takeIf(String::isNotBlank)
    }

    private fun buildReport(thread: Thread, throwable: Throwable, now: Long): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(now))
        return buildString {
            append("SnapCrop ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Time: ").append(stamp).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Thread category: ").append(if (thread.name == "main") "main" else "background")
                .append("\n\n")
            appendThrowable(throwable)
        }
    }

    private fun StringBuilder.appendThrowable(throwable: Throwable) {
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = throwable
        var causeCount = 0
        var remainingFrames = MAX_STACK_FRAMES
        while (current != null && causeCount < MAX_CAUSES && visited.add(current)) {
            if (causeCount > 0) append("Caused by: ")
            append(identifier(current.javaClass.name))
            sanitizeMessage(current.message)?.let { append(": ").append(it) }
            append('\n')
            val frames = current.stackTrace.take(remainingFrames)
            frames.forEach { frame ->
                append("\tat ").append(identifier(frame.className)).append('.')
                    .append(identifier(frame.methodName))
                if (frame.lineNumber >= 0) append("(line ").append(frame.lineNumber).append(')')
                append('\n')
            }
            remainingFrames -= frames.size
            if (frames.size < current.stackTrace.size) {
                append("\t[").append(current.stackTrace.size - frames.size).append(" frame(s) omitted]\n")
            }
            if (remainingFrames == 0) break
            current = current.cause
            causeCount++
        }
        if (current != null && causeCount >= MAX_CAUSES) append("[additional causes omitted]\n")
    }

    private fun identifier(value: String): String = value
        .take(MAX_IDENTIFIER_CHARS)
        .replace(INVALID_IDENTIFIER, "_")
        .ifBlank { "unknown" }

    private fun uniqueReportFile(dir: File, now: Long): File {
        var suffix = 0
        while (true) {
            val candidate = File(dir, "$PREFIX${now}_${suffix}.txt")
            if (!candidate.exists()) return candidate
            suffix++
        }
    }

    private fun prune(dir: File, now: Long) {
        if (!dir.isDirectory) return
        val cutoff = now - RETENTION_MS
        var keptCount = 0
        var keptBytes = 0L
        listCrashFiles(dir).forEach { file ->
            val bytes = file.length()
            val timestamp = file.lastModified()
            val retain = timestamp in cutoff..(now + 60_000L) &&
                bytes in 1..MAX_REPORT_BYTES.toLong() &&
                keptCount < MAX_LOGS &&
                keptBytes + bytes <= MAX_TOTAL_BYTES
            if (retain) {
                keptCount++
                keptBytes += bytes
            } else {
                runCatching { file.delete() }
            }
        }
    }

    private fun listCrashFiles(dir: File): List<File> =
        dir.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(".txt") }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending(File::getName))
            ?: emptyList()

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        val markerBytes = TRUNCATED.toByteArray(Charsets.UTF_8).size
        var low = 0
        var high = value.length
        var boundary = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            val candidate = if (middle > 0 && middle < value.length && value[middle - 1].isHighSurrogate()) {
                middle - 1
            } else {
                middle
            }
            if (value.substring(0, candidate).toByteArray(Charsets.UTF_8).size + markerBytes <= maxBytes) {
                boundary = candidate
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return value.substring(0, boundary) + TRUNCATED
    }

    private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]+")
    private val WHITESPACE = Regex("\\s+")
    private val WINDOWS_PATH = Regex("(?i)(?<![A-Za-z0-9_])[A-Z]:[\\\\/][^\\s]+")
    private val UNC_PATH = Regex("(?<![A-Za-z0-9_])\\\\\\\\[^\\s]+")
    private val URI_VALUE = Regex("(?i)\\b[a-z][a-z0-9+.-]{1,20}:(?://)?[^\\s]+")
    private val UNIX_PATH = Regex("(?<![A-Za-z0-9_])/(?:[^/\\s]+/)*[^/\\s]+")
    private val EMAIL_VALUE = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val ENDPOINT_VALUE = Regex(
        "(?i)\\b(?:localhost|(?:\\d{1,3}\\.){3}\\d{1,3}|(?:[a-z0-9-]+\\.)+[a-z]{2,})" +
            "(?::\\d{1,5})?(?:/[^\\s]*)?"
    )
    private val SENSITIVE_QUERY_VALUE = Regex(
        "(?i)\\b(api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|passwd|secret|" +
            "authorization|auth|cookie|session|query|q)=([^&\\s]+)"
    )
    private val QUERY_FRAGMENT = Regex("(?<![A-Za-z0-9_])\\?[^\\s]+")
    private val INVALID_IDENTIFIER = Regex("[^A-Za-z0-9_.$<>-]")
}
