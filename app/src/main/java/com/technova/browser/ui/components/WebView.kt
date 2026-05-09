package com.technova.browser.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebView(
    url: String,
    modifier: Modifier = Modifier,
    onProgressChanged: (Int) -> Unit = {},
    onPageTitleChanged: (String?) -> Unit = {},
    onUrlChanged: (String) -> Unit = {},
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
    onReceivedError: (WebResourceRequest?, WebResourceError?) -> Unit = { _, _ -> },
    shouldOverrideUrlLoading: (WebView, WebResourceRequest) -> Boolean = { _, _ -> false },
    webViewClient: WebViewClient? = null,
    webChromeClient: WebChromeClient? = null,
    settings: WebSettings.() -> Unit = {}
) {
    val context = LocalContext.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(false) }

    // Configure WebView
    val webViewClient = remember {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { onPageStarted(it) }
                isPageLoading = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    onPageFinished(it)
                    onUrlChanged(it)
                }
                isPageLoading = false
                progress = 1f
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                onReceivedError(request, error)
                Timber.e("WebView error: ${error?.description}")
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return request?.let { shouldOverrideUrlLoading(view!!, it) } ?: false
            }
        }
    }

    val webChromeClient = remember {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progress = newProgress / 100f
                onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                onPageTitleChanged(title)
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                this@apply.webViewClient = this@WebView.webViewClient
                this@apply.webChromeClient = this@WebView.webChromeClient

                // Production-grade WebView settings
                settings.apply {
                    // JavaScript
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true

                    // Performance
                    cacheMode = WebSettings.LOAD_DEFAULT
                    setAppCacheEnabled(true)
                    setAppCachePath(ctx.cacheDir.path)

                    // Viewport and layout
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = false
                    displayZoomControls = false

                    // User agent
                    userAgentString = System.getProperty("http.agent") + " NovaBrowser/1.0"

                    // Content access
                    allowContentAccess = true
                    allowFileAccess = false
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false

                    // Security
                    allowFileAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // Text encoding
                    defaultTextEncodingName = "utf-8"

                    // Other
                    saveFormData = false
                    savePassword = false
                    textZoom = 100
                }

                // Enable hardware acceleration
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

                // Initial URL
                loadUrl(url)
                this@apply.also { webView = it }
            }
        },
        update = { view ->
            if (view.url != url && !isPageLoading) {
                view.loadUrl(url)
            }
        }
    )

    // Progress indicator overlay
    if (isPageLoading) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
