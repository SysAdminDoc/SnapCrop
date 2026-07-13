package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class CompareAlignment(val xFraction: Float, val yFraction: Float) {
    TOP_LEFT(0f, 0f),
    CENTER(0.5f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
}

internal enum class CompareMode {
    SWIPE,
    OVERLAY,
    BLINK,
    DIFFERENCE,
}

internal data class ComparePixels(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "Comparison dimensions must be positive" }
        val expectedPixels = width.toLong() * height
        require(expectedPixels <= Int.MAX_VALUE) { "Comparison dimensions are too large" }
        require(pixels.size.toLong() == expectedPixels) {
            "Comparison pixel count does not match dimensions"
        }
    }
}

internal data class CompareSource(
    val bitmap: Bitmap,
    val sourceWidth: Int = bitmap.width,
    val sourceHeight: Int = bitmap.height,
) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
    }
}

internal data class CompareOffset(val x: Int, val y: Int)

internal data class CompareRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val changedPixels: Int,
)

internal data class CompareAnalysisResult(
    val width: Int,
    val height: Int,
    val changedPixels: Int,
    val totalPixels: Int,
    val beforeOffset: CompareOffset,
    val afterOffset: CompareOffset,
    val regions: List<CompareRegion>,
    val totalRegionCount: Int,
    val analysisScale: Float = 1f,
    val differenceBitmap: Bitmap,
) {
    val changedPercent: Double
        get() = if (totalPixels == 0) 0.0 else changedPixels * 100.0 / totalPixels
}

internal object CompareAnalyzer {
    const val MAX_ANALYSIS_PIXELS = 1_500_000
    const val MAX_REGIONS = 32
    const val CHANNEL_THRESHOLD = 12
    private const val MAX_TILE_AXIS = 48

    fun analyze(
        before: Bitmap,
        after: Bitmap,
        alignment: CompareAlignment,
    ): CompareAnalysisResult = analyze(CompareSource(before), CompareSource(after), alignment)

    fun analyze(
        before: CompareSource,
        after: CompareSource,
        alignment: CompareAlignment,
    ): CompareAnalysisResult {
        check(!before.bitmap.isRecycled && !after.bitmap.isRecycled) {
            "Cannot compare recycled bitmaps"
        }
        val dimensions = analysisDimensions(
            before.sourceWidth,
            before.sourceHeight,
            after.sourceWidth,
            after.sourceHeight,
        )
        val scale = minOf(
            dimensions.scale,
            before.bitmap.width.toFloat() / before.sourceWidth,
            before.bitmap.height.toFloat() / before.sourceHeight,
            after.bitmap.width.toFloat() / after.sourceWidth,
            after.bitmap.height.toFloat() / after.sourceHeight,
        )
        val beforePixels = scaledPixels(before, scale)
        val afterPixels = scaledPixels(after, scale)
        return analyze(beforePixels, afterPixels, alignment).copy(analysisScale = scale)
    }

    internal fun analyze(
        before: ComparePixels,
        after: ComparePixels,
        alignment: CompareAlignment,
    ): CompareAnalysisResult {
        val width = max(before.width, after.width)
        val height = max(before.height, after.height)
        val canvasPixels = width.toLong() * height
        require(canvasPixels <= Int.MAX_VALUE) { "Comparison canvas is too large" }
        val beforeOffset = offset(width, height, before.width, before.height, alignment)
        val afterOffset = offset(width, height, after.width, after.height, alignment)
        val difference = IntArray(canvasPixels.toInt())
        val tileSize = max(8, ceil(max(width, height) / MAX_TILE_AXIS.toDouble()).toInt())
        val tileColumns = ceil(width / tileSize.toDouble()).toInt()
        val tileRows = ceil(height / tileSize.toDouble()).toInt()
        val tileChanges = IntArray(tileColumns * tileRows)
        var changedPixels = 0
        var totalPixels = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val beforeColor = colorAt(before, beforeOffset, x, y)
                val afterColor = colorAt(after, afterOffset, x, y)
                if (beforeColor == null && afterColor == null) continue
                totalPixels++
                if (different(beforeColor, afterColor)) {
                    val index = y * width + x
                    difference[index] = when {
                        beforeColor == null -> Color.argb(224, 255, 183, 77)
                        afterColor == null -> Color.argb(224, 137, 220, 235)
                        else -> Color.argb(218, 255, 82, 168)
                    }
                    changedPixels++
                    tileChanges[(y / tileSize) * tileColumns + x / tileSize]++
                }
            }
        }

        val regionSummary = regions(tileChanges, tileColumns, tileRows, tileSize, width, height)
        return CompareAnalysisResult(
            width = width,
            height = height,
            changedPixels = changedPixels,
            totalPixels = totalPixels,
            beforeOffset = beforeOffset,
            afterOffset = afterOffset,
            regions = regionSummary.regions,
            totalRegionCount = regionSummary.totalCount,
            differenceBitmap = Bitmap.createBitmap(difference, width, height, Bitmap.Config.ARGB_8888),
        )
    }

    internal data class AnalysisDimensions(val scale: Float, val width: Int, val height: Int)

    internal fun analysisDimensions(
        beforeWidth: Int,
        beforeHeight: Int,
        afterWidth: Int,
        afterHeight: Int,
    ): AnalysisDimensions {
        require(beforeWidth > 0 && beforeHeight > 0 && afterWidth > 0 && afterHeight > 0)
        val width = max(beforeWidth, afterWidth)
        val height = max(beforeHeight, afterHeight)
        val pixels = width.toLong() * height
        val scale = if (pixels <= MAX_ANALYSIS_PIXELS) {
            1f
        } else {
            sqrt(MAX_ANALYSIS_PIXELS.toDouble() / pixels).toFloat()
        }
        return AnalysisDimensions(
            scale = scale,
            width = max(1, (width * scale).toInt()),
            height = max(1, (height * scale).toInt()),
        )
    }

    internal fun offset(
        canvasWidth: Int,
        canvasHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        alignment: CompareAlignment,
    ): CompareOffset = CompareOffset(
        x = ((canvasWidth - imageWidth) * alignment.xFraction).roundToInt(),
        y = ((canvasHeight - imageHeight) * alignment.yFraction).roundToInt(),
    )

    private fun scaledPixels(source: CompareSource, scale: Float): ComparePixels {
        val width = max(1, (source.sourceWidth * scale).toInt())
        val height = max(1, (source.sourceHeight * scale).toInt())
        val scaled = if (width == source.bitmap.width && height == source.bitmap.height) {
            source.bitmap
        } else {
            Bitmap.createScaledBitmap(source.bitmap, width, height, false)
        }
        return try {
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            ComparePixels(width, height, pixels)
        } finally {
            if (scaled !== source.bitmap) scaled.recycle()
        }
    }

    private fun colorAt(image: ComparePixels, offset: CompareOffset, x: Int, y: Int): Int? {
        val imageX = x - offset.x
        val imageY = y - offset.y
        return if (imageX in 0 until image.width && imageY in 0 until image.height) {
            image.pixels[imageY * image.width + imageX]
        } else {
            null
        }
    }

    private fun different(before: Int?, after: Int?): Boolean {
        if (before == null || after == null) return before != after
        val beforeAlpha = Color.alpha(before)
        val afterAlpha = Color.alpha(after)
        if (abs(beforeAlpha - afterAlpha) > CHANNEL_THRESHOLD) return true
        return abs(premultiplied(Color.red(before), beforeAlpha) - premultiplied(Color.red(after), afterAlpha)) > CHANNEL_THRESHOLD ||
                abs(premultiplied(Color.green(before), beforeAlpha) - premultiplied(Color.green(after), afterAlpha)) > CHANNEL_THRESHOLD ||
                abs(premultiplied(Color.blue(before), beforeAlpha) - premultiplied(Color.blue(after), afterAlpha)) > CHANNEL_THRESHOLD
    }

    private fun premultiplied(channel: Int, alpha: Int): Int = (channel * alpha + 127) / 255

    private data class RegionSummary(val regions: List<CompareRegion>, val totalCount: Int)

    private fun regions(
        tileChanges: IntArray,
        columns: Int,
        rows: Int,
        tileSize: Int,
        width: Int,
        height: Int,
    ): RegionSummary {
        val visited = BooleanArray(tileChanges.size)
        val queue = IntArray(tileChanges.size)
        val output = mutableListOf<CompareRegion>()
        for (start in tileChanges.indices) {
            if (tileChanges[start] == 0 || visited[start]) continue
            var queueStart = 0
            var queueEnd = 0
            queue[queueEnd++] = start
            visited[start] = true
            var minColumn = start % columns
            var maxColumn = minColumn
            var minRow = start / columns
            var maxRow = minRow
            var componentChanges = 0
            while (queueStart < queueEnd) {
                val current = queue[queueStart++]
                val column = current % columns
                val row = current / columns
                minColumn = minOf(minColumn, column)
                maxColumn = maxOf(maxColumn, column)
                minRow = minOf(minRow, row)
                maxRow = maxOf(maxRow, row)
                componentChanges += tileChanges[current]
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nextColumn = column + dx
                        val nextRow = row + dy
                        if (nextColumn !in 0 until columns || nextRow !in 0 until rows) continue
                        val next = nextRow * columns + nextColumn
                        if (!visited[next] && tileChanges[next] > 0) {
                            visited[next] = true
                            queue[queueEnd++] = next
                        }
                    }
                }
            }
            output += CompareRegion(
                left = minColumn * tileSize,
                top = minRow * tileSize,
                right = minOf(width, (maxColumn + 1) * tileSize),
                bottom = minOf(height, (maxRow + 1) * tileSize),
                changedPixels = componentChanges,
            )
        }
        val sorted = output.sortedWith(
            compareByDescending<CompareRegion> { it.changedPixels }
                .thenBy { it.top }
                .thenBy { it.left }
        )
        if (sorted.size <= MAX_REGIONS) return RegionSummary(sorted, sorted.size)
        val retained = sorted.take(MAX_REGIONS - 1)
        val overflow = sorted.drop(MAX_REGIONS - 1)
        val overflowRegion = CompareRegion(
            left = overflow.minOf(CompareRegion::left),
            top = overflow.minOf(CompareRegion::top),
            right = overflow.maxOf(CompareRegion::right),
            bottom = overflow.maxOf(CompareRegion::bottom),
            changedPixels = overflow.sumOf(CompareRegion::changedPixels),
        )
        return RegionSummary(retained + overflowRegion, sorted.size)
    }
}
