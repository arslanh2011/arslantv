package com.arslan.tv.data

/** How a source's playable URL is obtained. */
enum class StreamType {
    /** [Source.url] is already a playable HLS manifest. */
    HLS,

    /** [Source.url] returns JSON holding a short-lived signed manifest URL. */
    HLS_API,

    /**
     * [Source.url] is a web page with the manifest embedded in its HTML,
     * matched by [Source.pattern]. Several broadcasters sign the manifest
     * server-side and render it straight into the page.
     */
    HLS_SCRAPE,

    /** [Source.url] is a YouTube video id, played through the IFrame player. */
    YOUTUBE,

    /**
     * [Source.url] is a YouTube playlist id. Opening it lists the episodes
     * instead of playing immediately.
     */
    YOUTUBE_PLAYLIST,
}

/** One video inside a [StreamType.YOUTUBE_PLAYLIST]. */
data class Episode(val videoId: String, val title: String) {
    /** YouTube derives thumbnail URLs from the id, so none needs parsing. */
    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}

/**
 * One way to watch a channel. A channel lists these in preference order, so a
 * broadcaster's own stream is tried first and their YouTube feed picks up the
 * slack when it is down.
 */
data class Source(
    val type: StreamType,
    val url: String,
    /** Field holding the manifest URL, for [StreamType.HLS_API]. Dots descend into objects. */
    val jsonField: String = "url",
    /** Regex matching the manifest URL in the page, for [StreamType.HLS_SCRAPE]. */
    val pattern: String = "",
    /**
     * Set to `base64` when the scraped match is an encoded URL rather than the
     * URL itself - some players take their source as a base64 path segment,
     * so the plain manifest never appears in the HTML to match on.
     */
    val decode: String = "",
    /**
     * Owning YouTube channel. A broadcaster restarting their stream gets a new
     * video id, so when this is set the current live video is looked up at play
     * time and [url] is only the fallback.
     */
    val youtubeChannelId: String = "",
    /** Shown on the card for a playlist a channel offers. */
    val label: String = "",
    /** Artwork for that card. Falls back to the channel logo when blank. */
    val thumbUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    /** Highest vertical resolution this source actually publishes. */
    val maxHeight: Int = 1080,
) {
    val isYouTube: Boolean get() = type == StreamType.YOUTUBE
    val isPlaylist: Boolean get() = type == StreamType.YOUTUBE_PLAYLIST
}

data class Channel(
    val id: String,
    val name: String,
    val category: String,
    val sources: List<Source>,
    val logoUrl: String? = null,
    /** Brand colour, used for the card and billboard gradients. */
    val accent: Int = 0xFF2A2A2E.toInt(),
    /**
     * Position on the remote, counted across the whole list rather than within
     * a category, so a channel keeps its number whichever menu entry is open.
     */
    val number: Int = 0,
) {
    /** The best resolution any of this channel's sources can offer. */
    val maxHeight: Int get() = sources.maxOfOrNull { it.maxHeight } ?: 1080

    /** Playlists this channel offers, in the order they are listed. */
    val playlists: List<Source> get() = sources.filter { it.isPlaylist }

    /**
     * What a shelf puts on its picker: playlists, and single videos that carry
     * a title of their own. A channel's live feed is never labelled, which is
     * what keeps it out of this list and in [liveSource] instead.
     */
    val shelfItems: List<Source> get() = sources.filter { it.label.isNotBlank() }

    /** The channel's own stream, if it has one. */
    val liveSource: Source? get() = sources.firstOrNull { !it.isPlaylist && it.label.isBlank() }

    /**
     * Whether the channel is airing something right now, as opposed to being
     * nothing but a shelf. Zapping only ever lands on one of these.
     */
    val isLive: Boolean get() = liveSource != null
}

/**
 * The whole channel list plus the order its categories are shown in.
 *
 * The order is declared in `channels.json` rather than inferred from the
 * channels, so a category can sit at the bottom of the menu - or be listed at
 * all - while it still has no channels in it.
 */
data class Catalog(
    val channels: List<Channel> = emptyList(),
    val categories: List<String> = emptyList(),
)

/** A source that has been resolved to something the player can open. */
data class Playback(val source: Source, val url: String) {
    val isYouTube: Boolean get() = source.isYouTube
    val isPlaylist: Boolean get() = source.isPlaylist
}
