package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportPresetStoreTest {
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("export-preset-test", Context.MODE_PRIVATE)

    @Before
    fun reset() {
        prefs.edit().clear().commit()
    }

    @Test
    fun capturesResolvesAndAppliesAllExportSettings() {
        prefs.edit()
            .putBoolean("use_jpeg", true)
            .putInt("jpeg_quality", 88)
            .putBoolean("target_size_enabled", true)
            .putInt("target_size_kb", 750)
            .putInt("border_size", 24)
            .putInt("border_color", 3)
            .putBoolean("watermark_enabled", true)
            .putString("watermark_text", "Internal")
            .putString("filename_template", "Ticket_%counter%")
            .putString("save_path", "Downloads/SnapCrop")
            .commit()

        val presets = requireNotNull(ExportPresetStore.upsertCurrent(prefs, "Support ticket"))
        val preset = presets.single()
        assertEquals(ExportImageFormat.JPEG, preset.settings.format)
        assertEquals(88, preset.settings.quality)
        assertEquals(750, preset.settings.targetSizeKb)
        assertEquals(24, preset.settings.borderSize)
        assertEquals("Internal", preset.settings.watermarkText)
        assertEquals("Ticket_%counter%", preset.settings.filenameTemplate)
        assertEquals("Downloads/SnapCrop", preset.settings.savePath)
        assertEquals(preset.settings, ExportPresetStore.resolve(prefs, preset.id))

        prefs.edit().clear().commit()
        ExportPresetStore.applyToCurrent(prefs, preset)
        assertTrue(prefs.getBoolean("use_jpeg", false))
        assertFalse(prefs.getBoolean("use_webp", false))
        assertEquals(88, prefs.getInt("jpeg_quality", 0))
        assertEquals("Downloads/SnapCrop", prefs.getString("save_path", null))
    }

    @Test
    fun rejectsDuplicateOrBlankNamesAndClearsDeletedSelections() {
        val first = requireNotNull(ExportPresetStore.upsertCurrent(prefs, "Docs")).single()
        assertNull(ExportPresetStore.upsertCurrent(prefs, " docs "))
        assertNull(ExportPresetStore.upsertCurrent(prefs, "   "))
        prefs.edit()
            .putString(ExportPresetStore.PREF_EDITOR_PRESET_ID, first.id)
            .putString(ExportPresetStore.PREF_QUICK_PRESET_ID, first.id)
            .commit()

        assertTrue(ExportPresetStore.delete(prefs, first.id).isEmpty())
        assertNull(prefs.getString(ExportPresetStore.PREF_EDITOR_PRESET_ID, null))
        assertNull(prefs.getString(ExportPresetStore.PREF_QUICK_PRESET_ID, null))
    }

    @Test
    fun corruptPayloadFailsClosedAndValuesAreBounded() {
        prefs.edit().putString("export_presets_json", "not-json").commit()
        assertTrue(ExportPresetStore.load(prefs).isEmpty())
        prefs.edit().putString("export_presets_json", "{\"version\":99,\"presets\":[]}").commit()
        assertTrue(ExportPresetStore.load(prefs).isEmpty())
        prefs.edit()
            .putBoolean("use_webp", true)
            .putInt("jpeg_quality", 500)
            .putInt("target_size_kb", 99_999)
            .putInt("border_size", 500)
            .putString("save_path", "../../escape")
            .commit()

        val captured = ExportPresetStore.current(prefs)
        assertEquals(100, captured.quality)
        assertEquals(5000, captured.targetSizeKb)
        assertEquals(100, captured.borderSize)
        assertEquals("Pictures/SnapCrop", captured.savePath)
        assertEquals(captured, ExportPresetStore.resolve(prefs, "missing-preset"))
    }

    @Test
    fun filenameDecorationsAndTargetCompressionUseResolvedSnapshot() {
        val settings = ExportSettings(
            format = ExportImageFormat.JPEG,
            targetSizeEnabled = true,
            targetSizeKb = 50,
            borderSize = 3,
            borderColor = 1,
            watermarkEnabled = true,
            watermarkText = "Review",
            filenameTemplate = "Ticket_%date%_%time%_%counter%"
        )
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bordered = ExportPresetRenderer.applyBorder(source, settings)
        val watermarked = ExportPresetRenderer.applyWatermark(bordered, settings)
        val (bytes, quality) = ExportPresetRenderer.compressToTarget(watermarked, Bitmap.CompressFormat.JPEG, 50)

        assertEquals(70, bordered.width)
        assertEquals(Color.WHITE, bordered.getPixel(0, 0))
        assertNotSame(bordered, watermarked)
        assertTrue(bytes.size <= 50 * 1024)
        assertTrue(quality in 10..100)
        val filename = ExportPresetStore.nextFilename(prefs, settings, 1_700_000_000_000L)
        assertTrue(filename.matches(Regex("^Ticket_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_0001$")))

        watermarked.recycle()
        bordered.recycle()
        source.recycle()
    }
}
