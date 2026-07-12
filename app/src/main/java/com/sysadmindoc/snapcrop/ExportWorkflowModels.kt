package com.sysadmindoc.snapcrop

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportItemMetadata(
    val displayName: String,
    val relativePath: String = "",
    val sourceHint: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val dateAddedSeconds: Long = 0,
    val categories: Set<String> = emptySet(),
    val recognizedText: String = ""
) {
    val appToken: String
        get() = when {
            sourceHint.isNotBlank() -> sourceHint.substringAfterLast('.')
            relativePath.isNotBlank() -> relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "unknown" }
            displayName.isNotBlank() -> displayName.substringBeforeLast('.', displayName)
            else -> "unknown"
        }
}

object ReviewedOcr {
    const val MAX_PLAIN_TEXT_CHARS = 131_072

    fun sanitize(blocks: List<TextBlock>): List<TextBlock> = blocks.mapNotNull { block ->
        block.text.trim().takeIf(String::isNotEmpty)?.let { block.copy(text = it) }
    }

    fun plainText(blocks: List<TextBlock>): String = sanitize(blocks)
        .joinToString("\n") { it.text }
        .take(MAX_PLAIN_TEXT_CHARS)
}

object BatchRenameTemplate {
    private val invalidFilenameChars = Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]")
    private val whitespace = Regex("\\s+")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH-mm-ss", Locale.US)

    fun resolve(
        template: String,
        item: ExportItemMetadata,
        counter: Int,
        nowMillis: Long,
        profileName: String
    ): String {
        val baseTemplate = template.ifBlank { "SnapCrop_%date%_%counter%" }
        val raw = baseTemplate
            .replace("%app%", item.appToken)
            .replace("%date%", dateFormat.format(Date(nowMillis)))
            .replace("%time%", timeFormat.format(Date(nowMillis)))
            .replace("%timestamp%", nowMillis.toString())
            .replace("%counter%", counter.toString().padStart(4, '0'))
            .replace("%profile%", profileName.ifBlank { "default" })

        return sanitize(raw).ifBlank { "SnapCrop_${nowMillis}_${counter.toString().padStart(4, '0')}" }
    }

    fun sanitize(value: String): String {
        return value
            .replace(invalidFilenameChars, "_")
            .replace(whitespace, "_")
            .replace(Regex("_+"), "_")
            .trim('.', ' ', '_')
            .take(96)
    }
}
