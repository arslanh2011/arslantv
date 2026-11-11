package com.arslan.tv.player

/** How the video is mapped onto the screen. */
enum class PictureMode(val label: String, val description: String) {
    /** Whole picture visible, black bars where the shapes differ. */
    FIT("Fit to screen", "Shows the whole picture, adding bars if needed"),

    /** Fills the screen by cropping the overflowing edges. */
    FILL("Fill screen", "Crops the edges so no bars are shown"),

    /** Ignores the source shape entirely. */
    STRETCH("Stretch", "Stretches the picture to the screen, distorting it");

    companion object {
        val DEFAULT = FIT

        fun fromName(name: String?): PictureMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
