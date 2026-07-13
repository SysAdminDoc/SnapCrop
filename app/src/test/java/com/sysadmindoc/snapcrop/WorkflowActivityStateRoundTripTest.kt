package com.sysadmindoc.snapcrop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WorkflowActivityStateRoundTripTest {
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<*>>()

    @After
    fun tearDown() {
        controllers.reversed().forEach { runCatching { it.pause().stop().destroy() } }
    }

    @Test
    fun stitchAndCollageRoundTripUriOrderAndOptionsWithinBundleBudget() {
        val uris = arrayListOf(mediaFile("first.png"), mediaFile("second.png"))

        val stitch = roundTrip(
            StitchActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), StitchActivity::class.java)
                .putParcelableArrayListExtra(InboundShareContract.EXTRA_URIS, uris),
        )
        assertEquals(uris.map(Uri::toString), stitch.getStringArrayList("stitch_uris"))
        assertTrue(stitch.getBoolean("stitch_vertical"))
        assertBundleBounded(stitch)

        val collage = roundTrip(
            CollageActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), CollageActivity::class.java)
                .putParcelableArrayListExtra(InboundShareContract.EXTRA_URIS, uris),
        )
        assertEquals(uris.map(Uri::toString), collage.getStringArrayList("collage_uris"))
        assertEquals("2x1", collage.getString("collage_layout"))
        assertBundleBounded(collage)
    }

    @Test
    fun frameVideoAndWebRoundTripIdentityWithoutPixelsOrWebViewState() {
        val image = mediaFile("frame.png")
        val frame = roundTrip(
            DeviceFrameActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), DeviceFrameActivity::class.java).setData(image),
        )
        assertEquals(listOf(image.toString()), frame.getStringArrayList("frame_image_uri"))
        assertEquals("pixel", frame.getString("frame_key"))
        assertBundleBounded(frame)

        val video = mediaFile("clip.mp4")
        val videoState = roundTrip(
            VideoClipActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), VideoClipActivity::class.java).setData(video),
        )
        assertEquals(video.toString(), videoState.getString("video_uri"))
        assertEquals(0L, videoState.getLong("video_frame_position"))
        assertBundleBounded(videoState)

        val web = roundTrip(
            WebCaptureActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), WebCaptureActivity::class.java)
                .putExtra(WebCaptureActivity.EXTRA_URL, "https://example.com/path"),
        )
        assertEquals("https://example.com/path", web.getString("web_input_url"))
        assertEquals(null, web.getString("web_loaded_url"))
        assertBundleBounded(web)
    }

    private fun mediaFile(name: String): Uri {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, name)
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return Uri.fromFile(file)
    }

    private fun <T : ComponentActivity> roundTrip(type: Class<T>, intent: Intent): Bundle {
        val first = Robolectric.buildActivity(type, intent).create().start().resume().also(controllers::add)
        val saved = Bundle()
        first.saveInstanceState(saved)
        first.pause().stop().destroy()
        controllers.remove(first)

        val restored = Robolectric.buildActivity(type, intent).create(saved).start().resume().also(controllers::add)
        val second = Bundle()
        restored.saveInstanceState(second)
        assertNotNull(second)
        return second
    }

    private fun assertBundleBounded(bundle: Bundle) {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            assertTrue("workflow saved state must stay below 64 KiB", parcel.dataSize() < 64 * 1024)
        } finally {
            parcel.recycle()
        }
    }
}
