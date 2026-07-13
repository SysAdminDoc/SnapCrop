package com.sysadmindoc.snapcrop

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WebCapturePolicyTest {
    @Test
    fun normalizesPublicHttpsAndDropsFragments() {
        assertEquals("https://www.example.com/guide", WebCapturePolicy.normalizeHttpsUrl("www.example.com/guide#section"))
        assertEquals("https://example.com/path?q=1", WebCapturePolicy.normalizeHttpsUrl(" https://example.com/path?q=1 "))
    }

    @Test
    fun rejectsNonHttpsCredentialsAndLocalDestinations() {
        listOf(
            "http://example.com",
            "file:///etc/passwd",
            "content://media/item",
            "data:text/html,hello",
            "https://user:pass@example.com",
            "https://example.com:8443/page",
            "https://example.com/line\nbreak",
            "https://localhost/page",
            "https://printer.local/page",
            "https://127.0.0.1",
            "https://10.0.0.1",
            "https://169.254.169.254/latest/meta-data",
            "https://192.168.1.10",
            "https://100.64.0.1",
            "https://8.8.8.8",
            "https://[2606:4700:4700::1111]"
        ).forEach { assertNull(it, WebCapturePolicy.normalizeHttpsUrl(it)) }
    }

    @Test
    fun addressPolicyRejectsPrivateReservedAndIpv6LocalRanges() {
        listOf("0.0.0.0", "10.1.2.3", "127.0.0.1", "169.254.1.1", "172.20.1.1", "192.168.2.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "::1", "::127.0.0.1", "fc00::1", "fe80::1", "64:ff9b::7f00:1", "64:ff9b:1::1", "100::1", "2001:2::1", "2001:10::1", "2001:20::1", "2001:db8::1")
            .forEach { assertFalse(it, WebCapturePolicy.isPublicAddress(InetAddress.getByName(it))) }
        assertTrue(WebCapturePolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(WebCapturePolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test
    fun captureDimensionsEnforceHeightAndPixelCaps() {
        assertEquals(1080 to 10_000, WebCapturePolicy.captureDimensions(1080, 10_000))
        assertNull(WebCapturePolicy.captureDimensions(1080, WebCapturePolicy.MAX_CAPTURE_HEIGHT + 1))
        assertNull(WebCapturePolicy.captureDimensions(4000, 4_000))
        assertNull(WebCapturePolicy.captureDimensions(0, 100))
        assertEquals(1080 to 2_000, WebCapturePolicy.preflightCaptureDimensions(1080, 1_000, 2f))
        assertNull(WebCapturePolicy.preflightCaptureDimensions(1080, 20_000, 1f))
        assertNull(WebCapturePolicy.preflightCaptureDimensions(1080, 1_000, Float.NaN))
    }
}
