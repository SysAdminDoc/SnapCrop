package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.Locale

data class UserAppCropProfile(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val sourceHints: List<String>,
    val ocrKeywords: List<String>,
    val cropLeftFraction: Float,
    val cropTopFraction: Float,
    val cropRightFraction: Float,
    val cropBottomFraction: Float,
    val albumName: String,
    val redactSensitiveText: Boolean,
    val exportFormat: String
) {
    fun cropSummary(): String {
        fun pct(value: Float): String = "${(value * 100).toInt()}%"
        return "L ${pct(cropLeftFraction)}, T ${pct(cropTopFraction)}, R ${pct(cropRightFraction)}, B ${pct(cropBottomFraction)}"
    }

    fun normalizedExportFormat(): String =
        UserAppProfileStore.normalizeExportFormat(exportFormat)
}

data class UserAppCropProfileMatch(
    val profile: UserAppCropProfile,
    val confidence: Float,
    val reason: String
)

object UserAppProfileStore {
    const val PREF_KEY = "user_app_crop_profiles_json"

    private const val SCHEMA = "com.sysadmindoc.snapcrop.appProfiles"
    private const val VERSION = 1

    fun load(prefs: SharedPreferences): List<UserAppCropProfile> {
        val json = prefs.getString(PREF_KEY, null) ?: return emptyList()
        return runCatching { decode(json) }.getOrDefault(emptyList())
    }

    fun save(prefs: SharedPreferences, profiles: List<UserAppCropProfile>) {
        prefs.edit().putString(PREF_KEY, encode(profiles)).apply()
    }

    fun encode(profiles: List<UserAppCropProfile>): String =
        JSONObject()
            .put("schema", SCHEMA)
            .put("version", VERSION)
            .put("profiles", JSONArray().apply {
                profiles.forEach { put(it.toJson()) }
            })
            .toString(2)

    fun decode(json: String): List<UserAppCropProfile> {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return emptyList()
        val root = if (trimmed.startsWith("[")) {
            JSONObject().put("profiles", JSONArray(trimmed))
        } else {
            JSONObject(trimmed)
        }
        if (root.has("schema")) {
            require(root.optString("schema") == SCHEMA) { "Unsupported app profile pack" }
        }
        val version = root.optInt("version", VERSION)
        require(version == VERSION) { "Unsupported app profile pack version $version" }
        val profiles = root.optJSONArray("profiles") ?: JSONArray()
        return (0 until profiles.length()).mapNotNull { index ->
            profiles.optJSONObject(index)?.toUserProfile()
        }
    }

    fun create(
        label: String,
        sourceHints: List<String>,
        ocrKeywords: List<String>,
        cropLeftFraction: Float,
        cropTopFraction: Float,
        cropRightFraction: Float,
        cropBottomFraction: Float,
        albumName: String,
        redactSensitiveText: Boolean,
        exportFormat: String
    ): UserAppCropProfile {
        val cleanLabel = label.trim().ifBlank { "Custom app" }
        return UserAppCropProfile(
            id = "user-${slug(cleanLabel)}-${System.currentTimeMillis()}",
            label = cleanLabel.take(64),
            enabled = true,
            sourceHints = sourceHints.cleanTokens(),
            ocrKeywords = ocrKeywords.cleanTokens(),
            cropLeftFraction = cropLeftFraction.cleanCropFraction(),
            cropTopFraction = cropTopFraction.cleanCropFraction(),
            cropRightFraction = cropRightFraction.cleanCropFraction(),
            cropBottomFraction = cropBottomFraction.cleanCropFraction(),
            albumName = cleanAlbumName(albumName.ifBlank { cleanLabel }),
            redactSensitiveText = redactSensitiveText,
            exportFormat = normalizeExportFormat(exportFormat)
        )
    }

    fun upsert(
        profiles: List<UserAppCropProfile>,
        profile: UserAppCropProfile
    ): List<UserAppCropProfile> {
        val byId = LinkedHashMap<String, UserAppCropProfile>()
        profiles.forEach { byId[it.id] = it }
        byId[profile.id] = profile
        return byId.values.toList()
    }

    fun merge(
        existing: List<UserAppCropProfile>,
        incoming: List<UserAppCropProfile>
    ): List<UserAppCropProfile> {
        val byId = LinkedHashMap<String, UserAppCropProfile>()
        existing.forEach { byId[it.id] = it }
        incoming.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    fun parseTokenList(value: String): List<String> =
        value.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun needsOcr(profiles: List<UserAppCropProfile>): Boolean =
        profiles.any { it.enabled && it.ocrKeywords.isNotEmpty() }

    fun match(
        profiles: List<UserAppCropProfile>,
        sourceHints: List<String>,
        ocrTextHints: List<String>
    ): UserAppCropProfileMatch? {
        val sourceHaystack = sourceHints.joinToString(" ").lowercase()
        val ocrHaystack = ocrTextHints.joinToString(" ").lowercase()

        return profiles.asSequence()
            .filter { it.enabled }
            .mapNotNull { profile ->
                val sourceHits = profile.sourceHints
                    .map { it.lowercase() }
                    .filter { it.length > 1 && sourceHaystack.contains(it) }
                val ocrHits = profile.ocrKeywords
                    .map { it.lowercase() }
                    .filter { it.length > 1 && ocrHaystack.contains(it) }
                if (sourceHits.isEmpty() && ocrHits.isEmpty()) return@mapNotNull null

                val confidence = when {
                    sourceHits.isNotEmpty() && ocrHits.isNotEmpty() -> 0.96f
                    sourceHits.isNotEmpty() -> 0.86f
                    else -> 0.78f
                } + ((sourceHits.size + ocrHits.size - 1).coerceAtLeast(0) * 0.02f)

                val reason = buildString {
                    if (sourceHits.isNotEmpty()) append("source matched ${sourceHits.joinToString("/")}")
                    if (sourceHits.isNotEmpty() && ocrHits.isNotEmpty()) append("; ")
                    if (ocrHits.isNotEmpty()) append("OCR matched ${ocrHits.joinToString("/")}")
                }
                UserAppCropProfileMatch(
                    profile = profile,
                    confidence = confidence.coerceIn(0f, 0.99f),
                    reason = reason
                )
            }
            .maxWithOrNull(
                compareBy<UserAppCropProfileMatch> { it.confidence }
                    .thenBy { it.profile.sourceHints.size + it.profile.ocrKeywords.size }
            )
    }

    fun normalizeExportFormat(value: String): String {
        return when (value.trim().lowercase(Locale.US)) {
            "png" -> "png"
            "jpg", "jpeg" -> "jpeg"
            "webp" -> "webp"
            else -> "default"
        }
    }

    fun cleanAlbumName(value: String): String =
        value.trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), "-")
            .replace(Regex("\\s+"), " ")
            .take(48)
            .ifBlank { "Custom" }

    private fun UserAppCropProfile.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("label", label)
            .put("enabled", enabled)
            .put("sourceHints", sourceHints.toJsonArray())
            .put("ocrKeywords", ocrKeywords.toJsonArray())
            .put("crop", JSONObject()
                .put("left", cropLeftFraction.toDouble())
                .put("top", cropTopFraction.toDouble())
                .put("right", cropRightFraction.toDouble())
                .put("bottom", cropBottomFraction.toDouble())
            )
            .put("albumName", albumName)
            .put("redactSensitiveText", redactSensitiveText)
            .put("exportFormat", normalizedExportFormat())

    private fun JSONObject.toUserProfile(): UserAppCropProfile {
        val crop = optJSONObject("crop") ?: JSONObject()
        val label = optString("label", "Custom app").trim().ifBlank { "Custom app" }
        return UserAppCropProfile(
            id = optString("id", "user-${slug(label)}").trim().ifBlank { "user-${slug(label)}" },
            label = label.take(64),
            enabled = optBoolean("enabled", true),
            sourceHints = optJSONArray("sourceHints").toStringList().cleanTokens(),
            ocrKeywords = optJSONArray("ocrKeywords").toStringList().cleanTokens(),
            cropLeftFraction = crop.optDouble("left", 0.0).toFloat().cleanCropFraction(),
            cropTopFraction = crop.optDouble("top", 0.07).toFloat().cleanCropFraction(),
            cropRightFraction = crop.optDouble("right", 0.0).toFloat().cleanCropFraction(),
            cropBottomFraction = crop.optDouble("bottom", 0.07).toFloat().cleanCropFraction(),
            albumName = cleanAlbumName(optString("albumName", label)),
            redactSensitiveText = optBoolean("redactSensitiveText", false),
            exportFormat = normalizeExportFormat(optString("exportFormat", "default"))
        )
    }

    private fun List<String>.toJsonArray(): JSONArray =
        JSONArray().also { array -> forEach { array.put(it) } }

    private fun JSONArray?.toStringList(): List<String> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun List<String>.cleanTokens(): List<String> =
        map { it.trim() }
            .filter { it.length > 1 }
            .distinctBy { it.lowercase() }
            .take(24)

    private fun Float.cleanCropFraction(): Float =
        takeIf { it.isFinite() }?.coerceIn(0f, 0.45f) ?: 0f

    private fun slug(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "custom" }
            .take(32)
}
