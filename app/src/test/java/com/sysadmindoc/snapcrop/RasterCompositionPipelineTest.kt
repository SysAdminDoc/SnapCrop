package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterCompositionPipelineTest {
    private val uris = List(3) { Uri.parse("content://snapcrop.test/image/$it") }

    @Test
    fun boundsFailuresRequireConfirmationBeforeAnyAllocationOrDecode() {
        var allocations = 0
        val gateway = FakeGateway(
            inspections = mapOf(
                0 to BatchImageBoundsResult.Ready(100, 100),
                1 to BatchImageBoundsResult.Failure(BatchImageBoundsFailureKind.INVALID_IMAGE),
                2 to BatchImageBoundsResult.Ready(100, 100),
            ),
        )

        val result = RasterCompositionPipeline.compose(
            gateway = gateway,
            uris = uris,
            minimumInputs = 2,
            allowedOmissions = emptySet(),
            planner = { RasterCompositionLayouts.stitch(it, vertical = false) },
            allocator = { width, height -> allocations++; Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) },
        )

        result as RasterCompositionResult.ConfirmationRequired
        assertEquals(listOf(1), result.failedPositions)
        assertEquals(0, allocations)
        assertEquals(0, gateway.decodeCalls)
    }

    @Test
    fun confirmedOmissionRendersEveryRemainingInputAndKeepsTypedFailure() {
        val gateway = FakeGateway(
            inspections = mapOf(
                0 to BatchImageBoundsResult.Ready(100, 100),
                1 to BatchImageBoundsResult.Failure(BatchImageBoundsFailureKind.INVALID_IMAGE),
                2 to BatchImageBoundsResult.Ready(100, 100),
            ),
        )

        val result = RasterCompositionPipeline.compose(
            gateway = gateway,
            uris = uris,
            minimumInputs = 2,
            allowedOmissions = setOf(1),
            planner = { RasterCompositionLayouts.stitch(it, vertical = false) },
        ) as RasterCompositionResult.Success

        assertEquals(2, result.inputs.filterIsInstance<RasterInputOutcome.Rendered>().size)
        assertEquals(1, result.inputs.filterIsInstance<RasterInputOutcome.Failed>().size)
        assertEquals(2, gateway.decodeCalls)
        assertEquals(200, result.bitmap.width)
        assertEquals(100, result.bitmap.height)
        assertEquals(20_000L, result.budget.outputPixels)
        assertEquals(80_000L, result.budget.outputBytes)
        assertEquals(10_000L, result.budget.peakInputPixels)
        assertEquals(40_000L, result.budget.peakInputBytes)
        assertEquals(120_000L, result.budget.peakBytes)
        result.bitmap.recycle()
    }

    @Test
    fun aNewFailureAfterConfirmationRequiresAFreshSummary() {
        val gateway = FakeGateway(
            inspections = mapOf(
                0 to BatchImageBoundsResult.Ready(100, 100),
                1 to BatchImageBoundsResult.Failure(BatchImageBoundsFailureKind.INVALID_IMAGE),
                2 to BatchImageBoundsResult.Failure(BatchImageBoundsFailureKind.READ),
                3 to BatchImageBoundsResult.Ready(100, 100),
            ),
        )
        val fourUris = uris + Uri.parse("content://snapcrop.test/image/3")

        val result = RasterCompositionPipeline.compose(
            gateway = gateway,
            uris = fourUris,
            minimumInputs = 2,
            allowedOmissions = setOf(1),
            planner = { RasterCompositionLayouts.stitch(it, vertical = false) },
        ) as RasterCompositionResult.ConfirmationRequired

        assertEquals(listOf(1, 2), result.failedPositions)
        assertEquals(0, gateway.decodeCalls)
    }

    @Test
    fun oversizedLayoutFailsBeforeOutputAllocationOrPixelDecode() {
        var allocations = 0
        val gateway = FakeGateway(
            inspections = mapOf(
                0 to BatchImageBoundsResult.Ready(2_000, 16_000),
                1 to BatchImageBoundsResult.Ready(2_000, 16_000),
            ),
        )

        val result = RasterCompositionPipeline.compose(
            gateway = gateway,
            uris = uris.take(2),
            minimumInputs = 2,
            allowedOmissions = emptySet(),
            planner = { RasterCompositionLayouts.stitch(it, vertical = true) },
            allocator = { width, height -> allocations++; Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) },
        ) as RasterCompositionResult.Failure

        assertEquals(RasterCompositionFailure.BUDGET_EXCEEDED, result.reason)
        assertEquals(0, allocations)
        assertEquals(0, gateway.decodeCalls)
    }

    @Test
    fun decodeFailureRecyclesBothDecodedAndPartialOutputBitmaps() {
        val decoded = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        var output: Bitmap? = null
        val gateway = FakeGateway(
            inspections = mapOf(
                0 to BatchImageBoundsResult.Ready(100, 100),
                1 to BatchImageBoundsResult.Ready(100, 100),
            ),
            decoder = { input ->
                if (input.position == 0) {
                    BatchImageIntakeResult.Ready(decoded, 100, 100, 1)
                } else {
                    BatchImageIntakeResult.Unreadable("fixture")
                }
            },
        )

        val result = RasterCompositionPipeline.compose(
            gateway = gateway,
            uris = uris.take(2),
            minimumInputs = 2,
            allowedOmissions = emptySet(),
            planner = { RasterCompositionLayouts.stitch(it, vertical = false) },
            allocator = { width, height -> Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output = it } },
        ) as RasterCompositionResult.Failure

        assertEquals(RasterCompositionFailure.INPUT_FAILED, result.reason)
        assertTrue(decoded.isRecycled)
        assertTrue(requireNotNull(output).isRecycled)
        assertFalse(result.inputs.all { it is RasterInputOutcome.Rendered })
    }

    @Test
    fun allocationOomIsTypedAndNeverStartsDecoding() {
        val gateway = FakeGateway(
            inspections = mapOf(
                0 to BatchImageBoundsResult.Ready(100, 100),
                1 to BatchImageBoundsResult.Ready(100, 100),
            ),
        )

        val result = RasterCompositionPipeline.compose(
            gateway = gateway,
            uris = uris.take(2),
            minimumInputs = 2,
            allowedOmissions = emptySet(),
            planner = { RasterCompositionLayouts.stitch(it, vertical = false) },
            allocator = { _, _ -> throw OutOfMemoryError("fixture") },
        ) as RasterCompositionResult.Failure

        assertEquals(RasterCompositionFailure.ALLOCATION_FAILED, result.reason)
        assertEquals(0, gateway.decodeCalls)
    }

    @Test
    fun allThreeActivitiesRouteCompositionAndPublicationThroughSharedTransactions() {
        listOf("StitchActivity.kt", "CollageActivity.kt", "DeviceFrameActivity.kt").forEach { name ->
            val source = sourceFile("app/src/main/java/com/sysadmindoc/snapcrop/$name")
            assertTrue(name, source.contains("RasterCompositionPipeline.compose("))
            assertTrue(name, source.contains("MediaStoreImageWriter.write("))
        }
    }

    private class FakeGateway(
        private val inspections: Map<Int, BatchImageBoundsResult>,
        private val decoder: (RasterCompositionInput) -> BatchImageIntakeResult = {
            val bounds = inspections.getValue(it.position) as BatchImageBoundsResult.Ready
            BatchImageIntakeResult.Ready(
                Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888),
                bounds.width,
                bounds.height,
                1,
            )
        },
    ) : RasterCompositionSourceGateway {
        var decodeCalls = 0

        override fun inspect(input: RasterCompositionInput): BatchImageBoundsResult =
            inspections.getValue(input.position)

        override fun decode(input: RasterCompositionInput, targetMaxDimension: Int): BatchImageIntakeResult {
            decodeCalls++
            return decoder(input)
        }
    }

    private fun sourceFile(path: String): String {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate $path")
    }
}
