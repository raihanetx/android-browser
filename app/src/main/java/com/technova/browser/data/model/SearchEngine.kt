package com.technova.browser.data.model

enum class SearchEngine(val displayName: String, val searchUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    YAHOO("Yahoo", "https://search.yahoo.com/search?p=%s");

    companion object {
        fun fromDisplayName(name: String): SearchEngine? {
            return values().find { it.displayName.equals(name, ignoreCase = true) }
        }
    }
}
