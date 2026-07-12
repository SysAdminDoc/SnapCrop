package com.sysadmindoc.snapcrop

enum class CutAxis { HORIZONTAL, VERTICAL }

enum class CutSeparatorStyle { STRAIGHT, DASHED, TORN }

/** Half-open source interval [start, endExclusive) along [axis]. */
data class CutBand(
    val axis: CutAxis,
    val start: Int,
    val endExclusive: Int,
) {
    val length: Int
        get() = endExclusive - start
}

data class SqueezePoint(val x: Float, val y: Float)

data class SqueezeRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float
        get() = right - left
    val height: Float
        get() = bottom - top
}

enum class CutMappingDisposition { RETAINED, CLIPPED, REMOVED }

/** Removed points collapse to the matching separator coordinate and are marked [REMOVED]. */
data class CutPointMapping(
    val point: SqueezePoint,
    val disposition: CutMappingDisposition,
)

/** [rect] is null only when the entire rectangle was removed on at least one axis. */
data class CutRectMapping(
    val rect: SqueezeRect?,
    val disposition: CutMappingDisposition,
)

data class CutSeparator(
    val axis: CutAxis,
    val outputPosition: Int,
    val style: CutSeparatorStyle,
)

data class RetainedSourceSegment(
    val axis: CutAxis,
    val sourceStart: Int,
    val sourceEndExclusive: Int,
    val outputOffset: Int,
) {
    val length: Int
        get() = sourceEndExclusive - sourceStart
}

/** One source tile and the top-left output coordinate at which it should be rendered. */
data class RetainedSourceRect(
    val sourceRect: SqueezeRect,
    val outputOffset: SqueezePoint,
) {
    val outputRect: SqueezeRect
        get() = SqueezeRect(
            outputOffset.x,
            outputOffset.y,
            outputOffset.x + sourceRect.width,
            outputOffset.y + sourceRect.height,
        )
}

/**
 * Canonical non-destructive cut plan. Bands are clamped to the source, reversed drags are
 * normalized, empty bands are dropped, and touching/overlapping bands on the same axis merge.
 */
class CutoutSqueeze private constructor(
    val sourceWidth: Int,
    val sourceHeight: Int,
    bands: List<CutBand>,
    val separatorStyle: CutSeparatorStyle,
) {
    val bands: List<CutBand> = bands.toList()
    val horizontalBands: List<CutBand> = this.bands.filter { it.axis == CutAxis.HORIZONTAL }
    val verticalBands: List<CutBand> = this.bands.filter { it.axis == CutAxis.VERTICAL }

    val outputWidth: Int = sourceWidth - verticalBands.sumOf(CutBand::length)
    val outputHeight: Int = sourceHeight - horizontalBands.sumOf(CutBand::length)

    private val retainedXSegments: List<RetainedSourceSegment> =
        retainedSegmentsFor(CutAxis.VERTICAL, sourceWidth, verticalBands)
    private val retainedYSegments: List<RetainedSourceSegment> =
        retainedSegmentsFor(CutAxis.HORIZONTAL, sourceHeight, horizontalBands)

    val retainedSegments: List<RetainedSourceSegment> = retainedYSegments + retainedXSegments

    /** Cartesian retained tiles in deterministic row-major output order. */
    val retainedSourceRects: List<RetainedSourceRect> = buildList {
        for (y in retainedYSegments) {
            for (x in retainedXSegments) {
                add(
                    RetainedSourceRect(
                        sourceRect = SqueezeRect(
                            x.sourceStart.toFloat(),
                            y.sourceStart.toFloat(),
                            x.sourceEndExclusive.toFloat(),
                            y.sourceEndExclusive.toFloat(),
                        ),
                        outputOffset = SqueezePoint(x.outputOffset.toFloat(), y.outputOffset.toFloat()),
                    ),
                )
            }
        }
    }

    val separators: List<CutSeparator> = buildList {
        addAll(separatorsFor(horizontalBands))
        addAll(separatorsFor(verticalBands))
    }

    fun mapPoint(source: SqueezePoint): CutPointMapping {
        require(source.x.isFinite() && source.y.isFinite()) { "Point coordinates must be finite" }
        require(source.x in 0f..sourceWidth.toFloat() && source.y in 0f..sourceHeight.toFloat()) {
            "Point must be inside the source bounds"
        }
        val x = mapCoordinate(source.x, verticalBands)
        val y = mapCoordinate(source.y, horizontalBands)
        return CutPointMapping(
            point = SqueezePoint(x.output, y.output),
            disposition = if (x.removed || y.removed) {
                CutMappingDisposition.REMOVED
            } else {
                CutMappingDisposition.RETAINED
            },
        )
    }

    fun mapRect(source: SqueezeRect): CutRectMapping {
        validateRect(source)
        val left = mapCoordinate(source.left, verticalBands).output
        val right = mapCoordinate(source.right, verticalBands).output
        val top = mapCoordinate(source.top, horizontalBands).output
        val bottom = mapCoordinate(source.bottom, horizontalBands).output
        if (right <= left || bottom <= top) {
            return CutRectMapping(null, CutMappingDisposition.REMOVED)
        }
        val intersectsCut = verticalBands.any { it.intersects(source.left, source.right) } ||
            horizontalBands.any { it.intersects(source.top, source.bottom) }
        return CutRectMapping(
            rect = SqueezeRect(left, top, right, bottom),
            disposition = if (intersectsCut) CutMappingDisposition.CLIPPED else CutMappingDisposition.RETAINED,
        )
    }

    /** At a separator, a point deterministically selects the retained content after the cut. */
    fun outputPointToSource(output: SqueezePoint): SqueezePoint {
        require(output.x.isFinite() && output.y.isFinite()) { "Point coordinates must be finite" }
        require(output.x in 0f..outputWidth.toFloat() && output.y in 0f..outputHeight.toFloat()) {
            "Point must be inside the output bounds"
        }
        return SqueezePoint(
            inverseCoordinate(output.x, verticalBands, SeparatorBias.AFTER),
            inverseCoordinate(output.y, horizontalBands, SeparatorBias.AFTER),
        )
    }

    /**
     * Leading edges on a separator choose content after the cut; trailing edges choose content
     * before it. A rectangle spanning a separator therefore expands across that removed source band.
     */
    fun outputRectToSource(output: SqueezeRect): SqueezeRect {
        validateOutputRect(output)
        return SqueezeRect(
            left = inverseCoordinate(output.left, verticalBands, SeparatorBias.AFTER),
            top = inverseCoordinate(output.top, horizontalBands, SeparatorBias.AFTER),
            right = inverseCoordinate(output.right, verticalBands, SeparatorBias.BEFORE),
            bottom = inverseCoordinate(output.bottom, horizontalBands, SeparatorBias.BEFORE),
        )
    }

    private fun validateRect(rect: SqueezeRect) {
        require(rect.left.isFinite() && rect.top.isFinite() && rect.right.isFinite() && rect.bottom.isFinite()) {
            "Rectangle coordinates must be finite"
        }
        require(rect.left < rect.right && rect.top < rect.bottom) { "Rectangle must have positive area" }
        require(
            rect.left >= 0f && rect.top >= 0f &&
                rect.right <= sourceWidth.toFloat() && rect.bottom <= sourceHeight.toFloat(),
        ) { "Rectangle must be inside the source bounds" }
    }

    private fun validateOutputRect(rect: SqueezeRect) {
        require(rect.left.isFinite() && rect.top.isFinite() && rect.right.isFinite() && rect.bottom.isFinite()) {
            "Rectangle coordinates must be finite"
        }
        require(rect.left < rect.right && rect.top < rect.bottom) { "Rectangle must have positive area" }
        require(
            rect.left >= 0f && rect.top >= 0f &&
                rect.right <= outputWidth.toFloat() && rect.bottom <= outputHeight.toFloat(),
        ) { "Rectangle must be inside the output bounds" }
    }

    private fun retainedSegmentsFor(
        axis: CutAxis,
        sourceLimit: Int,
        axisBands: List<CutBand>,
    ): List<RetainedSourceSegment> {
        var sourceCursor = 0
        var outputCursor = 0
        return buildList {
            for (band in axisBands) {
                if (sourceCursor < band.start) {
                    add(RetainedSourceSegment(axis, sourceCursor, band.start, outputCursor))
                    outputCursor += band.start - sourceCursor
                }
                sourceCursor = band.endExclusive
            }
            if (sourceCursor < sourceLimit) {
                add(RetainedSourceSegment(axis, sourceCursor, sourceLimit, outputCursor))
            }
        }
    }

    private fun separatorsFor(axisBands: List<CutBand>): List<CutSeparator> {
        var removedBefore = 0
        return axisBands.mapNotNull { band ->
            val sourceLimit = if (band.axis == CutAxis.HORIZONTAL) sourceHeight else sourceWidth
            if (band.start == 0 || band.endExclusive == sourceLimit) {
                removedBefore += band.length
                return@mapNotNull null
            }
            CutSeparator(
                axis = band.axis,
                outputPosition = band.start - removedBefore,
                style = separatorStyle,
            ).also { removedBefore += band.length }
        }
    }

    private data class CoordinateMapping(val output: Float, val removed: Boolean)

    private enum class SeparatorBias { BEFORE, AFTER }

    private fun mapCoordinate(value: Float, axisBands: List<CutBand>): CoordinateMapping {
        var removedBefore = 0
        for (band in axisBands) {
            if (value < band.start) return CoordinateMapping(value - removedBefore, false)
            if (value < band.endExclusive) {
                return CoordinateMapping(band.start.toFloat() - removedBefore, true)
            }
            removedBefore += band.length
        }
        return CoordinateMapping(value - removedBefore, false)
    }

    private fun inverseCoordinate(
        value: Float,
        axisBands: List<CutBand>,
        bias: SeparatorBias,
    ): Float {
        var removedBefore = 0
        for (band in axisBands) {
            val separator = band.start - removedBefore
            if (value < separator || (value == separator.toFloat() && bias == SeparatorBias.BEFORE)) {
                return value + removedBefore
            }
            removedBefore += band.length
        }
        return value + removedBefore
    }

    private fun CutBand.intersects(start: Float, endExclusive: Float): Boolean =
        start < this.endExclusive && this.start < endExclusive

    companion object {
        const val MAX_DIMENSION = 1_000_000
        const val MAX_INPUT_BANDS = 32

        fun create(
            sourceWidth: Int,
            sourceHeight: Int,
            bands: List<CutBand>,
            separatorStyle: CutSeparatorStyle = CutSeparatorStyle.STRAIGHT,
        ): CutoutSqueeze {
            require(sourceWidth in 1..MAX_DIMENSION && sourceHeight in 1..MAX_DIMENSION) {
                "Source dimensions must be positive and bounded"
            }
            require(bands.size <= MAX_INPUT_BANDS) { "Too many cut bands" }

            val canonical = CutAxis.entries.flatMap { axis ->
                val limit = if (axis == CutAxis.HORIZONTAL) sourceHeight else sourceWidth
                mergeBands(
                    bands.asSequence()
                        .filter { it.axis == axis }
                        .mapNotNull { it.bounded(limit) }
                        .sortedWith(compareBy<CutBand>(CutBand::start).thenBy(CutBand::endExclusive))
                        .toList(),
                )
            }
            val removedWidth = canonical.asSequence()
                .filter { it.axis == CutAxis.VERTICAL }
                .sumOf { it.length.toLong() }
            val removedHeight = canonical.asSequence()
                .filter { it.axis == CutAxis.HORIZONTAL }
                .sumOf { it.length.toLong() }
            require(removedWidth < sourceWidth && removedHeight < sourceHeight) {
                "Cut bands must leave at least one output pixel on each axis"
            }
            return CutoutSqueeze(sourceWidth, sourceHeight, canonical, separatorStyle)
        }

        /** Intersects source-space bands with a crop and translates them into crop-local space. */
        fun createForCrop(
            sourceWidth: Int,
            sourceHeight: Int,
            cropLeft: Int,
            cropTop: Int,
            cropRight: Int,
            cropBottom: Int,
            bands: List<CutBand>,
            separatorStyle: CutSeparatorStyle = CutSeparatorStyle.STRAIGHT,
        ): CutoutSqueeze {
            require(cropLeft in 0 until cropRight && cropRight <= sourceWidth) { "Invalid crop width" }
            require(cropTop in 0 until cropBottom && cropBottom <= sourceHeight) { "Invalid crop height" }
            val localBands = bands.mapNotNull { band ->
                val cropStart = if (band.axis == CutAxis.HORIZONTAL) cropTop else cropLeft
                val cropEnd = if (band.axis == CutAxis.HORIZONTAL) cropBottom else cropRight
                val start = maxOf(band.start, cropStart)
                val end = minOf(band.endExclusive, cropEnd)
                if (end > start) CutBand(band.axis, start - cropStart, end - cropStart) else null
            }
            return create(cropRight - cropLeft, cropBottom - cropTop, localBands, separatorStyle)
        }

        private fun CutBand.bounded(limit: Int): CutBand? {
            val low = minOf(start, endExclusive).coerceIn(0, limit)
            val high = maxOf(start, endExclusive).coerceIn(0, limit)
            return if (high > low) CutBand(axis, low, high) else null
        }

        private fun mergeBands(sorted: List<CutBand>): List<CutBand> {
            if (sorted.isEmpty()) return emptyList()
            val merged = ArrayList<CutBand>(sorted.size)
            for (band in sorted) {
                val previous = merged.lastOrNull()
                if (previous != null && band.start <= previous.endExclusive) {
                    merged[merged.lastIndex] = previous.copy(
                        endExclusive = maxOf(previous.endExclusive, band.endExclusive),
                    )
                } else {
                    merged.add(band)
                }
            }
            return merged
        }
    }
}
