package com.veliora.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.veliora.R

// ── Type Scale ────────────────────────────────────────────────────────
// Clean, restrained hierarchy. Sizes chosen for generous line-height
// and readability on Android. Each level breathes — no cramped text.

// ── Satoshi Font Family ───────────────────────────────────────────────
// The single typeface of Veliora. Multiple weights as discrete resource
// fonts; Compose picks the nearest weight automatically when a text
// style requests a weight that has no dedicated file (e.g. SemiBold 600
// resolves to Bold 700).
val SatoshiFamily = FontFamily(
    Font(R.font.satoshi_light, weight = FontWeight.Light, style = FontStyle.Normal),
    Font(R.font.satoshi_regular, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(R.font.satoshi_medium, weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(R.font.satoshi_bold, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(R.font.satoshi_black, weight = FontWeight.Black, style = FontStyle.Normal),
    Font(R.font.satoshi_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
)

val Typography = Typography(
    // Display — used only for the org name on Home header
    displaySmall = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        color = Slate900,
    ),
    // Headline — section titles
    headlineMedium = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = Slate900,
    ),
    headlineSmall = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        color = Slate900,
    ),
    // Title — card titles, screen sub-headers
    titleLarge = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        color = Slate900,
    ),
    titleMedium = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
        color = Slate900,
    ),
    // Body — primary reading text
    bodyLarge = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        color = Slate700,
    ),
    bodyMedium = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        color = Slate700,
    ),
    bodySmall = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        color = Slate500,
    ),
    // Label — section headers, badges, metadata
    labelLarge = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.8.sp,
        color = Slate500,
    ),
    labelMedium = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
        color = Slate500,
    ),
    labelSmall = TextStyle(
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
        color = Slate400,
    ),
)
