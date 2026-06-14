package com.sysadmindoc.snapcrop

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Privacy-first local crash diagnostics. Installs a [Thread.UncaughtExceptionHandler] that writes a
 * stacktrace plus app/OS/device info to the app-private directory, then chains to the previous
 * handler so the system still records the crash. Nothing is ever sent off-device — the user can view,
 * share, or clear logs from Settings. Replaces the absent crash-log file mandated for GUI apps.
 */
object CrashReporter {
    private const val DIR = "crash"
    private const val PREFIX = "crash_"
    private const val MAX_LOGS = 5

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

    /** Writes a single crash report and prunes old ones. Exposed for testing. */
    internal fun record(context: Context, thread: Thread, throwable: Throwable): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stack = StringWriter().also { sw -> PrintWriter(sw).use { throwable.printStackTrace(it) } }.toString()
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))
        val report = buildString {
            append("SnapCrop ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Time: ").append(stamp).append('\n')
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Thread: ").append(thread.name).append("\n\n")
            append(stack)
        }
        val file = File(dir, "$PREFIX$now.txt")
        file.writeText(report)
        prune(dir)
        return file
    }

    fun crashLogs(context: Context): List<File> =
        File(context.filesDir, DIR)
            .listFiles { f -> f.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun latestCrash(context: Context): File? = crashLogs(context).firstOrNull()

    fun clear(context: Context) {
        crashLogs(context).forEach { runCatching { it.delete() } }
    }

    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_LOGS).forEach { runCatching { it.delete() } }
    }
}
