package com.zbrowser.app

import android.content.SharedPreferences
import android.webkit.WebView

/**
 * Popup blocker that controls whether JavaScript window.open() calls are allowed.
 * When enabled, programmatic popups (window.open without user gesture) are blocked,
 * but user-initiated clicks (target="_blank" with user gesture) still open new tabs.
 *
 * BUG-06 FIX: shouldBlockPopup now accepts isUserGesture parameter so that
 * user-initiated clicks can open new tabs even when the popup blocker is enabled.
 * Previously, ALL new windows were blocked, including legitimate user clicks.
 *
 * The blocker setting is persisted in SharedPreferences so it survives app restarts.
 */
class PopupBlocker(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_POPUP_BLOCKER_ENABLED = "popup_blocker_enabled"
        const val DEFAULT_ENABLED = true
    }

    /** Whether the popup blocker is currently active */
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_POPUP_BLOCKER_ENABLED, DEFAULT_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_POPUP_BLOCKER_ENABLED, value).apply()

    /**
     * Apply popup blocker settings to a WebView.
     * When blocker is ON: javaScriptCanOpenWindowsAutomatically = false
     * When blocker is OFF: javaScriptCanOpenWindowsAutomatically = true
     */
    fun applyToWebView(webView: WebView) {
        webView.settings.javaScriptCanOpenWindowsAutomatically = !isEnabled
    }

    /**
     * Check if a new window request should be blocked.
     *
     * BUG-06 FIX: Now considers isUserGesture. When the popup blocker is enabled:
     * - Programmatic popups (isUserGesture=false) are ALWAYS blocked
     * - User-initiated clicks (isUserGesture=true) are ALLOWED through
     *
     * This matches the expected browser behavior described in the class comment.
     */
    fun shouldBlockPopup(isUserGesture: Boolean = false): Boolean {
        // If popup blocker is off, allow everything
        if (!isEnabled) return false
        // If user initiated the action (clicked a link), allow it
        if (isUserGesture) return false
        // Block programmatic popups
        return true
    }
}
