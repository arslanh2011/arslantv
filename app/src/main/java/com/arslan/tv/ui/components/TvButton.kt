package com.arslan.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

/**
 * The app's only button. Focus - not hover or press - is what a D-pad user sees,
 * so focus drives the entire visual state.
 */
@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val background = when {
        focused -> AppColors.Accent
        selected -> AppColors.AccentMuted
        else -> Color.White.copy(alpha = 0.10f)
    }

    Row(
        modifier = modifier
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
        Text(text = label, style = AppType.Label, color = AppColors.TextPrimary)
    }
}
