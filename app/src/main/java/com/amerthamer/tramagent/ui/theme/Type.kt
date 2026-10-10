package com.amerthamer.tramagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.amerthamer.tramagent.R

/** Pontmátrix betűtípus a kijelzős elemekhez (VT323, OFL licenc). */
val LedFont = FontFamily(Font(R.font.vt323))

val Typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.6.sp),
)
