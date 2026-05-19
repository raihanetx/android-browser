package com.zbrowser.app

import android.content.SharedPreferences
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.regex.Pattern

/**
 * High-performance ad and tracker blocker.
 *
 * v4.1 FIX: Removed unused Context parameter (BUG-15).
 *
 * v4.0 OPTIMIZATIONS:
 * - HashSet for O(1) domain lookups (was O(n) linear scan)
 * - Pre-compiled regex for ad-path patterns (was string contains per pattern)
 * - CSS injection uses proper single-line string concatenation (was JS syntax error)
 * - Host-only check avoids full-URL scanning for most requests
 *
 * Blocks both the request AND hides ad containers via CSS injection
 * so there's no empty space / click-to-load issue.
 */
class AdBlocker(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_AD_BLOCKER_ENABLED = "ad_blocker_enabled"
        const val DEFAULT_ENABLED = true

        /**
         * Major ad / tracker domains — stored in a HashSet for O(1) .contains().
         * Only the host is checked against this set, which is fast and
         * avoids false positives on legitimate content.
         */
        val AD_HOSTS = HashSet<String>(listOf(
            // Major ad networks
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com",
            "adnxs.com", "adsrvr.org", "adform.net", "adroll.com",
            "amazon-adsystem.com", "ads.amazon.com",
            // Programmatic exchanges
            "rubiconproject.com", "pubmatic.com", "openx.net", "indexww.com",
            "casalemedia.com", "criteo.com", "criteo.net", "taboola.com",
            "outbrain.com", "mgid.com", "revcontent.com",
            // Tracking & analytics
            "scorecardresearch.com", "quantserve.com", "moatads.com",
            "chartbeat.com", "hotjar.com", "mixpanel.com", "segment.io",
            "amplitude.com", "fullstory.com", "newrelic.com", "nr-data.net",
            // Malvertising
            "adsterra.com", "popads.net", "propellerads.com", "hilltopads.com",
            "clickadu.com", "propeller.pw", "ad-maven.com"
        ))

        /**
         * Pre-compiled regex for common ad-serving URL path patterns.
         * A single regex match is faster than iterating 10+ string .contains() calls.
         */
        val AD_PATH_PATTERN = Pattern.compile(
            """(?:/ads/|/ad/|/adv/|/banner/|/banners/|/adserver/""" +
            """|/advertising/|/advert/|/tracking/|/tracker/|/pixel\.|/beacon\.)""",
            Pattern.CASE_INSENSITIVE
        )

        /**
         * CSS to hide common ad containers — lazily allocated once.
         *
         * v4.0 FIX: Replaced broken single-quoted string with newlines
         * (JS syntax error) with proper single-line string concatenation.
         * Each JS string literal is on one line with no embedded newlines.
         */
        const val AD_HIDE_CSS = "(function(){" +
            "var s=document.createElement('style');" +
            "s.textContent=" +
            "'[id^=\"google_ads\"],[id^=\"div-gpt-ad\"],[class*=\"ad-container\"]," +
            "[class*=\"ad-wrapper\"],[class*=\"ad-banner\"],[class*=\"sponsored\"]," +
            "[class*=\"taboola\"],[class*=\"outbrain\"],[class*=\"recommendation\"]," +
            "ins.adsbygoogle,div[id^=\"taboola-\"],div[class^=\"taboola-\"]," +
            "iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"]," +
            "iframe[src*=\"amazon-adsystem\"],iframe[src*=\"facebook.net/tr\"]," +
            "div[data-ad],div[data-ad-slot],div[data-adunit]," +
            "[class*=\"sticky-ad\"],[class*=\"floating-ad\"],[id*=\"push-notification\"]" +
            "{display:none!important;height:0!important;overflow:hidden!important}';" +
            "document.head.appendChild(s);" +
            "})()"
    }

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_AD_BLOCKER_ENABLED, DEFAULT_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_AD_BLOCKER_ENABLED, value).apply()

    /**
     * Check if a resource request should be blocked.
     *
     * Fast path: O(1) host lookup in HashSet.
     * Slow path: single regex match on URL path for ad-path patterns.
     * Only sub-resource requests are ever blocked (never the main frame).
     */
    fun shouldBlock(request: WebResourceRequest): Boolean {
        if (!isEnabled) return false
        if (request.isForMainFrame) return false

        // Fast O(1) host check
        val host = request.url.host?.lowercase() ?: ""
        if (host.isNotEmpty()) {
            // Check exact host or parent domain
            if (AD_HOSTS.contains(host)) return true
            // Check if any ad host is a suffix of the request host
            // (e.g. ads.doubleclick.net → doubleclick.net match)
            val parts = host.split(".")
            if (parts.size > 2) {
                val parentDomain = parts.takeLast(2).joinToString(".")
                if (AD_HOSTS.contains(parentDomain)) return true
            }
        }

        // Single regex check on URL path for ad-path patterns
        val path = request.url.path ?: ""
        if (path.isNotEmpty() && AD_PATH_PATTERN.matcher(path).find()) {
            return true
        }

        return false
    }

    fun createBlockedResponse(): WebResourceResponse {
        // Create a NEW ByteArrayInputStream each time — the stream position
        // is mutable, so sharing a single instance across threads corrupts data
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
    }
}
