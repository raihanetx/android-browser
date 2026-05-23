package com.zbrowser.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zbrowser.app.data.BookmarkDao
import com.zbrowser.app.data.BookmarkEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages browser bookmarks with dual storage:
 * 1. Room database (primary) — for structured queries, search, and reactive UI
 * 2. Encrypted SharedPreferences (migration fallback) — for reading old bookmarks
 *
 * All new bookmarks are stored in Room. On first access, any legacy
 * EncryptedSharedPreferences bookmarks are migrated to Room.
 */
@Singleton
class BookmarkManager @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    @LegacyBookmarks private val legacyPrefs: SharedPreferences
) {
    @Volatile
    private var migrated = false

    /**
     * Migrate legacy encrypted bookmarks to Room on first access.
     */
    suspend fun migrateIfNeeded() {
        if (migrated) return
        migrated = true

        val existing = bookmarkDao.getAllBookmarksOnce()
        if (existing.isNotEmpty()) return  // Already have bookmarks in Room

        withContext(Dispatchers.IO) {
            val legacyBookmarks = legacyPrefs.all
            for ((url, title) in legacyBookmarks) {
                val titleStr = (title as? String) ?: url
                bookmarkDao.insert(BookmarkEntity(url = url, title = titleStr))
            }
            // Clear legacy storage after migration
            if (legacyBookmarks.isNotEmpty()) {
                legacyPrefs.edit().clear().apply()
            }
        }
    }
}

/**
 * Qualifier annotation for legacy encrypted SharedPreferences bookmarks.
 */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LegacyBookmarks
