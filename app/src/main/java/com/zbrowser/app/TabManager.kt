package com.zbrowser.app

import android.webkit.WebView
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages browser tab lifecycle with maximum smoothness.
 *
 * v4.0 FIXES:
 * - C4 FIX: onTrimMemory now preserves tab URL/title for WebView recreation.
 *   When a background tab's WebView is ejected, the tab retains its URL
 *   so switchToTab can recreate the WebView on demand.
 * - WebViewPool.release() now handles parent detachment internally,
 *   so callers don't need to remove from container before calling closeTab.
 * - Added flag for WebView recreation after memory pressure.
 */
@Singleton
class TabManager @Inject constructor() {

    private val _tabs = mutableListOf<BrowserTab>()
    val tabs: List<BrowserTab> get() = _tabs.toList()

    private var _activeTabId: Int = -1
    val activeTabId: Int get() = _activeTabId

    private var _nextTabId: Int = 1
    val nextTabId: Int get() = _nextTabId

    companion object {
        const val MAX_TABS = 20
        const val HOME_URL = "https://www.google.com"
    }

    /**
     * Set the next tab ID — used during state restoration to prevent ID collisions.
     */
    fun setNextTabId(nextId: Int) {
        _nextTabId = nextId
    }

    fun addTab(webView: WebView?, url: String = HOME_URL, isDesktopMode: Boolean = false): BrowserTab? {
        if (_tabs.size >= MAX_TABS) return null
        if (webView != null && _tabs.any { it.webView === webView }) return null

        val tab = BrowserTab(
            id = _nextTabId++,
            url = url,
            webView = webView,
            isDesktopMode = isDesktopMode
        )
        _tabs.add(tab)
        return tab
    }

    /**
     * Switch to a tab — pauses the previously active tab and resumes the new one.
     *
     * C4 FIX: If the target tab's WebView was ejected by onTrimMemory (webView == null),
     * this method returns the tab but does NOT attempt to use the null WebView.
     * The caller (MainActivity.switchToTab) must check for null WebView and recreate it.
     *
     * The caller is responsible for setting the WebView visibility (VISIBLE/GONE).
     */
    fun switchToTab(tabId: Int): BrowserTab? {
        val tab = _tabs.find { it.id == tabId } ?: return null

        // Skip if already the active tab — prevents unnecessary onPause/onResume cycle
        if (_activeTabId == tabId) return tab

        // Pause the previously active tab to free CPU/GPU
        getActiveTab()?.let { oldTab ->
            oldTab.webView?.onPause()
        }

        _activeTabId = tabId

        // Resume the new active tab (only if WebView wasn't ejected by memory pressure)
        tab.webView?.onResume()

        return tab
    }

    fun closeTab(tabId: Int): BrowserTab? {
        val tab = _tabs.find { it.id == tabId } ?: return null
        val idx = _tabs.indexOf(tab)

        // Release WebView to pool — WebViewPool.release() handles parent detachment
        tab.webView?.let { WebViewPool.release(it, removeParent = true) }
        _tabs.removeAt(idx)

        if (_tabs.isEmpty()) return null
        val nextIdx = minOf(idx, _tabs.size - 1)
        return _tabs[nextIdx]
    }

    fun closeAllTabs() {
        _tabs.forEach { tab ->
            tab.webView?.let { WebViewPool.release(it, removeParent = true) }
        }
        _tabs.clear()
        _activeTabId = -1
    }

    fun getActiveTab(): BrowserTab? = _tabs.find { it.id == _activeTabId }
    fun getTabForWebView(webView: WebView): BrowserTab? = _tabs.find { it.webView === webView }
    fun getTab(tabId: Int): BrowserTab? = _tabs.find { it.id == tabId }
    fun indexOf(tab: BrowserTab): Int = _tabs.indexOf(tab)

    fun destroyAll() {
        _tabs.forEach { tab ->
            tab.webView?.let { wv ->
                wv.onPause()
                WebViewPool.release(wv, removeParent = true)
            }
        }
        _tabs.clear()
    }

    /**
     * C4 FIX: Called when the OS requests memory trimming.
     *
     * When background tab WebViews are ejected, the tab retains its URL and title
     * so that when the user switches back to that tab, the WebView can be recreated
     * and the page reloaded. This prevents the blank-screen issue.
     *
     * The BrowserTab.needsWebViewRecreation flag is set when a WebView is ejected,
     * and cleared when it's recreated by the Activity.
     */
    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            _tabs.filter { it.id != _activeTabId }.forEach { tab ->
                tab.webView?.let { wv ->
                    wv.onPause()
                    wv.clearCache(false)
                }
            }
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            // Destroy all background tabs' WebViews aggressively
            // C4 FIX: Set needsWebViewRecreation flag so Activity can recreate on switch
            _tabs.filter { it.id != _activeTabId }.forEach { tab ->
                tab.webView?.let { wv ->
                    WebViewPool.release(wv, removeParent = true)
                    tab.webView = null
                    tab.needsWebViewRecreation = true
                }
            }
        }
    }
}
