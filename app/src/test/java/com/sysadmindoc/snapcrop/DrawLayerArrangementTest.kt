package com.sysadmindoc.snapcrop

import android.graphics.PointF
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DrawLayerArrangementTest {
    private val crop = Rect(0, 0, 300, 200)

    @Test
    fun alignmentUsesTransformedVisualBoundsAndLeavesUnselectedLayersUntouched() {
        val selected = layer(20f, 30f, 60f, 70f).copy(transOffsetX = 10f, transScale = 1.5f)
        val untouched = layer(100f, 100f, 140f, 140f)

        LayerAlignment.entries.forEach { alignment ->
            val result = DrawLayerArrangement.align(listOf(selected, untouched), setOf(0), crop, alignment)
            val bounds = with(DrawLayerArrangement) { result[0].visualBounds() }
            when (alignment) {
                LayerAlignment.LEFT -> assertEquals(crop.left.toFloat(), bounds.left, 0.01f)
                LayerAlignment.CENTER -> assertEquals(crop.exactCenterX(), bounds.centerX(), 0.01f)
                LayerAlignment.RIGHT -> assertEquals(crop.right.toFloat(), bounds.right, 0.01f)
                LayerAlignment.TOP -> assertEquals(crop.top.toFloat(), bounds.top, 0.01f)
                LayerAlignment.MIDDLE -> assertEquals(crop.exactCenterY(), bounds.centerY(), 0.01f)
                LayerAlignment.BOTTOM -> assertEquals(crop.bottom.toFloat(), bounds.bottom, 0.01f)
            }
            assertEquals(untouched, result[1])
        }
    }

    @Test
    fun alignmentTargetsNonZeroSourceCoordinateCrop() {
        val offsetCrop = Rect(100, 200, 500, 800)
        val aligned = DrawLayerArrangement.align(
            listOf(layer(20f, 30f, 60f, 70f)),
            setOf(0),
            offsetCrop,
            LayerAlignment.LEFT,
        )
        val topAligned = DrawLayerArrangement.align(
            aligned,
            setOf(0),
            offsetCrop,
            LayerAlignment.TOP,
        )
        val bounds = with(DrawLayerArrangement) { topAligned.single().visualBounds() }

        assertEquals(100f, bounds.left, 0.01f)
        assertEquals(200f, bounds.top, 0.01f)
    }

    @Test
    fun distributionCreatesEqualCenterSpacingInBothAxes() {
        val layers = listOf(
            layer(10f, 10f, 30f, 30f),
            layer(80f, 45f, 100f, 65f),
            layer(240f, 160f, 260f, 180f),
            layer(150f, 90f, 170f, 110f),
        )
        val selected = setOf(0, 1, 2, 3)

        LayerDistribution.entries.forEach { axis ->
            val result = DrawLayerArrangement.distribute(layers, selected, crop, axis)
            val centers = result.map { layer ->
                with(DrawLayerArrangement) {
                    if (axis == LayerDistribution.HORIZONTAL) layer.visualBounds().centerX()
                    else layer.visualBounds().centerY()
                }
            }.sorted()
            val gaps = centers.zipWithNext { first, second -> second - first }
            assertTrue(gaps.all { kotlin.math.abs(it - gaps.first()) < 0.01f })
        }
    }

    @Test
    fun distributionEqualizesVisualEdgeGapsForDifferentSizes() {
        val layers = listOf(
            layer(10f, 10f, 30f, 30f),
            layer(100f, 10f, 160f, 30f),
            layer(250f, 10f, 280f, 30f),
        )
        val result = DrawLayerArrangement.distribute(
            layers,
            setOf(0, 1, 2),
            crop,
            LayerDistribution.HORIZONTAL,
        )
        val bounds = result.map { with(DrawLayerArrangement) { it.visualBounds() } }.sortedBy { it.left }
        val gaps = bounds.zipWithNext { first, second -> second.left - first.right }

        assertEquals(gaps[0], gaps[1], 0.01f)
        assertEquals(with(DrawLayerArrangement) { layers.first().visualBounds().left }, bounds.first().left, 0.01f)
        assertEquals(with(DrawLayerArrangement) { layers.last().visualBounds().right }, bounds.last().right, 0.01f)
    }

    @Test
    fun overlappingLayersDistributeAcrossCropAndFewerThanThreeAreNoOp() {
        val overlapping = List(3) { layer(100f, 70f, 120f, 90f) }
        val distributed = DrawLayerArrangement.distribute(
            overlapping,
            setOf(0, 1, 2),
            crop,
            LayerDistribution.HORIZONTAL,
        )
        val centers = distributed.map { with(DrawLayerArrangement) { it.visualBounds().centerX() } }
        assertEquals(listOf(12f, 150f, 288f), centers)
        assertEquals(
            overlapping,
            DrawLayerArrangement.distribute(overlapping, setOf(0, 1), crop, LayerDistribution.HORIZONTAL),
        )
    }

    @Test
    fun duplicateDeepCopiesSelectedGeometryAndSelectsVisibleOffsetCopies() {
        val original = layer(20f, 30f, 60f, 70f)
        val other = layer(100f, 100f, 130f, 130f).copy(visible = false)

        val result = DrawLayerArrangement.duplicate(listOf(original, other), setOf(0), crop)

        assertEquals(3, result.layers.size)
        assertEquals(setOf(1), result.selectedIndices)
        assertEquals(other, result.layers.last())
        val duplicate = result.layers[1]
        assertNotSame(original.points.first(), duplicate.points.first())
        assertEquals(original.transOffsetX + 8f, duplicate.transOffsetX, 0.01f)
        assertEquals(original.transOffsetY + 8f, duplicate.transOffsetY, 0.01f)
        assertEquals(original.color, duplicate.color)
        assertEquals(original.shapeType, duplicate.shapeType)
        assertTrue(!result.layers.last().visible)
    }

    @Test
    fun transformedBoundsCoverTextAndCalloutVisualGeometry() {
        val text = DrawPath(
            points = listOf(PointF(50f, 80f)),
            color = 0xff000000.toInt(),
            strokeWidth = 10f,
            shapeType = "text",
            text = "Support",
            filled = true,
        )
        val callout = DrawPath(
            points = listOf(PointF(100f, 100f)),
            color = 0xff000000.toInt(),
            strokeWidth = 10f,
            shapeType = "callout",
            text = "1",
        )

        with(DrawLayerArrangement) {
            assertTrue(text.visualBounds().width() > 0f)
            assertTrue(text.visualBounds().height() > 0f)
            assertEquals(40f, callout.visualBounds().width(), 0.01f)
            assertEquals(40f, callout.visualBounds().height(), 0.01f)
        }
    }

    @Test
    fun duplicateRejectsProjectLayerLimitAtomically() {
        val layers = List(SnapCropProjectSidecar.DEFAULT_LIMITS.maxDrawLayers) { index ->
            layer(index.toFloat(), 0f, index + 1f, 1f)
        }
        val result = DrawLayerArrangement.duplicate(layers, setOf(0), crop)

        assertEquals(layers, result.layers)
        assertTrue(result.selectedIndices.isEmpty())
    }

    @Test
    fun emptyAndInvalidSelectionsAreNoOps() {
        val empty = DrawPath(emptyList(), 0xff000000.toInt(), 4f)
        val layers = listOf(empty, layer(10f, 10f, 20f, 20f))

        assertEquals(layers, DrawLayerArrangement.align(layers, setOf(0, 99), crop, LayerAlignment.LEFT))
        assertEquals(layers, DrawLayerArrangement.distribute(layers, setOf(-1, 0, 99), crop, LayerDistribution.VERTICAL))
        assertEquals(layers, DrawLayerArrangement.duplicate(layers, setOf(99), crop).layers)
    }

    @Test
    fun arrangedPixelOperationResolvesTransformExactlyOnce() {
        val pixelLayer = DrawPath(
            points = listOf(PointF(40f, 50f), PointF(60f, 70f)),
            color = 0xff000000.toInt(),
            strokeWidth = 10f,
            shapeType = "smart_erase",
            transOffsetX = 12f,
        )
        val arranged = DrawLayerArrangement.align(
            listOf(pixelLayer),
            setOf(0),
            Rect(100, 0, 300, 200),
            LayerAlignment.LEFT,
        ).single()
        val resolved = arranged.transformedForPixelOperation()

        assertTrue(arranged.hasTransform)
        assertTrue(!resolved.hasTransform)
        assertEquals(105f, resolved.points.minOf { it.x }, 0.01f)
    }

    private fun layer(left: Float, top: Float, right: Float, bottom: Float): DrawPath = DrawPath(
        points = listOf(PointF(left, top), PointF(right, bottom)),
        color = 0xffff0000.toInt(),
        strokeWidth = 4f,
        shapeType = "rect",
    )
}
