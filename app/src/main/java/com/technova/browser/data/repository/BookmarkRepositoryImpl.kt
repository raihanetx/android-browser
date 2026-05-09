package com.technova.browser.data.repository

import com.technova.browser.data.local.dao.BookmarkDao
import com.technova.browser.data.local.entity.BookmarkEntity
import com.technova.browser.data.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class BookmarkRepositoryImpl(private val bookmarkDao: BookmarkDao) : BookmarkRepository {

    override fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getBookmarksByFolder(folder: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByFolder(folder).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getBookmarkById(id: Long): Flow<Bookmark?> {
        return bookmarkDao.getBookmarkById(id).map { entity ->
            entity?.toDomain()
        }
    }

    override fun searchBookmarks(query: String): Flow<List<Bookmark>> {
        return bookmarkDao.searchBookmarks(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addBookmark(bookmark: Bookmark): Long {
        return bookmarkDao.insertBookmark(bookmark.toEntity())
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.updateBookmark(bookmark.toEntity())
    }

    override suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark.toEntity())
    }

    override suspend fun deleteBookmarkById(id: Long) {
        bookmarkDao.deleteBookmarkById(id)
    }

    override suspend fun clearAllBookmarks() {
        bookmarkDao.deleteAllBookmarks()
    }

    private fun BookmarkEntity.toDomain(): Bookmark {
        return Bookmark(
            id = id,
            url = url,
            title = title,
            faviconUrl = faviconUrl,
            folder = folder,
            createdAt = createdAt,
            lastVisited = lastVisited,
            visitCount = visitCount
        )
    }

    private fun Bookmark.toEntity(): BookmarkEntity {
        return BookmarkEntity(
            id = id,
            url = url,
            title = title,
            faviconUrl = faviconUrl,
            folder = folder,
            createdAt = createdAt,
            lastVisited = lastVisited ?: Clock.System.now(),
            visitCount = visitCount
        )
    }
}
