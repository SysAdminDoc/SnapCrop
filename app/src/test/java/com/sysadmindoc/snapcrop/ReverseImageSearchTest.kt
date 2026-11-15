package com.sysadmindoc.snapcrop

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Issue #4: reverse image search from the editor. Response shapes captured from the live engines on 2026-11-14. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReverseImageSearchTest {
    private val yandexJson = """{"cnt":"pageview_candidate","blocks":[{"name":{"block":"b-page_type_search-by-image__link","mods":{}},""" +
        """"params":{"originalImageUrl":"https://avatars.mds.yandex.net/get-images-cbir/4330579/GvdqHWvhKsB0OaC1TfCOag5274/orig",""" +
        """"cbirId":"4330579/GvdqHWvhKsB0OaC1TfCOag5274","bundles":[]},"html":""}]}"""
    private val bingLocation =
        "https://www.bing.com/images/search?view=detailV2&insightsToken=bcid_Tkn4a2CYUZgKqxcxoNWLuD9SqbotqVTdP.s&FORM=SBIHMP&iss=SBIUPLOADGET"

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val location: String? = null,
        private val body: String = "",
        private val offline: Boolean = false,
    ) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getOutputStream(): OutputStream = if (offline) throw IOException("offline") else sent
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?): String? = if (name.equals("Location", ignoreCase = true)) location else null
        override fun getInputStream(): InputStream = body.byteInputStream()
    }

    @Test
    fun yandexResultsPageIsKeyedByTheStoredImageId() {
        assertEquals(
            "https://yandex.com/images/search?rpt=imageview&cbir_id=4330579%2FGvdqHWvhKsB0OaC1TfCOag5274",
            ReverseImageSearch.yandexResultUrl(yandexJson),
        )
        assertNull(ReverseImageSearch.yandexResultUrl("""{"blocks":[{"params":{}}]}"""))
        assertNull(ReverseImageSearch.yandexResultUrl("<html>captcha</html>"))
    }

    @Test
    fun bingRedirectMustLandOnBingResults() {
        assertEquals(bingLocation, ReverseImageSearch.bingResultUrl(302, bingLocation))
        assertEquals(
            "https://www.bing.com/images/search?view=detailV2&insightsToken=abc",
            ReverseImageSearch.bingResultUrl(302, "/images/search?view=detailV2&insightsToken=abc"),
        )
        assertNull(ReverseImageSearch.bingResultUrl(200, bingLocation))
        assertNull(ReverseImageSearch.bingResultUrl(302, null))
        assertNull(ReverseImageSearch.bingResultUrl(302, "https://evil.example/images/search?q=x"))
        assertNull(ReverseImageSearch.bingResultUrl(302, "http://www.bing.com/images/search?q=x"))
        assertNull(ReverseImageSearch.bingResultUrl(302, "https://www.bing.com/account/login"))
    }

    @Test
    fun bingUploadSendsTheImageAsBase64AndReturnsTheRedirect() {
        val jpeg = byteArrayOf(1, 2, 3, 4, 5)
        var fake: FakeConnection? = null
        val outcome = ReverseImageSearch.upload(ReverseSearchEngine.BING, jpeg) { url ->
            assertEquals(ReverseImageSearch.BING_UPLOAD_URL, url.toString())
            FakeConnection(url, 302, location = bingLocation).also { fake = it }
        }
        assertEquals(ReverseSearchOutcome.Results(bingLocation), outcome)
        val connection = fake!!
        assertEquals("POST", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        val body = connection.sent.toString(Charsets.UTF_8.name())
        assertTrue(body, body.contains("Content-Disposition: form-data; name=\"imageBin\"\r\n\r\n"))
        assertTrue(body, body.contains(Base64.encodeToString(jpeg, Base64.NO_WRAP)))
        assertFalse(body, body.contains("filename="))
        assertTrue(connection.disconnected)
    }

    @Test
    fun yandexUploadSendsTheJpegFileAndReadsTheResultsId() {
        val jpeg = "JPEGDATA".toByteArray()
        var fake: FakeConnection? = null
        val outcome = ReverseImageSearch.upload(ReverseSearchEngine.YANDEX, jpeg) { url ->
            FakeConnection(url, 200, body = yandexJson).also { fake = it }
        }
        assertEquals(
            ReverseSearchOutcome.Results("https://yandex.com/images/search?rpt=imageview&cbir_id=4330579%2FGvdqHWvhKsB0OaC1TfCOag5274"),
            outcome,
        )
        val body = fake!!.sent.toString(Charsets.UTF_8.name())
        assertTrue(body, body.contains("name=\"upfile\"; filename=\"image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\nJPEGDATA\r\n--"))
    }

    @Test
    fun failuresSayWhetherTheEngineWasUnreachableOrGaveNoResults() {
        val jpeg = byteArrayOf(9)
        assertEquals(
            ReverseSearchOutcome.Failed(ReverseSearchOutcome.Reason.NETWORK),
            ReverseImageSearch.upload(ReverseSearchEngine.BING, jpeg) { FakeConnection(it, 0, offline = true) },
        )
        assertEquals(
            ReverseSearchOutcome.Failed(ReverseSearchOutcome.Reason.NO_RESULTS_PAGE),
            ReverseImageSearch.upload(ReverseSearchEngine.BING, jpeg) { FakeConnection(it, 200) },
        )
        assertEquals(
            ReverseSearchOutcome.Failed(ReverseSearchOutcome.Reason.NO_RESULTS_PAGE),
            ReverseImageSearch.upload(ReverseSearchEngine.YANDEX, jpeg) { FakeConnection(it, 403, body = "captcha") },
        )
    }

    @Test
    fun searchImageIsBoundedAndTransparencyBecomesWhite() {
        val wide = Bitmap.createBitmap(4000, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val jpeg = ReverseImageSearch.encodeForSearch(wide)
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(1600, decoded.width)
        assertEquals(400, decoded.height)
        val corner = decoded.getPixel(0, 0)
        assertTrue("corner ${Integer.toHexString(corner)}", Color.red(corner) > 240 && Color.green(corner) > 240 && Color.blue(corner) > 240)
        assertFalse(wide.isRecycled)
    }

    @Test
    fun lensNeedsTheGoogleAppAndGetsTheImageDirectly() {
        assertEquals(
            listOf(ReverseSearchEngine.BING, ReverseSearchEngine.YANDEX),
            ReverseImageSearch.availableEngines(googleAppInstalled = false),
        )
        assertEquals(ReverseSearchEngine.entries, ReverseImageSearch.availableEngines(googleAppInstalled = true))
        val uri = Uri.parse("content://com.sysadmindoc.snapcrop.fileprovider/shared_crops/reverse-search.jpg")
        val intent = ReverseImageSearch.googleLensIntent(uri)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(ReverseImageSearch.GOOGLE_APP_PACKAGE, intent.`package`)
        assertEquals(uri, @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
