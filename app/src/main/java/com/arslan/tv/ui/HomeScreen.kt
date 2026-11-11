package com.arslan.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arslan.tv.data.Channel
import com.arslan.tv.player.Quality
import com.arslan.tv.ui.components.ChannelCard
import com.arslan.tv.ui.components.LiveBadge
import com.arslan.tv.ui.components.RemoteLogo
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

private val ContentPadding = 32.dp

/** Three across on anything TV-shaped, as asked. */
private const val GRID_COLUMNS = 3

/**
 * Below this the panel is a phone held upright, where three 16:9 cards side
 * by side leave each one too small to read a logo in.
 */
private val NarrowPanel = 600.dp

@Composable
fun HomeScreen(
    channels: List<Channel>,
    categoryOrder: List<String>,
    loading: Boolean,
    spotlight: Channel?,
    quality: Quality,
    onChannelFocused: (Channel) -> Unit,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    firstCardFocus: FocusRequester? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Billboard(spotlight)

        Column(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> LoadingState()
                channels.isEmpty() -> EmptyState()
                else -> {
                    // Compact header: the grid is the point of this screen, so the
                    // billboard gets one line instead of a third of the panel.
                    SpotlightBar(
                        channel = spotlight,
                        quality = quality,
                        modifier = Modifier.padding(
                            start = ContentPadding,
                            end = ContentPadding,
                            top = 18.dp,
                            bottom = 14.dp,
                        ),
                    )
                    ChannelGrid(
                        channels = channels,
                        categoryOrder = categoryOrder,
                        restoreChannelId = spotlight?.id,
                        onChannelFocused = onChannelFocused,
                        onChannelClick = onChannelClick,
                        firstCardFocus = firstCardFocus,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Brand-coloured backdrop that cross-fades as focus moves between channels. */
@Composable
private fun Billboard(channel: Channel?) {
    Crossfade(
        targetState = channel,
        animationSpec = tween(420),
        label = "billboard",
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f),
    ) { current ->
        val accent = current?.let { Color(it.accent) } ?: AppColors.Surface
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), AppColors.Background))
                )
        ) {
            RemoteLogo(
                url = current?.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.7f)
                    .padding(end = 60.dp)
                    .alpha(0.18f),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to AppColors.Background.copy(alpha = 0.7f),
                            1f to AppColors.Background,
                        )
                    )
            )
        }
    }
}

@Composable
private fun SpotlightBar(
    channel: Channel?,
    quality: Quality,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (channel == null) {
            Text("Arslan TV", style = AppType.Title, color = AppColors.TextPrimary)
            return@Row
        }
        LiveBadge()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = AppType.Title,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${channel.category}  ·  up to ${channel.maxHeight}p  ·  OK to watch",
                style = AppType.Label,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = quality.label, style = AppType.Label, color = AppColors.TextTertiary)
    }
}

@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    categoryOrder: List<String>,
    /** Where the remote was last, so coming back lands there and not at the top. */
    restoreChannelId: String?,
    onChannelFocused: (Channel) -> Unit,
    onChannelClick: (Channel) -> Unit,
    firstCardFocus: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    // Sections follow the declared menu order. A declared category with nothing
    // in it is skipped here - it still has its own sidebar entry, which is
    // where an empty category says so.
    val grouped = remember(channels, categoryOrder) {
        val byCategory = channels.groupBy { it.category }
        categoryOrder.mapNotNull { category ->
            byCategory[category]?.takeIf { it.isNotEmpty() }?.let { category to it }
        }
    }
    val fallbackFocus = remember { FocusRequester() }
    val focusTarget = firstCardFocus ?: fallbackFocus

    // Snapshotted rather than followed live: the id is wanted as it was on
    // arrival, and it changes again the moment focus lands anywhere. The
    // screen leaves the composition while a channel plays, so coming back
    // re-reads it and the grid returns to the channel just watched.
    val targetId = remember(channels) {
        restoreChannelId?.takeIf { id -> channels.any { it.id == id } }
            ?: channels.firstOrNull()?.id
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val columns = if (maxWidth < NarrowPanel) 2 else GRID_COLUMNS

    // Flat position of that channel, counting the span-wide category headers,
    // so the list can be scrolled to it before focus is asked for.
    val targetIndex = remember(grouped, targetId, columns) {
        var index = 0
        var found = -1
        for ((_, rowChannels) in grouped) {
            index += 1 // the category header occupies a line of its own
            val at = rowChannels.indexOfFirst { it.id == targetId }
            if (at >= 0) { found = index + at; break }
            index += rowChannels.size
        }
        found
    }
    val gridState = rememberLazyGridState()

    // A lazy grid cannot focus a cell it has not composed, so scroll first.
    // The sidebar grabs focus while the grid settles, hence the retry.
    LaunchedEffect(targetId) {
        if (targetId == null) return@LaunchedEffect
        if (targetIndex >= 0) runCatching { gridState.scrollToItem(targetIndex) }
        delay(250)
        repeat(60) {
            withFrameNanos { }
            if (runCatching { focusTarget.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = ContentPadding,
            end = ContentPadding,
            bottom = 28.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        grouped.forEach { (category, rowChannels) ->
            item(key = "header-$category", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = category,
                    style = AppType.RowHeader,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            items(rowChannels, key = { it.id }) { channel ->
                ChannelCard(
                    channel = channel,
                    onClick = { onChannelClick(channel) },
                    onFocused = { onChannelFocused(channel) },
                    // On the grid the number is part of the card and stays put.
                    // Only the one over video times out, where it would
                    // otherwise sit on the picture for the whole programme.
                    showNumber = true,
                    modifier = if (channel.id == targetId) {
                        Modifier.focusRequester(focusTarget)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No channels here", style = AppType.Title, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick another category from the menu",
                style = AppType.Body,
                color = AppColors.TextSecondary,
            )
        }
    }
}
