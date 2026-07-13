package com.sysadmindoc.snapcrop

internal object UnfiledInbox {
    fun photos(
        candidates: List<Photo>,
        exclusions: UnfiledExclusions,
    ): List<Photo> = candidates.asSequence()
        .filter { !it.isVideo && it.isScreenshot }
        .filterNot { photo ->
            val identity = ManualCollectionMedia(photo.uri, photo.dateAdded)
            identity in exclusions.filed || identity in exclusions.triaged || identity in exclusions.deferred
        }
        .distinctBy { it.uri.toString() to it.dateAdded }
        .sortedWith(compareByDescending<Photo> { it.dateAdded }.thenByDescending { it.uri.toString() })
        .toList()
}
