package com.arslan.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arslan.tv.ui.components.TvButton
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

/** Shown while the resolver works out which of a channel's sources is up. */
@Composable
fun OpeningScreen(channelName: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(20.dp))
            Text(channelName, style = AppType.Title, color = AppColors.TextPrimary)
        }
    }
}

/** Shown when every source a channel lists has failed. */
@Composable
fun ChannelFailedScreen(
    channelName: String,
    reason: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$channelName is not responding",
                style = AppType.Title,
                color = AppColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(text = reason, style = AppType.Body, color = AppColors.TextSecondary)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(label = "Try again", onClick = onRetry)
                TvButton(label = "Back to menu", onClick = onBack)
            }
        }
    }
}
