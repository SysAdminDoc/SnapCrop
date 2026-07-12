package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestScreenshotResolverTest {
    @Test
    fun acceptsScreenshotNamesAndFolders() {
        assertTrue(LatestScreenshotResolver.isScreenshotCandidate("Screenshot_20260712.png", "Pictures/"))
        assertTrue(LatestScreenshotResolver.isScreenshotCandidate("image.png", "Pictures/Screenshots/"))
        assertTrue(LatestScreenshotResolver.isScreenshotCandidate("capture.png", "DCIM/screenshot_archive/"))
    }

    @Test
    fun rejectsSnapCropAndAutomatedOutputs() {
        assertFalse(LatestScreenshotResolver.isScreenshotCandidate("SnapCrop_123.png", "Pictures/Screenshots/"))
        assertFalse(LatestScreenshotResolver.isScreenshotCandidate("image.png", "Pictures/SnapCrop/"))
        assertFalse(LatestScreenshotResolver.isScreenshotCandidate("reddit_123.png", "Pictures/Screenshots/"))
        assertFalse(LatestScreenshotResolver.isScreenshotCandidate("twitter_123.png", "Pictures/Screenshots/"))
    }

    @Test
    fun rejectsOrdinaryMedia() {
        assertFalse(LatestScreenshotResolver.isScreenshotCandidate("holiday.jpg", "DCIM/Camera/"))
    }
}
