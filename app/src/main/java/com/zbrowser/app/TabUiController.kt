package com.zbrowser.app

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.zbrowser.app.data.HistoryDao
import com.zbrowser.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TabUiController(
    private val binding: ActivityMainBinding,
    private val navigationController: NavigationController,
    private val activity: AppCompatActivity,
    private val tabManager: TabManager,
    private val viewModel: BrowserViewModel,
    private val adBlocker: AdBlocker,
    private val popupBlocker: PopupBlocker,
    private val historyDao: HistoryDao,
    private val downloadManagerHelper: DownloadManagerHelper,
    private val callback: BrowserWebViewClient.Callback,
    private val permissionManager: PermissionManager,
    private val onFileChooserLauncher: (Intent) -> Unit
) {
    var currentWebView: WebView? = null
    var mobileUserAgent: String? = null

    private val lifecycleScope get() = activity.lifecycleScope

    fun createTabView(title: String): View {
        val density = activity.resources.displayMetrics.density

        val container = LinearLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                setStroke((1 * density).toInt(), ContextCompat.getColor(activity, R.color.text_secondary))
                cornerRadius = (6 * density)
                setColor(ContextCompat.getColor(activity, android.R.color.transparent))
            }
        }

        val letterView = TextView(activity).apply {
            layoutParams = ViewGroup.LayoutParams((22 * density).toInt(), (22 * density).toInt())
            gravity = Gravity.CENTER
            text = if (title.isNotEmpty()) title.first().uppercase() else "N"
            textSize = 10f
            setTextColor(ContextCompat.getColor(activity, android.R.color.white))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(activity, R.color.colorPrimary))
                cornerRadius = (11 * density)
            }
        }
        container.addView(letterView)

        val space = View(activity).apply {
            layoutParams = ViewGroup.LayoutParams((4 * density).toInt(), 0)
        }
        container.addView(space)

        val titleView = TextView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = title
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
        }
        container.addView(titleView)

        return container
    }

    fun setupTabLayout() {
        // Tab layout removed - using swipe gesture to show tab icons
    }

    fun onTrimMemory(level: Int) {
        tabManager.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            currentWebView?.clearCache(false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(desktopMode: Boolean): WebView {
        val webView = WebViewPool.acquire(activity)
        webView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.loadsImagesAutomatically = true
        s.cacheMode = WebSettings.LOAD_DEFAULT

        if (mobileUserAgent == null) {
            mobileUserAgent = s.userAgentString
        }

        BrowserWebViewClient.applyModeSettings(s, desktopMode, mobileUserAgent)

        val wvClient = BrowserWebViewClient(
            context = activity,
            tabLookup = { wv -> wv?.let { tabManager.getTabForWebView(it) } },
            adBlocker = adBlocker,
            historyDao = historyDao,
            appScope = lifecycleScope
        )
        wvClient.callback = callback
        webView.webViewClient = wvClient

        webView.webChromeClient = BrowserWebChromeClient(
            onProgressChanged = { newProgress ->
                navigationController.animateProgress(newProgress)
            },
            onNewWindowRequested = { resultMsg -> handleNewWindow(resultMsg) },
            popupBlocker = popupBlocker,
            permissionManager = permissionManager,
            onPopupBlocked = {
                activity.runOnUiThread {
                    Toast.makeText(activity, R.string.popup_blocked_toast, Toast.LENGTH_SHORT).show()
                }
            },
            onFileChooserRequested = { intent, filePathCallback ->
                BrowserWebChromeClient.pendingFilePathCallback = filePathCallback
                onFileChooserLauncher(intent)
                true
            }
        )

        webView.setDownloadListener(downloadManagerHelper.webViewDownloadListener)
        webView.visibility = View.GONE
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun addNewTab(url: String = TabManager.HOME_URL) {
        val isDesktop = tabManager.getActiveTab()?.isDesktopMode ?: false
        val webView = createWebView(isDesktop)
        val tab = tabManager.addTab(webView, url, isDesktop)

        if (tab == null) {
            WebViewPool.release(webView)
            Toast.makeText(activity, R.string.tab_limit_reached, Toast.LENGTH_SHORT).show()
            return
        }

        binding.webViewContainer.addView(webView)
        webView.loadUrl(url)
        switchToTab(tab.id)
        updateTabCount()
    }

    fun switchToTab(tabId: Int) {
        val tab = tabManager.switchToTab(tabId) ?: return

        if (tab.needsWebViewRecreation || tab.webView == null) {
            val newWebView = createWebView(tab.isDesktopMode)
            tab.webView = newWebView
            tab.needsWebViewRecreation = false
            binding.webViewContainer.addView(newWebView)
            newWebView.visibility = View.VISIBLE
            newWebView.loadUrl(tab.url)
        }

        tab.webView?.let { wv ->
            if (wv.settings.cacheMode != WebSettings.LOAD_DEFAULT) {
                wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
                wv.reload()
            }
        }

        for (t in tabManager.tabs) {
            t.webView?.visibility = if (t.id == tabId) View.VISIBLE else View.GONE
        }

        currentWebView = tab.webView
        navigationController.setUrlBarText(tab.url)
        navigationController.updateSslIcon(tab.url)

        navigationController.updateNavigationButtons()
    }

    fun closeTab(tabId: Int) {
        val closedIdx = tabManager.tabs.indexOf(tabManager.getTab(tabId))
        val closedWebView = tabManager.getTab(tabId)?.webView

        closedWebView?.let { wv ->
            binding.webViewContainer.removeView(wv)
        }

        val nextTab = tabManager.closeTab(tabId)

        if (nextTab == null) {
            currentWebView = null
            addNewTab(TabManager.HOME_URL)
            return
        }

        switchToTab(nextTab.id)
        updateTabCount()
    }

    fun showTabSwitcher() {
        if (tabManager.tabs.isEmpty()) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.open_tabs)
            .setItems(tabManager.tabs.map { it.title }.toTypedArray()) { _, w ->
                switchToTab(tabManager.tabs[w].id)
            }
            .setNeutralButton(R.string.close_all) { _, _ ->
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.close_all)
                    .setMessage(R.string.close_all_confirmation)
                    .setPositiveButton(R.string.close_all) { _, _ ->
                        binding.webViewContainer.removeAllViews()
                        tabManager.closeAllTabs()
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
        // Tab count display removed - using swipe gesture to show tabs
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun toggleDesktopMode() {
        val tab = tabManager.getActiveTab() ?: return
        tab.isDesktopMode = !tab.isDesktopMode
        val isDesktop = tab.isDesktopMode
        currentWebView?.let { wv ->
            BrowserWebViewClient.applyModeSettings(wv.settings, isDesktop, mobileUserAgent)
            wv.reload()
            Toast.makeText(
                activity,
                if (isDesktop) R.string.desktop_mode_on else R.string.mobile_mode_on,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun handleRenderProcessCrash(crashedWebView: WebView) {
        val crashedTab = tabManager.getTabForWebView(crashedWebView)
        crashedTab?.webView = null

        val newWebView = createWebView(crashedTab?.isDesktopMode ?: false)
        crashedTab?.webView = newWebView
        binding.webViewContainer.addView(newWebView)

        val isActiveTab = crashedTab?.id == tabManager.activeTabId
        if (isActiveTab) {
            newWebView.visibility = View.VISIBLE
            currentWebView = newWebView
        }

        val reloadUrl = crashedTab?.url?.takeIf { it.isNotEmpty() } ?: TabManager.HOME_URL
        newWebView.loadUrl(reloadUrl)

        Toast.makeText(activity, R.string.webview_recovered, Toast.LENGTH_SHORT).show()
    }

    fun handleNewWindow(resultMsg: Message?) {
        val tempWebView = WebViewPool.acquire(activity)
        tempWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(wv: WebView?, request: WebResourceRequest?): Boolean {
                try {
                    val url = request?.url?.toString()
                    if (url != null && SecurityUtils.isUrlSafe(url)) {
                        addNewTab(url)
                    }
                    return true
                } finally {
                    if (wv != null) {
                        WebViewPool.release(wv, removeParent = false)
                    }
                }
            }
        }

        val transport = resultMsg?.obj as? WebView.WebViewTransport
        transport?.webView = tempWebView
        resultMsg?.sendToTarget()
    }

    fun restoreTabs() {
        val tabStates = viewModel.getTabStates()
        if (tabStates.isNullOrEmpty()) {
            addNewTab(TabManager.HOME_URL)
            return
        }

        val activeId = viewModel.getActiveTabId()

        for (state in tabStates) {
            val webView = createWebView(state.isDesktopMode)
            val tab = tabManager.addTab(webView, state.url, state.isDesktopMode)
            if (tab != null) {
                tab.title = state.title
                binding.webViewContainer.addView(webView)

                if (state.id == activeId) {
                    webView.loadUrl(state.url)
                } else {
                    webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    webView.loadUrl(state.url)
                }
            }
        }

        val savedNextTabId = viewModel.getNextTabId()
        if (savedNextTabId > tabManager.nextTabId) {
            tabManager.setNextTabId(savedNextTabId)
        }

        if (activeId > 0) {
            switchToTab(activeId)
        }
    }
}
