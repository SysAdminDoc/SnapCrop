package com.sysadmindoc.snapcrop

import android.content.Context
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FavoritesStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun clearPreferences() {
        context.getSharedPreferences("snapcrop_favorites", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun uriFavoritesKeepSameNumericImageAndVideoIdsIndependent() {
        val image = photo("content://media/external/images/media/42", isVideo = false)
        val video = photo("content://media/external/video/media/42", isVideo = true)

        assertTrue(FavoritesStore.toggle(context, video))
        assertTrue(FavoritesStore.isFavorite(context, video))
        assertFalse(FavoritesStore.isFavorite(context, image))

        assertTrue(FavoritesStore.toggle(context, image))
        assertTrue(FavoritesStore.isFavorite(context, image))
        assertTrue(FavoritesStore.isFavorite(context, video))

        assertFalse(FavoritesStore.toggle(context, video))
        assertTrue(FavoritesStore.isFavorite(context, image))
        assertFalse(FavoritesStore.isFavorite(context, video))
    }

    @Test
    fun legacyNumericFavoriteMigratesOnlyToScreenshotFirstImageIdentity() {
        context.getSharedPreferences("snapcrop_favorites", Context.MODE_PRIVATE)
            .edit().putBoolean("42", true).commit()
        val image = photo("content://media/external/images/media/42", isVideo = false)
        val video = photo("content://media/external/video/media/42", isVideo = true)

        assertTrue(FavoritesStore.isFavorite(context, image))
        assertFalse(FavoritesStore.isFavorite(context, video))
        assertFalse(FavoritesStore.toggle(context, image))
        assertFalse(FavoritesStore.isFavorite(context, image))
    }

    private fun photo(uri: String, isVideo: Boolean) = Photo(
        id = 42,
        uri = Uri.parse(uri),
        dateAdded = 1,
        isVideo = isVideo
    )
}
