package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import android.graphics.Bitmap

data class AutoActionRule(
    val id: String,
    val label: String,
    val albumName: String,
    val aliases: List<String>,
    val redactSensitiveText: Boolean = true,
    val exportFormat: String = "default",
    val explanation: String = ""
)

data class AutoActionResult(
    val bitmap: Bitmap,
    val redactionCount: Int
)

object ConditionalAutoActions {
    private val rules = listOf(
        AutoActionRule(
            id = "reddit",
            label = "Reddit privacy",
            albumName = "Reddit",
            aliases = listOf("reddit", "com.reddit.frontpage", "profile:reddit"),
            explanation = "Built-in Reddit rule matched the crop/profile source."
        ),
        AutoActionRule(
            id = "twitter",
            label = "X/Twitter privacy",
            albumName = "X-Twitter",
            aliases = listOf("twitter", "x.com", "com.twitter.android", "profile:x/twitter"),
            explanation = "Built-in X/Twitter rule matched the crop/profile source."
        )
    )

    fun resolve(
        prefs: SharedPreferences,
        cropMethod: String,
        sourceHints: List<String>,
        userProfiles: List<UserAppCropProfile> = UserAppProfileStore.load(prefs),
        profileTextHints: List<String> = emptyList()
    ): AutoActionRule? {
        if (!prefs.getBoolean("conditional_auto_actions", false)) return null
        val haystack = (sourceHints + cropMethod).joinToString(" ").lowercase()
        rules.firstOrNull { rule ->
            rule.aliases.any { alias -> haystack.contains(alias.lowercase()) }
        }?.let { return it }

        val methodProfile = cropMethod
            .removePrefix("profile:")
            .takeIf { it != cropMethod }
            ?.trim()
            ?.lowercase()
        val directUserProfile = methodProfile?.let { method ->
            userProfiles.firstOrNull { profile ->
                profile.enabled &&
                        (profile.label.lowercase() == method || profile.id.lowercase() == method)
            }
        }
        val matchedProfile = directUserProfile
            ?: UserAppProfileStore.match(userProfiles, sourceHints, profileTextHints)?.profile
            ?: return null

        return AutoActionRule(
            id = matchedProfile.id,
            label = "${matchedProfile.label} rule",
            albumName = matchedProfile.albumName,
            aliases = matchedProfile.sourceHints + matchedProfile.ocrKeywords + listOf("profile:${matchedProfile.label}"),
            redactSensitiveText = matchedProfile.redactSensitiveText,
            exportFormat = matchedProfile.normalizedExportFormat(),
            explanation = "User rule '${matchedProfile.label}' matched; crop ${matchedProfile.cropSummary()}."
        )
    }

    fun savePath(prefs: SharedPreferences, rule: AutoActionRule): String {
        val base = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
        return "${base.trimEnd('/')}/${rule.albumName}"
    }

    internal suspend fun redactSensitiveText(
        bitmap: Bitmap,
        style: RedactionStyle = RedactionStyle.SOLID,
        script: OcrScript = OcrScript.LATIN,
        customPatterns: List<CustomRedactionPattern> = emptyList(),
    ): AutoActionResult {
        // A rule that requests sensitive-text replacement fails closed: detection errors propagate
        // so Quick Crop cannot silently publish an unredacted image.
        val result = SensitiveTextDetector.detect(
            bitmap,
            script,
            failOnOcrError = true,
            customPatterns = customPatterns,
        )
        if (result.rects.isEmpty()) return AutoActionResult(bitmap, 0)
        return AutoActionResult(ImageRedactor.redact(bitmap, result.rects, style), result.rects.size)
    }
}
