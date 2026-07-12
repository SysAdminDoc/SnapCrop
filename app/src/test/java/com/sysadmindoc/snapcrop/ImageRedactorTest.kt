package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class ImageRedactorTest {
    @Test
    fun `opaque redaction replaces every source pixel in bounds`() {
        val source = patternedBitmap(12, 10)
        val rect = Rect(2, 3, 9, 8)

        val redacted = ImageRedactor.redact(source, listOf(rect))

        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                assertEquals(Color.BLACK, redacted.getPixel(x, y))
                assertNotEquals(source.getPixel(x, y), redacted.getPixel(x, y))
            }
        }
        assertEquals(source.getPixel(0, 0), redacted.getPixel(0, 0))
        source.recycle()
        redacted.recycle()
    }

    @Test
    fun `opaque pixels remain replaced after lossless export round trip`() {
        val source = patternedBitmap(16, 16)
        val rect = Rect(4, 4, 12, 12)
        val redacted = ImageRedactor.opaque(source, listOf(rect))
        val bytes = ByteArrayOutputStream().use { output ->
            redacted.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                assertEquals(Color.BLACK, decoded.getPixel(x, y))
            }
        }
        source.recycle()
        redacted.recycle()
        decoded.recycle()
    }

    @Test
    fun `cosmetic pixelation remains an explicit non-default style`() {
        assertEquals(RedactionStyle.SOLID, RedactionStyle.fromPreference(null))
        assertEquals(RedactionStyle.PIXELATE, RedactionStyle.fromPreference("pixelate"))
    }

    private fun patternedBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(x, y, Color.rgb(32 + x * 5, 48 + y * 5, 96 + (x + y) * 2))
                }
            }
        }
}
