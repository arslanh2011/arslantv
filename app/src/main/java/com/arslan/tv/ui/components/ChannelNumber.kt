package com.arslan.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

/** How long a channel number stays on screen after the last channel change. */
const val CHANNEL_NUMBER_TIMEOUT_MS = 4_000L

/** The small "Ch 12" tag in the corner of a card. */
@Composable
fun ChannelNumber(number: Int, modifier: Modifier = Modifier) {
    Text(
        text = "Ch $number",
        style = AppType.Badge,
        color = AppColors.TextPrimary,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * The same thing over video, sized to be read from a sofa. A translucent plate
 * rather than bare text, because a bright scene behind it would swallow it.
 */
@Composable
fun ChannelNumberOverlay(number: Int, modifier: Modifier = Modifier) {
    Text(
        text = "Ch $number",
        style = AppType.Title.copy(fontSize = 34.sp, fontWeight = FontWeight.Black),
        color = AppColors.TextPrimary,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
