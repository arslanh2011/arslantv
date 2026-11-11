package com.arslan.tv.player

/**
 * Resolution ladder offered in the player overlay.
 *
 * Every entry is a *ceiling*: if the channel does not publish the chosen
 * resolution, or the connection cannot sustain it, adaptive streaming walks
 * down the ladder on its own rather than stalling. It will not walk below
 * [FLOOR_HEIGHT] though - a picture that soft is worse than a moment of
 * buffering on a TV-sized screen - and it climbs back up as soon as there is
 * room. [FHD_1080] is the default, so a channel that publishes Full HD is
 * watched in Full HD without anyone touching a setting.
 */
enum class Quality(
    val label: String,
    val subtitle: String,
    val maxHeight: Int,
) {
    AUTO("Auto", "Climbs to 1080p when the connection allows", 1080),
    FHD_1080("1080p", "Recommended - Full HD, never drops below 480p", 1080),
    HD_720("720p", "Caps at HD, never drops below 480p", 720),
    SD_480("480p", "Data saver", 480);

    companion object {
        val DEFAULT = FHD_1080

        /**
         * The lowest rung adaptive streaming is allowed to pick. Honoured only
         * where the channel actually publishes something this big; a feed whose
         * best is 360p still plays at 360p rather than failing.
         */
        const val FLOOR_HEIGHT = 480

        fun fromName(name: String?): Quality =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
