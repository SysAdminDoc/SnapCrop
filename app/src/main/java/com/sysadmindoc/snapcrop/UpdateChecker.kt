package com.sysadmindoc.snapcrop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Privacy-clean sideload update check. Does a single anonymous GET to the public GitHub Releases
 * API and compares the latest tag against the installed version. No account, no telemetry, no
 * identifying headers beyond a static User-Agent; opt-in / user-initiated only. Sideload-distributed
 * builds otherwise have no update path, so security fixes never reach users.
 */
object UpdateChecker {
    const val PREF_AUTO = "update_check_auto"
    private const val LATEST_URL = "https://api.github.com/repos/SysAdminDoc/SnapCrop/releases/latest"

    data class UpdateInfo(val versionName: String, val htmlUrl: String, val notes: String)

    suspend fun check(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SnapCrop-UpdateCheck")
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim()
            val latest = tag.removePrefix("v").trim()
            val url = json.optString("html_url").ifBlank { "https://github.com/SysAdminDoc/SnapCrop/releases/latest" }
            val notes = json.optString("body").trim().take(600)
            if (latest.isNotBlank() && isNewer(latest, currentVersionName)) UpdateInfo(latest, url, notes) else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Numeric semver comparison; ignores non-numeric suffixes (e.g. "-beta"). Exposed for testing. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    private fun parse(version: String): List<Int> =
        version.trim().split('.', '-', '+')
            .map { part -> part.takeWhile { ch -> ch.isDigit() } }
            .filter { it.isNotEmpty() }
            .map { it.toInt() }
}
