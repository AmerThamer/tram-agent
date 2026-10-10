package com.amerthamer.tramagent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TramColorScheme = darkColorScheme(
    primary = TramYellow,
    onPrimary = Night,
    secondary = LedAmber,
    onSecondary = Night,
    background = Night,
    onBackground = TextPrimary,
    surface = Night,
    onSurface = TextPrimary,
    surfaceVariant = NightHigh,
    onSurfaceVariant = TextSecondary,
    outline = NightOutline,
    error = AlertRed,
)

/** Az app egyetlen témája – szándékosan mindig sötét („Éjszakai BKK”). */
@Composable
fun TramAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TramColorScheme,
        typography = Typography,
        content = content,
    )
}
