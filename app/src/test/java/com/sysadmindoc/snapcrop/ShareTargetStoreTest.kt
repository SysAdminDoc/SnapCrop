package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTargetStoreTest {
    @Test
    fun encodeDecodePreservesTargets() {
        val targets = listOf(
            ShareTargetShortcut(
                packageName = "com.example",
                className = "com.example.ShareActivity",
                label = "Example Share",
                count = 4,
                lastUsed = 1234L
            )
        )

        val decoded = ShareTargetStore.decode(ShareTargetStore.encode(targets))

        assertEquals(targets, decoded)
    }
}
