package com.sysadmindoc.snapcrop

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaIndexSyncInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun grantImagePermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.READ_MEDIA_IMAGES,
        )
    }

    @Test
    fun generationSyncAndIdentityFallbackConvergeAfterAddModifyAndDelete() = runBlocking<Unit> {
        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_incremental_seed.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapCropIndexTest/")
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
            )
        )
        try {
            resolver.openOutputStream(uri)?.use { output ->
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.BLUE)
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    bitmap.recycle()
                }
            }
            val store = ScreenshotIndexStore(context)
            store.purge()

            assertTrue(store.rebuildFromMediaStore(resolver, 1080, 2400, emptySet()) >= 1)
            assertEquals("Screenshot_incremental_seed.png", store.loadEntryMap()[uri.toString()]?.name)

            assertEquals(
                1,
                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_incremental_modified.png")
                    },
                    null,
                    null,
                ),
            )
            store.syncFromMediaStore(resolver, 1080, 2400, emptySet())
            assertEquals("Screenshot_incremental_modified.png", store.loadEntryMap()[uri.toString()]?.name)

            assertEquals(1, resolver.delete(uri, null, null))
            store.syncFromMediaStore(resolver, 1080, 2400, emptySet())
            assertFalse(store.loadEntryMap().containsKey(uri.toString()))
        } finally {
            runCatching { resolver.delete(uri, null, null) }
            ScreenshotIndexStore(context).purge()
        }
    }
}
