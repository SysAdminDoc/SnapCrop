package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun newerVersionsDetected() {
        assertTrue(UpdateChecker.isNewer("6.27.0", "6.26.0"))
        assertTrue(UpdateChecker.isNewer("6.26.1", "6.26.0"))
        assertTrue(UpdateChecker.isNewer("7.0.0", "6.26.0"))
        assertTrue(UpdateChecker.isNewer("6.26.10", "6.26.2"))
    }

    @Test
    fun sameOrOlderNotNewer() {
        assertFalse(UpdateChecker.isNewer("6.26.0", "6.26.0"))
        assertFalse(UpdateChecker.isNewer("6.25.0", "6.26.0"))
        assertFalse(UpdateChecker.isNewer("6.26.0", "6.26.1"))
        assertFalse(UpdateChecker.isNewer("5.9.0", "6.26.0"))
    }

    @Test
    fun nonNumericSuffixIgnored() {
        // "6.27.0-beta" parses as [6,27,0] and is newer than 6.26.0.
        assertTrue(UpdateChecker.isNewer("6.27.0-beta", "6.26.0"))
        assertFalse(UpdateChecker.isNewer("6.26.0-rc1", "6.26.0"))
    }
}
