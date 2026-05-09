package com.technova.browser.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val folder: String = "Uncategorized",
    val createdAt: Instant,
    val lastVisited: Instant? = null,
    val visitCount: Int = 0
)
