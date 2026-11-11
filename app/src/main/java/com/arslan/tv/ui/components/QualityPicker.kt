package com.arslan.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arslan.tv.player.Quality
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

/**
 * Horizontal quality chips. Options above the channel's own ceiling are hidden
 * rather than shown greyed out - offering 1080p on a 720p source would just be
 * a lie.
 */
@Composable
fun QualityPicker(
    selected: Quality,
    maxHeight: Int,
    onSelect: (Quality) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hide rungs the channel cannot reach; offering 1080p on a 720p feed lies.
    val options = remember(maxHeight) {
        Quality.entries.filter { it == Quality.AUTO || it.maxHeight <= maxHeight }
    }

    Column(modifier = modifier) {
        Text(
            text = "QUALITY",
            style = AppType.Badge,
            color = AppColors.TextTertiary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                QualityChip(
                    quality = option,
                    isSelected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
        Text(
            text = selected.subtitle,
            style = AppType.Body,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun QualityChip(
    quality: Quality,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val background = when {
        isSelected -> AppColors.Accent
        focused -> AppColors.SurfaceElevated
        else -> Color.White.copy(alpha = 0.08f)
    }

    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) AppColors.FocusRing else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = quality.label,
            style = AppType.Label,
            color = AppColors.TextPrimary,
        )
    }
}
