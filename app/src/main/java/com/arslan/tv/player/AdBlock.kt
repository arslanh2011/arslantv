package com.arslan.tv.player

import java.io.IOException

/**
 * Two layers of ad removal:
 *
 *  1. [isBlockedHost] rejects requests to known ad/tracking networks. The native
 *     player never loads an ad SDK in the first place, so this mostly matters for
 *     the WebView used by YouTube channels.
 *  2. [stripAdBreaks] rewrites HLS media playlists, dropping the segments inside
 *     server-side ad breaks that broadcasters mark with SCTE-35 cue tags.
 */
object AdBlock {

    class BlockedRequestException(url: String) : IOException("Blocked ad request: $url")

    private val BLOCKED_HOSTS = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "imasdk.googleapis.com",
        "2mdn.net",
        "moatads.com",
        "adsafeprotected.com",
        "scorecardresearch.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "innovid.com",
        "springserve.com",
        "spotxchange.com",
        "spotx.tv",
        "teads.tv",
        "smartadserver.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "adform.net",
        "zedo.com",
        "yieldmo.com",
        "sharethrough.com",
        "33across.com",
        "quantserve.com",
        "bidswitch.net",
        "serving-sys.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "vungle.com",
        "chartbeat.com",
        "mgid.com",
        "revcontent.com",
        "propellerads.com",
        "popads.net",
    )

    /**
     * Segment paths that unambiguously belong to an ad pod. Deliberately narrow:
     * a loose pattern such as "/ad/" matches plenty of legitimate CDN paths, and
     * dropping a real segment is worse than showing an ad.
     */
    private val AD_SEGMENT_MARKERS = listOf(
        "/adserving/",
        "/ad-insert",
        "/adbreak",
        "adsegment",
        "/preroll",
        "/midroll",
        "/postroll",
        "_ad_break_",
        "/vast/",
        "/vmap/",
    )

    fun isBlockedHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun isBlockedRequest(url: String): Boolean =
        isBlockedHost(url) || AD_SEGMENT_MARKERS.any { url.contains(it, ignoreCase = true) }

    fun looksLikePlaylist(url: String): Boolean {
        val path = url.substringBefore('?')
        return path.endsWith(".m3u8", ignoreCase = true) ||
            path.endsWith(".m3u", ignoreCase = true)
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return null
        return afterScheme.substringBefore('/')
            .substringBefore(':')
            .substringAfter('@')
            .lowercase()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Removes SCTE-35 ad breaks from an HLS media playlist and splices the
     * remaining content with a discontinuity so the decoder resets cleanly.
     *
     * Playlists with no ad markers are returned untouched, which is the common
     * case and costs one substring scan.
     */
    fun stripAdBreaks(playlist: String): String {
        if (!hasAdMarkers(playlist)) return playlist

        val out = StringBuilder(playlist.length)
        // Tags that describe the *next* segment and must be dropped along with it.
        val pendingSegmentTags = ArrayList<String>(4)
        var insideAdBreak = false
        var spliceNeeded = false

        for (rawLine in playlist.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                isCueOut(line) -> {
                    insideAdBreak = true
                    pendingSegmentTags.clear()
                    continue
                }

                isCueIn(line) -> {
                    if (insideAdBreak) spliceNeeded = true
                    insideAdBreak = false
                    pendingSegmentTags.clear()
                    continue
                }

                line.startsWith("#") -> {
                    if (insideAdBreak) continue
                    if (isSegmentScopedTag(line)) pendingSegmentTags += line else out.appendLine(line)
                    continue
                }
            }

            // A bare line is a segment (or variant) URI.
            if (insideAdBreak || isBlockedRequest(line)) {
                pendingSegmentTags.clear()
                continue
            }
            if (spliceNeeded) {
                out.appendLine("#EXT-X-DISCONTINUITY")
                spliceNeeded = false
            }
            pendingSegmentTags.forEach(out::appendLine)
            pendingSegmentTags.clear()
            out.appendLine(line)
        }
        return out.toString()
    }

    private fun hasAdMarkers(playlist: String): Boolean =
        playlist.contains("CUE-OUT") ||
            playlist.contains("CUE-IN") ||
            playlist.contains("SCTE35") ||
            playlist.contains("SCTE-35") ||
            AD_SEGMENT_MARKERS.any { playlist.contains(it, ignoreCase = true) }

    private fun isCueOut(line: String): Boolean =
        line.startsWith("#EXT-X-CUE-OUT") ||
            line.startsWith("#EXT-OATCLS-SCTE35") ||
            (line.startsWith("#EXT-X-DATERANGE") && line.contains("SCTE35-OUT"))

    private fun isCueIn(line: String): Boolean =
        line.startsWith("#EXT-X-CUE-IN") ||
            (line.startsWith("#EXT-X-DATERANGE") && line.contains("SCTE35-IN"))

    private fun isSegmentScopedTag(line: String): Boolean =
        line.startsWith("#EXTINF") ||
            line.startsWith("#EXT-X-BYTERANGE") ||
            line.startsWith("#EXT-X-PROGRAM-DATE-TIME")
}
