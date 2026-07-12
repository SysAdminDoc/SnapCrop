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
        assertEquals(RedactionStyle.BLUR, RedactionStyle.fromPreference("blur"))
    }

    @Test
    fun `mixed editable regions render enabled styles and preserve disabled pixels`() {
        val source = patternedBitmap(96, 64)
        val disabled = region("disabled", Rect(4, 4, 28, 28), RedactionStyle.SOLID, enabled = false)
        val pixelate = region("pixelate", Rect(32, 4, 64, 36), RedactionStyle.PIXELATE)
        val blur = region("blur", Rect(64, 4, 94, 36), RedactionStyle.BLUR)

        val output = ImageRedactor.render(source, listOf(disabled, pixelate, blur))

        assertEquals(source.getPixel(10, 10), output.getPixel(10, 10))
        assertNotEquals(source.getPixel(40, 12), output.getPixel(40, 12))
        assertNotEquals(source.getPixel(72, 12), output.getPixel(72, 12))
    }

    @Test
    fun `opaque bars render after cosmetic overlaps`() {
        val source = patternedBitmap(64, 64)
        val overlap = Rect(12, 12, 52, 52)

        val output = ImageRedactor.render(
            source,
            listOf(
                region("bar", overlap, RedactionStyle.SOLID),
                region("blur", overlap, RedactionStyle.BLUR),
                region("pixels", overlap, RedactionStyle.PIXELATE)
            )
        )

        for (y in overlap.top until overlap.bottom) {
            for (x in overlap.left until overlap.right) assertEquals(Color.BLACK, output.getPixel(x, y))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid enabled region aborts render`() {
        val source = patternedBitmap(32, 32)
        ImageRedactor.render(source, listOf(region("outside", Rect(20, 20, 40, 40), RedactionStyle.SOLID)))
    }

    private fun region(
        id: String,
        bounds: Rect,
        style: RedactionStyle,
        enabled: Boolean = true
    ) = RedactionRegion(
        id = id,
        bounds = bounds,
        categories = setOf(RedactionCategory.MANUAL),
        source = RedactionSource.MANUAL,
        style = style,
        enabled = enabled
    )

    private fun patternedBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(x, y, Color.rgb(32 + x * 5, 48 + y * 5, 96 + (x + y) * 2))
                }
            }
        }
}
