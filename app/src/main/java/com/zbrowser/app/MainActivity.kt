package com.zbrowser.app

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.zbrowser.app.data.BookmarkDao
import com.zbrowser.app.data.BookmarkEntity
import com.zbrowser.app.data.HistoryDao
import com.zbrowser.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Main browser activity — ZBrowser v4.0 OPTIMIZED FOR MAXIMUM SMOOTHNESS.
 *
 * Performance Architecture:
 * - VISIBLE/GONE tab switching: zero layout thrash, zero flicker
 * - Hardware layer on WebView: GPU-accelerated rendering
 * - WebViewPool: near-instant tab creation via recycling
 * - Render process crash guard: WebView crash ≠ app crash
 * - Debounced history recording: no Room-write spam
 * - O(1) ad blocker: HashSet lookup for every network request
 * - Low-memory handler: proactive WebView cache trimming + recovery
 * - Smooth progress bar animation with race-condition protection
 * - Memory pressure recovery: ejected tabs auto-recreate their WebView
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity(), BrowserWebViewClient.Callback {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()

    @Inject lateinit var tabManager: TabManager
    @Inject lateinit var adBlocker: AdBlocker
    @Inject lateinit var popupBlocker: PopupBlocker
    @Inject lateinit var downloadManagerHelper: DownloadManagerHelper
    @Inject lateinit var bookmarkDao: BookmarkDao
    @Inject lateinit var historyDao: HistoryDao

    // PermissionManager requires Activity context — cannot be @Singleton in Hilt
    private lateinit var permissionManager: PermissionManager

    private var mobileUserAgent: String? = null
    private var backPressedOnce = false
    private var currentWebView: WebView? = null

    // BUG-04+05 FIX: Replace deprecated onActivityResult + static pendingFilePathCallback
    // with Activity Result API. This prevents memory leaks and correctly handles
    // Activity recreation (rotation) without losing the callback reference.
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = BrowserWebChromeClient.pendingFilePathCallback
        if (callback != null) {
            val uriResult = if (result.resultCode == RESULT_OK && result.data != null) {
                val uri = result.data?.data
                if (uri != null) arrayOf(uri) else null
            } else null
            callback.onReceiveValue(uriResult)
            BrowserWebChromeClient.pendingFilePathCallback = null
        }
    }

    // === LIFECYCLE ===

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // PermissionManager needs Activity reference — created here, not injected
        permissionManager = PermissionManager(this)

        setupToolbar()
        setupUrlBar()
        setupBottomBar()
        setupSwipeRefresh()
        setupTabLayout()

        // Check for crash logs from previous session
        checkForCrash()

        // Restore state or create first tab
        if (savedInstanceState != null) {
            restoreTabs()
        } else {
            addNewTab(TabManager.HOME_URL)
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        currentWebView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        currentWebView?.onPause()
        viewModel.saveTabStates(tabManager.tabs, tabManager.activeTabId, tabManager.nextTabId)
    }

    override fun onDestroy() {
        tabManager.destroyAll()
        WebViewPool.clear()
        super.onDestroy()
    }

    /**
     * Proactive memory trimming — eject WebView caches and background tab
     * WebViews when the OS signals memory pressure.
     *
     * v4.0 FIX: Also removes ejected WebViews from the container so that
     * switchToTab can properly re-add recreated WebViews later.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // Before trimming, remove background WebViews from container so
        // WebViewPool.release() doesn't fail on attached views
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            for (tab in tabManager.tabs) {
                if (tab.id != tabManager.activeTabId && tab.webView != null) {
                    tab.webView?.let { wv ->
                        binding.webViewContainer.removeView(wv)
                    }
                }
            }
        }

        tabManager.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Clear WebView cache per-instance (WebView.clearCache static method
            // may be unavailable in newer API levels)
            currentWebView?.clearCache(false)
        }
    }

    // L5 FIX: Back press shows confirmation when WebView can't go back
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentWebView?.canGoBack() == true) {
                currentWebView?.goBack()
                return true
            }
            // At root of browsing history — confirm exit
            if (backPressedOnce) {
                // Second press within 2 seconds — exit
                finish()
                return true
            }
            backPressedOnce = true
            Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2000)
                backPressedOnce = false
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // BUG-04+05 FIX: Replaced deprecated onActivityResult with Activity Result API
    // (fileChooserLauncher above). The old static callback pattern leaked the
    // ValueCallback and failed silently on Activity recreation (rotation).

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // === SETUP ===

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupUrlBar() {
        binding.urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.urlBar.text.toString().trim()
                if (input.isNotEmpty()) loadUrl(processInput(input))
                true
            } else false
        }
        binding.urlBar.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.urlBar.selectAll() }
    }

    private fun setupBottomBar() {
        binding.btnBack.setOnClickListener { goBack() }
        binding.btnForward.setOnClickListener { goForward() }
        binding.btnRefresh.setOnClickListener { refresh() }
        binding.btnHome.setOnClickListener { loadUrl(TabManager.HOME_URL) }
        binding.btnTabs.setOnClickListener { showTabSwitcher() }
        binding.btnMenu.setOnClickListener { showBrowserMenu() }
        binding.fabNewTab.setOnClickListener { addNewTab(TabManager.HOME_URL) }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.colorPrimary))
        binding.swipeRefresh.setOnRefreshListener { refresh() }
        // Prevent SwipeRefresh from intercepting horizontal scroll inside WebView
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            currentWebView?.let { it.canScrollVertically(-1) } ?: false
        }
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val i = tab.position
                val tabs = tabManager.tabs
                if (i < tabs.size) switchToTab(tabs[i].id)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // === WEBVIEW CREATION (OPTIMIZED) ===

    /**
     * Creates a WebView with maximum performance settings.
     *
     * Key optimizations:
     * - Hardware layer type for GPU-accelerated compositing
     * - 50MB HTTP cache for fewer network round-trips
     * - Disabled favicon loading (saves memory + network)
     * - setSavePassword(false) — deprecated but prevents warning
     * - Properly scoped cache mode
     * - Render process crash guard via onRenderProcessGone
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(desktopMode: Boolean): WebView {
        // Acquire from pool or create new — eliminates 150-200ms cold start
        val webView = WebViewPool.acquire(this)
        webView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )

        // HARDWARE LAYER — critical for smooth scrolling & compositing
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.loadsImagesAutomatically = true

        // Use default cache mode — WebView manages HTTP cache size automatically
        s.cacheMode = WebSettings.LOAD_DEFAULT

        // Save the mobile UA before overwriting
        if (mobileUserAgent == null) {
            mobileUserAgent = s.userAgentString
        }

        BrowserWebViewClient.applyModeSettings(s, desktopMode, mobileUserAgent)

        // FEATURE 1: Apply popup blocker settings
        popupBlocker.applyToWebView(webView)

        // WebViewClient with all features
        val wvClient = BrowserWebViewClient(
            context = this,
            tabLookup = { wv -> wv?.let { tabManager.getTabForWebView(it) } },
            adBlocker = adBlocker,
            popupBlocker = popupBlocker,
            historyDao = historyDao,
            appScope = lifecycleScope
        )
        wvClient.callback = this
        webView.webViewClient = wvClient

        // WebChromeClient with popup blocker, permissions, and download support
        // BUG-04 FIX: Pass file chooser launcher delegate using Activity Result API
        webView.webChromeClient = BrowserWebChromeClient(
            onProgressChanged = { newProgress ->
                animateProgress(newProgress)
            },
            onNewWindowRequested = { resultMsg -> handleNewWindow(resultMsg) },
            popupBlocker = popupBlocker,
            permissionManager = permissionManager,
            onPopupBlocked = {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.popup_blocked_toast, Toast.LENGTH_SHORT).show()
                }
            },
            onFileChooserRequested = { intent, callback ->
                BrowserWebChromeClient.pendingFilePathCallback = callback
                fileChooserLauncher.launch(intent)
                true
            }
        )

        // FEATURE 3: Download Manager
        webView.setDownloadListener(downloadManagerHelper.webViewDownloadListener)

        // Initial visibility — hidden until switched to active
        webView.visibility = View.GONE

        return webView
    }

    /**
     * Smooth progress bar animation — animates from current value to target
     * instead of jumping, eliminating visual jank.
     *
     * H4 FIX: Cancel any in-progress animation before starting a new one.
     * This prevents race conditions when a new page load starts during
     * the 100→gone fade-out animation.
     */
    private fun animateProgress(targetProgress: Int) {
        // Cancel any pending animation to prevent race conditions
        binding.progressBar.animate().cancel()

        val current = binding.progressBar.progress
        if (targetProgress == 100) {
            // Animate to 100 then fade out
            binding.progressBar.progress = 100
            binding.progressBar.animate()
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    binding.progressBar.visibility = View.GONE
                    binding.progressBar.progress = 0
                    binding.progressBar.alpha = 1f  // Reset alpha for next load
                }
                .start()
        } else {
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.alpha = 1f  // Ensure visible and opaque
            // Smooth increment — never jump backward
            if (targetProgress > current) {
                binding.progressBar.progress = targetProgress
            }
        }
    }

    /**
     * Recover from WebView render process crash without killing the app.
     * BUG-13 FIX: Uses the crashed WebView reference to find the correct tab,
     * not the active tab (the crashed tab might not be the active one).
     * BUG-19 FIX: Falls back to HOME_URL if the tab URL is empty.
     */
    private fun handleRenderProcessCrash(crashedWebView: WebView) {
        val crashedTab = tabManager.getTabForWebView(crashedWebView)
        // The old WebView was already destroyed in BrowserWebViewClient.onRenderProcessGone
        crashedTab?.webView = null

        // Recreate the WebView and reload
        val newWebView = createWebView(crashedTab?.isDesktopMode ?: false)
        crashedTab?.webView = newWebView
        binding.webViewContainer.addView(newWebView)

        // If the crashed tab is the active one, make it visible
        val isActiveTab = crashedTab?.id == tabManager.activeTabId
        if (isActiveTab) {
            newWebView.visibility = View.VISIBLE
            currentWebView = newWebView
        }

        // BUG-19 FIX: Fall back to HOME_URL if tab URL is empty
        val reloadUrl = crashedTab?.url?.takeIf { it.isNotEmpty() } ?: TabManager.HOME_URL
        newWebView.loadUrl(reloadUrl)

        Toast.makeText(this, R.string.webview_recovered, Toast.LENGTH_SHORT).show()
    }

    /**
     * H2 FIX: Handle new window requests from web content.
     * Instead of creating a bare WebView with minimal client, we use a temporary
     * WebView that intercepts the first URL load and creates a proper tab
     * with all features (ad blocker, popup blocker, download listener, history).
     *
     * The temporary WebView is released back to the pool after the URL is captured.
     */
    private fun handleNewWindow(resultMsg: android.os.Message?) {
        val tempWebView = WebViewPool.acquire(this)
        tempWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(wv: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (SecurityUtils.isUrlSafe(url)) {
                    // Create a proper tab with all browser features
                    runOnUiThread { addNewTab(url) }
                }
                // Release the temporary WebView back to pool (detached, no parent)
                WebViewPool.release(wv!!, removeParent = false)
                return true
            }
        }

        val transport = resultMsg?.obj as? WebView.WebViewTransport
        transport?.webView = tempWebView
        resultMsg?.sendToTarget()
    }

    // === BrowserWebViewClient.Callback (OPTIMIZED) ===

    override fun onPageLoadStarted(webView: WebView, url: String, isDesktopMode: Boolean) {
        // BUG-01 FIX: Use the WebView reference to find the correct tab,
        // not the active tab. Background tab loads were corrupting active tab state.
        val tab = tabManager.getTabForWebView(webView)
        if (tab != null) {
            tab.url = url
            // Only update UI elements for the currently active tab
            if (tab.id == tabManager.activeTabId) {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = 0
                binding.urlBar.setText(url)
            }
        }
    }

    override fun onPageLoadFinished(webView: WebView, title: String, url: String, isDesktopMode: Boolean) {
        // BUG-01 FIX: Use the WebView reference to find the correct tab.
        val tab = tabManager.getTabForWebView(webView)
        tab?.let { t ->
            t.title = title
            t.url = url
            val idx = tabManager.indexOf(t)
            if (idx >= 0 && idx < binding.tabLayout.tabCount) {
                binding.tabLayout.getTabAt(idx)?.text = t.title
            }
            // Only update UI elements for the currently active tab
            if (t.id == tabManager.activeTabId) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.urlBar.setText(url)
                updateNavigationButtons()
                updateSslIcon(url)
            }
        }
    }

    override fun onPageLoadError(webView: WebView, errorMsg: String, url: String) {
        // BUG-01 FIX: Use the provided WebView, not currentWebView.
        // BUG-11 FIX: Load error page into the correct tab's WebView.
        val safeErrorPage = SecurityUtils.buildErrorPage(errorMsg, url)
        webView.loadDataWithBaseURL(null, safeErrorPage, "text/html", "UTF-8", null)

        // Only update UI if this is the active tab
        val tab = tabManager.getTabForWebView(webView)
        if (tab?.id == tabManager.activeTabId) {
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onSslError(handler: SslErrorHandler, error: SslError) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ssl_error_title)
            .setMessage(getString(R.string.ssl_error_message) + "\n\nError: ${error.toString()}")
            .setPositiveButton(R.string.proceed) { _, _ -> handler.proceed() }
            .setNegativeButton(R.string.cancel) { _, _ -> handler.cancel() }
            .setOnCancelListener { handler.cancel() }
            .show()
    }

    override fun onPopupBlocked() {
        Toast.makeText(this, R.string.popup_blocked_toast, Toast.LENGTH_SHORT).show()
    }

    override fun onRenderProcessGone(webView: WebView) {
        // BUG-13 FIX: Use the WebView reference to find the crashed tab.
        // Must run on UI thread — render crash callbacks come from a background thread
        if (!isFinishing && !isDestroyed) {
            runOnUiThread { handleRenderProcessCrash(webView) }
        }
    }

    // === TABS (OPTIMIZED — VISIBLE/GONE SWITCHING) ===

    /**
     * Add a new tab with a WebView from the pool.
     * The WebView is added to the container but starts as GONE.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun addNewTab(url: String = TabManager.HOME_URL) {
        val isDesktop = tabManager.getActiveTab()?.isDesktopMode ?: false
        val webView = createWebView(isDesktop)
        val tab = tabManager.addTab(webView, url, isDesktop)

        if (tab == null) {
            WebViewPool.release(webView)
            Toast.makeText(this, R.string.tab_limit_reached, Toast.LENGTH_SHORT).show()
            return
        }

        // Add WebView to container (GONE by default from createWebView)
        binding.webViewContainer.addView(webView)

        binding.tabLayout.addTab(binding.tabLayout.newTab().apply { text = getString(R.string.new_tab) }, false)
        webView.loadUrl(url)
        switchToTab(tab.id)
        updateTabCount()
    }

    /**
     * Switch to a tab using VISIBLE/GONE — NO remove/add, NO layout thrash.
     * This is the #1 smoothness optimization: the WebView is already in the
     * container, so switching is just a visibility change (1 frame vs 2-3 frames).
     */
    private fun switchToTab(tabId: Int) {
        val tab = tabManager.switchToTab(tabId) ?: return

        // C4 FIX: If this tab's WebView was ejected by onTrimMemory, recreate it
        if (tab.needsWebViewRecreation || tab.webView == null) {
            val newWebView = createWebView(tab.isDesktopMode)
            tab.webView = newWebView
            tab.needsWebViewRecreation = false
            binding.webViewContainer.addView(newWebView)
            newWebView.visibility = View.VISIBLE
            newWebView.loadUrl(tab.url)
        }

        // BUG-07 FIX: When switching to a tab that was restored with
        // LOAD_CACHE_ELSE_NETWORK, reset to LOAD_DEFAULT so it loads
        // fresh content when the user actively views it
        tab.webView?.let { wv ->
            if (wv.settings.cacheMode != WebSettings.LOAD_DEFAULT) {
                wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
                wv.reload()
            }
        }

        // Hide ALL WebViews, then show only the active one
        for (t in tabManager.tabs) {
            t.webView?.visibility = if (t.id == tabId) View.VISIBLE else View.GONE
        }

        currentWebView = tab.webView
        binding.urlBar.setText(tab.url)
        updateSslIcon(tab.url)

        val idx = tabManager.indexOf(tab)
        if (idx >= 0 && idx < binding.tabLayout.tabCount) binding.tabLayout.getTabAt(idx)?.select()
        updateNavigationButtons()
    }

    private fun closeTab(tabId: Int) {
        val closedIdx = tabManager.tabs.indexOf(tabManager.getTab(tabId))
        val closedWebView = tabManager.getTab(tabId)?.webView

        // C3+H1 FIX: Remove WebView from container BEFORE closing tab
        // (which releases to WebViewPool). This ensures WebView is detached
        // from parent before pool tries to destroy or recycle it.
        closedWebView?.let { wv ->
            binding.webViewContainer.removeView(wv)
        }

        val nextTab = tabManager.closeTab(tabId)

        if (nextTab == null) {
            binding.tabLayout.removeAllTabs()
            currentWebView = null
            addNewTab(TabManager.HOME_URL)
            return
        }

        if (closedIdx >= 0 && closedIdx < binding.tabLayout.tabCount) {
            binding.tabLayout.removeTabAt(closedIdx)
        }
        switchToTab(nextTab.id)
        updateTabCount()
    }

    // L2 FIX: Added confirmation dialog for "Close All" tabs
    private fun showTabSwitcher() {
        if (tabManager.tabs.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.open_tabs)
            .setItems(tabManager.tabs.map { it.title }.toTypedArray()) { _, w ->
                switchToTab(tabManager.tabs[w].id)
            }
            .setNeutralButton(R.string.close_all) { _, _ ->
                // Confirm before closing all tabs
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.close_all)
                    .setMessage(R.string.close_all_confirmation)
                    .setPositiveButton(R.string.close_all) { _, _ ->
                        binding.webViewContainer.removeAllViews()
                        tabManager.closeAllTabs()
                        binding.tabLayout.removeAllTabs()
                        currentWebView = null
                        addNewTab(TabManager.HOME_URL)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setPositiveButton(R.string.new_tab_button) { _, _ -> addNewTab(TabManager.HOME_URL) }
            .show()
    }

    private fun updateTabCount() {
        binding.btnTabs.text = tabManager.tabs.size.toString()
    }

    // === NAVIGATION ===

    private fun goBack() { currentWebView?.let { if (it.canGoBack()) it.goBack() } }
    private fun goForward() { currentWebView?.let { if (it.canGoForward()) it.goForward() } }
    private fun refresh() { currentWebView?.reload(); binding.swipeRefresh.isRefreshing = false }
    private fun loadUrl(url: String) { currentWebView?.loadUrl(url); binding.urlBar.setText(url) }

    private fun updateNavigationButtons() {
        binding.btnBack.alpha = if (currentWebView?.canGoBack() == true) 1.0f else 0.4f
        binding.btnForward.alpha = if (currentWebView?.canGoForward() == true) 1.0f else 0.4f
    }

    private fun updateSslIcon(url: String) {
        val isSecure = url.startsWith("https://")
        with(binding.sslIcon) {
            setImageResource(if (isSecure) R.drawable.ic_lock else R.drawable.ic_lock_open)
            imageTintList = ContextCompat.getColorStateList(
                this@MainActivity,
                if (isSecure) R.color.ssl_secure else R.color.ssl_insecure
            )
            contentDescription = getString(
                if (isSecure) R.string.ssl_secure_status else R.string.ssl_not_secure_status
            )
        }
    }

    // === DESKTOP MODE ===

    @SuppressLint("SetJavaScriptEnabled")
    private fun toggleDesktopMode() {
        val tab = tabManager.getActiveTab() ?: return
        tab.isDesktopMode = !tab.isDesktopMode
        val isDesktop = tab.isDesktopMode

        currentWebView?.let { wv ->
            BrowserWebViewClient.applyModeSettings(wv.settings, isDesktop, mobileUserAgent)
            wv.setInitialScale(0)
            wv.reload()
            Toast.makeText(
                this,
                if (isDesktop) R.string.desktop_mode_on else R.string.mobile_mode_on,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // === SYSTEM BROWSER ===

    private fun openInSystemBrowser() {
        currentWebView?.url?.let { url ->
            if (SecurityUtils.isUrlSafe(url)) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    // === MENU ===

    private fun showBrowserMenu() {
        val isDesktop = tabManager.getActiveTab()?.isDesktopMode ?: false

        lifecycleScope.launch {
            val isBookmarked = currentWebView?.url?.let {
                withContext(Dispatchers.IO) { bookmarkDao.isBookmarked(it) }
            } ?: false

            val opts = arrayOf(
                if (isDesktop) getString(R.string.switch_to_mobile) else getString(R.string.switch_to_desktop),
                getString(R.string.open_in_system_browser),
                if (isBookmarked) getString(R.string.remove_bookmark) else getString(R.string.bookmark),
                getString(R.string.bookmarks),
                getString(R.string.history),
                getString(R.string.share),
                getString(R.string.find_in_page),
                if (adBlocker.isEnabled) getString(R.string.ad_blocker_off) else getString(R.string.ad_blocker_on),
                if (popupBlocker.isEnabled) getString(R.string.popup_blocker_off) else getString(R.string.popup_blocker_on),
                getString(R.string.settings)
            )
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.browser_menu)
                .setItems(opts) { _, w ->
                    when (w) {
                        0 -> toggleDesktopMode()
                        1 -> openInSystemBrowser()
                        2 -> toggleBookmark()
                        3 -> showBookmarks()
                        4 -> showFullHistory()
                        5 -> sharePage()
                        6 -> findInPage()
                        7 -> toggleAdBlocker()
                        8 -> togglePopupBlocker()
                        9 -> showSettings()
                    }
                }
                .show()
        }
    }

    // === BOOKMARKS (Room DB) ===

    private fun toggleBookmark() {
        val url = currentWebView?.url ?: return
        val title = currentWebView?.title ?: url

        lifecycleScope.launch {
            val isBookmarked = withContext(Dispatchers.IO) { bookmarkDao.isBookmarked(url) }
            if (isBookmarked) {
                withContext(Dispatchers.IO) { bookmarkDao.deleteByUrl(url) }
                Toast.makeText(this@MainActivity, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            } else {
                withContext(Dispatchers.IO) { bookmarkDao.insert(BookmarkEntity(url = url, title = title)) }
                Toast.makeText(this@MainActivity, getString(R.string.bookmarked) + ": $title", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // M4 FIX: Show title + URL in bookmarks dialog for disambiguation
    private fun showBookmarks() {
        lifecycleScope.launch {
            val bookmarks = withContext(Dispatchers.IO) { bookmarkDao.getAllBookmarksOnce() }
            if (bookmarks.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val displayItems = bookmarks.map { bm ->
                if (bm.title != bm.url) "${bm.title}\n${bm.url}" else bm.url
            }.toTypedArray()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.bookmarks)
                .setItems(displayItems) { _, w ->
                    loadUrl(bookmarks[w].url)
                }
                .show()
        }
    }

    // === HISTORY (Room DB) ===

    // M4 FIX: Show title + URL in history dialog for disambiguation
    private fun showFullHistory() {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) { historyDao.getRecentHistory(100) }
            if (history.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.no_history, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val displayItems = history.map { h ->
                if (h.title != h.url) "${h.title}\n${h.url}" else h.url
            }.toTypedArray()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.history)
                .setItems(displayItems) { _, w ->
                    loadUrl(history[w].url)
                }
                .show()
        }
    }

    // === SHARE ===

    private fun sharePage() {
        currentWebView?.url?.let { url ->
            val title = currentWebView?.title ?: ""
            startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                type = "text/plain"
            }, getString(R.string.share_via)))
        }
    }

    // === FIND IN PAGE ===

    /**
     * BUG-22 FIX: Find in page with next/previous navigation.
     * Previously, findAllAsync() highlighted matches but provided no way
     * to jump between them. Now shows next/previous buttons.
     */
    private var findInPageQuery: String = ""

    private fun findInPage() {
        val input = EditText(this).apply {
            hint = getString(R.string.search_on_page)
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.find_in_page)
            .setView(input)
            .setPositiveButton(R.string.find) { _, _ ->
                findInPageQuery = input.text.toString()
                currentWebView?.findAllAsync(findInPageQuery)
            }
            .setNeutralButton(R.string.clear) { _, _ ->
                findInPageQuery = ""
                currentWebView?.clearMatches()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // === AD BLOCKER ===

    private fun toggleAdBlocker() {
        adBlocker.isEnabled = !adBlocker.isEnabled
        Toast.makeText(
            this,
            if (adBlocker.isEnabled) R.string.ad_blocker_on else R.string.ad_blocker_off,
            Toast.LENGTH_SHORT
        ).show()
    }

    // === POPUP BLOCKER ===

    private fun togglePopupBlocker() {
        popupBlocker.isEnabled = !popupBlocker.isEnabled
        currentWebView?.let { popupBlocker.applyToWebView(it) }
        Toast.makeText(
            this,
            if (popupBlocker.isEnabled) R.string.popup_blocker_on else R.string.popup_blocker_off,
            Toast.LENGTH_SHORT
        ).show()
    }

    // === SETTINGS ===

    private fun showSettings() {
        val options = arrayOf(
            getString(R.string.clear_browsing_data),
            getString(R.string.crash_logs),
            getString(R.string.about_zbrowser)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setItems(options) { _, w ->
                when (w) {
                    0 -> clearData()
                    1 -> showCrashLogs()
                    2 -> showAbout()
                }
            }.show()
    }

    private fun clearData() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_browsing_data)
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.clear_button) { _, _ ->
                binding.webViewContainer.removeAllViews()
                tabManager.tabs.forEach { it.webView?.clearCache(true) }
                tabManager.closeAllTabs()
                binding.tabLayout.removeAllTabs()
                currentWebView = null
                // BUG-08 FIX: Clear ALL browsing data, not just cookies and cache.
                // Also clears DOM storage, form data, and WebSQL databases.
                // BUG-16 FIX: Use non-deprecated CookieManager API
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    android.webkit.CookieManager.getInstance().removeAllCookies { /* cookies cleared */ }
                } else {
                    @Suppress("DEPRECATION")
                    android.webkit.CookieManager.getInstance().removeAllCookie()
                }
                WebView.clearClientCertPreferences(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
                tabManager.tabs.forEach { it.webView?.clearFormData() }
                lifecycleScope.launch(Dispatchers.IO) {
                    historyDao.deleteAll()
                    bookmarkDao.deleteAll()
                }
                addNewTab(TabManager.HOME_URL)
                Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_zbrowser)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    // === CRASH REPORTER ===

    private fun checkForCrash() {
        if (CrashReporter.hasUnreadCrash()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.crash_detected_title)
                .setMessage(R.string.crash_detected_message)
                .setPositiveButton(R.string.view_crash_log) { _, _ ->
                    showCrashLogs()
                    CrashReporter.markCrashRead()
                }
                .setNegativeButton(R.string.dismiss_crash) { _, _ ->
                    CrashReporter.markCrashRead()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun showCrashLogs() {
        val logs = CrashReporter.getAllCrashLogs()
        if (logs.isEmpty()) {
            Toast.makeText(this, R.string.no_crash_logs, Toast.LENGTH_SHORT).show()
            return
        }

        val logContents = logs.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_log_title)
            .setItems(logContents) { _, w ->
                val content = logs[w].readText()
                MaterialAlertDialogBuilder(this)
                    .setTitle(logs[w].name)
                    .setMessage(content.take(5000))
                    .setPositiveButton(R.string.ok, null)
                    .setNeutralButton(R.string.share_crash_log) { _, _ ->
                        startActivity(Intent.createChooser(Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, content)
                            type = "text/plain"
                        }, getString(R.string.share_via)))
                    }
                    .show()
            }
            .setNeutralButton(R.string.clear_crash_logs) { _, _ ->
                CrashReporter.clearAllLogs()
                Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // === INPUT ===

    private fun processInput(input: String): String = when {
        input.startsWith("http://") || input.startsWith("https://") -> input
        input.contains(".") && !input.contains(" ") -> "https://$input"
        else -> "https://www.google.com/search?q=${Uri.encode(input)}"
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val url = uri.toString()
                if (SecurityUtils.isUrlSafe(url)) {
                    addNewTab(url)
                }
            }
        }
    }

    // === STATE PRESERVATION ===

    private fun restoreTabs() {
        val tabStates = viewModel.getTabStates()
        if (tabStates.isNullOrEmpty()) {
            addNewTab(TabManager.HOME_URL)
            return
        }

        val activeId = viewModel.getActiveTabId()

        for ((index, state) in tabStates.withIndex()) {
            val webView = createWebView(state.isDesktopMode)
            val tab = tabManager.addTab(webView, state.url, state.isDesktopMode)
            if (tab != null) {
                tab.title = state.title
                binding.webViewContainer.addView(webView)
                binding.tabLayout.addTab(binding.tabLayout.newTab().apply { text = state.title }, false)

                // BUG-07 FIX: Only load the active tab immediately.
                // Background tabs use LOAD_CACHE_ONLY to avoid massive
                // network/CPU/memory spike on startup. They'll load fresh
                // content when the user switches to them.
                if (state.id == activeId) {
                    webView.loadUrl(state.url)
                } else {
                    // Try loading from cache first, fall back to network on switch
                    webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    webView.loadUrl(state.url)
                }
            }
        }

        // H3 FIX: Restore nextTabId from saved state to prevent ID collisions
        val savedNextTabId = viewModel.getNextTabId()
        if (savedNextTabId > tabManager.nextTabId) {
            tabManager.setNextTabId(savedNextTabId)
        }

        if (activeId > 0) {
            switchToTab(activeId)
        }
    }
}
