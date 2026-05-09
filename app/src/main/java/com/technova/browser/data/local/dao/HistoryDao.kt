package com.technova.browser.data.local.dao

import androidx.room.*
import com.technova.browser.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitTime DESC LIMIT 10000")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url ORDER BY visitTime DESC LIMIT 1")
    fun getHistoryByUrl(url: String): Flow<HistoryEntity?>

    @Query("SELECT * FROM history WHERE visitTime >= :startTime AND visitTime <= :endTime")
    fun getHistoryBetween(startTime: Long, endTime: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY visitTime DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(history: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItems(history: List<HistoryEntity>)

    @Update
    suspend fun updateHistoryItem(history: HistoryEntity)

    @Delete
    suspend fun deleteHistoryItem(history: HistoryEntity)

    @Query("DELETE FROM history WHERE visitTime < :timestamp")
    suspend fun deleteHistoryOlderThan(timestamp: Long)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}
