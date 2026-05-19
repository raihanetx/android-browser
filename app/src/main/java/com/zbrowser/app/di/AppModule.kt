package com.zbrowser.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.zbrowser.app.AdBlocker
import com.zbrowser.app.BookmarkManager
import com.zbrowser.app.DownloadManagerHelper
import com.zbrowser.app.LegacyBookmarks
import com.zbrowser.app.PopupBlocker
import com.zbrowser.app.data.BookmarkDao
import com.zbrowser.app.data.HistoryDao
import com.zbrowser.app.data.ZBrowserDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt module that provides singleton dependencies for the entire app.
 *
 * NOTE: PermissionManager is NOT provided here because it requires an Activity
 * reference and cannot be a @Singleton. It is created directly in MainActivity.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    @LegacyBookmarks
    fun provideLegacyBookmarksPrefs(@ApplicationContext context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secure_bookmarks",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // BUG-03 FIX: If the master key is invalidated (device lock change,
            // biometric enrollment), EncryptedSharedPreferences.create() throws
            // GeneralSecurityException. This would crash the app on startup.
            // Fall back to regular SharedPreferences so the app can at least start.
            // The migration code in BookmarkManager will handle the empty prefs gracefully.
            context.getSharedPreferences("secure_bookmarks_fallback", Context.MODE_PRIVATE)
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZBrowserDatabase {
        return ZBrowserDatabase.getDatabase(context)
    }

    @Provides
    fun provideBookmarkDao(database: ZBrowserDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    fun provideHistoryDao(database: ZBrowserDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideAdBlocker(prefs: SharedPreferences): AdBlocker {
        return AdBlocker(prefs)
    }

    @Provides
    @Singleton
    fun providePopupBlocker(prefs: SharedPreferences): PopupBlocker {
        return PopupBlocker(prefs)
    }

    @Provides
    @Singleton
    fun provideDownloadManagerHelper(@ApplicationContext context: Context): DownloadManagerHelper {
        return DownloadManagerHelper(context)
    }

    @Provides
    @Singleton
    fun provideBookmarkManager(
        bookmarkDao: BookmarkDao,
        @LegacyBookmarks legacyPrefs: SharedPreferences
    ): BookmarkManager {
        return BookmarkManager(bookmarkDao, legacyPrefs)
    }

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
