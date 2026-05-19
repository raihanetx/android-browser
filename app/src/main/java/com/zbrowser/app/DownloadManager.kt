package com.zbrowser.app

import android.app.DownloadManager as SystemDownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Handles file downloads from the browser.
 * Uses Android's system DownloadManager for reliable, background-capable downloads
 * with progress notifications and automatic resume on network change.
 *
 * Uses applicationContext to avoid memory leaks (injected as @Singleton).
 */
class DownloadManagerHelper(context: Context) {

    private val context: Context = context.applicationContext

    private val downloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as SystemDownloadManager
    }

    /**
     * Create a DownloadListener that can be attached to any WebView.
     * Handles URL detection, filename extraction, and download submission.
     */
    val webViewDownloadListener = DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
        val filename = extractFilename(url, contentDisposition, mimetype)
        val mimeType = mimetype ?: guessMimeTypeFromUrl(url)

        if (filename != null && isSafeToDownload(url)) {
            startDownload(url, filename, mimeType, userAgent)
            Toast.makeText(context, context.getString(R.string.download_started, filename), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Start a download using the system DownloadManager.
     * Includes cookies from the WebView so authenticated downloads work.
     */
    private fun startDownload(url: String, filename: String, mimeType: String, userAgent: String) {
        val request = SystemDownloadManager.Request(Uri.parse(url)).apply {
            setTitle(filename)
            setDescription(context.getString(R.string.app_name))
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent)

            // Include cookies so authenticated downloads don't redirect to login
            val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrEmpty()) {
                addRequestHeader("Cookie", cookies)
            }

            setNotificationVisibility(
                SystemDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        }

        downloadManager.enqueue(request)
    }

    /**
     * Extract a safe filename from the download URL, content disposition, or MIME type.
     */
    private fun extractFilename(url: String, contentDisposition: String?, mimeType: String?): String? {
        // Try to extract from Content-Disposition header first
        if (!contentDisposition.isNullOrEmpty()) {
            val filename = parseContentDisposition(contentDisposition)
            if (!filename.isNullOrEmpty()) return sanitizeFilename(filename)
        }

        // Fall back to URL path
        val path = Uri.parse(url)?.path
        if (!path.isNullOrEmpty()) {
            val lastSegment = path.substringAfterLast('/')
            if (lastSegment.isNotEmpty() && lastSegment.contains(".")) {
                return sanitizeFilename(lastSegment)
            }
        }

        // Generate a name based on timestamp
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        return "download_${System.currentTimeMillis()}.$extension"
    }

    /**
     * Parse Content-Disposition header to extract filename.
     * Handles both filename="name" and filename*=UTF-8''name formats.
     */
    private fun parseContentDisposition(disposition: String): String? {
        // Try filename*=UTF-8'' format first (RFC 5987)
        val utf8Pattern = Regex("""filename\*\s*=\s*UTF-8''(.+?)(?:;|$)""", RegexOption.IGNORE_CASE)
        utf8Pattern.find(disposition)?.groupValues?.get(1)?.let { return it }

        // Try filename="name" format
        val quotedPattern = Regex("""filename\s*=\s*"(.+?)"""", RegexOption.IGNORE_CASE)
        quotedPattern.find(disposition)?.groupValues?.get(1)?.let { return it }

        // Try filename=name format (unquoted)
        val unquotedPattern = Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
        unquotedPattern.find(disposition)?.groupValues?.get(1)?.trim()?.let { return it }

        return null
    }

    /**
     * Sanitize filename to prevent directory traversal and invalid characters.
     *
     * BUG-02 FIX: Previous implementation used `.replace("..", "")` which could be
     * bypassed by inputs like "....//" → "../" after removal. Now uses regex to
     * remove ALL path separators and directory traversal sequences, then loops
     * until no ".." remains to prevent multi-pass bypass.
     */
    private fun sanitizeFilename(name: String): String {
        // Remove all path separators first (prevents any traversal)
        var sanitized = name
            .replace("/", "_")
            .replace("\\", "_")

        // Remove ".." sequences in a loop until none remain
        // (handles bypass patterns like "....//" → "../" → "..")
        while (sanitized.contains("..")) {
            sanitized = sanitized.replace("..", "")
        }

        // Replace other invalid filesystem characters
        sanitized = sanitized
            .replace(":", "_")
            .replace("|", "_")
            .replace("?", "_")
            .replace("*", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("\"", "_")
            .replace("\u0000", "")  // Null byte

            .take(200)  // Limit filename length

        // Ensure the filename is not empty after sanitization
        return sanitized.ifEmpty { "download" }
    }

    /**
     * Guess MIME type from URL file extension.
     */
    private fun guessMimeTypeFromUrl(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    /**
     * Basic URL safety check before downloading.
     * Blocks dangerous schemes and obviously malicious URLs.
     */
    private fun isSafeToDownload(url: String): Boolean {
        val scheme = url.substringBefore(":", "").lowercase()
        return scheme == "http" || scheme == "https"
    }
}
