package com.streetball.voicescore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StreetBallDarkColors = darkColorScheme(
    primary = AppPrimary,
    onPrimary = AppBackground,
    background = AppBackground,
    onBackground = AppPrimary,
    surface = AppSurface,
    onSurface = AppPrimary,
)

@Composable
fun StreetBallVoiceScoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreetBallDarkColors,
        content = content,
    )
}
