package com.technova.browser.data.repository

import com.technova.browser.data.local.dao.HistoryDao
import com.technova.browser.data.local.entity.HistoryEntity
import com.technova.browser.data.model.History
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class HistoryRepositoryImpl(private val historyDao: HistoryDao) : HistoryRepository {

    override fun getAllHistory(): Flow<List<History>> {
        return historyDao.getAllHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getHistoryByUrl(url: String): Flow<History?> {
        return historyDao.getHistoryByUrl(url).map { entity ->
            entity?.toDomain()
        }
    }

    override fun searchHistory(query: String): Flow<List<History>> {
        return historyDao.searchHistory(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addHistoryItem(history: History) {
        historyDao.insertHistoryItem(history.toEntity())
    }

    override suspend fun addHistoryItems(history: List<History>) {
        historyDao.insertHistoryItems(history.map { it.toEntity() })
    }

    override suspend fun updateHistoryItem(history: History) {
        historyDao.updateHistoryItem(history.toEntity())
    }

    override suspend fun deleteHistoryItem(history: History) {
        historyDao.deleteHistoryItem(history.toEntity())
    }

    override suspend fun clearHistoryOlderThan(timestamp: Long) {
        historyDao.deleteHistoryOlderThan(timestamp)
    }

    override suspend fun clearAllHistory() {
        historyDao.clearAllHistory()
    }

    private fun HistoryEntity.toDomain(): History {
        return History(
            id = id,
            url = url,
            title = title,
            visitTime = visitTime,
            visitDuration = visitDuration
        )
    }

    private fun History.toEntity(): HistoryEntity {
        return HistoryEntity(
            id = id,
            url = url,
            title = title,
            visitTime = visitTime,
            visitDuration = visitDuration
        )
    }
}
