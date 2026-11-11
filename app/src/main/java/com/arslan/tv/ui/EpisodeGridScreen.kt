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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arslan.tv.data.Channel
import com.arslan.tv.data.Episode
import com.arslan.tv.ui.components.BackChip
import com.arslan.tv.ui.components.RemoteLogo
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

/** Episodes of a playlist-backed show, three across like the channel grid. */
@Composable
fun EpisodeGridScreen(
    channel: Channel,
    episodes: List<Episode>,
    loading: Boolean,
    onPlay: (Episode) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val firstTile = remember { FocusRequester() }
    LaunchedEffect(episodes.firstOrNull()?.videoId) {
        if (episodes.isEmpty()) return@LaunchedEffect
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
                        text = when {
                            loading -> "Loading episodes…"
                            episodes.isEmpty() -> "No episodes found"
                            else -> "${episodes.size} episodes  ·  Back to return"
                        },
                        style = AppType.Label,
                        color = AppColors.TextSecondary,
                    )
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = AppColors.Accent,
                        modifier = Modifier.size(48.dp),
                    )
                }

                episodes.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "This playlist is empty or unavailable",
                        style = AppType.Body,
                        color = AppColors.TextSecondary,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(episodes, key = { it.videoId }) { episode ->
                        EpisodeCard(
                            episode = episode,
                            onClick = { onPlay(episode) },
                            modifier = if (episode.videoId == episodes.first().videoId) {
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
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(160),
        label = "episodeScale",
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
                .background(AppColors.Surface)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) AppColors.FocusRing else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            RemoteLogo(
                url = episode.thumbnailUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = episode.title,
            style = AppType.Label,
            color = if (focused) AppColors.TextPrimary else AppColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
