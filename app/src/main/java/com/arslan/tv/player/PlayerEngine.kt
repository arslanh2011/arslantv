package com.arslan.tv.player

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.arslan.tv.data.Channel
import com.arslan.tv.data.Playback
import com.arslan.tv.data.Source
import com.arslan.tv.data.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns playback for every HLS channel.
 *
 * One ExoPlayer, created once and reused. An earlier design kept a second
 * player pre-buffering whichever channel was highlighted and promoted it on
 * OK, but a player prepared without a surface configures its video decoder
 * against a placeholder; re-targeting that running decoder at the real
 * SurfaceView afterwards leaves the picture black on many decoders. Warming is
 * done at the URL layer instead - see [prewarm] - which is where most of the
 * start-up latency lives anyway.
 */
@UnstableApi
class PlayerEngine(context: Context, private val prefs: Prefs) {

    private val appContext = context.applicationContext

    /** Shared so bandwidth history survives channel changes. */
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext)
        .setInitialBitrateEstimate(INITIAL_BITRATE_ESTIMATE)
        .setResetOnNetworkTypeChange(true)
        .build()

    private class Slot(val player: ExoPlayer, val selector: DefaultTrackSelector) {
        var channelId: String? = null

        /** Media is loaded but [ExoPlayer.prepare] is waiting for a surface. */
        var awaitingSurface = false
    }

    private var slot: Slot? = null
    private var surfaceView: SurfaceView? = null

    var quality: Quality = prefs.quality
        set(value) {
            field = value
            prefs.quality = value
            slot?.let { applyQuality(it, value) }
        }

    val currentPlayer: ExoPlayer? get() = slot?.player

    /**
     * Resolves [channel] ahead of time so pressing OK does not have to wait for
     * it. For the channels whose manifest is signed or embedded in a web page
     * this is the slow part of starting up - a page fetch plus a probe - and
     * the resolver caches the result for the next 90 seconds.
     */
    suspend fun prewarm(channel: Channel) {
        if (!prefs.preloadEnabled) return
        runCatching { StreamResolver.resolve(channel) }
            .onFailure { Log.w(TAG, "Prewarm failed for ${channel.id}", it) }
    }

    /** Starts [playback] of [channel] and returns the player bound to the screen. */
    suspend fun play(channel: Channel, playback: Playback): ExoPlayer =
        withContext(Dispatchers.Main) {
            val current = slot ?: createSlot().also { slot = it }
            bind(current, channel, playback)
            current.player
        }

    /** Re-resolves the channel, re-minting expired manifests and falling
     *  through to the next source if the current one has gone down. */
    suspend fun reload(channel: Channel): ExoPlayer {
        StreamResolver.invalidate(channel)
        val playback = StreamResolver.resolve(channel, forceRefresh = true)
        check(!playback.isYouTube) { "${channel.id} now resolves to YouTube" }
        return play(channel, playback)
    }

    fun attachSurface(view: SurfaceView) {
        surfaceView = view
        slot?.let { current ->
            current.player.setVideoSurfaceView(view)
            if (current.awaitingSurface) {
                current.awaitingSurface = false
                current.player.prepare()
            }
        }
    }

    fun detachSurface(view: SurfaceView) {
        if (surfaceView === view) {
            slot?.player?.clearVideoSurfaceView(view)
            surfaceView = null
        }
    }

    /**
     * Stops immediately. `stop()` cancels in-flight segment loads rather than
     * draining them, so audio and video cut on the frame Back was pressed.
     */
    fun stopPlayback() {
        slot?.let(::reset)
    }

    fun release() {
        surfaceView = null
        slot?.player?.let {
            it.stop()
            it.release()
        }
        slot = null
    }

    private fun createSlot(): Slot {
        // Tuned to sit high on the ladder: it spends more of the measured
        // bandwidth than the stock 0.7 and needs a shorter good patch before
        // stepping up, so a connection with room for 1080p gets there quickly
        // instead of settling at 720p.
        val selector = DefaultTrackSelector(
            appContext,
            AdaptiveTrackSelection.Factory(
                MIN_DURATION_FOR_QUALITY_INCREASE_MS,
                AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                BANDWIDTH_FRACTION,
            ),
        )
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            // Start on time, not on bytes: a 700 ms head start is enough to begin.
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(appContext)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                repeatMode = Player.REPEAT_MODE_OFF
            }

        val created = Slot(player, selector)
        applyQuality(created, quality)
        return created
    }

    private fun bind(target: Slot, channel: Channel, playback: Playback) {
        target.channelId = channel.id
        applyQuality(target, quality)
        target.player.setMediaSource(buildMediaSource(playback.source, playback.url))
        target.player.playWhenReady = true

        // The decoder must be configured against the real surface from the
        // start, so hold prepare() until there is one to give it.
        val view = surfaceView
        if (view != null) {
            target.player.setVideoSurfaceView(view)
            target.awaitingSurface = false
            target.player.prepare()
        } else {
            target.awaitingSurface = true
        }
    }

    private fun reset(target: Slot) {
        target.channelId = null
        target.awaitingSurface = false
        target.player.playWhenReady = false
        target.player.stop()
        target.player.clearMediaItems()
    }

    private fun applyQuality(target: Slot, quality: Quality) {
        // A ceiling and a floor. Below the ceiling adaptive streaming is free
        // to drop a rung when the manifest or the connection cannot carry the
        // chosen resolution, but it will not go under the floor - it rides out
        // a slow patch on 480p rather than falling to 240p. The floor is
        // relaxed by setExceedVideoConstraintsIfNecessary for a channel whose
        // best rung is lower than that, which would otherwise select nothing.
        val floor = minOf(Quality.FLOOR_HEIGHT, quality.maxHeight)
        target.selector.setParameters(
            target.selector.buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, quality.maxHeight)
                .setMinVideoSize(0, floor)
                .setForceHighestSupportedBitrate(false)
                .setExceedVideoConstraintsIfNecessary(true)
        )
    }

    private fun buildMediaSource(source: Source, url: String): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (source.headers.isNotEmpty()) setDefaultRequestProperties(source.headers)
            }

        val dataSourceFactory: DataSource.Factory =
            if (prefs.adBlockEnabled) AdFilterDataSource.Factory(http) else http

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(TARGET_LIVE_OFFSET_MS)
                    .build()
            )
            .build()

        return HlsMediaSource.Factory(dataSourceFactory)
            // Skips the extra "download a segment to learn the codecs" round trip.
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
    }

    private companion object {
        const val TAG = "PlayerEngine"

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** Assume a usable connection so ABR does not open on the 240p variant. */
        // Opens high enough that the first segments are picked off the 1080p
        // rung; the meter corrects within a few seconds either way.
        const val INITIAL_BITRATE_ESTIMATE = 5_000_000L

        /** Fraction of measured bandwidth the ladder is allowed to spend. */
        const val BANDWIDTH_FRACTION = 0.8f

        /** How long throughput must hold up before stepping up a rung. */
        const val MIN_DURATION_FOR_QUALITY_INCREASE_MS = 5_000

        const val MIN_BUFFER_MS = 15_000
        const val MAX_BUFFER_MS = 30_000
        const val BUFFER_FOR_PLAYBACK_MS = 700
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_500

        /** How far behind the live edge to sit. Lower is snappier but riskier. */
        const val TARGET_LIVE_OFFSET_MS = 8_000L

        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000
    }
}
