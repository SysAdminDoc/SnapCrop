package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class RasterCompositionInput(
    val position: Int,
    val uri: Uri,
)

internal data class RasterSourceBounds(
    val input: RasterCompositionInput,
    val width: Int,
    val height: Int,
)

internal enum class RasterInputFailure {
    OVERSIZED,
    OPEN,
    READ,
    INVALID_IMAGE,
    DECODE,
    SOURCE_CHANGED,
    RENDER,
    CANCELLED,
}

internal sealed interface RasterInputOutcome {
    val input: RasterCompositionInput

    data class Ready(
        override val input: RasterCompositionInput,
        val width: Int,
        val height: Int,
    ) : RasterInputOutcome

    data class Rendered(
        override val input: RasterCompositionInput,
        val decodedWidth: Int,
        val decodedHeight: Int,
        val decodedBytes: Long,
    ) : RasterInputOutcome

    data class Failed(
        override val input: RasterCompositionInput,
        val reason: RasterInputFailure,
    ) : RasterInputOutcome
}

internal enum class RasterScaleMode { FIT, CENTER_CROP }

internal data class RasterPlacement(
    val inputPosition: Int,
    val destination: Rect,
    val scaleMode: RasterScaleMode = RasterScaleMode.FIT,
) {
    val targetMaxDimension: Int get() = maxOf(destination.width(), destination.height())
}

internal data class RasterLayout<Metadata>(
    val width: Int,
    val height: Int,
    val placements: List<RasterPlacement>,
    val backgroundColor: Int? = null,
    val metadata: Metadata,
)

internal data class RasterCompositionBudget(
    val outputPixels: Long,
    val outputBytes: Long,
    val peakInputPixels: Long,
    val peakInputBytes: Long,
    val peakBytes: Long,
)

internal enum class RasterCompositionFailure(@param:StringRes val messageRes: Int) {
    NOT_ENOUGH_INPUTS(R.string.raster_composition_input_failed),
    INVALID_LAYOUT(R.string.raster_composition_failed),
    BUDGET_EXCEEDED(R.string.raster_composition_too_large),
    ALLOCATION_FAILED(R.string.raster_composition_memory_failed),
    INPUT_FAILED(R.string.raster_composition_input_failed),
    RENDER_FAILED(R.string.raster_composition_failed),
    CANCELLED(R.string.raster_composition_cancelled),
}

internal sealed interface RasterCompositionResult {
    val inputs: List<RasterInputOutcome>

    data class Success(
        val bitmap: Bitmap,
        val budget: RasterCompositionBudget,
        override val inputs: List<RasterInputOutcome>,
    ) : RasterCompositionResult

    data class ConfirmationRequired(
        override val inputs: List<RasterInputOutcome>,
    ) : RasterCompositionResult {
        val failedPositions: List<Int> = inputs.filterIsInstance<RasterInputOutcome.Failed>()
            .map { it.input.position }
    }

    data class Failure(
        val reason: RasterCompositionFailure,
        override val inputs: List<RasterInputOutcome>,
    ) : RasterCompositionResult
}

internal interface RasterCompositionSourceGateway {
    fun inspect(input: RasterCompositionInput): BatchImageBoundsResult
    fun decode(input: RasterCompositionInput, targetMaxDimension: Int): BatchImageIntakeResult
}

internal object RasterCompositionPipeline {
    const val MAX_OUTPUT_EDGE = 16_384
    const val MAX_OUTPUT_PIXELS = 20_000_000L
    const val MAX_OUTPUT_BYTES = MAX_OUTPUT_PIXELS * 4L
    const val MAX_PEAK_BYTES = 128L * 1024L * 1024L

    fun <Metadata> compose(
        resolver: ContentResolver,
        uris: List<Uri>,
        minimumInputs: Int,
        allowedOmissions: Set<Int>,
        planner: (List<RasterSourceBounds>) -> RasterLayout<Metadata>?,
        drawBeforeInputs: (Canvas, RasterLayout<Metadata>) -> Unit = { _, _ -> },
        drawInput: ((Canvas, RasterLayout<Metadata>, RasterPlacement, Bitmap) -> Unit)? = null,
        drawAfterInputs: (Canvas, RasterLayout<Metadata>) -> Unit = { _, _ -> },
    ): RasterCompositionResult = compose(
        gateway = object : RasterCompositionSourceGateway {
            override fun inspect(input: RasterCompositionInput): BatchImageBoundsResult =
                BatchImageIntake.inspectBounds(resolver, input.uri)

            override fun decode(
                input: RasterCompositionInput,
                targetMaxDimension: Int,
            ): BatchImageIntakeResult = BatchImageIntake.decodeForAnalysis(
                resolver = resolver,
                uri = input.uri,
                targetMaxDimension = targetMaxDimension,
            )
        },
        uris = uris,
        minimumInputs = minimumInputs,
        allowedOmissions = allowedOmissions,
        planner = planner,
        drawBeforeInputs = drawBeforeInputs,
        drawInput = drawInput,
        drawAfterInputs = drawAfterInputs,
    )

    internal fun <Metadata> compose(
        gateway: RasterCompositionSourceGateway,
        uris: List<Uri>,
        minimumInputs: Int,
        allowedOmissions: Set<Int>,
        planner: (List<RasterSourceBounds>) -> RasterLayout<Metadata>?,
        drawBeforeInputs: (Canvas, RasterLayout<Metadata>) -> Unit = { _, _ -> },
        drawInput: ((Canvas, RasterLayout<Metadata>, RasterPlacement, Bitmap) -> Unit)? = null,
        drawAfterInputs: (Canvas, RasterLayout<Metadata>) -> Unit = { _, _ -> },
        allocator: (Int, Int) -> Bitmap = { width, height ->
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        },
    ): RasterCompositionResult {
        require(minimumInputs > 0)
        val inputs = uris.mapIndexed { index, uri -> RasterCompositionInput(index, uri) }
        val boundsByPosition = linkedMapOf<Int, RasterSourceBounds>()
        val outcomes = inputs.associate { input ->
            val inspected = try {
                gateway.inspect(input)
            } catch (_: CancellationException) {
                BatchImageBoundsResult.Failure(BatchImageBoundsFailureKind.CANCELLED)
            } catch (_: OutOfMemoryError) {
                BatchImageBoundsResult.Failure(BatchImageBoundsFailureKind.INVALID_IMAGE)
            } catch (_: Exception) {
                BatchImageBoundsResult.Failure(BatchImageBoundsFailureKind.READ)
            }
            val outcome = when (inspected) {
                is BatchImageBoundsResult.Ready -> {
                    val bounds = RasterSourceBounds(input, inspected.width, inspected.height)
                    boundsByPosition[input.position] = bounds
                    RasterInputOutcome.Ready(input, inspected.width, inspected.height)
                }
                is BatchImageBoundsResult.Failure -> RasterInputOutcome.Failed(
                    input,
                    inspected.kind.toRasterFailure(),
                )
            }
            input.position to outcome
        }.toMutableMap()
        val readyBounds = boundsByPosition.values.toList()
        val hasPreflightFailures = outcomes.values.any { it is RasterInputOutcome.Failed }
        if (readyBounds.size < minimumInputs) {
            return RasterCompositionResult.Failure(
                RasterCompositionFailure.NOT_ENOUGH_INPUTS,
                outcomes.values.sortedBy { it.input.position },
            )
        }
        val failedPositions = outcomes.values.filterIsInstance<RasterInputOutcome.Failed>()
            .mapTo(linkedSetOf()) { it.input.position }
        if (hasPreflightFailures && !allowedOmissions.containsAll(failedPositions)) {
            return RasterCompositionResult.ConfirmationRequired(
                outcomes.values.sortedBy { it.input.position },
            )
        }

        val layout = try {
            planner(readyBounds)
        } catch (_: RuntimeException) {
            null
        } ?: return RasterCompositionResult.Failure(
            RasterCompositionFailure.INVALID_LAYOUT,
            outcomes.values.sortedBy { it.input.position },
        )
        val readyPositions = readyBounds.mapTo(linkedSetOf()) { it.input.position }
        val placementPositions = layout.placements.map { it.inputPosition }
        if (layout.width <= 0 || layout.height <= 0 ||
            placementPositions.toSet() != readyPositions ||
            placementPositions.size != readyPositions.size ||
            layout.placements.any {
                it.destination.left < 0 || it.destination.top < 0 ||
                    it.destination.right > layout.width || it.destination.bottom > layout.height ||
                    it.destination.width() <= 0 || it.destination.height() <= 0
            }
        ) {
            return RasterCompositionResult.Failure(
                RasterCompositionFailure.INVALID_LAYOUT,
                outcomes.values.sortedBy { it.input.position },
            )
        }

        val budget = calculateBudget(layout, boundsByPosition)
            ?: return RasterCompositionResult.Failure(
                RasterCompositionFailure.BUDGET_EXCEEDED,
                outcomes.values.sortedBy { it.input.position },
            )
        val output = try {
            allocator(layout.width, layout.height)
        } catch (_: OutOfMemoryError) {
            return RasterCompositionResult.Failure(
                RasterCompositionFailure.ALLOCATION_FAILED,
                outcomes.values.sortedBy { it.input.position },
            )
        } catch (_: RuntimeException) {
            return RasterCompositionResult.Failure(
                RasterCompositionFailure.ALLOCATION_FAILED,
                outcomes.values.sortedBy { it.input.position },
            )
        }
        val allocatedOutputBytes = output.allocationByteCount.toLong()
        if (output.width != layout.width || output.height != layout.height ||
            allocatedOutputBytes > MAX_OUTPUT_BYTES ||
            allocatedOutputBytes + budget.peakInputBytes > MAX_PEAK_BYTES
        ) {
            recycle(output)
            return RasterCompositionResult.Failure(
                RasterCompositionFailure.BUDGET_EXCEEDED,
                outcomes.values.sortedBy { it.input.position },
            )
        }

        val canvas = Canvas(output)
        try {
            layout.backgroundColor?.let(canvas::drawColor)
            drawBeforeInputs(canvas, layout)
            layout.placements.forEach { placement ->
                val bounds = requireNotNull(boundsByPosition[placement.inputPosition])
                val decoded = try {
                    gateway.decode(bounds.input, placement.targetMaxDimension)
                } catch (_: OutOfMemoryError) {
                    BatchImageIntakeResult.Failed(BatchStreamFailureKind.DECODE)
                } catch (_: RuntimeException) {
                    BatchImageIntakeResult.Unreadable("decoder failed")
                }
                val ready = decoded as? BatchImageIntakeResult.Ready
                if (ready == null) {
                    outcomes[placement.inputPosition] = RasterInputOutcome.Failed(
                        bounds.input,
                        decoded.toRasterFailure(),
                    )
                    recycle(output)
                    return RasterCompositionResult.Failure(
                        if (decoded is BatchImageIntakeResult.Cancelled) {
                            RasterCompositionFailure.CANCELLED
                        } else {
                            RasterCompositionFailure.INPUT_FAILED
                        },
                        outcomes.values.sortedBy { it.input.position },
                    )
                }
                val bitmap = ready.bitmap
                try {
                    if (ready.sourceWidth != bounds.width || ready.sourceHeight != bounds.height) {
                        outcomes[placement.inputPosition] = RasterInputOutcome.Failed(
                            bounds.input,
                            RasterInputFailure.SOURCE_CHANGED,
                        )
                        recycle(output)
                        return RasterCompositionResult.Failure(
                            RasterCompositionFailure.INPUT_FAILED,
                            outcomes.values.sortedBy { it.input.position },
                        )
                    }
                    val decodedBytes = bitmap.allocationByteCount.toLong()
                    if (decodedBytes > BatchImageIntake.MAX_WORKING_BYTES ||
                        allocatedOutputBytes + decodedBytes > MAX_PEAK_BYTES
                    ) {
                        outcomes[placement.inputPosition] = RasterInputOutcome.Failed(
                            bounds.input,
                            RasterInputFailure.OVERSIZED,
                        )
                        recycle(output)
                        return RasterCompositionResult.Failure(
                            RasterCompositionFailure.BUDGET_EXCEEDED,
                            outcomes.values.sortedBy { it.input.position },
                        )
                    }
                    if (drawInput == null) {
                        drawDefault(canvas, placement, bitmap)
                    } else {
                        drawInput(canvas, layout, placement, bitmap)
                    }
                    outcomes[placement.inputPosition] = RasterInputOutcome.Rendered(
                        bounds.input,
                        bitmap.width,
                        bitmap.height,
                        decodedBytes,
                    )
                } catch (_: OutOfMemoryError) {
                    outcomes[placement.inputPosition] = RasterInputOutcome.Failed(
                        bounds.input,
                        RasterInputFailure.RENDER,
                    )
                    recycle(output)
                    return RasterCompositionResult.Failure(
                        RasterCompositionFailure.RENDER_FAILED,
                        outcomes.values.sortedBy { it.input.position },
                    )
                } catch (_: RuntimeException) {
                    outcomes[placement.inputPosition] = RasterInputOutcome.Failed(
                        bounds.input,
                        RasterInputFailure.RENDER,
                    )
                    recycle(output)
                    return RasterCompositionResult.Failure(
                        RasterCompositionFailure.RENDER_FAILED,
                        outcomes.values.sortedBy { it.input.position },
                    )
                } finally {
                    recycle(bitmap)
                }
            }
            drawAfterInputs(canvas, layout)
        } catch (_: OutOfMemoryError) {
            recycle(output)
            return RasterCompositionResult.Failure(
                RasterCompositionFailure.RENDER_FAILED,
                outcomes.values.sortedBy { it.input.position },
            )
        } catch (_: RuntimeException) {
            recycle(output)
            return RasterCompositionResult.Failure(
                RasterCompositionFailure.RENDER_FAILED,
                outcomes.values.sortedBy { it.input.position },
            )
        }
        return RasterCompositionResult.Success(
            bitmap = output,
            budget = budget,
            inputs = outcomes.values.sortedBy { it.input.position },
        )
    }

    private fun <Metadata> calculateBudget(
        layout: RasterLayout<Metadata>,
        boundsByPosition: Map<Int, RasterSourceBounds>,
    ): RasterCompositionBudget? {
        if (layout.width > MAX_OUTPUT_EDGE || layout.height > MAX_OUTPUT_EDGE) return null
        val outputPixels = layout.width.toLong() * layout.height.toLong()
        val outputBytes = outputPixels * 4L
        if (outputPixels > MAX_OUTPUT_PIXELS || outputBytes > MAX_OUTPUT_BYTES) return null

        var peakInputPixels = 0L
        var peakInputBytes = 0L
        layout.placements.forEach { placement ->
            val bounds = boundsByPosition[placement.inputPosition] ?: return null
            val decode = BatchImageIntake.plan(
                bounds.width,
                bounds.height,
                placement.targetMaxDimension,
            ) as? BatchDecodePlan.Decode ?: return null
            val decodedWidth = ceilDiv(bounds.width, decode.sampleSize)
            val decodedHeight = ceilDiv(bounds.height, decode.sampleSize)
            val pixels = decodedWidth.toLong() * decodedHeight.toLong()
            val bytes = pixels * 4L
            peakInputPixels = maxOf(peakInputPixels, pixels)
            peakInputBytes = maxOf(peakInputBytes, bytes)
        }
        val peakBytes = outputBytes + peakInputBytes
        if (peakBytes > MAX_PEAK_BYTES) return null
        return RasterCompositionBudget(
            outputPixels = outputPixels,
            outputBytes = outputBytes,
            peakInputPixels = peakInputPixels,
            peakInputBytes = peakInputBytes,
            peakBytes = peakBytes,
        )
    }

    private fun drawDefault(canvas: Canvas, placement: RasterPlacement, bitmap: Bitmap) {
        val source = when (placement.scaleMode) {
            RasterScaleMode.FIT -> null
            RasterScaleMode.CENTER_CROP -> centerCropRect(
                bitmap.width,
                bitmap.height,
                placement.destination.width(),
                placement.destination.height(),
            )
        }
        canvas.drawBitmap(bitmap, source, placement.destination, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun centerCropRect(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Rect {
        val sourceRatio = sourceWidth.toDouble() / sourceHeight
        val targetRatio = targetWidth.toDouble() / targetHeight
        return if (sourceRatio > targetRatio) {
            val width = (sourceHeight * targetRatio).roundToInt().coerceIn(1, sourceWidth)
            val left = (sourceWidth - width) / 2
            Rect(left, 0, left + width, sourceHeight)
        } else {
            val height = (sourceWidth / targetRatio).roundToInt().coerceIn(1, sourceHeight)
            val top = (sourceHeight - height) / 2
            Rect(0, top, sourceWidth, top + height)
        }
    }

    private fun BatchImageBoundsFailureKind.toRasterFailure(): RasterInputFailure = when (this) {
        BatchImageBoundsFailureKind.ENCODED_TOO_LARGE,
        BatchImageBoundsFailureKind.SOURCE_TOO_LARGE -> RasterInputFailure.OVERSIZED
        BatchImageBoundsFailureKind.OPEN -> RasterInputFailure.OPEN
        BatchImageBoundsFailureKind.READ -> RasterInputFailure.READ
        BatchImageBoundsFailureKind.INVALID_IMAGE -> RasterInputFailure.INVALID_IMAGE
        BatchImageBoundsFailureKind.CANCELLED -> RasterInputFailure.CANCELLED
    }

    private fun BatchImageIntakeResult.toRasterFailure(): RasterInputFailure = when (this) {
        is BatchImageIntakeResult.Oversized -> RasterInputFailure.OVERSIZED
        is BatchImageIntakeResult.Failed -> when (kind) {
            BatchStreamFailureKind.OPEN -> RasterInputFailure.OPEN
            BatchStreamFailureKind.READ -> RasterInputFailure.READ
            BatchStreamFailureKind.DECODE -> RasterInputFailure.DECODE
        }
        is BatchImageIntakeResult.Unreadable -> RasterInputFailure.INVALID_IMAGE
        BatchImageIntakeResult.Cancelled -> RasterInputFailure.CANCELLED
        is BatchImageIntakeResult.Ready,
        is BatchImageIntakeResult.Skipped -> RasterInputFailure.DECODE
    }

    private fun ceilDiv(value: Int, divisor: Int): Int =
        ((value.toLong() + divisor - 1L) / divisor).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

internal data class RasterDeviceGeometry(
    val deviceRect: Rect,
    val screenRect: Rect,
    val totalWidth: Int,
    val screenHeight: Int,
)

internal object RasterCompositionLayouts {
    private const val STITCH_NORMALIZED_EDGE = 2_048
    private const val COLLAGE_WIDTH = 1_080
    private const val DEVICE_SCREEN_MAX_EDGE = 4_096
    private const val DEVICE_SCREEN_MAX_PIXELS = 12_000_000L

    fun stitch(bounds: List<RasterSourceBounds>, vertical: Boolean): RasterLayout<Unit>? {
        if (bounds.isEmpty()) return null
        val placements = mutableListOf<RasterPlacement>()
        return if (vertical) {
            val width = minOf(bounds.maxOf(RasterSourceBounds::width), STITCH_NORMALIZED_EDGE)
            var top = 0L
            bounds.forEach { source ->
                val height = scaled(source.height, width, source.width) ?: return null
                val bottom = top + height
                if (bottom > Int.MAX_VALUE) return null
                placements += RasterPlacement(source.input.position, Rect(0, top.toInt(), width, bottom.toInt()))
                top = bottom
            }
            RasterLayout(width, top.toInt(), placements, metadata = Unit)
        } else {
            val height = minOf(bounds.maxOf(RasterSourceBounds::height), STITCH_NORMALIZED_EDGE)
            var left = 0L
            bounds.forEach { source ->
                val width = scaled(source.width, height, source.height) ?: return null
                val right = left + width
                if (right > Int.MAX_VALUE) return null
                placements += RasterPlacement(source.input.position, Rect(left.toInt(), 0, right.toInt(), height))
                left = right
            }
            RasterLayout(left.toInt(), height, placements, metadata = Unit)
        }
    }

    fun collage(
        bounds: List<RasterSourceBounds>,
        columns: Int,
        rows: Int,
        gap: Int,
        cellAspect: Float,
        backgroundColor: Int,
    ): RasterLayout<Unit>? {
        if (columns <= 0 || rows <= 0 || gap < 0 || !cellAspect.isFinite() || cellAspect <= 0f) return null
        if (bounds.size > columns * rows) return null
        val availableWidth = COLLAGE_WIDTH - gap * (columns + 1)
        if (availableWidth <= 0) return null
        val cellWidth = (availableWidth / columns).coerceAtLeast(1)
        val cellHeight = (cellWidth / cellAspect).roundToInt().coerceAtLeast(1)
        val totalHeight = cellHeight.toLong() * rows + gap.toLong() * (rows + 1)
        if (totalHeight > Int.MAX_VALUE) return null
        val placements = bounds.mapIndexed { index, source ->
            val column = index % columns
            val row = index / columns
            val left = gap + column * (cellWidth + gap)
            val top = gap + row * (cellHeight + gap)
            RasterPlacement(
                source.input.position,
                Rect(left, top, left + cellWidth, top + cellHeight),
                RasterScaleMode.CENTER_CROP,
            )
        }
        return RasterLayout(
            width = COLLAGE_WIDTH,
            height = totalHeight.toInt(),
            placements = placements,
            backgroundColor = backgroundColor,
            metadata = Unit,
        )
    }

    fun deviceFrame(
        bounds: RasterSourceBounds,
        bezelWidthFraction: Float,
        backgroundColor: Int,
    ): RasterLayout<RasterDeviceGeometry>? {
        if (!bezelWidthFraction.isFinite() || bezelWidthFraction < 0f) return null
        val (screenWidth, screenHeight) = boundedDimensions(bounds.width, bounds.height)
        val bezel = (screenWidth * bezelWidthFraction).roundToInt().coerceAtLeast(1)
        val totalWidth = screenWidth.toLong() + bezel * 2L
        val totalHeight = screenHeight.toLong() + bezel * 2L
        val padding = (totalWidth * 0.06).roundToInt().coerceAtLeast(1)
        val canvasWidth = totalWidth + padding * 2L
        val canvasHeight = totalHeight + padding * 2L
        if (canvasWidth > Int.MAX_VALUE || canvasHeight > Int.MAX_VALUE) return null
        val deviceRect = Rect(
            padding,
            padding,
            (padding + totalWidth).toInt(),
            (padding + totalHeight).toInt(),
        )
        val screenRect = Rect(
            deviceRect.left + bezel,
            deviceRect.top + bezel,
            deviceRect.right - bezel,
            deviceRect.bottom - bezel,
        )
        val geometry = RasterDeviceGeometry(deviceRect, screenRect, totalWidth.toInt(), screenHeight)
        return RasterLayout(
            width = canvasWidth.toInt(),
            height = canvasHeight.toInt(),
            placements = listOf(RasterPlacement(bounds.input.position, screenRect)),
            backgroundColor = backgroundColor,
            metadata = geometry,
        )
    }

    private fun boundedDimensions(width: Int, height: Int): Pair<Int, Int> {
        val pixels = width.toLong() * height.toLong()
        val edgeScale = DEVICE_SCREEN_MAX_EDGE.toDouble() / maxOf(width, height)
        val pixelScale = sqrt(DEVICE_SCREEN_MAX_PIXELS.toDouble() / pixels)
        val scale = minOf(1.0, edgeScale, pixelScale)
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun scaled(value: Int, target: Int, source: Int): Int? {
        if (value <= 0 || target <= 0 || source <= 0) return null
        val result = (value.toDouble() * target / source).roundToInt()
        return result.coerceAtLeast(1)
    }
}

internal fun ComponentActivity.showRasterOmissionConfirmation(
    result: RasterCompositionResult.ConfirmationRequired,
    onConfirm: () -> Unit,
) {
    val failed = result.failedPositions.map { it + 1 }
    android.app.AlertDialog.Builder(this)
        .setTitle(R.string.raster_omission_title)
        .setMessage(
            getString(
                R.string.raster_omission_message,
                failed.size,
                result.inputs.size,
                failed.joinToString(", "),
            ),
        )
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.raster_omission_continue) { _, _ -> onConfirm() }
        .show()
}
