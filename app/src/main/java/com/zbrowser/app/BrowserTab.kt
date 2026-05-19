package com.zbrowser.app

import android.webkit.WebView

/**
 * Represents a single browser tab.
 * Holds a direct reference to its WebView, eliminating fragile index-based coupling.
 * The WebView is nullable to support state preservation during tab lifecycle transitions
 * and aggressive memory trimming (WebViewPool release).
 *
 * v4.0 FIX: Added needsWebViewRecreation flag — set when onTrimMemory ejects the
 * WebView so the Activity can recreate it when the user switches back to this tab.
 *
 * BUG-17 FIX: Changed from data class to regular class. A data class with mutable
 * var properties is problematic because equals(), hashCode(), and copy() are based
 * on ALL properties including webView, url, title. If any property changes,
 * hashCode changes, causing issues in hashed collections. Since we never need
 * automatic equals/hashCode/copy for tabs (we use ID-based identity), a regular
 * class is more appropriate and safer.
 */
class BrowserTab(
    val id: Int,
    var title: String = "New Tab",
    var url: String = "",
    var webView: WebView? = null,
    var isDesktopMode: Boolean = false,
    var needsWebViewRecreation: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrowserTab) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }
}
