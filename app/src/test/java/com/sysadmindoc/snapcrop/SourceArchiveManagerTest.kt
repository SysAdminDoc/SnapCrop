package com.sysadmindoc.snapcrop

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceArchiveManagerTest {
    @Test
    fun archiveTargetsExactVisiblePicturesFolder() {
        assertEquals("Pictures/trashed/", SourceArchiveManager.ARCHIVE_RELATIVE_PATH)
        assertEquals(
            SourceArchiveManager.ARCHIVE_RELATIVE_PATH,
            SourceArchiveManager.archiveValues().getAsString(MediaStore.MediaColumns.RELATIVE_PATH),
        )
    }
}
