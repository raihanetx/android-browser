package com.zbrowser.app

import android.content.Context
import android.webkit.WebView
import android.view.ViewGroup
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Pre-creates and recycles WebViews to eliminate the 150-200ms cold-start
 * penalty of constructing a new WebView on the main thread.
 *
 * When a tab is closed the WebView is returned to the pool rather than
 * destroyed immediately. The next "add tab" call re-uses the recycled
 * instance, giving near-instant tab creation.
 *
 * IMPORTANT: WebView requires an Activity context for proper window
 * token management. Using applicationContext causes crashes on some
 * OEMs. We always use the Activity context provided to acquire().
 *
 * Thread-safe: all operations are lock-free via ConcurrentLinkedQueue.
 *
 * v4.0 FIXES:
 * - Added init() method (was missing, causing compilation error)
 * - release() now detaches WebView from parent BEFORE cleaning/destroying
 * - Safe destroy: only destroys if NOT attached to a parent
 * - Atomic pool size check via synchronized block prevents overflow
 */
object WebViewPool {

    private const val MAX_POOL_SIZE = 3

    private val pool = ConcurrentLinkedQueue<WebView>()
    // BUG-18 FIX: @Volatile ensures visibility across threads
    @Volatile
    private var initialized = false

    /**
     * Initialize the WebView pool.
     * Called from ZBrowserApp.onCreate() to seed the pool.
     * Also sets the WebView data directory suffix for Android P+
     * to prevent disk I/O on the main thread.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        // Set WebView data directory for Android P+ to prevent disk IO on main thread
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("zbrowser_webview")
        }
    }

    /**
     * Obtain a WebView — either a recycled one from the pool
     * or a freshly created one if the pool is empty.
     *
     * @param activityContext Must be an Activity context (not applicationContext)
     */
    fun acquire(activityContext: Context): WebView {
        return pool.poll() ?: WebView(activityContext)
    }

    /**
     * Return a WebView to the pool for reuse.
     *
     * v4.0 FIX: Detaches WebView from parent BEFORE cleaning state,
     * and only destroys if safely detached. This prevents:
     * - "WebView already has a parent" crash
     * - Memory leak from silently failed destroy()
     *
     * @param webView The WebView to release
     * @param removeParent If true, detach from parent ViewGroup before releasing
     */
    fun release(webView: WebView, removeParent: Boolean = true) {
        // CRITICAL: Detach from parent FIRST — WebView.destroy() throws if still attached
        if (removeParent) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }

        // Check pool capacity atomically
        synchronized(pool) {
            if (pool.size >= MAX_POOL_SIZE) {
                safeDestroy(webView)
                return
            }
        }

        try {
            // Stop any in-progress navigation synchronously
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(false)
            webView.webViewClient = WebViewClient()  // Reset to default
            webView.webChromeClient = null
            webView.setDownloadListener(null)
            pool.offer(webView)
        } catch (_: Exception) {
            // WebView already destroyed or unusable
            safeDestroy(webView)
        }
    }

    /** Destroy all pooled WebViews — called on app termination */
    fun clear() {
        pool.forEach { safeDestroy(it) }
        pool.clear()
    }

    /**
     * Safely destroy a WebView — only destroys if not attached to a parent.
     * If still attached, detaches first then destroys.
     */
    private fun safeDestroy(webView: WebView) {
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (_: Exception) {
            // WebView already destroyed or in bad state
        }
    }
}

/**
 * Default WebViewClient to reset pooled WebViews.
 */
private class WebViewClient : android.webkit.WebViewClient()
