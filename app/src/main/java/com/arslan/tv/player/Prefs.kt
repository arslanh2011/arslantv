package com.arslan.tv.player

import android.content.Context

/** Small SharedPreferences wrapper - nothing here justifies a database. */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("arslan_tv", Context.MODE_PRIVATE)

    var quality: Quality
        get() = Quality.fromName(prefs.getString(KEY_QUALITY, null))
        set(value) = prefs.edit().putString(KEY_QUALITY, value.name).apply()

    var adBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_AD_BLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_AD_BLOCK, value).apply()

    /** Pre-buffer the channel the user is hovering over on the home screen. */
    var preloadEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRELOAD, true)
        set(value) = prefs.edit().putBoolean(KEY_PRELOAD, value).apply()

    var pictureMode: PictureMode
        get() = PictureMode.fromName(prefs.getString(KEY_PICTURE, null))
        set(value) = prefs.edit().putString(KEY_PICTURE, value.name).apply()

    /**
     * Overscan compensation in dp, per edge. TVs crop by different amounts on
     * different sides, so each edge is adjusted on its own.
     */
    var padTop: Int
        get() = read(KEY_PAD_TOP)
        set(value) = write(KEY_PAD_TOP, value)

    var padBottom: Int
        get() = read(KEY_PAD_BOTTOM)
        set(value) = write(KEY_PAD_BOTTOM, value)

    var padStart: Int
        get() = read(KEY_PAD_START)
        set(value) = write(KEY_PAD_START, value)

    var padEnd: Int
        get() = read(KEY_PAD_END)
        set(value) = write(KEY_PAD_END, value)

    private fun read(key: String): Int =
        prefs.getInt(key, 0).coerceIn(0, MAX_OVERSCAN_DP)

    private fun write(key: String, value: Int) =
        prefs.edit().putInt(key, value.coerceIn(0, MAX_OVERSCAN_DP)).apply()

    var lastChannelId: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_CHANNEL, value).apply()

    companion object {
        const val MAX_OVERSCAN_DP = 64
        const val OVERSCAN_STEP_DP = 8

        private const val KEY_PICTURE = "picture_mode"
        private const val KEY_PAD_TOP = "pad_top_dp"
        private const val KEY_PAD_BOTTOM = "pad_bottom_dp"
        private const val KEY_PAD_START = "pad_start_dp"
        private const val KEY_PAD_END = "pad_end_dp"
        private const val KEY_QUALITY = "quality"
        private const val KEY_AD_BLOCK = "ad_block"
        private const val KEY_PRELOAD = "preload"
        private const val KEY_LAST_CHANNEL = "last_channel"
    }
}
