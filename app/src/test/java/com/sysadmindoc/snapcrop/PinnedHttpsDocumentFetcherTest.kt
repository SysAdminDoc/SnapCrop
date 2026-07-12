package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinnedHttpsDocumentFetcherTest {
    @Test
    fun parsesBoundedContentLengthHtml() {
        val body = "<html><body>safe</body></html>"
        val response = response(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${body.length}\r\n\r\n$body"
        )

        assertEquals(200, response?.first)
        assertEquals("text/html; charset=UTF-8", response?.second?.get("content-type"))
        assertEquals(body, response?.third?.toString(Charsets.UTF_8))
    }

    @Test
    fun parsesChunkedBodyAndRedirectWithoutFollowingIt() {
        val chunked = response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Type: text/html\r\n\r\n5\r\nhello\r\n0\r\n\r\n")
        val redirect = response("HTTP/1.1 302 Found\r\nLocation: https://example.com/next\r\n\r\n")

        assertEquals("hello", chunked?.third?.toString(Charsets.UTF_8))
        assertEquals(302, redirect?.first)
        assertEquals("https://example.com/next", redirect?.second?.get("location"))
    }

    @Test
    fun rejectsMalformedAndOversizedBodies() {
        assertNull(response("not-http\r\n\r\n"))
        assertNull(response("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 99999999\r\n\r\n"))
        assertNull(response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nZ\r\nbad\r\n"))
        assertNull(response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n1\r\na\r\n7fffffff\r\n"))
        assertNull(response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n"))
        assertNull(response("HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 1\r\n\r\na"))
        assertNull(response("HTTP/1.1 200 OK\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\na"))
        assertNull(response("HTTP/2 200 OK\r\nContent-Length: 0\r\n\r\n"))
        assertNull(response("HTTP/1.1 100 Continue\r\n\r\n"))
    }

    private fun response(value: String) =
        PinnedHttpsDocumentFetcher.parseResponseForTest(value.toByteArray(Charsets.US_ASCII))
}
