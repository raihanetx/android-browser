package com.zbrowser.app

import android.app.Application
import com.zbrowser.app.data.HistoryDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for ZBrowser v4.1.
 *
 * Initializes:
 * - Crash reporter (BEFORE WebView pool, so crashes during init are caught)
 * - WebView pool for near-instant tab creation (includes data directory suffix)
 * - BookmarkManager migration from legacy EncryptedSharedPreferences
 *
 * BUG-09 FIX: Automatic history cleanup on startup — deletes entries older
 * than 90 days to prevent unbounded database growth.
 *
 * BUG-14 FIX: Removed duplicate CoroutineScope — now uses the Hilt-provided
 * application scope from AppModule instead of creating a separate one.
 *
 * Uses @HiltAndroidApp for proper Hilt initialization.
 */
@HiltAndroidApp
class ZBrowserApp : Application() {

    @Inject lateinit var bookmarkManager: BookmarkManager
    @Inject lateinit var historyDao: HistoryDao
    @Inject lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        // H6 FIX: Initialize crash reporter FIRST so it catches any subsequent init crashes
        CrashReporter.init(this)

        // Initialize WebView pool — sets data directory suffix for Android P+ internally
        WebViewPool.init(this)

        // Migrate legacy bookmarks to Room in background (non-blocking)
        appScope.launch(Dispatchers.IO) {
            bookmarkManager.migrateIfNeeded()
        }

        // BUG-09 FIX: Auto-cleanup history older than 90 days on startup
        // Prevents unbounded database growth that degrades query performance
        appScope.launch(Dispatchers.IO) {
            val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000
            historyDao.deleteOlderThan(System.currentTimeMillis() - ninetyDaysMs)
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}
