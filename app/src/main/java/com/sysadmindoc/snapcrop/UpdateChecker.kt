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

    const val FALLBACK_URL = "https://github.com/SysAdminDoc/SnapCrop/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val htmlUrl: String,
        val notes: String,
        val apkUrl: String? = null,
        val apkSha256: String? = null,
    ) {
        val downloadUrl: String
            get() = apkUrl ?: htmlUrl
    }

    /** Result of a check: distinguishes "no update" from "couldn't reach GitHub" so the UI can show
     *  the right message instead of a false "you're up to date". */
    sealed interface Result {
        data class Available(val info: UpdateInfo) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    suspend fun check(currentVersionName: String): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false // only ever talk to the fixed api.github.com endpoint
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SnapCrop-UpdateCheck")
            }
            if (conn.responseCode !in 200..299) return@withContext Result.Failed
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseLatestRelease(body, currentVersionName)
        } catch (_: Exception) {
            Result.Failed
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseLatestRelease(body: String, currentVersionName: String): Result {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim()
        val latest = tag.removePrefix("v").trim()
        val rawUrl = json.optString("html_url").trim()
        val releaseUrl = rawUrl.takeIf(::isTrustedReleaseUrl) ?: FALLBACK_URL
        val notes = json.optString("body").trim().take(600)
        if (latest.isBlank() || !isNewer(latest, currentVersionName)) return Result.UpToDate

        val expectedName = "SnapCrop-$latest.apk"
        val expectedPrefix = "https://github.com/SysAdminDoc/SnapCrop/releases/download/"
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        var apkSha256: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") != expectedName || asset.optString("state", "uploaded") != "uploaded") continue
                val candidate = asset.optString("browser_download_url").trim()
                if (!candidate.startsWith(expectedPrefix) || !candidate.endsWith("/$expectedName")) continue
                apkUrl = candidate
                apkSha256 = asset.optString("digest")
                    .removePrefix("sha256:")
                    .lowercase()
                    .takeIf { SHA256.matches(it) }
                break
            }
        }
        return Result.Available(UpdateInfo(latest, releaseUrl, notes, apkUrl, apkSha256))
    }

    private fun isTrustedReleaseUrl(value: String): Boolean =
        value.startsWith("https://github.com/SysAdminDoc/SnapCrop/releases/")

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

    private val SHA256 = Regex("^[0-9a-f]{64}$")
}
