package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoClipLoaderTest {
    @Test
    fun positiveDurationIsReadyWhileZeroAndExceptionsAreTypedFailures() {
        assertEquals(VideoMetadataResult.Ready(5_000), VideoClipLoader.metadata { 5_000 })
        assertEquals(
            VideoLoadFailure.INVALID_DURATION,
            (VideoClipLoader.metadata { 0 } as VideoMetadataResult.Failed).reason,
        )
        val thrown = VideoClipLoader.metadata { throw IOException("unavailable") } as VideoMetadataResult.Failed
        assertEquals(VideoLoadFailure.RETRIEVER_FAILURE, thrown.reason)
        assertEquals(IOException::class.java, thrown.cause?.javaClass)
    }

    @Test
    fun previewBitmapIsReadyWhileNullAndExceptionsAreTypedFailures() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val ready = VideoClipLoader.frame { bitmap } as VideoFrameResult.Ready
        assertSame(bitmap, ready.bitmap)
        assertEquals(
            VideoLoadFailure.FRAME_UNAVAILABLE,
            (VideoClipLoader.frame { null } as VideoFrameResult.Failed).reason,
        )
        assertTrue(VideoClipLoader.frame { throw IOException() } is VideoFrameResult.Failed)
        bitmap.recycle()
    }
}
