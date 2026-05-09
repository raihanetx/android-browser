package com.technova.browser.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val position: Int,
    val createdAt: Long = System.currentTimeMillis()
)
