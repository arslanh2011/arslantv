package com.arslan.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.arslan.tv.ui.CollapsedSidebarWidth
import com.arslan.tv.ui.HomeScreen
import com.arslan.tv.ui.ChannelFailedScreen
import com.arslan.tv.ui.EpisodeGridScreen
import com.arslan.tv.ui.NavEntry
import com.arslan.tv.ui.OpeningScreen
import com.arslan.tv.ui.NavTarget
import com.arslan.tv.ui.PlayerScreen
import com.arslan.tv.ui.PlaylistPickerScreen
import com.arslan.tv.ui.Screen
import com.arslan.tv.ui.SearchPanel
import com.arslan.tv.ui.SettingsPanel
import com.arslan.tv.ui.Sidebar
import com.arslan.tv.ui.TvViewModel
import com.arslan.tv.ui.YouTubePlayerScreen
import com.arslan.tv.ui.components.NavIcon
import com.arslan.tv.ui.iconForCategory
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.ArslanTvTheme

@UnstableApi
class MainActivity : ComponentActivity() {

    private var viewModel: TvViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Live TV has no user interaction for long stretches.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ArslanTvTheme {
                val vm: TvViewModel = viewModel()
                viewModel = vm
                AppRoot(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Release sockets and decoders as soon as the app leaves the foreground.
        viewModel?.onAppBackgrounded()
    }
}

@UnstableApi
@Composable
private fun AppRoot(vm: TvViewModel) {
    val loading by vm.loading.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val spotlight by vm.spotlight.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val pictureMode by vm.pictureMode.collectAsStateWithLifecycle()
    val padding by vm.padding.collectAsStateWithLifecycle()
    val adBlockEnabled by vm.adBlockEnabled.collectAsStateWithLifecycle()
    val preloadEnabled by vm.preloadEnabled.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val visibleChannels by vm.visibleChannels.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val nav by vm.nav.collectAsStateWithLifecycle()

    var settingsOpen by remember { mutableStateOf(false) }
    val firstCardFocus = remember { FocusRequester() }

    val entries = remember(categories) {
        buildList {
            add(NavEntry(NavTarget.Search, "Search", NavIcon.SEARCH))
            add(NavEntry(NavTarget.Home, "Home", NavIcon.HOME))
            categories.forEach {
                add(NavEntry(NavTarget.Category(it), it, iconForCategory(it)))
            }
            add(NavEntry(NavTarget.Settings, "Settings", NavIcon.SETTINGS))
        }
    }

    // Two insets stack here. safeDrawing keeps the system bars, gesture handle
    // and camera cutout off the UI on a phone - it is zero on a TV, so the
    // panel layout is unchanged there. The user's overscan padding then
    // applies inside that, compensating for a TV that crops the signal.
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                top = padding.top.dp,
                bottom = padding.bottom.dp,
                start = padding.start.dp,
                end = padding.end.dp,
            )
    ) {
        when (val current = screen) {
            Screen.Home -> {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(start = CollapsedSidebarWidth)) {
                        if (nav is NavTarget.Search) {
                            SearchPanel(
                                query = searchQuery,
                                results = searchResults,
                                onQueryChange = vm::setSearchQuery,
                                onChannelClick = vm::openChannel,
                                onChannelFocused = vm::onChannelFocused,
                                onBack = { vm.select(NavTarget.Home) },
                            )
                        } else {
                            HomeScreen(
                                channels = visibleChannels,
                                categoryOrder = categories,
                                loading = loading,
                                spotlight = spotlight,
                                quality = quality,
                                onChannelFocused = vm::onChannelFocused,
                                onChannelClick = vm::openChannel,
                                firstCardFocus = firstCardFocus,
                            )
                        }
                    }

                    Sidebar(
                        entries = entries,
                        selected = nav,
                        onSelect = { target ->
                            if (target is NavTarget.Settings) settingsOpen = true else vm.select(target)
                        },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }

            is Screen.Opening -> OpeningScreen(current.channel.name)

            is Screen.Failed -> ChannelFailedScreen(
                channelName = current.channel.name,
                reason = current.reason,
                onRetry = { vm.openChannel(current.channel) },
                onBack = vm::closePlayer,
            )

            is Screen.Playlists -> PlaylistPickerScreen(
                channel = current.channel,
                onPlayLive = { vm.playLive(current.channel) },
                onPickPlaylist = { vm.openPlaylistSource(current.channel, it) },
                onPlayVideo = { vm.playShelfVideo(current.channel, it) },
                onBack = vm::closePlayer,
            )

            is Screen.Episodes -> EpisodeGridScreen(
                channel = current.channel,
                episodes = current.episodes,
                loading = current.loading,
                onPlay = { vm.playEpisode(current.channel, it) },
                onBack = vm::closeEpisodes,
            )

            is Screen.Watching -> {
                // Which player to use depends on the source that actually
                // answered, not on how the channel is listed.
                if (current.playback.isYouTube) {
                    YouTubePlayerScreen(
                        channel = current.channel,
                        quality = quality,
                        adBlockEnabled = adBlockEnabled,
                        onQualityChange = vm::setQuality,
                        // A show returns to its episode list, a channel to Home.
                        onExit = {
                            if (current.channel.sources.any { it.isPlaylist }) {
                                vm.backToEpisodes()
                            } else {
                                vm.closePlayer()
                            }
                        },
                        onZap = vm::zap,
                        videoId = current.playback.url,
                        upNext = current.upNext,
                        onStep = if (current.isPlaylist) vm::stepEpisode else null,
                        hasPrevious = current.hasPrevious,
                        hasNext = current.hasNext,
                        onVideoChanged = vm::syncPlayingVideo,
                    )
                } else {
                    PlayerScreen(
                        channel = current.channel,
                        playback = current.playback,
                        quality = quality,
                        pictureMode = pictureMode,
                        onQualityChange = vm::setQuality,
                        onExit = vm::closePlayer,
                        onZap = vm::zap,
                        startPlayback = vm::startPlayback,
                        reloadPlayback = vm::reloadPlayback,
                        attachSurface = vm::attachSurface,
                        detachSurface = vm::detachSurface,
                    )
                }
            }
        }

        if (settingsOpen) {
            SettingsPanel(
                quality = quality,
                pictureMode = pictureMode,
                padding = padding,
                adBlockEnabled = adBlockEnabled,
                preloadEnabled = preloadEnabled,
                onQualityChange = vm::setQuality,
                onPictureModeChange = vm::setPictureMode,
                onPaddingChange = vm::adjustPadding,
                onPaddingReset = vm::resetPadding,
                onAdBlockChange = vm::setAdBlockEnabled,
                onPreloadChange = vm::setPreloadEnabled,
                onDismiss = { settingsOpen = false },
            )
        }
    }

    // Returning to Home should land the remote back on the grid, not the menu.
    LaunchedEffect(nav, screen) {
        if (screen is Screen.Home && nav is NavTarget.Home) {
            repeat(60) {
                withFrameNanos { }
                if (runCatching { firstCardFocus.requestFocus() }.isSuccess) {
                    return@LaunchedEffect
                }
            }
        }
    }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !settingsOpen && screen is Screen.Home && nav != NavTarget.Home) {
        vm.select(NavTarget.Home)
    }
}
