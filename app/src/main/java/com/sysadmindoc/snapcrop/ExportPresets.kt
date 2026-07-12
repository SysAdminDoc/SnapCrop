package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

enum class ExportImageFormat(val key: String) {
    PNG("png"), JPEG("jpeg"), WEBP("webp");

    companion object {
        fun fromKey(value: String?): ExportImageFormat = entries.firstOrNull { it.key == value } ?: PNG
    }
}

data class ExportSettings(
    val format: ExportImageFormat = ExportImageFormat.PNG,
    val quality: Int = 95,
    val targetSizeEnabled: Boolean = false,
    val targetSizeKb: Int = 500,
    val borderSize: Int = 0,
    val borderColor: Int = 0,
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "SnapCrop",
    val filenameTemplate: String = "SnapCrop_%timestamp%",
    val savePath: String = "Pictures/SnapCrop"
)

data class ExportPreset(val id: String, val name: String, val settings: ExportSettings)

object ExportPresetStore {
    const val PREF_EDITOR_PRESET_ID = "export_preset_editor"
    const val PREF_QUICK_PRESET_ID = "export_preset_quick"
    private const val PREF_PRESETS = "export_presets_json"
    private const val SCHEMA_VERSION = 1
    private const val MAX_PRESETS = 20
    private const val MAX_JSON_CHARS = 256 * 1024
    private val validId = Regex("^[a-zA-Z0-9-]{1,80}$")
    private val validPaths = setOf("Pictures/SnapCrop", "DCIM/SnapCrop", "Downloads/SnapCrop")

    fun current(prefs: SharedPreferences): ExportSettings = normalize(
        ExportSettings(
            format = when {
                prefs.getBoolean("use_webp", false) -> ExportImageFormat.WEBP
                prefs.getBoolean("use_jpeg", false) -> ExportImageFormat.JPEG
                else -> ExportImageFormat.PNG
            },
            quality = prefs.getInt("jpeg_quality", 95),
            targetSizeEnabled = prefs.getBoolean("target_size_enabled", false),
            targetSizeKb = prefs.getInt("target_size_kb", 500),
            borderSize = prefs.getInt("border_size", 0),
            borderColor = prefs.getInt("border_color", 0),
            watermarkEnabled = prefs.getBoolean("watermark_enabled", false),
            watermarkText = prefs.getString("watermark_text", "SnapCrop").orEmpty(),
            filenameTemplate = prefs.getString("filename_template", "SnapCrop_%timestamp%").orEmpty(),
            savePath = prefs.getString("save_path", "Pictures/SnapCrop").orEmpty()
        )
    )

    fun resolve(prefs: SharedPreferences, presetId: String?): ExportSettings =
        load(prefs).firstOrNull { it.id == presetId }?.settings ?: current(prefs)

    fun load(prefs: SharedPreferences): List<ExportPreset> {
        val raw = prefs.getString(PREF_PRESETS, null) ?: return emptyList()
        if (raw.length > MAX_JSON_CHARS) return emptyList()
        return try {
            val root = JSONObject(raw)
            if (root.optInt("version", 0) != SCHEMA_VERSION) return emptyList()
            val array = root.getJSONArray("presets")
            buildList {
                val seenIds = mutableSetOf<String>()
                val seenNames = mutableSetOf<String>()
                for (index in 0 until minOf(array.length(), MAX_PRESETS)) {
                    val item = array.getJSONObject(index)
                    val id = item.optString("id").takeIf(validId::matches) ?: continue
                    val name = normalizeName(item.optString("name")) ?: continue
                    if (!seenIds.add(id) || !seenNames.add(name.lowercase())) continue
                    add(ExportPreset(id, name, item.getJSONObject("settings").toSettings()))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun upsertCurrent(prefs: SharedPreferences, name: String, presetId: String? = null): List<ExportPreset>? {
        val normalizedName = normalizeName(name) ?: return null
        val existing = load(prefs).toMutableList()
        val editingIndex = presetId?.let { id -> existing.indexOfFirst { it.id == id } } ?: -1
        if (existing.any { it.name.equals(normalizedName, true) && it.id != presetId }) return null
        if (editingIndex < 0 && existing.size >= MAX_PRESETS) return null
        val preset = ExportPreset(
            id = if (editingIndex >= 0) existing[editingIndex].id else "preset-${UUID.randomUUID()}",
            name = normalizedName,
            settings = current(prefs)
        )
        if (editingIndex >= 0) existing[editingIndex] = preset else existing += preset
        save(prefs, existing)
        return existing
    }

    fun delete(prefs: SharedPreferences, presetId: String): List<ExportPreset> {
        val updated = load(prefs).filterNot { it.id == presetId }
        save(prefs, updated)
        val editor = prefs.edit()
        if (prefs.getString(PREF_EDITOR_PRESET_ID, null) == presetId) editor.remove(PREF_EDITOR_PRESET_ID)
        if (prefs.getString(PREF_QUICK_PRESET_ID, null) == presetId) editor.remove(PREF_QUICK_PRESET_ID)
        editor.apply()
        return updated
    }

    fun applyToCurrent(prefs: SharedPreferences, preset: ExportPreset) {
        val value = normalize(preset.settings)
        prefs.edit()
            .putBoolean("use_jpeg", value.format == ExportImageFormat.JPEG)
            .putBoolean("use_webp", value.format == ExportImageFormat.WEBP)
            .putInt("jpeg_quality", value.quality)
            .putBoolean("target_size_enabled", value.targetSizeEnabled)
            .putInt("target_size_kb", value.targetSizeKb)
            .putInt("border_size", value.borderSize)
            .putInt("border_color", value.borderColor)
            .putBoolean("watermark_enabled", value.watermarkEnabled)
            .putString("watermark_text", value.watermarkText)
            .putString("filename_template", value.filenameTemplate)
            .putString("save_path", value.savePath)
            .apply()
    }

    fun nextFilename(
        prefs: SharedPreferences,
        settings: ExportSettings,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val counter = prefs.getInt("save_counter", 1).coerceAtLeast(1)
        prefs.edit().putInt("save_counter", counter + 1).apply()
        val date = Date(nowMillis)
        return settings.filenameTemplate
            .replace("%timestamp%", nowMillis.toString())
            .replace("%date%", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date))
            .replace("%time%", SimpleDateFormat("HH-mm-ss", Locale.US).format(date))
            .replace("%counter%", counter.toString().padStart(4, '0'))
            .take(120)
    }

    private fun save(prefs: SharedPreferences, presets: List<ExportPreset>) {
        val array = JSONArray()
        presets.take(MAX_PRESETS).forEach { preset ->
            array.put(JSONObject()
                .put("id", preset.id)
                .put("name", preset.name)
                .put("settings", normalize(preset.settings).toJson()))
        }
        prefs.edit().putString(
            PREF_PRESETS,
            JSONObject().put("version", SCHEMA_VERSION).put("presets", array).toString()
        ).apply()
    }

    private fun normalize(settings: ExportSettings): ExportSettings = settings.copy(
        quality = settings.quality.coerceIn(50, 100),
        targetSizeEnabled = settings.targetSizeEnabled && settings.format != ExportImageFormat.PNG,
        targetSizeKb = settings.targetSizeKb.coerceIn(50, 5000),
        borderSize = settings.borderSize.coerceIn(0, 100),
        borderColor = settings.borderColor.coerceIn(0, 5),
        watermarkText = settings.watermarkText.take(120),
        filenameTemplate = sanitizeTemplate(settings.filenameTemplate),
        savePath = settings.savePath.takeIf(validPaths::contains) ?: "Pictures/SnapCrop"
    )

    private fun normalizeName(value: String): String? = value.trim()
        .replace(Regex("\\s+"), " ")
        .take(40)
        .takeIf { it.isNotBlank() && it.none(Char::isISOControl) }

    private fun sanitizeTemplate(value: String): String = value
        .replace(Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]"), "_")
        .take(96)
        .trim('.', ' ')
        .ifBlank { "SnapCrop_%timestamp%" }

    private fun ExportSettings.toJson(): JSONObject = JSONObject()
        .put("format", format.key)
        .put("quality", quality)
        .put("targetSizeEnabled", targetSizeEnabled)
        .put("targetSizeKb", targetSizeKb)
        .put("borderSize", borderSize)
        .put("borderColor", borderColor)
        .put("watermarkEnabled", watermarkEnabled)
        .put("watermarkText", watermarkText)
        .put("filenameTemplate", filenameTemplate)
        .put("savePath", savePath)

    private fun JSONObject.toSettings(): ExportSettings = normalize(ExportSettings(
        format = ExportImageFormat.fromKey(optString("format")),
        quality = optInt("quality", 95),
        targetSizeEnabled = optBoolean("targetSizeEnabled", false),
        targetSizeKb = optInt("targetSizeKb", 500),
        borderSize = optInt("borderSize", 0),
        borderColor = optInt("borderColor", 0),
        watermarkEnabled = optBoolean("watermarkEnabled", false),
        watermarkText = optString("watermarkText", "SnapCrop"),
        filenameTemplate = optString("filenameTemplate", "SnapCrop_%timestamp%"),
        savePath = optString("savePath", "Pictures/SnapCrop")
    ))
}

object ExportPresetRenderer {
    private val borderColors = intArrayOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF1E1E2E.toInt(),
        0xFF89B4FA.toInt(), 0xFFA6E3A1.toInt(), 0xFFF38BA8.toInt()
    )

    fun applyBorder(bitmap: Bitmap, settings: ExportSettings): Bitmap {
        if (settings.borderSize <= 0) return bitmap
        val size = settings.borderSize.coerceIn(0, 100)
        val result = Bitmap.createBitmap(bitmap.width + size * 2, bitmap.height + size * 2, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(borderColors[settings.borderColor.coerceIn(borderColors.indices)])
            drawBitmap(bitmap, size.toFloat(), size.toFloat(), null)
        }
        preserveUltraHdrGainmap(bitmap, result, Matrix().apply { postTranslate(size.toFloat(), size.toFloat()) })
        return result
    }

    fun applyWatermark(bitmap: Bitmap, settings: ExportSettings): Bitmap {
        val text = settings.watermarkText.take(120)
        if (!settings.watermarkEnabled || text.isBlank()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40FFFFFF
            textSize = (bitmap.width * 0.04f).coerceAtLeast(24f)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(-30f, bitmap.width / 2f, bitmap.height / 2f)
        val spacing = paint.textSize * 3
        val diagonal = sqrt((bitmap.width.toDouble() * bitmap.width + bitmap.height.toDouble() * bitmap.height)).toFloat()
        var y = -diagonal / 2
        while (y < diagonal * 1.5f) {
            var x = -diagonal / 2
            while (x < diagonal * 1.5f) {
                canvas.drawText(text, x, y, paint)
                x += paint.measureText(text) + spacing
            }
            y += spacing
        }
        canvas.restore()
        preserveUltraHdrGainmap(bitmap, result)
        return result
    }

    fun compressToTarget(bitmap: Bitmap, format: Bitmap.CompressFormat, targetKb: Int): Pair<ByteArray, Int> {
        var low = 10
        var high = 100
        var best: ByteArray? = null
        var bestQuality = 10
        while (low <= high) {
            val quality = (low + high) / 2
            val output = ByteArrayOutputStream()
            bitmap.compress(format, quality, output)
            val bytes = output.toByteArray()
            if (bytes.size <= targetKb.coerceIn(50, 5000) * 1024) {
                best = bytes
                bestQuality = quality
                low = quality + 1
            } else {
                high = quality - 1
            }
        }
        if (best == null) {
            val output = ByteArrayOutputStream()
            bitmap.compress(format, 10, output)
            best = output.toByteArray()
        }
        return requireNotNull(best) to bestQuality
    }
}
