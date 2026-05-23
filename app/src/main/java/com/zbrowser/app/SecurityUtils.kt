package com.zbrowser.app

import android.net.Uri
import java.net.URLDecoder

/**
 * Security utility methods for the browser engine.
 * Provides HTML escaping to prevent XSS, URL validation, and safe intent handling.
 */
object SecurityUtils {

    /**
     * Escape HTML special characters to prevent XSS injection.
     * Must be used whenever user-controlled data is embedded in HTML (e.g., error pages).
     */
    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("/", "&#x2F;")
    }

    /**
     * Validate that a URL is safe to load in the WebView.
     * Blocks dangerous schemes like file://, javascript:, data: (except our own error pages).
     */
    fun isUrlSafe(url: String): Boolean {
        if (url.isEmpty()) return false
        val scheme = url.substringBefore(":", "").lowercase()
        return when (scheme) {
            "http", "https" -> true
            "tel", "mailto", "sms", "geo" -> true  // Handled externally
            else -> false  // Block file://, javascript:, data:, intent://, etc.
        }
    }

    /**
     * Validate that an intent URI is safe to process.
     * Only allows http/https fallback URLs from intent:// schemes.
     * Prevents package enumeration and arbitrary app launching.
     *
     * Android intent:// URIs have the format:
     *   intent://host#Intent;S.browser_fallback_url=https%3A%2F%2Fexample.com;end
     * Uri.parse() doesn't handle the #Intent;...;end fragment, so we parse manually.
     */
    fun extractSafeFallbackFromIntent(intentUrl: String): String? {
        if (!intentUrl.lowercase().startsWith("intent://")) return null
        return try {
            val fragmentStart = intentUrl.indexOf('#')
            if (fragmentStart < 0) return null
            val fragment = intentUrl.substring(fragmentStart + 1)
            // Fragment looks like: Intent;key=value;S.browser_fallback_url=https%3A...;end
            val params = fragment.split(";")
            for (param in params) {
                val trimmed = param.trim()
                if (trimmed.startsWith("S.browser_fallback_url=", ignoreCase = true)) {
                    val encoded = trimmed.substringAfter("=")
                    val fallback = URLDecoder.decode(encoded, "UTF-8")
                    if (fallback.startsWith("http://") || fallback.startsWith("https://")) {
                        return fallback
                    }
                    return null
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Build a safe HTML error page with properly escaped content.
     */
    fun buildErrorPage(errorMsg: String, pageUrl: String): String {
        val safeError = escapeHtml(errorMsg)
        val safeUrl = escapeHtml(pageUrl)
        return """<!DOCTYPE html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
                body{display:flex;justify-content:center;align-items:center;height:100vh;
                font-family:sans-serif;text-align:center;color:#666;margin:0;padding:20px;box-sizing:border-box}
                h2{color:#333;margin-bottom:8px}p{margin:4px 0}a{color:#1A73E8}
            </style></head>
            <body><div><h2>Page Load Error</h2><p>$safeError</p>
            <p><a href="$safeUrl">Try Again</a></p></div></body></html>"""
    }
}
