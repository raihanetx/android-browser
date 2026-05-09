package com.technova.browser.data.model

import kotlinx.datetime.Instant

data class Bookmark(
    val id: Long = 0,
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val folder: String = "Uncategorized",
    val createdAt: Instant,
    val lastVisited: Instant? = null,
    val visitCount: Int = 0
)
