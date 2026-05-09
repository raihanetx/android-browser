package com.technova.browser.data.repository

import com.technova.browser.data.model.History
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<History>>
    fun getHistoryByUrl(url: String): Flow<History?>
    fun searchHistory(query: String): Flow<List<History>>
    suspend fun addHistoryItem(history: History)
    suspend fun addHistoryItems(history: List<History>)
    suspend fun updateHistoryItem(history: History)
    suspend fun deleteHistoryItem(history: History)
    suspend fun clearHistoryOlderThan(timestamp: Long)
    suspend fun clearAllHistory()
}
