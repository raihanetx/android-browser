package com.technova.browser.data.repository

import com.technova.browser.data.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getAllBookmarks(): Flow<List<Bookmark>>
    fun getBookmarksByFolder(folder: String): Flow<List<Bookmark>>
    fun getBookmarkById(id: Long): Flow<Bookmark?>
    fun searchBookmarks(query: String): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark): Long
    suspend fun updateBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark)
    suspend fun deleteBookmarkById(id: Long)
    suspend fun clearAllBookmarks()
}
