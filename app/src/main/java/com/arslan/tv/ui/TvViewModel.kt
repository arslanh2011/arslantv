package com.arslan.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.arslan.tv.data.Channel
import com.arslan.tv.data.ChannelRepository
import com.arslan.tv.data.Episode
import com.arslan.tv.data.Playback
import com.arslan.tv.data.Source
import com.arslan.tv.data.StreamType
import com.arslan.tv.data.StreamResolver
import com.arslan.tv.player.PictureMode
import com.arslan.tv.player.PlayerEngine
import com.arslan.tv.player.Prefs
import com.arslan.tv.player.Quality
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The queue rides in a player parameter, so it cannot be unbounded. */
private const val MAX_QUEUED_EPISODES = 150

/** Where the app currently is. */
sealed interface Screen {
    data object Home : Screen

    /** Resolving which of the channel's sources is actually up. */
    data class Opening(val channel: Channel) : Screen

    /** The playlists a channel offers, when it has more than one. */
    data class Playlists(val channel: Channel) : Screen

    /** Episode list for a playlist-backed show. */
    data class Episodes(
        val channel: Channel,
        val playlistId: String,
        val episodes: List<Episode> = emptyList(),
        val loading: Boolean = true,
        /** Whether Back should land on the playlist picker rather than home. */
        val fromPicker: Boolean = false,
    ) : Screen

    data class Watching(
        val channel: Channel,
        val playback: Playback,
        /** The whole episode list, in the order the grid showed it. */
        val queue: List<String> = emptyList(),
        /** Where in [queue] the player actually is; -1 when not a playlist. */
        val index: Int = -1,
    ) : Screen {
        /** Handed to the embed so it rolls on by itself when a clip ends. */
        val upNext: List<String>
            get() = if (index < 0) emptyList() else queue.drop(index + 1).take(MAX_QUEUED_EPISODES)

        val isPlaylist: Boolean get() = index >= 0
        val hasPrevious: Boolean get() = index > 0
        val hasNext: Boolean get() = index >= 0 && index < queue.lastIndex
    }

    data class Failed(val channel: Channel, val reason: String) : Screen
}

@UnstableApi
class TvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChannelRepository(application)
    private val prefs = Prefs(application)
    private val engine = PlayerEngine(application, prefs)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Channel under the D-pad on the home screen; drives the billboard. */
    private val _spotlight = MutableStateFlow<Channel?>(null)
    val spotlight: StateFlow<Channel?> = _spotlight.asStateFlow()

    private val _quality = MutableStateFlow(prefs.quality)
    val quality: StateFlow<Quality> = _quality.asStateFlow()

    private val _adBlockEnabled = MutableStateFlow(prefs.adBlockEnabled)
    val adBlockEnabled: StateFlow<Boolean> = _adBlockEnabled.asStateFlow()

    private val _preloadEnabled = MutableStateFlow(prefs.preloadEnabled)
    val preloadEnabled: StateFlow<Boolean> = _preloadEnabled.asStateFlow()

    private val _pictureMode = MutableStateFlow(prefs.pictureMode)
    val pictureMode: StateFlow<PictureMode> = _pictureMode.asStateFlow()

    /** Overscan inset per edge, in dp. */
    data class Padding(val top: Int, val bottom: Int, val start: Int, val end: Int)

    private val _padding = MutableStateFlow(
        Padding(prefs.padTop, prefs.padBottom, prefs.padStart, prefs.padEnd)
    )
    val padding: StateFlow<Padding> = _padding.asStateFlow()

    /** Which sidebar entry is active. */
    private val _nav = MutableStateFlow<NavTarget>(NavTarget.Home)
    val nav: StateFlow<NavTarget> = _nav.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Menu order, as declared by `categories` in channels.json. */
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    /**
     * Channels for the current sidebar selection. Shelves sit on the home grid
     * alongside live channels now that each one is a single card - the filter
     * that used to hide them was there for the 23 loose playlist cards that
     * have since been folded into one.
     */
    val visibleChannels: StateFlow<List<Channel>> = combine(_channels, _nav) { list, target ->
        when (target) {
            is NavTarget.Category -> list.filter { it.category == target.name }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Channels matching the search box, across every category. */
    val searchResults: StateFlow<List<Channel>> =
        combine(_channels, _searchQuery) { list, query ->
            val q = query.trim()
            if (q.isEmpty()) {
                list
            } else {
                list.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.category.contains(q, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var prewarmJob: Job? = null

    init {
        viewModelScope.launch {
            val catalog = repository.load()
            _channels.value = catalog.channels
            _categories.value = catalog.categories
            _loading.value = false
            _spotlight.value = _channels.value.firstOrNull {
                it.id == prefs.lastChannelId
            } ?: _channels.value.firstOrNull()
        }
    }

    /**
     * Called as focus moves between cards. The delay keeps a fast scroll through
     * the row from kicking off a download per card; only where focus settles
     * gets pre-buffered.
     */
    fun onChannelFocused(channel: Channel) {
        _spotlight.value = channel
        prewarmJob?.cancel()
        prewarmJob = viewModelScope.launch {
            delay(PREWARM_SETTLE_MS)
            engine.prewarm(channel)
        }
    }

    private var openJob: Job? = null

    /**
     * Resolution happens before the player screen opens so that the right
     * player - ExoPlayer or the YouTube WebView - is chosen for whichever
     * source turned out to be up.
     */
    fun openChannel(channel: Channel) {
        // A shelf of several playlists is a menu, not a stream: show what is on
        // it rather than guessing which one was meant.
        if (channel.playlists.size > 1) {
            prewarmJob?.cancel()
            openJob?.cancel()
            _screen.value = Screen.Playlists(channel)
            return
        }
        prefs.lastChannelId = channel.id
        // A prewarm landing after playback starts would re-arm the spare slot
        // and make the engine think it has something better to promote.
        prewarmJob?.cancel()
        openJob?.cancel()
        _screen.value = Screen.Opening(channel)
        openJob = viewModelScope.launch {
            val result = runCatching { StreamResolver.resolve(channel) }
            result.fold(
                onSuccess = { playback ->
                    if (playback.isPlaylist) {
                        openPlaylist(channel, playback.url)
                    } else {
                        _screen.value = Screen.Watching(channel, playback)
                    }
                },
                onFailure = {
                    _screen.value =
                        Screen.Failed(channel, it.message ?: "This channel is not responding")
                },
            )
        }
    }

    /** Back out of playback. Stopping first means the stream is already dead by
     *  the time the home screen draws. */
    fun closePlayer() {
        prewarmJob?.cancel()
        openJob?.cancel()
        engine.stopPlayback()
        _screen.value = Screen.Home
    }

    /** Opens one playlist off a channel's shelf. */
    fun openPlaylistSource(channel: Channel, source: Source) {
        viewModelScope.launch { openPlaylist(channel, source.url, fromPicker = true) }
    }

    /** Plays a single film sitting on a shelf next to its playlists. */
    fun playShelfVideo(channel: Channel, source: Source) {
        engine.stopPlayback()
        _screen.value = Screen.Watching(
            channel,
            Playback(source.copy(type = StreamType.YOUTUBE), source.url),
        )
    }

    /** Plays the live feed of a channel that also carries playlists. */
    fun playLive(channel: Channel) {
        val live = channel.liveSource ?: return
        openChannel(channel.copy(sources = listOf(live)))
    }

    private suspend fun openPlaylist(
        channel: Channel,
        playlistId: String,
        fromPicker: Boolean = false,
    ) {
        _screen.value = Screen.Episodes(channel, playlistId, fromPicker = fromPicker)
        val episodes = runCatching { StreamResolver.episodes(playlistId) }.getOrDefault(emptyList())
        // Only apply if the user has not navigated away while this loaded.
        val current = _screen.value
        if (current is Screen.Episodes && current.playlistId == playlistId) {
            _screen.value = current.copy(episodes = episodes, loading = false)
        }
    }

    /** Back out of an episode list: to the shelf it came from, or home. */
    fun closeEpisodes() {
        val current = _screen.value as? Screen.Episodes ?: return closePlayer()
        // The picker is driven off the full channel, not the trimmed copy the
        // episode list was opened with.
        val full = _channels.value.firstOrNull { it.id == current.channel.id }
        _screen.value = if (current.fromPicker && full != null) {
            Screen.Playlists(full)
        } else {
            Screen.Home
        }
    }

    /** Plays one episode of a playlist-backed show. */
    fun playEpisode(channel: Channel, episode: Episode) {
        val source = channel.sources.firstOrNull { it.isPlaylist } ?: return
        // Everything after the chosen episode, in the order the list showed
        // it, so the player rolls on instead of stopping at the end of a clip.
        val queue = (_screen.value as? Screen.Episodes)?.episodes.orEmpty().map(Episode::videoId)
        _screen.value = Screen.Watching(
            channel,
            Playback(source.copy(type = StreamType.YOUTUBE, url = episode.videoId), episode.videoId),
            queue = queue,
            index = queue.indexOf(episode.videoId),
        )
    }

    /**
     * The embed advances through its queue on its own, so the index here would
     * go stale and Next would jump from the wrong place. The page reports what
     * it is actually playing and this follows it.
     */
    fun syncPlayingVideo(videoId: String) {
        val current = _screen.value as? Screen.Watching ?: return
        if (!current.isPlaylist) return
        val at = current.queue.indexOf(videoId)
        if (at < 0 || at == current.index) return
        _screen.value = current.copy(index = at)
    }

    /** Step through the episode list. [offset] is -1 for back, +1 for next. */
    fun stepEpisode(offset: Int) {
        val current = _screen.value as? Screen.Watching ?: return
        if (!current.isPlaylist) return
        val at = current.index + offset
        if (at !in current.queue.indices) return
        val id = current.queue[at]
        _screen.value = current.copy(
            playback = Playback(current.playback.source.copy(url = id), id),
            index = at,
        )
    }

    /** Back from an episode or its player returns to the episode list. */
    fun backToEpisodes() {
        val channel = (_screen.value as? Screen.Watching)?.channel ?: return
        val playlist = channel.sources.firstOrNull { it.isPlaylist }
        if (playlist != null) {
            engine.stopPlayback()
            viewModelScope.launch {
                openPlaylist(channel, playlist.url, fromPicker = channel.playlists.size > 1)
            }
        } else {
            closePlayer()
        }
    }

    /** Up/Down on the remote zaps to the neighbouring channel. */
    fun zap(offset: Int) {
        // Only ever land on something that plays: a playlist shelf would swap
        // the video for a menu, which is not what a channel-up press means.
        val list = _channels.value.filter { it.isLive }
        val current = when (val screen = _screen.value) {
            is Screen.Watching -> screen.channel
            is Screen.Opening -> screen.channel
            is Screen.Failed -> screen.channel
            is Screen.Episodes -> return // zapping has no meaning in a show
            is Screen.Playlists -> return
            Screen.Home -> null
        } ?: return
        if (list.size < 2) return
        val index = list.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: return
        val next = list[(index + offset + list.size) % list.size]
        _spotlight.value = next
        openChannel(next)
    }

    suspend fun startPlayback(channel: Channel, playback: Playback): ExoPlayer =
        engine.play(channel, playback)

    fun attachSurface(view: android.view.SurfaceView) = engine.attachSurface(view)

    fun detachSurface(view: android.view.SurfaceView) = engine.detachSurface(view)

    suspend fun reloadPlayback(channel: Channel): ExoPlayer = engine.reload(channel)

    fun setQuality(value: Quality) {
        _quality.value = value
        engine.quality = value
    }

    fun setAdBlockEnabled(value: Boolean) {
        prefs.adBlockEnabled = value
        _adBlockEnabled.value = value
    }

    fun setPreloadEnabled(value: Boolean) {
        prefs.preloadEnabled = value
        _preloadEnabled.value = value
    }

    fun setPictureMode(value: PictureMode) {
        prefs.pictureMode = value
        _pictureMode.value = value
    }

    enum class Edge { TOP, BOTTOM, START, END }

    fun adjustPadding(edge: Edge, value: Int) {
        when (edge) {
            Edge.TOP -> prefs.padTop = value
            Edge.BOTTOM -> prefs.padBottom = value
            Edge.START -> prefs.padStart = value
            Edge.END -> prefs.padEnd = value
        }
        _padding.value = Padding(prefs.padTop, prefs.padBottom, prefs.padStart, prefs.padEnd)
    }

    fun resetPadding() {
        prefs.padTop = 0
        prefs.padBottom = 0
        prefs.padStart = 0
        prefs.padEnd = 0
        _padding.value = Padding(0, 0, 0, 0)
    }

    fun select(target: NavTarget) {
        _nav.value = target
        if (target !is NavTarget.Search) _searchQuery.value = ""
    }

    fun setSearchQuery(value: String) {
        _searchQuery.value = value.take(40)
    }

    /** Called when the activity stops, so a backgrounded app holds no sockets. */
    fun onAppBackgrounded() {
        prewarmJob?.cancel()
        engine.stopPlayback()
    }

    override fun onCleared() {
        prewarmJob?.cancel()
        engine.release()
    }

    private companion object {
        const val PREWARM_SETTLE_MS = 450L

    }
}
