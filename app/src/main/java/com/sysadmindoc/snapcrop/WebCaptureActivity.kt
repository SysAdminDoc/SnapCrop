package com.sysadmindoc.snapcrop

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ClientCertRequest
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewRenderProcess
import android.webkit.WebViewRenderProcessClient
import android.net.http.SslError
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.sysadmindoc.snapcrop.ui.theme.Black
import com.sysadmindoc.snapcrop.ui.theme.OnPrimary
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Outline
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File

class WebCaptureActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URL = "web_capture_url"
        const val EXTRA_SOURCE_URL = "web_capture_source_url"
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val RENDER_TIMEOUT_MS = 5_000L
        private const val CAPTURE_TIMEOUT_MS = 10_000L
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        private const val STATE_INPUT_URL = "web_input_url"
        private const val STATE_LOADED_URL = "web_loaded_url"
    }

    private var webView: WebView? = null
    private var inputUrl by mutableStateOf("")
    private var loadedUrl by mutableStateOf<String?>(null)
    private var status by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var isCapturing by mutableStateOf(false)
    private var loadJob: Job? = null
    private var settleJob: Job? = null
    private var captureJob: Job? = null
    private var generation = 0L
    private var rendererEpoch by mutableIntStateOf(0)
    private var pendingDocumentUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureWindow(this)
        val restoredLoadedUrl = savedInstanceState?.getString(STATE_LOADED_URL)
        inputUrl = savedInstanceState?.getString(STATE_INPUT_URL)
            ?.take(WebCapturePolicy.MAX_URL_CHARS)
            ?: intent.getStringExtra(EXTRA_URL).orEmpty()
        CookieManager.getInstance().removeAllCookies(null)
        WebView.setWebContentsDebuggingEnabled(false)
        setContent {
            SnapCropTheme {
                WebCaptureScreen()
            }
        }
        if (restoredLoadedUrl != null) {
            inputUrl = restoredLoadedUrl.take(WebCapturePolicy.MAX_URL_CHARS)
            window.decorView.post(::loadPage)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_INPUT_URL, inputUrl.take(WebCapturePolicy.MAX_URL_CHARS))
        outState.putString(STATE_LOADED_URL, loadedUrl)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        applySecureWindow(this)
    }

    override fun onDestroy() {
        cancelWork()
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            clearCache(true)
            removeAllViews()
            destroy()
        }
        webView = null
        CookieManager.getInstance().removeAllCookies(null)
        super.onDestroy()
    }

    @Composable
    private fun WebCaptureScreen() {
        BackHandler(onBack = ::handleBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .safeDrawingPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = ::handleBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = OnSurface)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.web_capture_title), color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.web_capture_privacy), color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it.take(WebCapturePolicy.MAX_URL_CHARS) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.web_capture_url)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Outline,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        cursorColor = Primary
                    )
                )
                OutlinedButton(
                    onClick = ::loadPage,
                    enabled = !isLoading && !isCapturing
                ) {
                    Icon(Icons.Default.Language, null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(stringResource(R.string.web_capture_load))
                }
            }
            if (status.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status, color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (isLoading || isCapturing) {
                        TextButton(onClick = ::cancelWork) {
                            Text(stringResource(R.string.cancel), color = OnSurfaceVariant)
                        }
                    }
                }
            }
            key(rendererEpoch) {
                AndroidView(
                    factory = { context -> createWebView(context).also { webView = it } },
                    modifier = Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Color.White)
                )
            }
            Button(
                onClick = ::captureFullPage,
                enabled = loadedUrl != null && !isLoading && !isCapturing,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isLoading || isCapturing) {
                    CircularProgressIndicator(color = OnPrimary, strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                } else {
                    Icon(Icons.Default.CameraAlt, null, tint = OnPrimary)
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(if (isCapturing) R.string.web_capture_capturing else R.string.web_capture_capture), color = OnPrimary)
            }
        }
    }

    private fun handleBack() {
        val current = webView
        when {
            isLoading || isCapturing -> cancelWork()
            loadedUrl != null -> {
                cancelWork(updateStatus = false)
                current?.loadUrl("about:blank")
                status = ""
            }
            current?.canGoBack() == true -> current.goBack()
            else -> finish()
        }
    }

    private fun loadPage() {
        val normalized = WebCapturePolicy.normalizeHttpsUrl(inputUrl)
        if (normalized == null) {
            status = getString(R.string.web_capture_invalid_url)
            return
        }
        cancelWork(updateStatus = false)
        val requestGeneration = ++generation
        isLoading = true
        loadedUrl = null
        status = getString(R.string.web_capture_checking)
        loadJob = lifecycleScope.launch {
            val document = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { PinnedHttpsDocumentFetcher.fetch(normalized) }
            }
            if (requestGeneration != generation) return@launch
            if (document == null) {
                isLoading = false
                status = getString(R.string.web_capture_fetch_failed)
                return@launch
            }
            inputUrl = document.finalUrl
            pendingDocumentUrl = document.finalUrl
            status = getString(R.string.web_capture_rendering)
            webView?.loadDataWithBaseURL(null, document.html, "text/html", document.charset, null)
            settleJob = lifecycleScope.launch {
                delay(RENDER_TIMEOUT_MS)
                if (requestGeneration == generation && pendingDocumentUrl != null) {
                    replaceRenderer(getString(R.string.web_capture_unstable))
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun captureFullPage() {
        val current = webView ?: return
        if (isCapturing) return
        isCapturing = true
        status = getString(R.string.web_capture_capturing)
        val captureGeneration = generation
        val sourceUrl = loadedUrl ?: run {
            isCapturing = false
            status = getString(R.string.web_capture_failed)
            return
        }
        val estimatedDimensions = WebCapturePolicy.preflightCaptureDimensions(
            current.width,
            current.contentHeight,
            current.scale
        )
        if (estimatedDimensions == null) {
            isCapturing = false
            status = getString(R.string.web_capture_too_large)
            return
        }
        val picture = current.capturePicture()
        val dimensions = WebCapturePolicy.captureDimensions(picture.width, picture.height)
        if (dimensions == null) {
            isCapturing = false
            status = getString(R.string.web_capture_too_large)
            return
        }
        captureJob = lifecycleScope.launch {
            val file = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val bitmap = try {
                        Bitmap.createBitmap(dimensions.first, dimensions.second, Bitmap.Config.ARGB_8888).also { output ->
                            Canvas(output).apply {
                                drawColor(android.graphics.Color.WHITE)
                                drawPicture(picture)
                            }
                        }
                    } catch (_: Throwable) {
                        return@withContext null
                    }
                    try {
                        writeCapture(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            if (captureGeneration != generation) return@launch
            isCapturing = false
            if (file == null) {
                status = getString(R.string.web_capture_failed)
                return@launch
            }
            try {
                val uri = FileProvider.getUriForFile(
                    this@WebCaptureActivity,
                    "${packageName}.fileprovider",
                    file
                )
                startActivity(Intent(this@WebCaptureActivity, CropActivity::class.java).apply {
                    data = uri
                    clipData = ClipData.newRawUri("Captured web page", uri)
                    putExtra(EXTRA_SOURCE_URL, sourceUrl)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                finish()
            } catch (_: Exception) {
                file.delete()
                status = getString(R.string.web_capture_failed)
            }
        }
    }

    private fun settlePage(view: WebView) {
        val settleGeneration = generation
        view.postVisualStateCallback(
            settleGeneration,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    settleJob?.cancel()
                    settleJob = lifecycleScope.launch {
                        var previousHeight = -1
                        var stableSamples = 0
                        repeat(4) {
                            delay(400)
                            if (settleGeneration != generation) return@launch
                            val height = view.contentHeight
                            if (height > 0 && height == previousHeight) stableSamples++ else stableSamples = 0
                            previousHeight = height
                        }
                        if (settleGeneration != generation) return@launch
                        isLoading = false
                        if (stableSamples >= 1 && previousHeight > 0) {
                            loadedUrl = pendingDocumentUrl
                            pendingDocumentUrl = null
                            status = getString(R.string.web_capture_ready_static)
                        } else {
                            loadedUrl = null
                            pendingDocumentUrl = null
                            status = getString(R.string.web_capture_unstable)
                        }
                    }
                }
            }
        )
    }

    private fun cancelWork(updateStatus: Boolean = true) {
        generation++
        loadJob?.cancel()
        settleJob?.cancel()
        captureJob?.cancel()
        loadJob = null
        settleJob = null
        captureJob = null
        webView?.stopLoading()
        isLoading = false
        isCapturing = false
        loadedUrl = null
        pendingDocumentUrl = null
        if (updateStatus) status = getString(R.string.web_capture_cancelled)
    }

    private fun replaceRenderer(message: String) {
        generation++
        loadJob?.cancel()
        settleJob?.cancel()
        captureJob?.cancel()
        loadJob = null
        settleJob = null
        captureJob = null
        val previous = webView
        webView = null
        pendingDocumentUrl = null
        loadedUrl = null
        isLoading = false
        isCapturing = false
        previous?.stopLoading()
        previous?.destroy()
        rendererEpoch++
        status = message
    }

    private fun writeCapture(bitmap: Bitmap): File? {
        val dir = File(cacheDir, "web_capture").apply { mkdirs() }
        val staleBefore = System.currentTimeMillis() - CACHE_TTL_MS
        dir.listFiles()?.filter { it.lastModified() < staleBefore }?.forEach(File::delete)
        val file = File(dir, "SnapCrop_Web_${System.currentTimeMillis()}.png")
        return try {
            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) error("PNG encoder failed")
            }
            file
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createWebView(context: android.content.Context): WebView = WebView(context).apply {
        setBackgroundColor(android.graphics.Color.WHITE)
        settings.javaScriptEnabled = false
        settings.domStorageEnabled = false
        settings.databaseEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.blockNetworkLoads = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setGeolocationEnabled(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            setWebViewRenderProcessClient(
                mainExecutor,
                object : WebViewRenderProcessClient() {
                    override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) = Unit

                    override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
                        if (webView !== view) return
                        if (renderer?.terminate() != true) {
                            replaceRenderer(getString(R.string.web_capture_renderer_failed))
                        }
                    }
                }
            )
        }
        CookieManager.getInstance().setAcceptCookie(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        setDownloadListener { _, _, _, _, _ -> status = getString(R.string.web_capture_download_blocked) }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                return true
            }
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                status = getString(R.string.web_capture_navigation_blocked)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (pendingDocumentUrl != null) settlePage(view)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
                handler.cancel()
            }

            override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler, host: String?, realm: String?) {
                handler.cancel()
            }

            override fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest) {
                request.cancel()
            }

            override fun onSafeBrowsingHit(
                view: WebView?,
                request: WebResourceRequest?,
                threatType: Int,
                callback: SafeBrowsingResponse
            ) {
                callback.backToSafety(true)
                status = getString(R.string.web_capture_navigation_blocked)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (webView !== view) return true
                replaceRenderer(getString(R.string.web_capture_renderer_failed))
                return true
            }
        }
    }
}
