package com.arslan.tv.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a [Channel] into something the player can open.
 *
 * Sources are tried in the order the channel lists them and the first one that
 * actually answers wins, so a broadcaster's own stream going down falls through
 * to their YouTube feed instead of showing an error.
 *
 * Signed manifests (ARY's expire in roughly seven minutes) are cached only
 * briefly and re-minted whenever the player reports the URL went stale.
 */
object StreamResolver {

    private const val TAG = "StreamResolver"
    private const val CACHE_TTL_MS = 90_000L
    private const val TIMEOUT_MS = 8_000
    private const val PROBE_TIMEOUT_MS = 6_000

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * YouTube serves a different page layout to a TV user-agent, and that one
     * carries none of the `playlistVideoRenderer` entries the episode list is
     * parsed from. Desktop UA for youtube.com, TV UA for the broadcasters.
     */
    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private const val PLAYLIST_ITEM = "\"playlistVideoRenderer\":"
    private val VIDEO_ID = Regex(""""videoId":"([A-Za-z0-9_-]{11})"""")
    private val ITEM_TITLE = Regex(""""title":\{"runs":\[\{"text":"((?:[^"\\]|\\.)*)"""")

    private val UPCOMING = Regex(""""isUpcoming":true""")

    private val CANONICAL_VIDEO =
        Regex("""<link rel="canonical" href="https://www\.youtube\.com/watch\?v=([A-Za-z0-9_-]{11})""")

    private class Entry(val url: String, val mintedAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    /**
     * The first source of [channel] that resolves to a working stream.
     *
     * @throws IllegalStateException when every source failed.
     */
    suspend fun resolve(channel: Channel, forceRefresh: Boolean = false): Playback =
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            for ((index, source) in channel.sources.withIndex()) {
                val key = "${channel.id}#$index"
                val result = runCatching { resolveSource(source, key, forceRefresh) }
                result.getOrNull()?.let { url ->
                    if (index > 0) Log.i(TAG, "${channel.id}: fell back to source $index")
                    return@withContext Playback(source, url)
                }
                failures += "${source.type}: ${result.exceptionOrNull()?.message}"
                cache.remove(key)
            }
            error("No working source for ${channel.id} (${failures.joinToString("; ")})")
        }

    /** Drops cached URLs for every source of [channel]. */
    fun invalidate(channel: Channel) {
        channel.sources.indices.forEach { cache.remove("${channel.id}#$it") }
    }

    private fun resolveSource(source: Source, key: String, forceRefresh: Boolean): String =
        when (source.type) {
            StreamType.HLS -> {
                probeManifest(source.url, source.headers)
                source.url
            }

            StreamType.HLS_API -> cached(key, forceRefresh) {
                val body = httpGet(source.url, source.headers)
                val manifest = extractField(body, source.jsonField)
                    ?: error("no '${source.jsonField}' in ${source.url}")
                probeManifest(manifest, source.headers)
                manifest
            }

            StreamType.HLS_SCRAPE -> cached(key, forceRefresh) {
                require(source.pattern.isNotBlank()) { "scrape source has no pattern" }
                val html = httpGet(source.url, source.headers)
                val match = Regex(source.pattern).find(html)?.value
                    ?: error("pattern did not match on ${source.url}")
                val manifest = if (source.decode.equals("base64", ignoreCase = true)) {
                    runCatching {
                        String(Base64.decode(match, Base64.DEFAULT), Charsets.UTF_8).trim()
                    }.getOrElse { error("match on ${source.url} is not base64") }
                } else {
                    match
                }
                require(manifest.startsWith("http")) { "decoded value is not a URL" }
                probeManifest(manifest, source.headers)
                manifest
            }

            StreamType.YOUTUBE -> resolveYouTube(source, key, forceRefresh)

            // Nothing to resolve: the id is the playlist, and the episode list
            // is fetched separately once the user opens it.
            StreamType.YOUTUBE_PLAYLIST -> source.url
        }

    /**
     * The video id the channel is airing now. Falls back to the id baked into
     * channels.json so a lookup hiccup does not drop an otherwise fine source.
     */
    private fun resolveYouTube(source: Source, key: String, forceRefresh: Boolean): String {
        if (source.youtubeChannelId.isBlank()) {
            return source.url.ifBlank { error("youtube source has no video id") }
        }
        return runCatching {
            cached(key, forceRefresh) {
                val html = httpGet(
                    "https://www.youtube.com/channel/${source.youtubeChannelId}/live",
                    emptyMap(),
                    DESKTOP_USER_AGENT,
                )
                // A channel with a stream scheduled but not started still
                // publishes a canonical video on /live. Opening it gives a
                // countdown placeholder, not television, so treat it as
                // nothing and let the next source have a go.
                if (UPCOMING.containsMatchIn(html)) {
                    error("stream has not started yet on ${source.youtubeChannelId}")
                }
                CANONICAL_VIDEO.find(html)?.groupValues?.get(1)
                    ?: error("nothing live on channel ${source.youtubeChannelId}")
            }
        }.getOrElse {
            Log.w(TAG, "Live lookup failed; using the stored id", it)
            source.url.ifBlank { throw it }
        }
    }

    /**
     * The videos in a YouTube playlist, in order.
     *
     * Only the first page is read - roughly a hundred entries - which is more
     * than any of the playlists here need. Thumbnails are derived from the
     * video id rather than parsed.
     */
    suspend fun episodes(playlistId: String): List<Episode> = withContext(Dispatchers.IO) {
        val html = httpGet(
            "https://www.youtube.com/playlist?list=$playlistId",
            emptyMap(),
            DESKTOP_USER_AGENT,
        )
        val out = LinkedHashMap<String, Episode>()
        var index = 0
        while (true) {
            val at = html.indexOf(PLAYLIST_ITEM, index)
            if (at < 0) break
            index = at + PLAYLIST_ITEM.length
            val window = html.substring(index, minOf(index + 2000, html.length))
            val id = VIDEO_ID.find(window)?.groupValues?.get(1) ?: continue
            val title = ITEM_TITLE.find(window)?.groupValues?.get(1).orEmpty()
            out.putIfAbsent(id, Episode(id, unescape(title)))
        }
        if (out.isEmpty()) {
            // Layout changed: fall back to every video id on the page.
            VIDEO_ID.findAll(html).forEach { match ->
                val id = match.groupValues[1]
                out.putIfAbsent(id, Episode(id, ""))
            }
        }
        out.values.toList()
    }

    private fun unescape(raw: String): String = raw
        .replace("\\u0026", "&")
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\n", " ")

    /** Confirms a manifest really answers before we commit the player to it. */
    private fun probeManifest(url: String, headers: Map<String, String>) {
        val connection = open(url, headers, PROBE_TIMEOUT_MS)
        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode}")
            }
            val head = connection.inputStream.bufferedReader().use { reader ->
                CharArray(64).let { buffer ->
                    val read = reader.read(buffer)
                    if (read <= 0) "" else String(buffer, 0, read)
                }
            }
            if (!head.trimStart().startsWith("#EXTM3U")) error("not an HLS manifest")
        } finally {
            connection.disconnect()
        }
    }

    private inline fun cached(key: String, forceRefresh: Boolean, mint: () -> String): String {
        val entry = cache[key]
        if (!forceRefresh && entry != null &&
            System.currentTimeMillis() - entry.mintedAt < CACHE_TTL_MS
        ) {
            return entry.url
        }
        val url = mint()
        cache[key] = Entry(url, System.currentTimeMillis())
        return url
    }

    private fun extractField(body: String, path: String): String? {
        var node = JSONObject(body)
        val parts = path.split('.')
        parts.dropLast(1).forEach { key ->
            node = node.optJSONObject(key) ?: return null
        }
        return node.optString(parts.last()).takeIf { it.isNotBlank() }
    }

    private fun httpGet(
        url: String,
        headers: Map<String, String>,
        userAgent: String = USER_AGENT,
    ): String {
        val connection = open(url, headers, TIMEOUT_MS, userAgent)
        return try {
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode} from $url")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        url: String,
        headers: Map<String, String>,
        timeout: Int,
        userAgent: String = USER_AGENT,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeout
            readTimeout = timeout
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "*/*")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
}
