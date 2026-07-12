package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutSqueezeTest {
    @Test
    fun emptyPlanIsAnIdentityMapping() {
        val squeeze = CutoutSqueeze.create(100, 80, emptyList())

        assertEquals(100, squeeze.outputWidth)
        assertEquals(80, squeeze.outputHeight)
        assertTrue(squeeze.bands.isEmpty())
        assertTrue(squeeze.separators.isEmpty())
        assertEquals(
            CutPointMapping(SqueezePoint(25f, 30f), CutMappingDisposition.RETAINED),
            squeeze.mapPoint(SqueezePoint(25f, 30f)),
        )
        assertEquals(
            CutRectMapping(SqueezeRect(10f, 20f, 40f, 60f), CutMappingDisposition.RETAINED),
            squeeze.mapRect(SqueezeRect(10f, 20f, 40f, 60f)),
        )
    }

    @Test
    fun bandsAreBoundedReversedDroppedMergedAndCanonicallyOrdered() {
        val squeeze = CutoutSqueeze.create(
            sourceWidth = 100,
            sourceHeight = 80,
            bands = listOf(
                CutBand(CutAxis.VERTICAL, 90, 70),
                CutBand(CutAxis.HORIZONTAL, 30, 15),
                CutBand(CutAxis.HORIZONTAL, -10, 20),
                CutBand(CutAxis.VERTICAL, 80, 120),
                CutBand(CutAxis.HORIZONTAL, 30, 40),
                CutBand(CutAxis.HORIZONTAL, 50, 50),
                CutBand(CutAxis.HORIZONTAL, 90, 100),
            ),
        )

        assertEquals(
            listOf(
                CutBand(CutAxis.HORIZONTAL, 0, 40),
                CutBand(CutAxis.VERTICAL, 70, 100),
            ),
            squeeze.bands,
        )
        assertEquals(70, squeeze.outputWidth)
        assertEquals(40, squeeze.outputHeight)
    }

    @Test
    fun overlappingAndTouchingBandsMergeButSeparatedBandsRemain() {
        val squeeze = CutoutSqueeze.create(
            200,
            200,
            listOf(
                CutBand(CutAxis.HORIZONTAL, 10, 20),
                CutBand(CutAxis.HORIZONTAL, 18, 25),
                CutBand(CutAxis.HORIZONTAL, 25, 30),
                CutBand(CutAxis.HORIZONTAL, 40, 50),
            ),
        )

        assertEquals(
            listOf(
                CutBand(CutAxis.HORIZONTAL, 10, 30),
                CutBand(CutAxis.HORIZONTAL, 40, 50),
            ),
            squeeze.horizontalBands,
        )
        assertEquals(170, squeeze.outputHeight)
    }

    @Test
    fun invalidDimensionsBandCountAndFullAxisRemovalAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CutoutSqueeze.create(0, 10, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            CutoutSqueeze.create(CutoutSqueeze.MAX_DIMENSION + 1, 10, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CutoutSqueeze.create(
                100,
                100,
                List(CutoutSqueeze.MAX_INPUT_BANDS + 1) { CutBand(CutAxis.HORIZONTAL, 10, 20) },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CutoutSqueeze.create(100, 100, listOf(CutBand(CutAxis.HORIZONTAL, -10, 110)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CutoutSqueeze.create(100, 100, listOf(CutBand(CutAxis.VERTICAL, 0, 100)))
        }
    }

    @Test
    fun pointsAfterBandsShiftAndPointsInsideCollapseToSeparator() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(
                CutBand(CutAxis.VERTICAL, 20, 30),
                CutBand(CutAxis.HORIZONTAL, 40, 60),
            ),
        )

        assertEquals(
            CutPointMapping(SqueezePoint(20f, 40f), CutMappingDisposition.REMOVED),
            squeeze.mapPoint(SqueezePoint(25f, 50f)),
        )
        assertEquals(
            CutPointMapping(SqueezePoint(20f, 40f), CutMappingDisposition.RETAINED),
            squeeze.mapPoint(SqueezePoint(30f, 60f)),
        )
        assertEquals(
            CutPointMapping(SqueezePoint(40f, 50f), CutMappingDisposition.RETAINED),
            squeeze.mapPoint(SqueezePoint(50f, 70f)),
        )
    }

    @Test
    fun multipleBandsAccumulateRemovedDistance() {
        val squeeze = CutoutSqueeze.create(
            120,
            120,
            listOf(
                CutBand(CutAxis.VERTICAL, 10, 20),
                CutBand(CutAxis.VERTICAL, 40, 55),
                CutBand(CutAxis.HORIZONTAL, 5, 15),
                CutBand(CutAxis.HORIZONTAL, 60, 80),
            ),
        )

        assertEquals(
            CutPointMapping(SqueezePoint(75f, 65f), CutMappingDisposition.RETAINED),
            squeeze.mapPoint(SqueezePoint(100f, 95f)),
        )
        assertEquals(95, squeeze.outputWidth)
        assertEquals(90, squeeze.outputHeight)
    }

    @Test
    fun spanningRectCompressesAcrossBothAxesAndIsMarkedClipped() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(
                CutBand(CutAxis.VERTICAL, 20, 30),
                CutBand(CutAxis.HORIZONTAL, 40, 60),
            ),
        )

        val mapped = squeeze.mapRect(SqueezeRect(10f, 30f, 50f, 80f))

        assertEquals(SqueezeRect(10f, 30f, 40f, 60f), mapped.rect)
        assertEquals(CutMappingDisposition.CLIPPED, mapped.disposition)
    }

    @Test
    fun rectStartingInsideBandClipsToSeparator() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(CutBand(CutAxis.HORIZONTAL, 40, 60)),
        )

        assertEquals(
            CutRectMapping(SqueezeRect(10f, 40f, 30f, 50f), CutMappingDisposition.CLIPPED),
            squeeze.mapRect(SqueezeRect(10f, 50f, 30f, 70f)),
        )
    }

    @Test
    fun rectEntirelyInsideAnyRemovedAxisIsRemoved() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(CutBand(CutAxis.VERTICAL, 20, 40)),
        )

        val mapped = squeeze.mapRect(SqueezeRect(25f, 10f, 35f, 90f))

        assertNull(mapped.rect)
        assertEquals(CutMappingDisposition.REMOVED, mapped.disposition)
    }

    @Test
    fun separatorsUseCompressedCoordinatesAndChosenStyle() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(
                CutBand(CutAxis.HORIZONTAL, 10, 20),
                CutBand(CutAxis.HORIZONTAL, 30, 40),
                CutBand(CutAxis.VERTICAL, 50, 70),
            ),
            separatorStyle = CutSeparatorStyle.TORN,
        )

        assertEquals(
            listOf(
                CutSeparator(CutAxis.HORIZONTAL, 10, CutSeparatorStyle.TORN),
                CutSeparator(CutAxis.HORIZONTAL, 20, CutSeparatorStyle.TORN),
                CutSeparator(CutAxis.VERTICAL, 50, CutSeparatorStyle.TORN),
            ),
            squeeze.separators,
        )
        assertEquals(setOf(CutSeparatorStyle.STRAIGHT, CutSeparatorStyle.DASHED, CutSeparatorStyle.TORN), CutSeparatorStyle.entries.toSet())
    }

    @Test
    fun retainedSegmentsAndCartesianRectsExposeOutputOffsets() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(
                CutBand(CutAxis.VERTICAL, 20, 30),
                CutBand(CutAxis.HORIZONTAL, 40, 60),
            ),
        )

        assertEquals(
            listOf(
                RetainedSourceSegment(CutAxis.HORIZONTAL, 0, 40, 0),
                RetainedSourceSegment(CutAxis.HORIZONTAL, 60, 100, 40),
                RetainedSourceSegment(CutAxis.VERTICAL, 0, 20, 0),
                RetainedSourceSegment(CutAxis.VERTICAL, 30, 100, 20),
            ),
            squeeze.retainedSegments,
        )
        assertEquals(
            listOf(
                RetainedSourceRect(SqueezeRect(0f, 0f, 20f, 40f), SqueezePoint(0f, 0f)),
                RetainedSourceRect(SqueezeRect(30f, 0f, 100f, 40f), SqueezePoint(20f, 0f)),
                RetainedSourceRect(SqueezeRect(0f, 60f, 20f, 100f), SqueezePoint(0f, 40f)),
                RetainedSourceRect(SqueezeRect(30f, 60f, 100f, 100f), SqueezePoint(20f, 40f)),
            ),
            squeeze.retainedSourceRects,
        )
        assertEquals(SqueezeRect(20f, 40f, 90f, 80f), squeeze.retainedSourceRects.last().outputRect)
    }

    @Test
    fun outputPointsInvertWithAfterCutSeparatorBias() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(
                CutBand(CutAxis.VERTICAL, 20, 30),
                CutBand(CutAxis.HORIZONTAL, 40, 60),
            ),
        )

        assertEquals(SqueezePoint(10f, 10f), squeeze.outputPointToSource(SqueezePoint(10f, 10f)))
        assertEquals(SqueezePoint(30f, 60f), squeeze.outputPointToSource(SqueezePoint(20f, 40f)))
        assertEquals(SqueezePoint(50f, 70f), squeeze.outputPointToSource(SqueezePoint(40f, 50f)))
    }

    @Test
    fun outputRectsUseOppositeBiasesAtSeparatorEdges() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(
                CutBand(CutAxis.VERTICAL, 20, 30),
                CutBand(CutAxis.HORIZONTAL, 40, 60),
            ),
        )

        assertEquals(
            SqueezeRect(0f, 0f, 20f, 40f),
            squeeze.outputRectToSource(SqueezeRect(0f, 0f, 20f, 40f)),
        )
        assertEquals(
            SqueezeRect(30f, 60f, 40f, 70f),
            squeeze.outputRectToSource(SqueezeRect(20f, 40f, 30f, 50f)),
        )
        assertEquals(
            SqueezeRect(10f, 30f, 40f, 70f),
            squeeze.outputRectToSource(SqueezeRect(10f, 30f, 30f, 50f)),
        )
    }

    @Test
    fun inverseMappingHandlesLeadingAndTrailingEdgeCuts() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(
                CutBand(CutAxis.VERTICAL, 0, 10),
                CutBand(CutAxis.VERTICAL, 90, 100),
            ),
        )

        assertEquals(80, squeeze.outputWidth)
        assertEquals(SqueezePoint(10f, 50f), squeeze.outputPointToSource(SqueezePoint(0f, 50f)))
        assertEquals(SqueezePoint(100f, 50f), squeeze.outputPointToSource(SqueezePoint(80f, 50f)))
        assertEquals(
            SqueezeRect(10f, 0f, 90f, 100f),
            squeeze.outputRectToSource(SqueezeRect(0f, 0f, 80f, 100f)),
        )
    }

    @Test
    fun inverseMappingRejectsInvalidOutputGeometry() {
        val squeeze = CutoutSqueeze.create(
            100,
            100,
            listOf(CutBand(CutAxis.VERTICAL, 20, 30)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            squeeze.outputPointToSource(SqueezePoint(91f, 5f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            squeeze.outputPointToSource(SqueezePoint(Float.NaN, 5f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            squeeze.outputRectToSource(SqueezeRect(0f, 0f, 91f, 10f))
        }
    }

    @Test
    fun canonicalPlanDoesNotRetainMutableInputList() {
        val input = mutableListOf(CutBand(CutAxis.HORIZONTAL, 10, 20))
        val squeeze = CutoutSqueeze.create(100, 100, input)

        input.clear()

        assertEquals(listOf(CutBand(CutAxis.HORIZONTAL, 10, 20)), squeeze.bands)
    }

    @Test
    fun mappingRejectsNonFiniteDegenerateAndOutOfBoundsGeometry() {
        val squeeze = CutoutSqueeze.create(100, 100, emptyList())

        assertThrows(IllegalArgumentException::class.java) { squeeze.mapPoint(SqueezePoint(Float.NaN, 2f)) }
        assertThrows(IllegalArgumentException::class.java) { squeeze.mapPoint(SqueezePoint(101f, 2f)) }
        assertThrows(IllegalArgumentException::class.java) { squeeze.mapRect(SqueezeRect(5f, 5f, 5f, 10f)) }
        assertThrows(IllegalArgumentException::class.java) { squeeze.mapRect(SqueezeRect(-1f, 5f, 10f, 10f)) }
        assertThrows(IllegalArgumentException::class.java) {
            squeeze.mapRect(SqueezeRect(0f, 0f, Float.POSITIVE_INFINITY, 10f))
        }
    }

    @Test
    fun canonicalOutputIsIndependentOfInputOrder() {
        val bands = listOf(
            CutBand(CutAxis.VERTICAL, 70, 80),
            CutBand(CutAxis.HORIZONTAL, 10, 20),
            CutBand(CutAxis.VERTICAL, 60, 75),
        )

        val forward = CutoutSqueeze.create(100, 100, bands)
        val reverse = CutoutSqueeze.create(100, 100, bands.reversed())

        assertEquals(forward.bands, reverse.bands)
        assertEquals(forward.separators, reverse.separators)
        assertEquals(forward.outputWidth, reverse.outputWidth)
        assertEquals(forward.outputHeight, reverse.outputHeight)
    }
}
