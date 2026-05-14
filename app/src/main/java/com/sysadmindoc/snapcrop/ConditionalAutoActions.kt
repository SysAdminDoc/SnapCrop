package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect

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

    private val sensitivePatterns = listOf(
        Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE),
        Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)"),
        Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
        Regex("(?<!\\d)(?:\\d[ -]*?){13,19}(?!\\d)")
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
        val rects = TextExtractor.extract(bitmap)
            .filter { block -> sensitivePatterns.any { it.containsMatchIn(block.text) } }
            .map { block -> block.bounds.padded(bitmap.width, bitmap.height) }

        if (rects.isEmpty()) return AutoActionResult(bitmap, 0)
        return AutoActionResult(ImageRedactor.pixelate(bitmap, rects), rects.size)
    }

    private fun Rect.padded(maxWidth: Int, maxHeight: Int): Rect {
        val padX = (width() * 0.08f).toInt().coerceAtLeast(8)
        val padY = (height() * 0.16f).toInt().coerceAtLeast(6)
        return Rect(
            (left - padX).coerceIn(0, maxWidth),
            (top - padY).coerceIn(0, maxHeight),
            (right + padX).coerceIn(0, maxWidth),
            (bottom + padY).coerceIn(0, maxHeight)
        )
    }
}
