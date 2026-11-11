package com.arslan.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.arslan.tv.player.PictureMode
import com.arslan.tv.player.Prefs
import com.arslan.tv.player.Quality
import com.arslan.tv.ui.components.QualityPicker
import com.arslan.tv.ui.components.TvButton
import com.arslan.tv.BuildConfig
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

@Composable
fun SettingsPanel(
    quality: Quality,
    pictureMode: PictureMode,
    padding: TvViewModel.Padding,
    adBlockEnabled: Boolean,
    preloadEnabled: Boolean,
    onQualityChange: (Quality) -> Unit,
    onPictureModeChange: (PictureMode) -> Unit,
    onPaddingChange: (TvViewModel.Edge, Int) -> Unit,
    onPaddingReset: () -> Unit,
    onAdBlockChange: (Boolean) -> Unit,
    onPreloadChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Scrim)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.Surface)
                .verticalScroll(scroll)
                .padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Text("Settings", style = AppType.Title, color = AppColors.TextPrimary)

            QualityPicker(
                selected = quality,
                maxHeight = 1080,
                onSelect = onQualityChange,
                modifier = Modifier.focusRequester(focus),
            )

            Section(
                title = "Picture",
                description = pictureMode.description,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PictureMode.entries.forEach { mode ->
                        TvButton(
                            label = mode.label,
                            onClick = { onPictureModeChange(mode) },
                            selected = mode == pictureMode,
                        )
                    }
                }
            }

            Section(
                title = "Screen padding",
                description = "Pull the picture in from whichever edges your TV crops " +
                    "(overscan). Each side adjusts on its own.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EdgeRow("Top", padding.top) { onPaddingChange(TvViewModel.Edge.TOP, it) }
                    EdgeRow("Bottom", padding.bottom) { onPaddingChange(TvViewModel.Edge.BOTTOM, it) }
                    EdgeRow("Left", padding.start) { onPaddingChange(TvViewModel.Edge.START, it) }
                    EdgeRow("Right", padding.end) { onPaddingChange(TvViewModel.Edge.END, it) }
                    TvButton(label = "Reset all edges", onClick = onPaddingReset)
                }
            }

            ToggleRow(
                title = "Block ads",
                description = "Drops ad-network requests and skips marked ad breaks in the stream",
                enabled = adBlockEnabled,
                onToggle = { onAdBlockChange(!adBlockEnabled) },
            )

            ToggleRow(
                title = "Pre-buffer on hover",
                description = "Starts loading a channel while it is highlighted, so OK plays instantly",
                enabled = preloadEnabled,
                onToggle = { onPreloadChange(!preloadEnabled) },
            )

            Text(
                text = "Arslan TV ${BuildConfig.VERSION_NAME}  ·  build ${BuildConfig.VERSION_CODE}",
                style = AppType.Label,
                color = AppColors.TextTertiary,
            )

            TvButton(label = "Done", onClick = onDismiss)
        }
    }
}

@Composable
private fun EdgeRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AppType.Label,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(72.dp),
        )
        TvButton(label = "−", onClick = { onChange(value - Prefs.OVERSCAN_STEP_DP) })
        Text(
            text = "${value}dp",
            style = AppType.Label,
            color = AppColors.TextPrimary,
            modifier = Modifier.width(64.dp).padding(horizontal = 6.dp),
        )
        TvButton(label = "+", onClick = { onChange(value + Prefs.OVERSCAN_STEP_DP) })
    }
}

@Composable
private fun Section(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title.uppercase(),
            style = AppType.Badge,
            color = AppColors.TextTertiary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
        Spacer(Modifier.height(10.dp))
        Text(text = description, style = AppType.Body, color = AppColors.TextSecondary)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        TvButton(
            label = "$title: ${if (enabled) "On" else "Off"}",
            onClick = onToggle,
            selected = enabled,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = description, style = AppType.Body, color = AppColors.TextSecondary)
    }
}
