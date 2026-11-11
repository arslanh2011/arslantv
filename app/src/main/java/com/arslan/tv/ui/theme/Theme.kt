package com.arslan.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Streaming-app palette: near-black ground, one saturated red accent. */
object AppColors {
    val Background = Color(0xFF0B0B0C)
    val Surface = Color(0xFF17171A)
    val SurfaceElevated = Color(0xFF222226)
    val Accent = Color(0xFFE50914)
    val AccentMuted = Color(0xFF8E0610)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextTertiary = Color(0xFF6E6E73)
    val FocusRing = Color(0xFFFFFFFF)
    val LiveRed = Color(0xFFE50914)
    val Scrim = Color(0xE60B0B0C)
}

/** TV viewers sit ~3 m away, so every size here is a step up from phone scale. */
object AppType {
    val Hero = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
    val RowHeader = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
    val Badge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
}

@Composable
fun ArslanTvTheme(content: @Composable () -> Unit) {
    // A TV app is always dark; isSystemInDarkTheme is read only so the call site
    // stays honest about ignoring it.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Accent,
            background = AppColors.Background,
            surface = AppColors.Surface,
            onPrimary = AppColors.TextPrimary,
            onBackground = AppColors.TextPrimary,
            onSurface = AppColors.TextPrimary,
        ),
        content = content,
    )
}
