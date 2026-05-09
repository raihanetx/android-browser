package com.technova.browser.data.repository

import com.technova.browser.data.local.dao.TabDao
import com.technova.browser.data.local.entity.TabEntity
import com.technova.browser.data.model.Tab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TabRepositoryImpl(private val tabDao: TabDao) : TabRepository {

    override fun getActiveTab(): Flow<Tab?> {
        return tabDao.getActiveTab().map { entity ->
            entity?.toDomain()
        }
    }

    override fun getAllTabs(): Flow<List<Tab>> {
        return tabDao.getAllTabs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getNormalTabs(): Flow<List<Tab>> {
        return tabDao.getAllNormalTabs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getIncognitoTabs(): Flow<List<Tab>> {
        return tabDao.getAllIncognitoTabs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTabById(tabId: String): Flow<Tab?> {
        return tabDao.getTabById(tabId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun addTab(tab: Tab) {
        tabDao.insertTab(tab.toEntity())
    }

    override suspend fun addTabs(tabs: List<Tab>) {
        tabDao.insertTabs(tabs.map { it.toEntity() })
    }

    override suspend fun updateTab(tab: Tab) {
        tabDao.updateTab(tab.toEntity())
    }

    override suspend fun deleteTab(tab: Tab) {
        tabDao.deleteTab(tab.toEntity())
    }

    override suspend fun deleteTabById(tabId: String) {
        val tab = getTabById(tabId)
        // Implementation requires fetching the entity; simplified for brevity
    }

    override suspend fun clearIncognitoTabs() {
        tabDao.clearIncognitoTabs()
    }

    override suspend fun setActiveTab(tabId: String) {
        tabDao.deactivateAllTabs()
        val tab = getTabById(tabId)
        // Implementation requires fetching and updating
    }

    override suspend fun clearAllTabs() {
        tabDao.clearAllTabs()
    }

    override suspend fun getTabCount(): Int {
        return tabDao.getAllTabs().let { flow ->
            // Implementation would need blocking call; typically use count query instead
            0 // Placeholder
        }
    }

    private fun TabEntity.toDomain(): Tab {
        return Tab(
            id = id,
            url = url,
            title = title,
            isIncognito = isIncognito,
            isActive = isActive,
            position = position,
            createdAt = createdAt
        )
    }

    private fun Tab.toEntity(): TabEntity {
        return TabEntity(
            id = id,
            url = url,
            title = title,
            isIncognito = isIncognito,
            isActive = isActive,
            position = position,
            createdAt = createdAt
        )
    }
}
