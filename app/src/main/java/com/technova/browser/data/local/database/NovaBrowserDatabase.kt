package com.technova.browser.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.technova.browser.data.local.dao.BookmarkDao
import com.technova.browser.data.local.dao.HistoryDao
import com.technova.browser.data.local.dao.TabDao
import com.technova.browser.data.local.entity.BookmarkEntity
import com.technova.browser.data.local.entity.HistoryEntity
import com.technova.browser.data.local.entity.TabEntity
import com.technova.browser.data.local.typeconverters.Converters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        TabEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NovaBrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun tabDao(): TabDao

    companion object {
        @Volatile
        private var INSTANCE: NovaBrowserDatabase? = null

        fun getDatabase(context: android.content.Context): NovaBrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovaBrowserDatabase::class.java,
                    "nova_browser_database"
                )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Pre-populate with default bookmarks if needed
                            CoroutineScope(Dispatchers.IO).launch {
                                // Could add default bookmarks here
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
