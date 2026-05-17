package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedEraseBackendTest {
    @Test
    fun localSmartEraseIsAlwaysActivatable() {
        assertTrue(AdvancedEraseBackendRegistry.isCandidateActivatable(AdvancedEraseBackendRegistry.localSmartErase))
    }

    @Test
    fun researchModelPackIsBlockedUntilMeasuredAndLicensed() {
        assertFalse(AdvancedEraseBackendRegistry.isCandidateActivatable(AdvancedEraseBackendRegistry.researchModelPack))
    }

    @Test
    fun candidateRequiresQualityLatencySizeAndResolvedLicense() {
        val candidate = AdvancedEraseBackendRegistry.researchModelPack.copy(
            license = "Apache-2.0 model redistribution approved",
            estimatedSizeMb = 80,
            measuredMedianLatencyMs = 1_800,
            qualityLiftPercent = 14
        )

        assertTrue(AdvancedEraseBackendRegistry.isCandidateActivatable(candidate))
        assertFalse(AdvancedEraseBackendRegistry.isCandidateActivatable(candidate.copy(estimatedSizeMb = 180)))
        assertFalse(AdvancedEraseBackendRegistry.isCandidateActivatable(candidate.copy(measuredMedianLatencyMs = 3_200)))
        assertFalse(AdvancedEraseBackendRegistry.isCandidateActivatable(candidate.copy(qualityLiftPercent = 4)))
    }
}
