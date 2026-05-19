package com.zbrowser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for browsing history.
 * Provides CRUD operations with Flow for reactive UI updates.
 */
@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 100): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY visitedAt DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE visitedAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getCount(): Int

    // BUG-24 FIX: Added method to delete individual history entries by ID
    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
