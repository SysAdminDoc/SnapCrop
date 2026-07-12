package com.sysadmindoc.snapcrop

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InboundShareContractTest {
    @Test
    fun sendMultipleMergesExtrasClipDataAndDataInStableUniqueOrder() {
        val first = Uri.parse("content://fixture/one")
        val second = Uri.parse("content://fixture/two")
        val third = Uri.parse("content://fixture/three")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData.newRawUri("images", second).apply { addItem(ClipData.Item(third)) }
            data = first
        }

        assertEquals(listOf(first, second, third), InboundShareContract.extractUris(intent))
    }

    @Test
    fun malformedSingleSendStillKeepsEveryClipDataImage() {
        val first = Uri.parse("content://fixture/one")
        val second = Uri.parse("content://fixture/two")
        val intent = Intent(Intent.ACTION_SEND).apply {
            clipData = ClipData.newRawUri("images", first).apply { addItem(ClipData.Item(second)) }
        }

        assertEquals(listOf(first, second), InboundShareContract.extractUris(intent))
    }

    @Test
    fun overflowIsNotSilentlyTruncatedBeforeValidation() {
        val uris = (0 until InboundShareContract.MAX_ITEMS + 7).map { Uri.parse("content://fixture/$it") }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }

        assertEquals(uris, InboundShareContract.extractUris(intent))
    }
}
