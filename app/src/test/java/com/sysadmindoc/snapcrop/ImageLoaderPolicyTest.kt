package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ImageLoaderPolicyTest {
    @Test
    fun singletonUsesBoundedBackgroundCachePolicy() {
        assertEquals(0.25, SnapCropApp.BACKGROUND_IMAGE_CACHE_PERCENT, 0.0)
        val loader = SnapCropApp().newImageLoader(RuntimeEnvironment.getApplication())
        try {
            assertTrue(loader.memoryCache != null)
        } finally {
            loader.shutdown()
        }
    }
}
