package com.zbrowser.app

import android.content.Intent
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Custom WebChromeClient for the browser.
 * Handles progress updates, new window creation (with popup blocking),
 * geolocation permissions, file chooser, and download detection.
 *
 * BUG-04 FIX: Replaced static pendingFilePathCallback with a delegate pattern
 * that uses the Activity's Activity Result API launcher. The static callback
 * leaked the ValueCallback and failed silently on Activity recreation.
 *
 * BUG-06 FIX: onCreateWindow now receives isUserGesture and passes it to
 * PopupBlocker so user-initiated clicks can open new tabs even when the
 * popup blocker is enabled.
 */
class BrowserWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onNewWindowRequested: (android.os.Message?) -> Unit,
    private val popupBlocker: PopupBlocker,
    private val permissionManager: PermissionManager,
    private val onPopupBlocked: () -> Unit = {},
    private val onFileChooserRequested: ((Intent, ValueCallback<Array<Uri>>?) -> Boolean)? = null
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    /**
     * FEATURE 1: Popup Blocker - block window.open() when enabled.
     *
     * BUG-06 FIX: Now passes isUserGesture to shouldBlockPopup() so that
     * user-initiated clicks (e.g., <a target="_blank">) can open new tabs
     * even when the popup blocker is enabled. Only programmatic popups
     * (window.open without user gesture) are blocked.
     */
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean {
        if (popupBlocker.shouldBlockPopup(isUserGesture)) {
            // Block the popup and notify the user
            onPopupBlocked()
            return false  // Returning false prevents the window from being created
        }
        onNewWindowRequested(resultMsg)
        return true
    }

    /**
     * FEATURE 10: Runtime Permissions - handle geolocation requests from web content.
     */
    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (origin != null && callback != null) {
            permissionManager.onGeolocationPermissionsShowPrompt(origin, callback)
        }
    }

    /**
     * FEATURE 10: Runtime Permissions - handle camera/mic requests from web content.
     */
    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request != null) {
            permissionManager.onPermissionRequest(request)
        }
    }

    /**
     * Handle <input type="file"> file chooser requests.
     *
     * BUG-04 FIX: Instead of using deprecated startActivityForResult with a
     * static callback, the Activity provides a launcher function via
     * onFileChooserRequested. This uses the Activity Result API which
     * correctly handles Activity recreation (rotation).
     */
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        val intent = fileChooserParams?.createIntent()
        return try {
            if (intent != null && onFileChooserRequested != null) {
                onFileChooserRequested(intent, filePathCallback)
            } else {
                filePathCallback?.onReceiveValue(null)
                false
            }
        } catch (_: Exception) {
            filePathCallback?.onReceiveValue(null)
            false
        }
    }

    companion object {
        /**
         * BUG-04 FIX: Still kept as static for backward compatibility with the
         * fileChooserLauncher in MainActivity, but now managed by the Activity
         * Result API instead of deprecated onActivityResult.
         */
        var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null
    }
}
