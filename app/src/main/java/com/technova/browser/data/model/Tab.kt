package com.technova.browser.data.model

data class Tab(
    val id: String,
    val url: String,
    val title: String,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val position: Int,
    val createdAt: Long = System.currentTimeMillis()
)
