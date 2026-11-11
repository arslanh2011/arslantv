package com.arslan.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arslan.tv.data.Channel
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

/** Width of a card in a horizontal row. Grids size cards from the cell instead. */
val CardWidth = 232.dp

/** Below this a card is small enough that the badges crowd the logo. */
private val NarrowCard = 240.dp

/**
 * A 16:9 channel tile. Focus grows it, rings it in white and lifts it above its
 * neighbours - the same read as a streaming-service row, and the only focus
 * affordance a D-pad user gets.
 */
@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    showNumber: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "cardScale",
    )
    val numberAlpha by animateFloatAsState(
        targetValue = if (showNumber) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "cardNumberAlpha",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val accent = Color(channel.accent)

    // The caller owns the width: a row pins it to CardWidth, while a grid cell
    // hands down a fixed width that Modifier.width would be clamped up to anyway.
    Column(
        modifier = modifier.zIndex(if (focused) 1f else 0f)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .scale(scale)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clickable(
                    interactionSource = interactionSource,
                    // Focus styling below replaces the ripple, which TVs do not use.
                    indication = null,
                    onClick = onClick,
                )
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.95f),
                            accent.copy(alpha = 0.45f),
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
            // A fraction, not a fixed inset: 22dp of padding is a comfortable
            // margin on a TV card but eats almost all the height of a
            // two-column card on a phone, which left logos tiny there. The
            // badges are a fixed size though, so on a narrow card they need
            // more room cleared for them than on a wide one.
            val logoFraction = if (maxWidth < NarrowCard) 0.58f else 0.74f
            RemoteLogo(
                url = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(logoFraction),
            )
            // A shelf of playlists is not on air, so it does not claim to be.
            if (channel.isLive) {
                LiveBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }
            ChannelNumber(
                number = channel.number,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .alpha(numberAlpha),
            )
        }

        Text(
            text = channel.name,
            style = AppType.Label,
            color = if (focused) AppColors.TextPrimary else AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
        )
    }
}
