package com.zbrowser.app

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zbrowser.app.data.BookmarkDao
import com.zbrowser.app.data.BookmarkEntity
import com.zbrowser.app.data.HistoryDao
import com.zbrowser.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserMenuController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val currentWebView: () -> WebView?,
    private val tabManager: TabManager,
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val adBlocker: AdBlocker,
    private val popupBlocker: PopupBlocker,
    private val navigationController: NavigationController,
    private val tabUiController: TabUiController
) {
    private val lifecycleScope get() = activity.lifecycleScope

    fun showBrowserMenu() {
        val isDesktop = tabManager.getActiveTab()?.isDesktopMode ?: false

        lifecycleScope.launch {
            val isBookmarked = currentWebView()?.url?.let {
                withContext(Dispatchers.IO) { bookmarkDao.isBookmarked(it) }
            } ?: false

            val opts = arrayOf(
                if (isDesktop) activity.getString(R.string.switch_to_mobile) else activity.getString(R.string.switch_to_desktop),
                activity.getString(R.string.open_in_system_browser),
                if (isBookmarked) activity.getString(R.string.remove_bookmark) else activity.getString(R.string.bookmark),
                activity.getString(R.string.bookmarks),
                activity.getString(R.string.history),
                activity.getString(R.string.share),
                activity.getString(R.string.find_in_page),
                if (adBlocker.isEnabled) activity.getString(R.string.ad_blocker_off) else activity.getString(R.string.ad_blocker_on),
                if (popupBlocker.isEnabled) activity.getString(R.string.popup_blocker_off) else activity.getString(R.string.popup_blocker_on),
                activity.getString(R.string.settings)
            )
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.browser_menu)
                .setItems(opts) { _, w ->
                    when (w) {
                        0 -> tabUiController.toggleDesktopMode()
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

    private fun openInSystemBrowser() {
        currentWebView()?.url?.let { url ->
            if (SecurityUtils.isUrlSafe(url)) {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    private fun toggleBookmark() {
        val url = currentWebView()?.url ?: return
        val title = currentWebView()?.title ?: url

        lifecycleScope.launch {
            val isBookmarked = withContext(Dispatchers.IO) { bookmarkDao.isBookmarked(url) }
            if (isBookmarked) {
                withContext(Dispatchers.IO) { bookmarkDao.deleteByUrl(url) }
                Toast.makeText(activity, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            } else {
                withContext(Dispatchers.IO) { bookmarkDao.insert(BookmarkEntity(url = url, title = title)) }
                Toast.makeText(activity, activity.getString(R.string.bookmarked) + ": $title", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBookmarks() {
        lifecycleScope.launch {
            val bookmarks = withContext(Dispatchers.IO) { bookmarkDao.getAllBookmarksOnce() }
            if (bookmarks.isEmpty()) {
                Toast.makeText(activity, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val displayItems = bookmarks.map { bm ->
                if (bm.title != bm.url) "${bm.title}\n${bm.url}" else bm.url
            }.toTypedArray()
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.bookmarks)
                .setItems(displayItems) { _, w ->
                    navigationController.loadUrl(bookmarks[w].url)
                }
                .show()
        }
    }

    private fun showFullHistory() {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) { historyDao.getRecentHistory(100) }
            if (history.isEmpty()) {
                Toast.makeText(activity, R.string.no_history, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val historyItems = history.map { h ->
                if (h.title != h.url) "${h.title}\n${h.url}" else h.url
            }.toTypedArray()

            var selectedIdx = -1
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.history)
                .setSingleChoiceItems(historyItems, -1) { _, w -> selectedIdx = w }
                .setPositiveButton(R.string.open) { _, _ ->
                    if (selectedIdx >= 0) navigationController.loadUrl(history[selectedIdx].url)
                }
                .setNeutralButton(R.string.delete_history_entry) { _, _ ->
                    if (selectedIdx >= 0) {
                        val entry = history[selectedIdx]
                        lifecycleScope.launch(Dispatchers.IO) {
                            historyDao.deleteById(entry.id)
                        }
                        Toast.makeText(activity, R.string.history_entry_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    private fun sharePage() {
        currentWebView()?.url?.let { url ->
            val title = currentWebView()?.title ?: ""
            activity.startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                type = "text/plain"
            }, activity.getString(R.string.share_via)))
        }
    }

    private fun findInPage() {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.search_on_page)
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.find_in_page)
            .setView(input)
            .setPositiveButton(R.string.find) { _, _ ->
                currentWebView()?.findAllAsync(input.text.toString())
            }
            .setNeutralButton(R.string.clear) { _, _ ->
                currentWebView()?.clearMatches()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleAdBlocker() {
        adBlocker.isEnabled = !adBlocker.isEnabled
        Toast.makeText(
            activity,
            if (adBlocker.isEnabled) R.string.ad_blocker_on else R.string.ad_blocker_off,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun togglePopupBlocker() {
        popupBlocker.isEnabled = !popupBlocker.isEnabled
        currentWebView()?.let { popupBlocker.applyToWebView(it) }
        Toast.makeText(
            activity,
            if (popupBlocker.isEnabled) R.string.popup_blocker_on else R.string.popup_blocker_off,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun showSettings() {
        val options = arrayOf(
            activity.getString(R.string.clear_browsing_data),
            activity.getString(R.string.crash_logs),
            activity.getString(R.string.about_zbrowser)
        )
        MaterialAlertDialogBuilder(activity)
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
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.clear_browsing_data)
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.clear_button) { _, _ ->
                binding.webViewContainer.removeAllViews()
                tabManager.tabs.forEach { it.webView?.clearCache(true) }
                tabManager.closeAllTabs()
                currentWebView()?.let { tabUiController.currentWebView = null }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    android.webkit.CookieManager.getInstance().removeAllCookies { }
                } else {
                    @Suppress("DEPRECATION")
                    android.webkit.CookieManager.getInstance().removeAllCookie()
                }
                WebView.clearClientCertPreferences(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
                tabManager.tabs.forEach { it.webView?.clearFormData() }
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        historyDao.deleteAll()
                        bookmarkDao.deleteAll()
                    }
                    tabUiController.addNewTab(TabManager.HOME_URL)
                    Toast.makeText(activity, R.string.cleared, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCrashLogs() {
        val logs = CrashReporter.getAllCrashLogs()
        if (logs.isEmpty()) {
            Toast.makeText(activity, R.string.no_crash_logs, Toast.LENGTH_SHORT).show()
            return
        }

        val logContents = logs.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.crash_log_title)
            .setItems(logContents) { _, w ->
                val content = logs[w].readText()
                MaterialAlertDialogBuilder(activity)
                    .setTitle(logs[w].name)
                    .setMessage(content.take(5000))
                    .setPositiveButton(R.string.ok, null)
                    .setNeutralButton(R.string.share_crash_log) { _, _ ->
                        activity.startActivity(Intent.createChooser(Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, content)
                            type = "text/plain"
                        }, activity.getString(R.string.share_via)))
                    }
                    .show()
            }
            .setNeutralButton(R.string.clear_crash_logs) { _, _ ->
                CrashReporter.clearAllLogs()
                Toast.makeText(activity, R.string.cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.about_zbrowser)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    fun checkForCrash() {
        if (CrashReporter.hasUnreadCrash()) {
            MaterialAlertDialogBuilder(activity)
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
}
