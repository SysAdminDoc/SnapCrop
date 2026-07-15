package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMutationOutcomeTest {
    @Test
    fun `denial retains every requested item`() {
        val outcome = MediaMutationOutcome(requested = 3, succeeded = 0)

        assertEquals(MediaMutationResult.RETAINED, outcome.result)
        assertEquals(3, outcome.retained)
    }

    @Test
    fun `partial completion preserves exact retained count`() {
        val outcome = MediaMutationOutcome(requested = 5, succeeded = 2)

        assertEquals(MediaMutationResult.PARTIAL, outcome.result)
        assertEquals(3, outcome.retained)
        assertEquals(DiagnosticResult.PARTIAL, outcome.diagnosticResult)
        assertEquals(
            DiagnosticPartial.Counts(requested = 5, succeeded = 2, retained = 3),
            outcome.diagnosticPartial,
        )
        assertEquals(R.string.toast_trashed_partial, outcome.messageResource(scopedTrash = true))
        assertEquals(R.string.toast_deleted_partial, outcome.messageResource(scopedTrash = false))
    }

    @Test
    fun `all confirmed items are successful`() {
        val outcome = MediaMutationOutcome(requested = 2, succeeded = 2)

        assertEquals(MediaMutationResult.SUCCESS, outcome.result)
        assertEquals(0, outcome.retained)
        assertEquals(DiagnosticResult.SUCCESS, outcome.diagnosticResult)
        assertEquals(null, outcome.diagnosticPartial)
        assertEquals(R.string.toast_trashed_count, outcome.messageResource(scopedTrash = true))
        assertEquals(R.string.toast_deleted_items_count, outcome.messageResource(scopedTrash = false))
    }

    @Test
    fun `invalid counts fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaMutationOutcome(requested = 1, succeeded = 2)
        }
    }

    @Test
    fun `recoverable trash preserves user metadata while permanent deletion cleans it`() {
        assertFalse(MediaMutationMetadataPolicy.cleanImmediately(platformSdk = 30))
        assertFalse(MediaMutationMetadataPolicy.cleanImmediately(platformSdk = 37))
        assertTrue(MediaMutationMetadataPolicy.cleanImmediately(platformSdk = 29))
    }
}
