package com.sysadmindoc.snapcrop

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.StringRes
import java.text.Normalizer
import java.util.Locale

enum class SettingsDestination(val wireValue: String) {
    THEME("theme"),
    RECENT_WORKFLOWS("recent_workflows"),
    DELETE_ORIGINAL("delete_original"),
    PROJECT_SIDECARS("project_sidecars"),
    OCR_TEXT_SIDECARS("ocr_text_sidecars"),
    APP_CROP_PROFILES("app_crop_profiles"),
    SCREENSHOT_INDEX("screenshot_index"),
    IMAGE_FORMAT("image_format"),
    TARGET_SIZE("target_size"),
    STRIP_EXIF("strip_exif"),
    OCR_SCRIPT("ocr_script"),
    ML_MODELS("ml_models"),
    FILENAME_TEMPLATE("filename_template"),
    ANNOTATION_PRESETS("annotation_presets"),
    NETWORK_EXPORTS("network_exports"),
    LOCAL_NETWORK("local_network"),
    WATERMARK("watermark"),
    EXPORT_BORDER("export_border"),
    SAVE_LOCATION("save_location"),
    EXPORT_PRESETS("export_presets"),
    AUTO_START("auto_start"),
    CONDITIONAL_AUTO_ACTIONS("conditional_auto_actions"),
    REDACT_ON_SHARE("redact_on_share"),
    CUSTOM_PATTERNS("custom_patterns"),
    REDACTION_STYLE("redaction_style"),
    SECURE_EDITOR("secure_editor"),
    STORAGE("storage"),
    BACKUP("backup"),
    ABOUT("about"),
    AUTO_UPDATE("auto_update"),
    OPERATION_JOURNAL("operation_journal"),
    CRASH_LOGS("crash_logs");

    companion object {
        fun fromWireValue(value: String?): SettingsDestination? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class SettingsSearchEntry(
    val destination: SettingsDestination,
    val title: String,
    val summary: String,
    private val keywords: String,
    val resetKeys: Set<String> = emptySet(),
) {
    internal val searchableText: String = normalizeSearchText("$title $summary $keywords")
}

internal data class SettingsNavigationState(
    val revealedDestination: SettingsDestination? = null,
    val requestedDestination: SettingsDestination? = null,
    val highlightedDestination: SettingsDestination? = null,
) {
    fun open(destination: SettingsDestination): SettingsNavigationState = copy(
        revealedDestination = destination,
        requestedDestination = destination,
    )

    fun highlight(destination: SettingsDestination): SettingsNavigationState =
        if (requestedDestination == destination) copy(highlightedDestination = destination) else this

    fun complete(destination: SettingsDestination): SettingsNavigationState =
        if (requestedDestination == destination) {
            copy(requestedDestination = null, highlightedDestination = null)
        } else {
            this
        }

    companion object {
        fun initial(destination: SettingsDestination?): SettingsNavigationState = SettingsNavigationState(
            revealedDestination = destination,
            requestedDestination = destination,
        )
    }
}

object SettingsRegistry {
    const val EXTRA_DESTINATION = "com.sysadmindoc.snapcrop.extra.SETTINGS_DESTINATION"

    private val appearanceResetKeys = setOf("theme")
    private val saveResetKeys = setOf(
        "delete_original",
        "project_sidecars",
        "ocr_text_sidecars",
        "app_crop_profiles",
    )
    private val formatResetKeys = setOf(
        "use_jpeg",
        "use_webp",
        "jpeg_quality",
        "target_size_enabled",
        "target_size_kb",
        "target_size_allow_resize",
        "strip_exif",
        OcrScript.PREF_KEY,
        "filename_template",
    )
    private val watermarkResetKeys = setOf("watermark_enabled", "watermark_text")
    private val borderResetKeys = setOf("border_size", "border_color")
    private val locationResetKeys = setOf("save_path")
    private val serviceResetKeys = setOf(
        "auto_start",
        "conditional_auto_actions",
        "redact_on_share",
        ImageRedactor.PREF_REDACTION_STYLE,
        SecurePreviewPolicy.PREF_ENABLED,
    )

    fun intent(context: Context, destination: SettingsDestination): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(EXTRA_DESTINATION, destination.wireValue)

    fun destination(intent: Intent?): SettingsDestination? =
        SettingsDestination.fromWireValue(intent?.getStringExtra(EXTRA_DESTINATION))

    fun entries(context: Context): List<SettingsSearchEntry> = listOf(
        entry(context, SettingsDestination.THEME, R.string.settings_theme_title,
            R.string.settings_search_summary_appearance, R.string.settings_search_keywords_appearance, appearanceResetKeys),
        entry(context, SettingsDestination.RECENT_WORKFLOWS, R.string.settings_recent_workflows_title,
            R.string.settings_recent_workflows_subtitle, R.string.settings_search_keywords_appearance),
        entry(context, SettingsDestination.DELETE_ORIGINAL, R.string.settings_replace_title,
            R.string.settings_replace_subtitle, R.string.settings_search_keywords_save, saveResetKeys),
        entry(context, SettingsDestination.PROJECT_SIDECARS, R.string.settings_sidecar_title,
            R.string.settings_sidecar_subtitle, R.string.settings_search_keywords_save, saveResetKeys),
        entry(context, SettingsDestination.OCR_TEXT_SIDECARS, R.string.settings_ocr_text_sidecar_title,
            R.string.settings_ocr_text_sidecar_subtitle, R.string.settings_search_keywords_save, saveResetKeys),
        entry(context, SettingsDestination.APP_CROP_PROFILES, R.string.settings_profiles_title,
            R.string.settings_profiles_subtitle, R.string.settings_search_keywords_intelligence, saveResetKeys),
        entry(context, SettingsDestination.SCREENSHOT_INDEX, R.string.settings_index_title,
            R.string.settings_index_subtitle, R.string.settings_search_keywords_intelligence),
        entry(context, SettingsDestination.IMAGE_FORMAT, R.string.settings_section_format,
            R.string.settings_format_hint, R.string.settings_search_keywords_format, formatResetKeys),
        entry(context, SettingsDestination.TARGET_SIZE, R.string.settings_target_size_title,
            R.string.settings_search_target_size_summary, R.string.settings_search_keywords_format, formatResetKeys),
        entry(context, SettingsDestination.STRIP_EXIF, R.string.settings_strip_title,
            R.string.settings_strip_subtitle, R.string.settings_search_keywords_format, formatResetKeys),
        entry(context, SettingsDestination.OCR_SCRIPT, R.string.settings_ocr_script_title,
            R.string.settings_ocr_script_subtitle, R.string.settings_search_keywords_intelligence, formatResetKeys),
        entry(context, SettingsDestination.ML_MODELS, R.string.ml_models_title,
            R.string.ml_models_summary, R.string.ml_models_search_keywords),
        entry(context, SettingsDestination.FILENAME_TEMPLATE, R.string.settings_filename_title,
            R.string.settings_filename_hint, R.string.settings_search_keywords_format, formatResetKeys),
        entry(context, SettingsDestination.ANNOTATION_PRESETS, R.string.settings_section_presets,
            R.string.settings_presets_body, R.string.settings_search_keywords_presets),
        entry(context, SettingsDestination.NETWORK_EXPORTS, R.string.settings_network_title,
            R.string.settings_network_subtitle, R.string.settings_search_keywords_network),
        entry(context, SettingsDestination.LOCAL_NETWORK, R.string.settings_local_network_title,
            R.string.settings_local_network_body, R.string.settings_search_keywords_network),
        entry(context, SettingsDestination.WATERMARK, R.string.settings_watermark_title,
            R.string.settings_watermark_subtitle, R.string.settings_search_keywords_watermark, watermarkResetKeys),
        entry(context, SettingsDestination.EXPORT_BORDER, R.string.settings_section_border,
            R.string.settings_border_hint, R.string.settings_search_keywords_border, borderResetKeys),
        entry(context, SettingsDestination.SAVE_LOCATION, R.string.settings_section_location,
            R.string.settings_location_hint, R.string.settings_search_keywords_location, locationResetKeys),
        entry(context, SettingsDestination.EXPORT_PRESETS, R.string.settings_export_presets_title,
            R.string.settings_export_presets_hint, R.string.settings_search_keywords_presets),
        entry(context, SettingsDestination.AUTO_START, R.string.settings_autostart_title,
            R.string.settings_autostart_subtitle, R.string.settings_search_keywords_service, serviceResetKeys),
        entry(context, SettingsDestination.CONDITIONAL_AUTO_ACTIONS, R.string.settings_autoactions_title,
            R.string.settings_autoactions_subtitle, R.string.settings_search_keywords_service, serviceResetKeys),
        entry(context, SettingsDestination.REDACT_ON_SHARE, R.string.settings_redact_share_title,
            R.string.settings_redact_share_subtitle, R.string.settings_search_keywords_service, serviceResetKeys),
        entry(context, SettingsDestination.CUSTOM_PATTERNS, R.string.settings_custom_patterns_title,
            R.string.settings_search_custom_patterns_summary, R.string.settings_search_keywords_service),
        entry(context, SettingsDestination.REDACTION_STYLE, R.string.settings_redaction_style_title,
            R.string.settings_redaction_style_subtitle, R.string.settings_search_keywords_service, serviceResetKeys),
        entry(context, SettingsDestination.SECURE_EDITOR, R.string.settings_secure_editor_title,
            R.string.settings_secure_editor_subtitle, R.string.settings_search_keywords_service, serviceResetKeys),
        entry(context, SettingsDestination.STORAGE, R.string.settings_clear_cache,
            R.string.settings_clear_cache_subtitle, R.string.settings_search_keywords_storage),
        entry(context, SettingsDestination.BACKUP, R.string.settings_section_backup,
            R.string.settings_backup_hint, R.string.settings_search_keywords_backup),
        entry(context, SettingsDestination.ABOUT, R.string.settings_section_about,
            R.string.settings_about_tagline, R.string.settings_search_keywords_about),
        entry(context, SettingsDestination.AUTO_UPDATE, R.string.settings_update_auto_title,
            R.string.settings_update_auto_subtitle, R.string.settings_search_keywords_about),
        entry(context, SettingsDestination.OPERATION_JOURNAL, R.string.settings_journal_title,
            R.string.settings_journal_subtitle, R.string.settings_search_keywords_about),
        entry(context, SettingsDestination.CRASH_LOGS, R.string.settings_crash_title,
            R.string.settings_search_crash_logs_summary, R.string.settings_search_keywords_about),
    )

    fun search(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
        val tokens = normalizeSearchText(query).split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) return emptyList()
        return entries.filter { entry -> tokens.all(entry.searchableText::contains) }
    }

    /** Atomically removes only the registry's explicit, non-secret preference allowlist. */
    fun reset(prefs: SharedPreferences, entry: SettingsSearchEntry): Boolean {
        if (entry.resetKeys.isEmpty()) return false
        return prefs.edit().apply { entry.resetKeys.forEach(::remove) }.commit()
    }

    private fun entry(
        context: Context,
        destination: SettingsDestination,
        @StringRes title: Int,
        @StringRes summary: Int,
        @StringRes keywords: Int,
        resetKeys: Set<String> = emptySet(),
    ) = SettingsSearchEntry(
        destination = destination,
        title = context.getString(title),
        summary = context.getString(summary),
        keywords = context.getString(keywords),
        resetKeys = resetKeys,
    )
}

private fun normalizeSearchText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
