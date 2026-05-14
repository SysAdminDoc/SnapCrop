package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import android.graphics.Bitmap

data class AutoActionRule(
    val id: String,
    val label: String,
    val albumName: String,
    val aliases: List<String>
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
            aliases = listOf("reddit", "com.reddit.frontpage", "profile:reddit")
        ),
        AutoActionRule(
            id = "twitter",
            label = "X/Twitter privacy",
            albumName = "X-Twitter",
            aliases = listOf("twitter", "x.com", "com.twitter.android", "profile:x/twitter")
        )
    )

    fun resolve(
        prefs: SharedPreferences,
        cropMethod: String,
        sourceHints: List<String>
    ): AutoActionRule? {
        if (!prefs.getBoolean("conditional_auto_actions", false)) return null
        val haystack = (sourceHints + cropMethod).joinToString(" ").lowercase()
        return rules.firstOrNull { rule ->
            rule.aliases.any { alias -> haystack.contains(alias.lowercase()) }
        }
    }

    fun savePath(prefs: SharedPreferences, rule: AutoActionRule): String {
        val base = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
        return "${base.trimEnd('/')}/${rule.albumName}"
    }

    suspend fun redactSensitiveText(bitmap: Bitmap): AutoActionResult {
        val result = SensitiveTextDetector.detect(bitmap)
        if (result.rects.isEmpty()) return AutoActionResult(bitmap, 0)
        return AutoActionResult(ImageRedactor.pixelate(bitmap, result.rects), result.rects.size)
    }
}
