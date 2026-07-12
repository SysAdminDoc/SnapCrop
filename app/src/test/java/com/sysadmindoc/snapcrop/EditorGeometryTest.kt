package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorGeometryTest {
    @Test
    fun straightenKeepsEditorCoordinateFrameSize() {
        val bitmap = Bitmap.createBitmap(31, 21, Bitmap.Config.ARGB_8888)

        val straightened = createEditorSpaceStraightenedBitmap(bitmap, 17f)

        assertEquals(bitmap.width, straightened.width)
        assertEquals(bitmap.height, straightened.height)
    }

    @Test
    fun straightenKeepsCenterContentInEditorCoordinates() {
        val bitmap = Bitmap.createBitmap(31, 31, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        for (y in 13..17) {
            for (x in 13..17) {
                bitmap.setPixel(x, y, Color.RED)
            }
        }

        val straightened = createEditorSpaceStraightenedBitmap(bitmap, 35f)
        val center = straightened.getPixel(15, 15)

        assertTrue(Color.red(center) > 180)
        assertTrue(Color.green(center) < 80)
        assertTrue(Color.blue(center) < 80)
    }
}
