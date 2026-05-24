package com.zbrowser.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.zbrowser.app.data.HistoryDao
import com.zbrowser.app.data.HistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Custom WebViewClient optimized for maximum smoothness.
 *
 * v4.0 FIXES:
 * - onRenderProcessGone: properly removes WebView from parent before destroy
 * - Debounced history recording (500ms) prevents Room write spam
 * - Ad blocker uses O(1) HashSet lookup
 * - Minimal work in shouldOverrideUrlLoading
 * - CSS injection uses proper single-line strings (no JS syntax error)
 */
class BrowserWebViewClient(
    private val context: Context,
    private val tabLookup: (WebView?) -> BrowserTab?,
    private val adBlocker: AdBlocker,
    private val historyDao: HistoryDao?,
    private val appScope: CoroutineScope? = null
) : WebViewClient() {

    interface Callback {
        fun onPageLoadStarted(webView: WebView, url: String, isDesktopMode: Boolean)
        fun onPageLoadFinished(webView: WebView, title: String, url: String, isDesktopMode: Boolean)
        fun onPageLoadError(webView: WebView, errorMsg: String, url: String)
        fun onSslError(handler: SslErrorHandler, error: SslError)
        fun onPopupBlocked()
        fun onRenderProcessGone(webView: WebView)
    }

    var callback: Callback? = null

    // Debounce timer for history recording
    private var historyJob: Job? = null

    // Viewport JavaScript — cached strings, never re-allocated
    // Desktop mode: do NOT inject viewport meta — WebView settings
    // (loadWithOverviewMode + useWideViewPort) handle zooming automatically
    private val mobileViewportJs = """
        (function() {
            var meta = document.querySelector('meta[name="viewport"]');
            if (meta) {
                meta.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=5');
            } else {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                meta.content = 'width=device-width, initial-scale=1, maximum-scale=5';
                document.head.appendChild(meta);
            }
        })();
    """.trimIndent()

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request != null && adBlocker.shouldBlock(request)) {
            return adBlocker.createBlockedResponse()
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val scheme = request.url.scheme?.lowercase() ?: ""

        when (scheme) {
            "tel", "mailto", "sms", "geo" -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    // No app to handle
                }
                return true
            }
            "intent" -> {
                val fallback = SecurityUtils.extractSafeFallbackFromIntent(url)
                if (fallback != null) {
                    view?.loadUrl(fallback)
                }
                return true
            }
            "file", "javascript", "data" -> return true
        }

        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        val isDesktop = tabLookup(view)?.isDesktopMode ?: false
        view?.let { wv ->
            applyModeSettings(wv.settings, isDesktop)

            // Inject ad-hide CSS before page renders to prevent layout flash
            if (adBlocker.isEnabled && url != null && url.startsWith("http")) {
                wv.evaluateJavascript(AdBlocker.AD_HIDE_CSS, null)
            }
        }

        if (view != null && url != null) {
            callback?.onPageLoadStarted(view, url, isDesktop)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { wv ->
            val title = wv.title ?: ""
            val pageUrl = url ?: ""
            val isDesktop = tabLookup(wv)?.isDesktopMode ?: false

            if (!isDesktop) {
                wv.evaluateJavascript(mobileViewportJs, null)
            }



            if (title.isNotEmpty() && pageUrl.startsWith("http")) {
                recordHistoryDebounced(title, pageUrl)
            }

            callback?.onPageLoadFinished(
                webView = wv,
                title = if (title.isNotEmpty()) title else pageUrl,
                url = pageUrl,
                isDesktopMode = isDesktop
            )
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val errorMsg = error?.description?.toString() ?: "Unknown error"
            val pageUrl = view?.url ?: ""
            if (view != null) {
                callback?.onPageLoadError(view, errorMsg, pageUrl)
            }
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        if (handler != null && error != null) {
            callback?.onSslError(handler, error)
        }
    }

    /**
     * RENDER PROCESS CRASH GUARD — properly handles WebView renderer crashes.
     *
     * FIX: Must remove WebView from its parent ViewGroup before destroying,
     * otherwise "WebView already has a parent" crash on recovery.
     *
     * Delegates to the Callback so the Activity can handle UI recovery
     * on the main thread.
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        if (view != null) {
            // Remove from parent before destroy — prevents "already has a parent" crash
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            // Notify the Activity BEFORE destroying so it can identify the tab
            // via the still-valid WebView reference
            callback?.onRenderProcessGone(view)
            view.destroy()
        }
        return true
    }

    private fun recordHistoryDebounced(title: String, url: String) {
        val dao = historyDao ?: return
        val scope = appScope ?: return

        historyJob?.cancel()
        historyJob = scope.launch(Dispatchers.IO) {
            delay(500)
            dao.insert(HistoryEntity(url = url, title = title))
        }
    }

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"

        @SuppressLint("SetJavaScriptEnabled")
        fun applyModeSettings(settings: WebSettings, desktopMode: Boolean, mobileUserAgent: String? = null) {
            if (desktopMode) {
                settings.userAgentString = DESKTOP_USER_AGENT
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            } else {
                settings.userAgentString = mobileUserAgent ?: settings.userAgentString
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            }
        }
    }
}
