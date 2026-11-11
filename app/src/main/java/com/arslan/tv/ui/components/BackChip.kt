package com.arslan.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
 * The "◀ Back" affordance every screen below home carries in its top-left
 * corner. The Back key already does this; the chip is here so the way out is
 * visible rather than something you have to know, and so a remote without a
 * usable Back key can still reach it.
 *
 * It is focusable, so it can be clicked, but it is not auto-focused: the
 * content of the screen is what should have the remote when it opens.
 */
@Composable
fun BackChip(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Back",
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onBack)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) AppColors.Accent else Color.Black.copy(alpha = 0.55f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.FocusRing else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("◀", style = AppType.Label, color = AppColors.TextPrimary)
        Text(label, style = AppType.Label, color = AppColors.TextPrimary)
    }
}
