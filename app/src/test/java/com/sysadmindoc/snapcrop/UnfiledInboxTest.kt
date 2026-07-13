package com.sysadmindoc.snapcrop

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnfiledInboxTest {
    @Test
    fun includesOnlyPendingScreenshotImagesByExactIdentity() {
        val filed = photo(1, 100)
        val triaged = photo(2, 100)
        val deferred = photo(3, 100)
        val reusedUri = filed.copy(dateAdded = 200)
        val ordinary = photo(4, 300, isScreenshot = false)
        val video = photo(5, 400, isVideo = true)

        val result = UnfiledInbox.photos(
            candidates = listOf(filed, triaged, deferred, reusedUri, ordinary, video),
            exclusions = UnfiledExclusions(
                filed = setOf(filed.identity()),
                triaged = setOf(triaged.identity()),
                deferred = setOf(deferred.identity()),
            ),
        )

        assertEquals(listOf(reusedUri), result)
    }

    @Test
    fun orderingAndDeduplicationAreDeterministic() {
        val lowerUri = photo(7, 200)
        val higherUri = photo(9, 200)
        val newest = photo(1, 300)

        val result = UnfiledInbox.photos(
            candidates = listOf(lowerUri, newest, higherUri, higherUri.copy(name = "duplicate")),
            exclusions = UnfiledExclusions(emptySet(), emptySet()),
        )

        assertEquals(listOf(newest, higherUri, lowerUri), result)
        assertEquals(3, result.size)
    }

    @Test
    fun emptyInboxIsStable() {
        val screenshot = photo(1, 100)

        val result = UnfiledInbox.photos(
            candidates = listOf(screenshot),
            exclusions = UnfiledExclusions(filed = emptySet(), triaged = setOf(screenshot.identity())),
        )

        assertTrue(result.isEmpty())
    }

    private fun photo(
        id: Long,
        dateAdded: Long,
        isScreenshot: Boolean = true,
        isVideo: Boolean = false,
    ) = Photo(
        id = id,
        uri = Uri.parse("content://media/external/${if (isVideo) "video" else "images"}/media/$id"),
        dateAdded = dateAdded,
        name = "Screenshot_$id.png",
        isScreenshot = isScreenshot,
        isVideo = isVideo,
    )

    private fun Photo.identity() = ManualCollectionMedia(uri, dateAdded)
}
