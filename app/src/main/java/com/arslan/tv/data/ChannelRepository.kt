package com.arslan.tv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Channels come from `assets/channels.json`. A copy dropped at
 * `<filesDir>/channels.json` wins, so the list can be changed on a device over
 * adb without rebuilding the app.
 */
class ChannelRepository(private val context: Context) {

    suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        val override = File(context.filesDir, FILE_NAME)
        val json = runCatching {
            if (override.isFile) override.readText() else null
        }.getOrNull() ?: context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }

        runCatching { parse(json) }
            .onFailure { Log.e(TAG, "channels.json is malformed", it) }
            .getOrDefault(Catalog())
    }

    private fun parse(json: String): Catalog {
        val root = JSONObject(json)
        val channels = parseChannels(root.getJSONArray("channels"))
        return Catalog(channels = channels, categories = parseCategories(root, channels))
    }

    private fun parseChannels(array: JSONArray): List<Channel> {
        val parsed = (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val sources = parseSources(o)
            if (sources.isEmpty()) return@mapNotNull null
            Channel(
                id = id,
                name = o.optString("name", id),
                category = o.optString("category", "Live TV"),
                sources = sources,
                logoUrl = o.optString("logoUrl").takeIf { it.isNotBlank() },
                accent = parseColor(o.optString("accent")),
            )
        }
        // Numbered after the drop-outs are gone, so the numbers run 1..n with
        // no gaps where a malformed entry used to be.
        return parsed.mapIndexed { index, channel -> channel.copy(number = index + 1) }
    }

    /**
     * The optional top-level `categories` array fixes the menu order. Anything
     * a channel declares but the array omits is appended, so adding a channel
     * in a brand-new category still shows up without touching the array.
     */
    private fun parseCategories(root: JSONObject, channels: List<Channel>): List<String> {
        val declared = root.optJSONArray("categories")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).takeIf { it.isNotBlank() }
            }
        }.orEmpty()
        return (declared + channels.map(Channel::category)).distinct()
    }

    /** Accepts a `sources` array, or a single source spelled inline on the channel. */
    private fun parseSources(channel: JSONObject): List<Source> {
        val array: JSONArray? = channel.optJSONArray("sources")
        if (array != null) {
            return (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let(::parseSource)
            }
        }
        return listOfNotNull(parseSource(channel).takeIf { it.url.isNotBlank() })
    }

    private fun parseSource(o: JSONObject): Source {
        val type = when (o.optString("type", "hls").lowercase()) {
            "hls_api", "api" -> StreamType.HLS_API
            "hls_scrape", "scrape" -> StreamType.HLS_SCRAPE
            "youtube", "yt" -> StreamType.YOUTUBE
            "youtube_playlist", "playlist" -> StreamType.YOUTUBE_PLAYLIST
            else -> StreamType.HLS
        }
        return Source(
            type = type,
            url = o.optString("url"),
            jsonField = o.optString("jsonField", "url"),
            pattern = o.optString("pattern"),
            decode = o.optString("decode"),
            youtubeChannelId = o.optString("youtubeChannelId"),
            label = o.optString("label"),
            thumbUrl = o.optString("thumbUrl"),
            headers = o.optJSONObject("headers")?.let { h ->
                h.keys().asSequence().associateWith { k -> h.optString(k) }
            } ?: emptyMap(),
            maxHeight = o.optInt("maxHeight", 1080),
        )
    }

    private fun parseColor(value: String): Int {
        val hex = value.removePrefix("#")
        val parsed = when (hex.length) {
            6 -> runCatching { hex.toLong(16) or 0xFF000000L }.getOrNull()
            8 -> runCatching { hex.toLong(16) }.getOrNull()
            else -> null
        }
        return parsed?.toInt() ?: 0xFF2A2A2E.toInt()
    }

    private companion object {
        const val TAG = "ChannelRepository"
        const val FILE_NAME = "channels.json"
    }
}
