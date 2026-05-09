package com.technova.browser.data.model

import kotlinx.datetime.Instant

data class History(
    val id: Long = 0,
    val url: String,
    val title: String,
    val visitTime: Instant,
    val visitDuration: Long = 0L
)
