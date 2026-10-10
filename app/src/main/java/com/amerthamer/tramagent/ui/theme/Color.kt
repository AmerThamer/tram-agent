package com.amerthamer.tramagent.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Éjszakai alap
val Night = Color(0xFF0B1426)
val NightHigh = Color(0xFF1B2F55)
val NightSurface = Color(0x0FFFFFFF)        // üveg kártya kitöltés
val NightOutline = Color(0x17FFFFFF)        // üveg kártya kerete
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9FB0CC)

// BKK villamossárga – fő kiemelőszín
val TramYellow = Color(0xFFFFD800)
val TramYellowDeep = Color(0xFFFFB300)

// Utastájékoztató (LED / pontmátrix) elemek
val LedAmber = Color(0xFFFFB000)
val LedAmberDim = Color(0xFF8A6A20)
val LedPanel = Color(0xFF0A0A0A)
val LedPanelDot = Color(0xFF2A1D00)

// Állapotszínek
val AlertRed = Color(0xFFE53935)
val OnTimeGreen = Color(0xFF43A047)
val LateBlue = Color(0xFF42A5F5)

/** A képernyők közös háttere: felül világosabb éjkék „égbolt”. */
val NightBackground = Brush.radialGradient(
    colors = listOf(NightHigh, Night),
    radius = 1600f,
)
