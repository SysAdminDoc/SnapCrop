package com.sysadmindoc.snapcrop

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RedactionModelsTest {
    @Test
    fun sensitiveFactoryGroupsSameBoundsAndPreservesConservativeEvidence() {
        val bounds = Rect(10, 20, 80, 55)
        val regions = RedactionRegions.fromSensitiveDetections(
            listOf(
                detection(SensitiveTextCategory.EMAIL, bounds, SensitiveTextDetectionSource.ENTITY),
                detection(SensitiveTextCategory.PHONE, bounds, SensitiveTextDetectionSource.REGEX),
                detection(SensitiveTextCategory.EMAIL, Rect(bounds), SensitiveTextDetectionSource.REGEX)
            )
        )

        assertEquals(1, regions.size)
        val region = regions.single()
        assertEquals(setOf(RedactionCategory.EMAIL, RedactionCategory.PHONE), region.categories)
        assertEquals(RedactionSource.OCR_REGEX, region.source)
        assertEquals(RedactionStyle.SOLID, region.style)
        assertTrue(region.enabled)
    }

    @Test
    fun sensitiveFactoryIdsAreStableAcrossInputOrderAndCategoryEnrichment() {
        val bounds = Rect(2, 3, 40, 30)
        val email = detection(SensitiveTextCategory.EMAIL, bounds, SensitiveTextDetectionSource.REGEX)
        val phone = detection(SensitiveTextCategory.PHONE, bounds, SensitiveTextDetectionSource.ENTITY)

        val first = RedactionRegions.fromSensitiveDetections(listOf(email, phone)).single()
        val reordered = RedactionRegions.fromSensitiveDetections(listOf(phone, email)).single()
        val enrichedLater = RedactionRegions.fromSensitiveDetections(listOf(email)).single()

        assertEquals(first.id, reordered.id)
        assertEquals(first.id, enrichedLater.id)
    }

    @Test
    fun faceAndManualFactoriesAreTypedAndDeduplicateGeometry() {
        val face = Rect(4, 5, 30, 40)
        val faces = RedactionRegions.fromFaces(listOf(face, Rect(face), Rect(0, 0, 0, 2)))
        val manual = RedactionRegions.manual(Rect(50, 60, 90, 100), RedactionStyle.PIXELATE)

        assertEquals(1, faces.size)
        assertEquals(setOf(RedactionCategory.FACE), faces.single().categories)
        assertEquals(RedactionSource.FACE_DETECTOR, faces.single().source)
        assertEquals(setOf(RedactionCategory.MANUAL), manual.categories)
        assertEquals(RedactionSource.MANUAL, manual.source)
        assertEquals(RedactionStyle.PIXELATE, manual.style)
        assertNotEquals(faces.single().id, manual.id)
    }

    @Test
    fun regionDefensivelyCopiesInputGetterCopyAndCategorySet() {
        val originalBounds = Rect(1, 2, 20, 30)
        val originalCategories = linkedSetOf(RedactionCategory.EMAIL)
        val region = RedactionRegion(
            id = "region",
            bounds = originalBounds,
            categories = originalCategories,
            source = RedactionSource.OCR_REGEX,
            style = RedactionStyle.SOLID
        )

        originalBounds.offset(100, 100)
        originalCategories.add(RedactionCategory.PHONE)
        val leakedGetter = region.bounds
        leakedGetter.offset(50, 50)
        val copied = region.copy()
        leakedGetter.set(0, 0, 1, 1)

        assertEquals(Rect(1, 2, 20, 30), region.bounds)
        assertEquals(Rect(1, 2, 20, 30), copied.bounds)
        assertEquals(setOf(RedactionCategory.EMAIL), region.categories)
    }

    @Test
    fun mergeDeduplicatesAndPreservesExistingUserState() {
        val bounds = Rect(10, 10, 50, 40)
        val existing = RedactionRegions.fromSensitiveDetections(
            listOf(detection(SensitiveTextCategory.EMAIL, bounds, SensitiveTextDetectionSource.ENTITY)),
            style = RedactionStyle.PIXELATE
        ).single().copy(enabled = false)
        val incoming = RedactionRegions.fromSensitiveDetections(
            listOf(detection(SensitiveTextCategory.PHONE, bounds, SensitiveTextDetectionSource.REGEX))
        ).single()

        val merged = RedactionRegions.merge(listOf(existing), listOf(incoming)).single()

        assertEquals(existing.id, merged.id)
        assertEquals(setOf(RedactionCategory.EMAIL, RedactionCategory.PHONE), merged.categories)
        assertEquals(RedactionSource.OCR_REGEX, merged.source)
        assertEquals(RedactionStyle.PIXELATE, merged.style)
        assertFalse(merged.enabled)
    }

    @Test
    fun categoryToggleOnlyChangesMatchingRegions() {
        val email = RedactionRegions.fromSensitiveDetections(
            listOf(detection(SensitiveTextCategory.EMAIL, Rect(0, 0, 20, 20)))
        ).single()
        val face = RedactionRegions.fromFaces(listOf(Rect(30, 30, 60, 60))).single()

        val disabled = RedactionRegions.toggleCategory(listOf(email, face), RedactionCategory.EMAIL)
        assertFalse(disabled.first().enabled)
        assertTrue(disabled.last().enabled)

        val enabledAgain = RedactionRegions.toggleCategory(disabled, RedactionCategory.EMAIL)
        assertTrue(enabledAgain.first().enabled)
        assertTrue(enabledAgain.last().enabled)
    }

    @Test
    fun bulkCategoryToggleHandlesMixedInitialStateConservatively() {
        val first = region("first", Rect(0, 0, 10, 10), RedactionCategory.EMAIL, enabled = true)
        val second = region("second", Rect(20, 20, 30, 30), RedactionCategory.EMAIL, enabled = false)

        val disabled = RedactionRegions.toggleCategory(listOf(first, second), RedactionCategory.EMAIL)
        assertTrue(disabled.none { it.enabled })

        val enabled = RedactionRegions.toggleCategory(disabled, RedactionCategory.EMAIL)
        assertTrue(enabled.all { it.enabled })
    }

    @Test
    fun movePreservesSizeAndClampsToImageEdges() {
        val region = RedactionRegions.manual(Rect(10, 20, 40, 50))

        val topLeft = RedactionRegions.move(region, -100, -100, 100, 80)
        val bottomRight = RedactionRegions.move(region, 500, 500, 100, 80)

        assertEquals(Rect(0, 0, 30, 30), topLeft.bounds)
        assertEquals(Rect(70, 50, 100, 80), bottomRight.bounds)
        assertEquals(30, bottomRight.bounds.width())
        assertEquals(30, bottomRight.bounds.height())
        assertEquals(Rect(10, 20, 40, 50), region.bounds)
    }

    @Test
    fun resizeNormalizesClampsAndEnforcesMinimumSize() {
        val region = RedactionRegions.manual(Rect(10, 10, 30, 30))

        val tinyAtEdge = RedactionRegions.resize(region, Rect(99, 79, 98, 78), 100, 80)
        val outside = RedactionRegions.resize(region, Rect(-20, -30, 150, 120), 100, 80)

        assertEquals(Rect(96, 76, 100, 80), tinyAtEdge.bounds)
        assertEquals(Rect(0, 0, 100, 80), outside.bounds)
        assertTrue(tinyAtEdge.bounds.width() >= RedactionRegions.MIN_REGION_SIZE)
        assertTrue(tinyAtEdge.bounds.height() >= RedactionRegions.MIN_REGION_SIZE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun resizeRejectsCanvasSmallerThanMinimumRegion() {
        val region = RedactionRegions.manual(Rect(0, 0, 10, 10))
        RedactionRegions.resize(region, Rect(0, 0, 1, 1), 3, 3)
    }

    private fun detection(
        category: SensitiveTextCategory,
        bounds: Rect,
        source: SensitiveTextDetectionSource = SensitiveTextDetectionSource.REGEX
    ) = SensitiveTextDetection(category, Rect(bounds), source)

    private fun region(
        id: String,
        bounds: Rect,
        category: RedactionCategory,
        enabled: Boolean
    ) = RedactionRegion(
        id = id,
        bounds = bounds,
        categories = setOf(category),
        source = RedactionSource.OCR_REGEX,
        style = RedactionStyle.SOLID,
        enabled = enabled
    )
}
