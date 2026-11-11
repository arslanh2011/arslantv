package com.arslan.tv.ui

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.arslan.tv.data.Channel
import com.arslan.tv.data.Playback
import com.arslan.tv.player.PictureMode
import com.arslan.tv.player.Quality
import com.arslan.tv.ui.components.LiveBadge
import com.arslan.tv.ui.components.BackChip
import com.arslan.tv.ui.components.CHANNEL_NUMBER_TIMEOUT_MS
import com.arslan.tv.ui.components.ChannelNumberOverlay
import com.arslan.tv.ui.components.QualityPicker
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 4_500L

@UnstableApi
@Composable
fun PlayerScreen(
    channel: Channel,
    playback: Playback,
    quality: Quality,
    pictureMode: PictureMode,
    onQualityChange: (Quality) -> Unit,
    onExit: () -> Unit,
    onZap: (Int) -> Unit,
    startPlayback: suspend (Channel, Playback) -> ExoPlayer,
    attachSurface: (SurfaceView) -> Unit,
    detachSurface: (SurfaceView) -> Unit,
    reloadPlayback: suspend (Channel) -> ExoPlayer,
) {
    // Bumping this restarts playback, which is how an expired manifest recovers.
    var attempt by remember(channel.id) { mutableIntStateOf(0) }
    var buffering by remember(channel.id) { mutableStateOf(true) }
    var errorMessage by remember(channel.id) { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayNonce by remember { mutableIntStateOf(0) }
    // Keyed on the channel, so zapping restarts it and the new number shows.
    var numberVisible by remember(channel.id) { mutableStateOf(true) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }

    val player by produceState<ExoPlayer?>(null, channel.id, attempt) {
        value = runCatching {
            if (attempt == 0) startPlayback(channel, playback) else reloadPlayback(channel)
        }.onFailure {
            errorMessage = it.message ?: "Could not open this channel"
        }.getOrNull()
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    // Auto-hide the overlay; every remote press bumps overlayNonce to restart it.
    LaunchedEffect(overlayVisible, overlayNonce) {
        if (overlayVisible) {
            delay(OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        }
    }

    LaunchedEffect(channel.id) {
        delay(CHANNEL_NUMBER_TIMEOUT_MS)
        numberVisible = false
    }

    DisposableEffect(player) {
        val current = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) errorMessage = null
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    videoAspect = size.width * size.pixelWidthHeightRatio / size.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                val expiredToken = cause is HttpDataSource.InvalidResponseCodeException &&
                    cause.responseCode in listOf(401, 403, 410)
                if (expiredToken && attempt < MAX_ATTEMPTS) {
                    // Signed manifests go stale; mint a fresh one and carry on.
                    attempt += 1
                } else {
                    errorMessage = error.errorCodeName
                }
            }
        }
        current.addListener(listener)
        buffering = current.playbackState == Player.STATE_BUFFERING
        onDispose { current.removeListener(listener) }
    }

    // Back always leaves. It deliberately does not close the overlay first -
    // the overlay times out on its own, and one press should never need a
    // second to get out of the channel.
    BackHandler { onExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            // Touch devices have no key to press and the overlay times out, so
            // without this a phone user is stuck in the channel with no way
            // back. A tap anywhere does what any remote key does.
            .pointerInput(Unit) {
                detectTapGestures {
                    overlayVisible = true
                    overlayNonce += 1
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    // OK leaves too, unless the overlay is up - then it belongs
                    // to whichever overlay button has focus.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (overlayVisible) false else { onExit(); true }
                    }

                    Key.DirectionUp -> {
                        if (overlayVisible) false else { onZap(-1); true }
                    }

                    Key.DirectionDown -> {
                        if (overlayVisible) false else { onZap(1); true }
                    }

                    // Back is handled by BackHandler above. Letting it fall into
                    // the catch-all would raise the overlay on key-down, and the
                    // handler would then spend the press closing it again.
                    Key.Back, Key.Escape -> false

                    else -> {
                        overlayVisible = true
                        overlayNonce += 1
                        false
                    }
                }
            }
    ) {
        PlayerSurface(
            pictureMode = pictureMode,
            videoAspect = videoAspect,
            attachSurface = attachSurface,
            detachSurface = detachSurface,
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && errorMessage == null) {
            CircularProgressIndicator(
                color = AppColors.Accent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp),
            )
        }

        errorMessage?.let { message ->
            ErrorPanel(
                channel = channel,
                message = message,
                onRetry = { errorMessage = null; attempt += 1 },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            ControlsOverlay(
                channel = channel,
                quality = quality,
                onBackToMenu = onExit,
                onQualityChange = {
                    onQualityChange(it)
                    overlayNonce += 1
                },
            )
        }

        AnimatedVisibility(
            visible = numberVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp),
        ) {
            ChannelNumberOverlay(channel.number)
        }

        // Shown with the overlay rather than always, so it never sits over the
        // picture while you are just watching.
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(32.dp),
        ) {
            BackChip(onBack = onExit)
        }
    }
}

/**
 * A plain SurfaceView, not media3's PlayerView: the overlay above is drawn in
 * Compose, so the only thing needed from the view layer is a video surface.
 *
 * The surface is sized to the picture rather than stretched to the window, so
 * a 16:9 broadcast keeps its shape on a wider or narrower panel.
 */
@UnstableApi
@Composable
private fun PlayerSurface(
    pictureMode: PictureMode,
    videoAspect: Float,
    attachSurface: (SurfaceView) -> Unit,
    detachSurface: (SurfaceView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val screenAspect =
            if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else videoAspect
        val wider = videoAspect > screenAspect

        val sizing = when (pictureMode) {
            // Letterbox or pillarbox so the whole picture is visible.
            PictureMode.FIT ->
                if (wider) Modifier.fillMaxWidth().aspectRatio(videoAspect)
                else Modifier.fillMaxHeight().aspectRatio(videoAspect)
            // Overflow the short edge and let the frame crop it.
            PictureMode.FILL ->
                if (wider) Modifier.fillMaxHeight().aspectRatio(videoAspect)
                else Modifier.fillMaxWidth().aspectRatio(videoAspect)
            PictureMode.STRETCH -> Modifier.fillMaxSize()
        }

        AndroidView(
            modifier = sizing,
            factory = { context ->
                SurfaceView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }.also { surfaceView = it }
            },
        )
    }

    // Keyed on the view alone. The engine re-points the surface itself when it
    // swaps players, so this never has to fire for a player change - and so it
    // cannot race that swap.
    val view = surfaceView
    DisposableEffect(view) {
        view?.let(attachSurface)
        onDispose { view?.let(detachSurface) }
    }
}

@Composable
private fun ControlsOverlay(
    channel: Channel,
    quality: Quality,
    onBackToMenu: () -> Unit,
    onQualityChange: (Quality) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, AppColors.Scrim)
                )
            )
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveBadge()
            Spacer(Modifier.size(14.dp))
            Text(text = channel.name, style = AppType.Title, color = AppColors.TextPrimary)
        }
        Text(
            text = "${channel.category}  ·  ▲▼ changes channel  ·  ▼ reaches the buttons below",
            style = AppType.Body,
            color = AppColors.TextSecondary,
        )
        com.arslan.tv.ui.components.TvButton(
            label = "◀  Back to menu",
            onClick = onBackToMenu,
        )
        QualityPicker(
            selected = quality,
            maxHeight = channel.maxHeight,
            onSelect = onQualityChange,
        )
    }
}

@Composable
private fun ErrorPanel(
    channel: Channel,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(AppColors.Surface)
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${channel.name} is not responding",
            style = AppType.Title,
            color = AppColors.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(text = message, style = AppType.Body, color = AppColors.TextSecondary)
        Spacer(Modifier.height(22.dp))
        com.arslan.tv.ui.components.TvButton(label = "Try again", onClick = onRetry)
    }
}

private const val MAX_ATTEMPTS = 3
