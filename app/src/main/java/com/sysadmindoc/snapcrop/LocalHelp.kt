package com.sysadmindoc.snapcrop

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
    SETTINGS_PROJECTS,
    GALLERY_FILTERS,
    GALLERY_COLLECTIONS,
}

data class HelpEntry(
    val id: String,
    val title: String,
    val summary: String,
    val keywords: Set<String>,
    val route: HelpRoute,
)

/** Static, offline task help. List order is the useful empty-query order. */
object LocalHelpCatalog {
    val entries: List<HelpEntry> = listOf(
        HelpEntry(
            id = "recover-permissions",
            title = "Recover missing permissions",
            summary = "Use the permission cards on Home to restore Photos, Videos, or Notifications separately. Photo access is required for screenshot monitoring; Android pickers remain available without library access.",
            keywords = setOf("permission", "photos", "videos", "notifications", "monitor", "denied", "recovery"),
            route = HelpRoute.HOME_PERMISSIONS,
        ),
        HelpEntry(
            id = "save-replace-or-copy",
            title = "Choose Save & Replace or Save Copy",
            summary = "Save & Replace writes the edited result and then uses Android's removal flow for the original. Save Copy keeps the original and creates a separate result.",
            keywords = setOf("save", "replace", "copy", "original", "delete", "trash", "editor"),
            route = HelpRoute.EDITOR_SAVE_ACTIONS,
        ),
        HelpEntry(
            id = "editor-modes-and-tools",
            title = "Find editor modes and drawing tools",
            summary = "Use Crop, Pixelate, Draw, OCR, and Adjust mode chips. Draw contains Pen, arrows, shapes, text, highlight, callout, spotlight, magnifier, emoji, neon, blur, eraser, fill, Smart Erase, and ruler tools.",
            keywords = setOf("editor", "crop", "pixelate", "draw", "ocr", "adjust", "pen", "arrow", "shape", "text", "emoji", "eraser", "ruler"),
            route = HelpRoute.EDITOR_REDACTION,
        ),
        HelpEntry(
            id = "save-sidecars-safely",
            title = "Understand project and OCR sidecars",
            summary = "Project and OCR-text sidecars are optional and may contain reversible layers or sensitive recognized text. If a requested sidecar fails, Save & Replace retains the original instead of deleting it.",
            keywords = setOf("save", "project", "ocr", "sidecar", "sensitive", "replace", "retain", "failure"),
            route = HelpRoute.SETTINGS_PROJECTS,
        ),
        HelpEntry(
            id = "redact-safely",
            title = "Redact secrets safely",
            summary = "Use opaque Bar replacement for secrets because it destroys the covered pixels. Pixelate and Blur are cosmetic effects and may be partly reversible.",
            keywords = setOf("redact", "privacy", "secret", "opaque", "bar", "pixelate", "blur", "safe"),
            route = HelpRoute.EDITOR_REDACTION,
        ),
        HelpEntry(
            id = "remove-middle-content",
            title = "Cut out irrelevant rows or columns",
            summary = "Use Cut Out before perspective or free rotation. Add horizontal or vertical source bands, edit or remove them non-destructively, choose a straight, dashed, or torn seam, then use Preview to inspect the squeezed output. Retained annotations move with their pixels when exported.",
            keywords = setOf("cut", "squeeze", "remove", "middle", "band", "seam", "long", "screenshot", "preview"),
            route = HelpRoute.EDITOR_CUTOUT,
        ),
        HelpEntry(
            id = "troubleshoot-long-screenshot",
            title = "Fix Long Screenshot capture",
            summary = "Enable the disclosed Long Screenshot Accessibility service, return to the target app, and start from its Quick Settings tile. Retry from review when scrolling stalls or a seam is wrong.",
            keywords = setOf("long", "screenshot", "scroll", "accessibility", "tile", "stitch", "seam", "retry"),
            route = HelpRoute.HOME_LONG_SCREENSHOT,
        ),
        HelpEntry(
            id = "troubleshoot-delayed-capture",
            title = "Fix Delayed Capture",
            summary = "Grant full photo access so monitoring can see the next screenshot, choose 3, 5, or 10 seconds, then stay in the target app until Android captures it. Notifications are the fallback when overlays are unavailable.",
            keywords = setOf("delay", "timer", "capture", "permission", "monitor", "overlay", "notification"),
            route = HelpRoute.HOME_PERMISSIONS,
        ),
        HelpEntry(
            id = "secure-preview-capture-conflict",
            title = "Fix blank protected previews",
            summary = "Protect media previews blocks screenshots, recording, Recents, overlays, and notification pixels. Disable it temporarily in Settings only when you intentionally need to capture SnapCrop itself.",
            keywords = setOf("secure", "protect", "blank", "preview", "screenshot", "recording", "recents"),
            route = HelpRoute.HOME_PERMISSIONS,
        ),
        HelpEntry(
            id = "troubleshoot-step-capture",
            title = "Fix Step Capture",
            summary = "Enable the separate Step Capture Accessibility service and start its Quick Settings tile before tapping through the task. Stop from the tile or ongoing notification to build the guide.",
            keywords = setOf("step", "capture", "guide", "accessibility", "tile", "notification", "stop", "troubleshoot"),
            route = HelpRoute.HOME_STEP_CAPTURE,
        ),
        HelpEntry(
            id = "troubleshoot-static-web-capture",
            title = "Fix static web capture",
            summary = "Enter a public HTTPS page. SnapCrop renders the bounded main document offline without scripts or external resources; use Long Screenshot when the page is too large or needs live content.",
            keywords = setOf("web", "url", "https", "page", "offline", "script", "large", "troubleshoot"),
            route = HelpRoute.HOME_WEB_CAPTURE,
        ),
        HelpEntry(
            id = "share-metadata-and-source-link",
            title = "Control share metadata and source links",
            summary = "Review the metadata categories before sharing, choose Strip all, Keep safe, or Preserve, and include the explicit source link only when the destination should receive it.",
            keywords = setOf("share", "metadata", "exif", "location", "source", "link", "strip", "privacy"),
            route = HelpRoute.SHARE_PRIVACY,
        ),
        HelpEntry(
            id = "save-and-reopen-projects",
            title = "Save and reopen editable projects",
            summary = "Enable project sidecars before saving to keep crop, redaction, adjustment, and annotation state. Open the .snapcrop.json file later and relink only to a source with the expected fingerprint.",
            keywords = setOf("project", "sidecar", "reopen", "editable", "relink", "fingerprint", "json"),
            route = HelpRoute.SETTINGS_PROJECTS,
        ),
        HelpEntry(
            id = "filter-gallery",
            title = "Filter the Gallery",
            summary = "Combine media, source, category, date, orientation, dimensions, favorite, and format filters. Clear all filters to return to the full album without changing any files.",
            keywords = setOf("gallery", "filter", "source", "category", "date", "orientation", "favorite", "format"),
            route = HelpRoute.GALLERY_FILTERS,
        ),
        HelpEntry(
            id = "organize-gallery-collections",
            title = "Organize manual collections",
            summary = "Select supported screenshots and add them to a named collection. Membership organizes the existing media without moving or duplicating its files.",
            keywords = setOf("gallery", "collection", "album", "organize", "select", "membership", "move", "duplicate"),
            route = HelpRoute.GALLERY_COLLECTIONS,
        ),
    )

    fun search(query: String): List<HelpEntry> {
        val normalizedQuery = query.normalizedSearchText()
        if (normalizedQuery.isEmpty()) return entries
        val queryTokens = normalizedQuery.tokens().distinct()
        if (queryTokens.isEmpty()) return entries

        return entries.mapIndexedNotNull { usefulOrder, entry ->
            val title = entry.title.normalizedSearchText()
            val summary = entry.summary.normalizedSearchText()
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
