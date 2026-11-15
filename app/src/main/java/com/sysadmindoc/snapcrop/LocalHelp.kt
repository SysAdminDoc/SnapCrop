package com.sysadmindoc.snapcrop

import androidx.annotation.StringRes
import java.text.Normalizer
import java.util.Locale

enum class HelpRoute {
    HOME_PERMISSIONS,
    EDITOR_SAVE_ACTIONS,
    EDITOR_REDACTION,
    EDITOR_CUTOUT,
    HOME_LONG_SCREENSHOT,
    HOME_STEP_CAPTURE,
    HOME_WEB_CAPTURE,
    SHARE_PRIVACY,
    SETTINGS_PROJECT_SIDECARS,
    SETTINGS_SECURE_EDITOR,
    GALLERY_FILTERS,
    GALLERY_COLLECTIONS,
    GALLERY_COMPARE,
}

/**
 * [id] and [keywords] are search aids that never reach the screen; the visible title and summary
 * live in `strings.xml` so translations cover them.
 */
data class HelpEntry(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val keywords: Set<String>,
    val route: HelpRoute,
)

/** Static, offline task help. List order is the useful empty-query order. */
object LocalHelpCatalog {
    val entries: List<HelpEntry> = listOf(
        HelpEntry(
            id = "recover-permissions",
            titleRes = R.string.help_entry_recover_permissions_title,
            summaryRes = R.string.help_entry_recover_permissions_summary,
            keywords = setOf("permission", "photos", "videos", "notifications", "monitor", "denied", "recovery"),
            route = HelpRoute.HOME_PERMISSIONS,
        ),
        HelpEntry(
            id = "save-replace-or-copy",
            titleRes = R.string.help_entry_save_replace_or_copy_title,
            summaryRes = R.string.help_entry_save_replace_or_copy_summary,
            keywords = setOf("save", "replace", "copy", "original", "delete", "trash", "editor"),
            route = HelpRoute.EDITOR_SAVE_ACTIONS,
        ),
        HelpEntry(
            id = "editor-modes-and-tools",
            titleRes = R.string.help_entry_editor_modes_and_tools_title,
            summaryRes = R.string.help_entry_editor_modes_and_tools_summary,
            keywords = setOf("editor", "crop", "pixelate", "draw", "ocr", "adjust", "pen", "arrow", "shape", "text", "emoji", "eraser", "ruler"),
            route = HelpRoute.EDITOR_REDACTION,
        ),
        HelpEntry(
            id = "save-sidecars-safely",
            titleRes = R.string.help_entry_save_sidecars_safely_title,
            summaryRes = R.string.help_entry_save_sidecars_safely_summary,
            keywords = setOf("save", "project", "ocr", "sidecar", "sensitive", "replace", "retain", "failure"),
            route = HelpRoute.SETTINGS_PROJECT_SIDECARS,
        ),
        HelpEntry(
            id = "redact-safely",
            titleRes = R.string.help_entry_redact_safely_title,
            summaryRes = R.string.help_entry_redact_safely_summary,
            keywords = setOf("redact", "privacy", "secret", "opaque", "bar", "pixelate", "blur", "safe"),
            route = HelpRoute.EDITOR_REDACTION,
        ),
        HelpEntry(
            id = "remove-middle-content",
            titleRes = R.string.help_entry_remove_middle_content_title,
            summaryRes = R.string.help_entry_remove_middle_content_summary,
            keywords = setOf("cut", "squeeze", "remove", "middle", "band", "seam", "long", "screenshot", "preview"),
            route = HelpRoute.EDITOR_CUTOUT,
        ),
        HelpEntry(
            id = "troubleshoot-long-screenshot",
            titleRes = R.string.help_entry_troubleshoot_long_screenshot_title,
            summaryRes = R.string.help_entry_troubleshoot_long_screenshot_summary,
            keywords = setOf("long", "screenshot", "scroll", "accessibility", "tile", "stitch", "seam", "retry"),
            route = HelpRoute.HOME_LONG_SCREENSHOT,
        ),
        HelpEntry(
            id = "troubleshoot-delayed-capture",
            titleRes = R.string.help_entry_troubleshoot_delayed_capture_title,
            summaryRes = R.string.help_entry_troubleshoot_delayed_capture_summary,
            keywords = setOf("delay", "timer", "capture", "permission", "monitor", "overlay", "notification"),
            route = HelpRoute.HOME_PERMISSIONS,
        ),
        HelpEntry(
            id = "secure-preview-capture-conflict",
            titleRes = R.string.help_entry_secure_preview_capture_conflict_title,
            summaryRes = R.string.help_entry_secure_preview_capture_conflict_summary,
            keywords = setOf("secure", "protect", "blank", "preview", "screenshot", "recording", "recents"),
            route = HelpRoute.SETTINGS_SECURE_EDITOR,
        ),
        HelpEntry(
            id = "troubleshoot-step-capture",
            titleRes = R.string.help_entry_troubleshoot_step_capture_title,
            summaryRes = R.string.help_entry_troubleshoot_step_capture_summary,
            keywords = setOf("step", "capture", "guide", "accessibility", "tile", "notification", "stop", "troubleshoot"),
            route = HelpRoute.HOME_STEP_CAPTURE,
        ),
        HelpEntry(
            id = "troubleshoot-static-web-capture",
            titleRes = R.string.help_entry_troubleshoot_static_web_capture_title,
            summaryRes = R.string.help_entry_troubleshoot_static_web_capture_summary,
            keywords = setOf("web", "url", "https", "page", "offline", "script", "large", "troubleshoot"),
            route = HelpRoute.HOME_WEB_CAPTURE,
        ),
        HelpEntry(
            id = "share-metadata-and-source-link",
            titleRes = R.string.help_entry_share_metadata_and_source_link_title,
            summaryRes = R.string.help_entry_share_metadata_and_source_link_summary,
            keywords = setOf("share", "metadata", "exif", "location", "source", "link", "strip", "privacy"),
            route = HelpRoute.SHARE_PRIVACY,
        ),
        HelpEntry(
            id = "save-and-reopen-projects",
            titleRes = R.string.help_entry_save_and_reopen_projects_title,
            summaryRes = R.string.help_entry_save_and_reopen_projects_summary,
            keywords = setOf("project", "sidecar", "reopen", "editable", "relink", "fingerprint", "json"),
            route = HelpRoute.SETTINGS_PROJECT_SIDECARS,
        ),
        HelpEntry(
            id = "filter-gallery",
            titleRes = R.string.help_entry_filter_gallery_title,
            summaryRes = R.string.help_entry_filter_gallery_summary,
            keywords = setOf("gallery", "filter", "source", "category", "date", "orientation", "favorite", "format"),
            route = HelpRoute.GALLERY_FILTERS,
        ),
        HelpEntry(
            id = "organize-gallery-collections",
            titleRes = R.string.help_entry_organize_gallery_collections_title,
            summaryRes = R.string.help_entry_organize_gallery_collections_summary,
            keywords = setOf("gallery", "collection", "album", "organize", "select", "membership", "move", "duplicate"),
            route = HelpRoute.GALLERY_COLLECTIONS,
        ),
        HelpEntry(
            id = "compare-two-screenshots",
            titleRes = R.string.help_entry_compare_two_screenshots_title,
            summaryRes = R.string.help_entry_compare_two_screenshots_summary,
            keywords = setOf("gallery", "compare", "difference", "overlay", "blink", "before", "after", "local"),
            route = HelpRoute.GALLERY_COMPARE,
        ),
    )

    /** [text] resolves a string resource id, so the search matches what the user reads. */
    fun search(query: String, text: (Int) -> String): List<HelpEntry> {
        val normalizedQuery = query.normalizedSearchText()
        if (normalizedQuery.isEmpty()) return entries
        val queryTokens = normalizedQuery.tokens().distinct()
        if (queryTokens.isEmpty()) return entries

        return entries.mapIndexedNotNull { usefulOrder, entry ->
            val title = text(entry.titleRes).normalizedSearchText()
            val summary = text(entry.summaryRes).normalizedSearchText()
            val id = entry.id.replace('-', ' ').normalizedSearchText()
            val keywords = entry.keywords.map(String::normalizedSearchText)
            val titleTokens = title.tokens()
            val summaryTokens = summary.tokens()
            val idTokens = id.tokens()
            val keywordTokens = keywords.flatMap(String::tokens)

            var score = 0
            for (token in queryTokens) {
                val tokenScore = when {
                    token in titleTokens -> 24
                    token in keywordTokens -> 18
                    token in idTokens -> 16
                    titleTokens.any { it.startsWith(token) } -> 12
                    keywordTokens.any { it.startsWith(token) } -> 10
                    summaryTokens.any { it == token } -> 8
                    summaryTokens.any { it.startsWith(token) } -> 5
                    title.contains(token) || summary.contains(token) || id.contains(token) -> 2
                    else -> 0
                }
                if (tokenScore == 0) return@mapIndexedNotNull null
                score += tokenScore
            }
            if (title == normalizedQuery) score += 80
            else if (title.startsWith(normalizedQuery)) score += 40
            else if (title.contains(normalizedQuery)) score += 20
            if (keywords.any { it == normalizedQuery }) score += 30
            RankedHelp(entry, score, usefulOrder)
        }.sortedWith(
            compareByDescending<RankedHelp>(RankedHelp::score)
                .thenBy(RankedHelp::usefulOrder)
                .thenBy { it.entry.id },
        ).map(RankedHelp::entry)
    }

    private data class RankedHelp(
        val entry: HelpEntry,
        val score: Int,
        val usefulOrder: Int,
    )
}

private fun String.normalizedSearchText(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

private fun String.tokens(): List<String> =
    Regex("[\\p{L}\\p{N}]+").findAll(this).map(MatchResult::value).toList()
