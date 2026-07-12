package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Gainmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class UltraHdrRoundTripTest {
    @Test
    fun jpegRoundTripRetainsGainmapAfterPixelEdit() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val source = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.DKGRAY)
            gainmap = Gainmap(Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            })
        }
        val edited = source.copy(Bitmap.Config.ARGB_8888, true)
        edited.setPixel(4, 4, Color.RED)
        preserveUltraHdrGainmap(source, edited)
        val bytes = ByteArrayOutputStream().use { output ->
            assertTrue(edited.compress(Bitmap.CompressFormat.JPEG, 95, output))
            output.toByteArray()
        }

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertTrue(decoded.hasGainmap())
    }
}
