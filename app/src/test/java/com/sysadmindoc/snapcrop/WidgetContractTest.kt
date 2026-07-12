package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetContractTest {
    @Test
    fun manifestAndProviderExposeThreePrivateShortcutActions() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val provider = File("src/main/java/com/sysadmindoc/snapcrop/SnapCropWidgetProvider.kt").readText()
        val layout = File("src/main/res/layout/widget_snapcrop.xml").readText()

        assertTrue(manifest.contains(".SnapCropWidgetProvider"))
        assertTrue(manifest.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        assertTrue(provider.contains("ACTION_EDIT_LATEST"))
        assertTrue(provider.contains("ACTION_QUICK_CROP"))
        assertTrue(provider.contains("ACTION_GALLERY"))
        assertTrue(layout.contains("@+id/widget_latest"))
        assertTrue(layout.contains("@+id/widget_quick_crop"))
        assertTrue(layout.contains("@+id/widget_gallery"))
    }

    @Test
    fun widgetNeverLoadsOrPublishesMediaPixels() {
        val provider = File("src/main/java/com/sysadmindoc/snapcrop/SnapCropWidgetProvider.kt").readText()
        val layout = File("src/main/res/layout/widget_snapcrop.xml").readText()

        assertFalse(provider.contains("setImageViewBitmap"))
        assertFalse(provider.contains("BitmapFactory"))
        assertFalse(provider.contains("MediaStore"))
        assertFalse(layout.contains("widget_thumbnail"))
    }
}
