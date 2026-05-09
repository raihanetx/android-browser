package com.technova.browser.util

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest

data class WebViewState(
    val currentUrl: String = "https://www.google.com",
    val title: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: WebViewError? = null
)

data class WebViewError(
    val url: String?,
    val errorCode: Int,
    val description: String?
) {
    constructor(request: WebResourceRequest?, error: WebResourceError?) : this(
        url = request?.url?.toString(),
        errorCode = error?.errorCode ?: -1,
        description = error?.description?.toString()
    )
}
