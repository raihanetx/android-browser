package com.technova.browser.data.repository

import com.technova.browser.data.model.Tab
import kotlinx.coroutines.flow.Flow

interface TabRepository {
    fun getActiveTab(): Flow<Tab?>
    fun getAllTabs(): Flow<List<Tab>>
    fun getNormalTabs(): Flow<List<Tab>>
    fun getIncognitoTabs(): Flow<List<Tab>>
    fun getTabById(tabId: String): Flow<Tab?>
    suspend fun addTab(tab: Tab)
    suspend fun addTabs(tabs: List<Tab>)
    suspend fun updateTab(tab: Tab)
    suspend fun deleteTab(tab: Tab)
    suspend fun deleteTabById(tabId: String)
    suspend fun clearIncognitoTabs()
    suspend fun setActiveTab(tabId: String)
    suspend fun clearAllTabs()
    suspend fun getTabCount(): Int
}
