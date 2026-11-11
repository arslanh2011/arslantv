package com.arslan.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arslan.tv.data.Channel
import com.arslan.tv.data.Source
import com.arslan.tv.ui.components.BackChip
import com.arslan.tv.ui.components.LiveBadge
import com.arslan.tv.ui.components.RemoteLogo
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

/**
 * What a channel carries when it is more than one stream: its live feed first,
 * then the playlists behind it. Opening a channel with several playlists lands
 * here instead of guessing which one was meant.
 */
@Composable
fun PlaylistPickerScreen(
    channel: Channel,
    onPlayLive: () -> Unit,
    onPickPlaylist: (Source) -> Unit,
    onPlayVideo: (Source) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val playlists = channel.shelfItems
    val hasLive = channel.isLive
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(channel.id) {
        repeat(60) {
            withFrameNanos { }
            if (runCatching { firstTile.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackChip(onBack = onBack)
                Spacer(Modifier.size(18.dp))
                RemoteLogo(
                    url = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(channel.name, style = AppType.Title, color = AppColors.TextPrimary)
                    Text(
                        text = buildString {
                            if (hasLive) append("Live  ·  ")
                            append("${playlists.size} ")
                            append(if (playlists.size == 1) "title" else "titles")
                        },
                        style = AppType.Label,
                        color = AppColors.TextSecondary,
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (hasLive) {
                    item(key = "live") {
                        ShelfTile(
                            title = "Watch live",
                            accent = Color(channel.accent),
                            live = true,
                            logoUrl = channel.logoUrl,
                            onClick = onPlayLive,
                            modifier = Modifier.focusRequester(firstTile),
                        )
                    }
                }
                itemsIndexed(playlists, key = { _, s -> s.url }) { index, source ->
                    ShelfTile(
                        title = source.label.ifBlank { "Playlist ${index + 1}" },
                        accent = Color(channel.accent),
                        live = false,
                        logoUrl = source.thumbUrl.ifBlank { channel.logoUrl },
                        fillTile = source.thumbUrl.isNotBlank(),
                        onClick = {
                            if (source.isPlaylist) onPickPlaylist(source) else onPlayVideo(source)
                        },
                        modifier = if (!hasLive && index == 0) {
                            Modifier.focusRequester(firstTile)
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
private fun ShelfTile(
    title: String,
    accent: Color,
    live: Boolean,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Playlist artwork is a 16:9 still, so it fills the tile; a logo insets. */
    fillTile: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "shelfScale",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(modifier = modifier.zIndex(if (focused) 1f else 0f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = if (live) 0.95f else 0.55f),
                            accent.copy(alpha = 0.3f),
                            AppColors.Surface,
                        )
                    )
                )
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) AppColors.FocusRing else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            RemoteLogo(
                url = logoUrl,
                contentDescription = null,
                contentScale = if (fillTile) ContentScale.Crop else ContentScale.Fit,
                modifier = if (fillTile) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxSize().padding(26.dp)
                },
            )
            if (live) {
                LiveBadge(modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
        Text(
            text = title,
            style = AppType.Label,
            color = if (focused) AppColors.TextPrimary else AppColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
        )
    }
}
