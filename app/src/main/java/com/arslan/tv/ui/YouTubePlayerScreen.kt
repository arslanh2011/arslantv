package com.arslan.tv.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.arslan.tv.data.Channel
import com.arslan.tv.player.AdBlock
import com.arslan.tv.player.Quality
import com.arslan.tv.player.WebDiagnostics
import com.arslan.tv.player.YouTubeHtml
import com.arslan.tv.ui.components.LiveBadge
import com.arslan.tv.ui.components.BackChip
import com.arslan.tv.ui.components.CHANNEL_NUMBER_TIMEOUT_MS
import com.arslan.tv.ui.components.ChannelNumberOverlay
import com.arslan.tv.ui.components.QualityPicker
import com.arslan.tv.ui.components.TvButton
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

private const val OVERLAY_TIMEOUT_MS = 4_500L

@Composable
fun YouTubePlayerScreen(
    channel: Channel,
    quality: Quality,
    adBlockEnabled: Boolean,
    onQualityChange: (Quality) -> Unit,
    onExit: () -> Unit,
    onZap: (Int) -> Unit,
    videoId: String,
    upNext: List<String> = emptyList(),
    /** Non-null while playing a playlist: -1 steps back, +1 steps on. */
    onStep: ((Int) -> Unit)? = null,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    onVideoChanged: (String) -> Unit = {},
) {
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayNonce by remember { mutableIntStateOf(0) }
    // Keyed on the channel, so zapping restarts it and the new number shows.
    var numberVisible by remember(channel.id) { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }


    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

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

    // Quality changes go to the running player, no reload needed.
    LaunchedEffect(quality, webView) {
        webView?.evaluateJavascript(
            "if (window.setArslanQuality) setArslanQuality('${YouTubeHtml.qualityHint(quality)}')",
            null,
        )
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
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                        if (overlayVisible) false else { onExit(); true }

                    Key.DirectionUp ->
                        if (overlayVisible) false
                        else { (onStep ?: onZap)(-1); true }
                    Key.DirectionDown ->
                        if (overlayVisible) false
                        else { (onStep ?: onZap)(1); true }

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
        // Keyed so switching channels rebuilds the WebView on the new video.
        // It deliberately does not key on the queue: the page advances through
        // that itself, and rebuilding would restart the run.
        key(videoId) {
            YouTubeSurface(
                videoId = videoId,
                upNext = upNext,
                quality = quality,
                adBlockEnabled = adBlockEnabled,
                onCreated = { webView = it },
                onVideoChanged = onVideoChanged,
                onTap = {
                    overlayVisible = true
                    overlayNonce += 1
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, AppColors.Scrim)))
                    .padding(horizontal = 48.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge()
                    Spacer(Modifier.size(14.dp))
                    Text(channel.name, style = AppType.Title, color = AppColors.TextPrimary)
                }
                Text(
                    text = "${channel.category}  ·  via YouTube  ·  " +
                        (if (onStep != null) "▲▼ changes episode  ·  " else "▲▼ changes channel  ·  ") +
                        "▼ reaches the buttons below",
                    style = AppType.Body,
                    color = AppColors.TextSecondary,
                )
                if (onStep != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (hasPrevious) {
                            TvButton(label = "⏮  Previous", onClick = { onStep(-1) })
                        }
                        if (hasNext) {
                            TvButton(label = "Next  ⏭", onClick = { onStep(1) })
                        }
                    }
                }
                TvButton(label = "◀  Back to menu", onClick = onExit)
                QualityPicker(
                    selected = quality,
                    maxHeight = channel.maxHeight,
                    onSelect = onQualityChange,
                )
            }
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeSurface(
    videoId: String,
    upNext: List<String>,
    quality: Quality,
    adBlockEnabled: Boolean,
    onCreated: (WebView) -> Unit,
    onVideoChanged: (String) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The factory runs once, so it must not capture the first lambda for good.
    val latestOnVideo = rememberUpdatedState(onVideoChanged)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                // Deliberately NOT setLayerType(LAYER_TYPE_HARDWARE): that renders
                // the WebView into an offscreen texture, and the video surface
                // then never composites - audio plays over a black picture.
                // WebView manages its own acceleration.
                // The remote is handled by Compose above; the page itself is not focusable.
                isFocusable = false
                isFocusableInTouchMode = false
                // Touch is handled here rather than in Compose: an embedded
                // View is hit-tested before Compose nodes drawn over it, so a
                // tap would otherwise reach YouTube's own chrome and pause the
                // stream. Swallow every touch and report the tap upwards, so a
                // phone user raises our overlay instead of YouTube's.
                @Suppress("ClickableViewAccessibility")
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_UP) onTap()
                    true
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    javaScriptCanOpenWindowsAutomatically = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                webViewClient = PlayerWebViewClient(
                    adBlockEnabled = adBlockEnabled,
                    hostPage = YouTubeHtml.page(videoId, quality, upNext),
                )
                // A WebChromeClient is not optional for <video>: Chromium only
                // wires up the media pipeline when one is present, which is why
                // the embed loads but the picture stays black without it.
                val diagnostics = WebDiagnostics(context)
                addJavascriptInterface(diagnostics.Bridge(latestOnVideo.value), "ArslanTv")
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        diagnostics.log("console: ${message.message()} @${message.lineNumber()}")
                        return true
                    }
                }
                diagnostics.log("loading video $videoId")
                loadUrl(YouTubeHtml.HOST_URL)
                onCreated(this)
            }
        },
        // Tearing the WebView down is what makes playback stop the instant the
        // user leaves, rather than letting it keep streaming in the background.
        onRelease = { view ->
            view.loadUrl("about:blank")
            view.stopLoading()
            view.destroy()
        },
    )
}

/**
 * Serves the host page for [YouTubeHtml.HOST_URL] and drops requests to ad and
 * tracking networks.
 *
 * The host page is handed to the WebView here rather than fetched, which gives
 * the document a real origin without needing a local HTTP server - see
 * [YouTubeHtml] for why that matters.
 *
 * YouTube serves in-stream ads from the same hosts as the video itself, so the
 * blocking removes overlay and banner ads and the tracking that comes with
 * them, not every in-stream break.
 */
private class PlayerWebViewClient(
    private val adBlockEnabled: Boolean,
    private val hostPage: String,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (url == YouTubeHtml.HOST_URL || url == YouTubeHtml.HOST_URL.trimEnd('/')) {
            return WebResourceResponse(
                "text/html",
                "utf-8",
                ByteArrayInputStream(hostPage.toByteArray()),
            )
        }
        if (adBlockEnabled && AdBlock.isBlockedHost(url)) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return null
    }
}
