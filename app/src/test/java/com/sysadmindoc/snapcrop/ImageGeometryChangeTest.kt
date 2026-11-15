package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #4 follow-up: rotate, flip and resize swapped the bitmap but left redactions, drawings and
 * the crop in the old pixel grid, so a redaction stopped covering what it was drawn over.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImageGeometryChangeTest {
    private val w = 40
    private val h = 60
    private val secret = Rect(5, 40, 22, 47)

    @Test
    fun rotatedRectCoversExactlyThePixelsTheBitmapRotationMoves() {
        val change = ImageGeometryChange.RotateClockwise(w, h)
        val rotated = Bitmap.createBitmap(sourceWithSecret(), 0, 0, w, h, Matrix().apply { postRotate(90f) }, true)
        assertEquals(h, rotated.width)
        assertEquals(w, rotated.height)
        assertEquals(redPixels(rotated), change.mapRect(secret))
    }

    @Test
    fun flippedRectsCoverExactlyThePixelsTheBitmapMirrorMoves() {
        for (horizontal in listOf(true, false)) {
            val matrix = Matrix().apply { if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f) }
            val flipped = Bitmap.createBitmap(sourceWithSecret(), 0, 0, w, h, matrix, true)
            assertEquals(redPixels(flipped), ImageGeometryChange.Flip(w, h, horizontal).mapRect(secret))
        }
    }

    @Test
    fun downscaledRectStillCoversEverySecretPixel() {
        val change = ImageGeometryChange.resizeFor(w, h, 25)!!
        assertEquals(16, change.targetWidth)
        assertEquals(25, change.targetHeight)
        val scaled = Bitmap.createScaledBitmap(sourceWithSecret(), change.targetWidth, change.targetHeight, true)
        val mapped = change.mapRect(secret)
        val tinted = tintedPixels(scaled)
        assertTrue("no tinted pixels survived the downscale", tinted.isNotEmpty())
        tinted.forEach { (x, y) -> assertTrue("($x, $y) escaped $mapped", mapped.contains(x, y)) }
    }

    @Test
    fun rectsStayInsideTheNewImageWithAtLeastOnePixel() {
        val edge = ImageGeometryChange.Scale(1000, 1000, 3, 3).mapRect(Rect(998, 998, 1000, 1000))
        assertEquals(Rect(2, 2, 3, 3), edge)
        val sliver = ImageGeometryChange.Scale(1000, 1000, 10, 10).mapRect(Rect(0, 0, 1, 1))
        assertEquals(Rect(0, 0, 1, 1), sliver)
    }

    @Test
    fun resizeIsSkippedWhenTheImageAlreadyFits() {
        assertNull(ImageGeometryChange.resizeFor(400, 800, 800))
        assertNull(ImageGeometryChange.resizeFor(400, 800, 1080))
        val plan = ImageGeometryChange.resizeFor(1080, 2400, 1080)!!
        assertEquals(486, plan.targetWidth)
        assertEquals(1080, plan.targetHeight)
    }

    @Test
    fun redactionKeepsItsIdentityAndMovesWithThePicture() {
        val region = RedactionRegion("link", secret, setOf(RedactionCategory.MANUAL), RedactionSource.MANUAL, RedactionStyle.PIXELATE, enabled = false)
        val turned = ImageGeometryChange.RotateClockwise(w, h).mapRedaction(region)
        assertEquals(region.copy(bounds = Rect(13, 5, 20, 22)), turned)
    }

    @Test
    fun strokesTurnPointByPointAndKeepTheirLayerTransformAimed() {
        val stroke = DrawPath(
            points = listOf(PointF(2f, 3f), PointF(10f, 20f)),
            color = Color.RED,
            strokeWidth = 4f,
            shapeType = "arrow",
            controlPoint = PointF(6f, 8f),
            transOffsetX = 5f,
            transOffsetY = -2f,
            transScale = 1.5f,
            transRotation = 30f,
        )
        val turned = ImageGeometryChange.RotateClockwise(w, h).mapDrawPath(stroke)
        assertEquals(listOf(PointF(57f, 2f), PointF(40f, 10f)), turned.points)
        assertEquals(PointF(52f, 6f), turned.controlPoint)
        assertEquals(2f, turned.transOffsetX, 0f)
        assertEquals(5f, turned.transOffsetY, 0f)
        assertEquals(30f, turned.transRotation, 0f)
        assertEquals(1.5f, turned.transScale, 0f)

        val mirrored = ImageGeometryChange.Flip(w, h, horizontal = true).mapDrawPath(stroke)
        assertEquals(listOf(PointF(38f, 3f), PointF(30f, 20f)), mirrored.points)
        assertEquals(-5f, mirrored.transOffsetX, 0f)
        assertEquals(-2f, mirrored.transOffsetY, 0f)
        assertEquals(-30f, mirrored.transRotation, 0f)
    }

    @Test
    fun transformedStrokeLandsWhereTheMappedOriginalWould() {
        val stroke = DrawPath(
            points = listOf(PointF(4f, 6f), PointF(16f, 30f), PointF(9f, 12f)),
            color = Color.RED,
            strokeWidth = 4f,
            transOffsetX = 3f,
            transOffsetY = 7f,
            transScale = 1.25f,
            transRotation = 20f,
        )
        val changes = listOf(
            ImageGeometryChange.RotateClockwise(w, h),
            ImageGeometryChange.Flip(w, h, horizontal = true),
            ImageGeometryChange.Flip(w, h, horizontal = false),
            ImageGeometryChange.Scale(w, h, 20, 30),
        )
        for (change in changes) {
            val expected = stroke.transformedForPixelOperation().points.map { change.mapPoint(it.x, it.y) }
            val actual = change.mapDrawPath(stroke).transformedForPixelOperation().points
            expected.zip(actual).forEach { (e, a) ->
                assertEquals("${change::class.simpleName} x", e.x, a.x, 0.01f)
                assertEquals("${change::class.simpleName} y", e.y, a.y, 0.01f)
            }
        }
    }

    @Test
    fun labelsStayUprightAndKeepTheirCenterOnTheSameSpot() {
        val label = DrawPath(points = listOf(PointF(10f, 50f)), color = Color.RED, strokeWidth = 8f, shapeType = "callout", text = "1")
        val change = ImageGeometryChange.RotateClockwise(w, h)
        val turned = change.mapDrawPath(label)
        val before = with(DrawLayerArrangement) { label.visualBounds() }
        val after = with(DrawLayerArrangement) { turned.visualBounds() }
        val expected = change.mapPoint(before.centerX(), before.centerY())
        assertEquals(expected.x, after.centerX(), 0.01f)
        assertEquals(expected.y, after.centerY(), 0.01f)
        assertEquals(before.width(), after.width(), 0.01f)
        assertEquals(before.height(), after.height(), 0.01f)
    }

    @Test
    fun downscaleShrinksStrokesAndLabels() {
        val change = ImageGeometryChange.Scale(w, h, 20, 30)
        val stroke = DrawPath(points = listOf(PointF(10f, 10f), PointF(30f, 50f)), color = Color.RED, strokeWidth = 6f)
        val scaled = change.mapDrawPath(stroke)
        assertEquals(listOf(PointF(5f, 5f), PointF(15f, 25f)), scaled.points)
        assertEquals(3f, scaled.strokeWidth, 0f)
        val text = DrawPath(points = listOf(PointF(20f, 20f)), color = Color.RED, strokeWidth = 6f, shapeType = "text", text = "hi")
        assertEquals(3f, change.mapDrawPath(text).strokeWidth, 0f)
    }

    @Test
    fun perspectiveCornersAreRelabeledForTheNewOrientation() {
        val tl = PointF(1f, 2f); val tr = PointF(30f, 3f); val br = PointF(31f, 50f); val bl = PointF(2f, 49f)
        val quad = listOf(tl, tr, br, bl)
        for (change in listOf(
            ImageGeometryChange.RotateClockwise(w, h),
            ImageGeometryChange.Flip(w, h, horizontal = true),
            ImageGeometryChange.Flip(w, h, horizontal = false),
        )) {
            val (mtl, mtr, mbr, mbl) = change.mapQuad(quad)
            assertTrue("${change::class.simpleName} top-left is left of top-right", mtl.x < mtr.x)
            assertTrue("${change::class.simpleName} bottom-left is left of bottom-right", mbl.x < mbr.x)
            assertTrue("${change::class.simpleName} top-left is above bottom-left", mtl.y < mbl.y)
            assertTrue("${change::class.simpleName} top-right is above bottom-right", mtr.y < mbr.y)
            assertEquals(quad.map { change.mapPoint(it.x, it.y) }.toSet(), setOf(mtl, mtr, mbr, mbl))
        }
    }

    @Test
    fun cutBandsSwapAxisOnATurnAndMirrorOnAFlip() {
        val bands = listOf(CutBand(CutAxis.HORIZONTAL, 10, 20), CutBand(CutAxis.VERTICAL, 4, 8))
        assertEquals(
            listOf(CutBand(CutAxis.HORIZONTAL, 4, 8), CutBand(CutAxis.VERTICAL, 40, 50)),
            ImageGeometryChange.RotateClockwise(w, h).mapCutBands(bands),
        )
        assertEquals(
            listOf(CutBand(CutAxis.HORIZONTAL, 10, 20), CutBand(CutAxis.VERTICAL, 32, 36)),
            ImageGeometryChange.Flip(w, h, horizontal = true).mapCutBands(bands),
        )
        assertEquals(
            listOf(CutBand(CutAxis.HORIZONTAL, 40, 50), CutBand(CutAxis.VERTICAL, 4, 8)),
            ImageGeometryChange.Flip(w, h, horizontal = false).mapCutBands(bands),
        )
        assertEquals(
            listOf(CutBand(CutAxis.HORIZONTAL, 3, 7), CutBand(CutAxis.VERTICAL, 1, 3)),
            ImageGeometryChange.Scale(w, h, 13, 20).mapCutBands(bands),
        )
    }

    @Test
    fun snapshotsInTheHistoryFollowAndDropTheOldScan() {
        val region = RedactionRegion("r", secret, setOf(RedactionCategory.MANUAL), RedactionSource.MANUAL, RedactionStyle.SOLID)
        val snapshot = EditorSnapshot(
            crop = Rect(0, 0, w, h), bright = 0f, contr = 0f, sat = 0f, warm = 0f, vig = 0f, sharp = 0f,
            rotAngle = 7f, hi = 0f, sh = 0f, tilt = 0f, dn = 0f, gradBg = 0, filter = ImageFilter.entries.first(),
            redactions = listOf(region),
            draws = emptyList(),
            ocrBlocks = listOf(TextBlock("hello", Rect(1, 1, 5, 5))),
            ocrReviewed = true,
        )
        val history = EditorHistoryController()
        history.record(snapshot)
        history.record(snapshot.copy(rotAngle = 3f))
        history.undo(snapshot)
        val flip = ImageGeometryChange.Flip(w, h, horizontal = true)
        history.remap(flip::mapSnapshot)

        val undone = history.undoSnapshots.single()
        assertEquals(Rect(18, 40, 35, 47), undone.redactions.single().bounds)
        assertEquals(-7f, undone.rotAngle, 0f)
        assertTrue(undone.ocrBlocks.isEmpty())
        assertFalse(undone.ocrReviewed)
        assertEquals(1, history.redoSnapshots.size)
        assertEquals(Rect(18, 40, 35, 47), history.redoSnapshots.single().redactions.single().bounds)
    }

    @Test
    fun lockedRatiosTurnWithThePicture() {
        assertEquals(AspectRatio.RATIO_9_16, AspectRatio.RATIO_16_9.quarterTurned())
        assertEquals(AspectRatio.RATIO_4_3, AspectRatio.RATIO_3_4.quarterTurned())
        assertEquals(AspectRatio.FREE, AspectRatio.RATIO_21_9.quarterTurned())
        assertEquals(AspectRatio.FREE, AspectRatio.FREE.quarterTurned())
    }

    private fun sourceWithSecret(): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
        for (y in secret.top until secret.bottom) for (x in secret.left until secret.right) setPixel(x, y, Color.RED)
    }

    private fun redPixels(bitmap: Bitmap): Rect {
        var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = -1; var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) == Color.RED) {
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
            }
        }
        return Rect(left, top, right, bottom)
    }

    private fun tintedPixels(bitmap: Bitmap): List<Pair<Int, Int>> = buildList {
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.green(pixel) < 192) add(x to y)
        }
    }
}
