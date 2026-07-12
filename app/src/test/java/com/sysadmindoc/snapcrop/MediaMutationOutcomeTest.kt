package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    }

    @Test
    fun `all confirmed items are successful`() {
        val outcome = MediaMutationOutcome(requested = 2, succeeded = 2)

        assertEquals(MediaMutationResult.SUCCESS, outcome.result)
        assertEquals(0, outcome.retained)
    }

    @Test
    fun `invalid counts fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaMutationOutcome(requested = 1, succeeded = 2)
        }
    }
}
