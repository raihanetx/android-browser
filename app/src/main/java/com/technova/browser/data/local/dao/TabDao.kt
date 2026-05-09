package com.technova.browser.data.local.dao

import androidx.room.*
import com.technova.browser.data.local.entity.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    fun getActiveTab(): Flow<TabEntity?>

    @Query("SELECT * FROM tabs WHERE isIncognito = 0 ORDER BY position ASC")
    fun getAllNormalTabs(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE isIncognito = 1 ORDER BY position ASC")
    fun getAllIncognitoTabs(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun getAllTabs(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE id = :tabId")
    fun getTabById(tabId: String): Flow<TabEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<TabEntity>)

    @Update
    suspend fun updateTab(tab: TabEntity)

    @Delete
    suspend fun deleteTab(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE isIncognito = 1")
    suspend fun clearIncognitoTabs()

    @Query("UPDATE tabs SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllTabs()

    @Query("DELETE FROM tabs")
    suspend fun clearAllTabs()
}
