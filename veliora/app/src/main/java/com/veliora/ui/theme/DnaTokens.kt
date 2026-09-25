package com.veliora.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * DNA-driven visual tokens (22-mobile-ux.md §16), mapped minimally:
 * - Risk      → accent warmth (cool blue → warm red)
 * - Hierarchy → card elevation
 * - Speed     → animation duration scale
 */
data class DnaTokens(
    val accentWarmth: Color = BrandBlue,      // cool default
    val cardElevation: Dp = 0.dp,             // flat default (low hierarchy)
    val animDurationScale: Float = 1f,        // normal speed
)

val LocalDnaTokens = staticCompositionLocalOf { DnaTokens() }

/** Compute tokens from an org DNA profile (values 0..100). */
fun dnaTokensFor(
    risk: Int,
    hierarchy: Int,
    speed: Int,
): DnaTokens {
    // Warmth: 0 (cool blue) → 1 (warm red), driven by risk.
    val warmth = risk / 100f
    val accent = lerpColor(BrandBlue, Color(0xFFF97316), warmth)
    val elevation = when {
        hierarchy >= 70 -> 4.dp
        hierarchy >= 40 -> 2.dp
        else -> 0.dp
    }
    val durationScale = when {
        speed >= 70 -> 0.7f
        speed >= 40 -> 1f
        else -> 1.4f
    }
    return DnaTokens(
        accentWarmth = accent,
        cardElevation = elevation,
        animDurationScale = durationScale,
    )
}

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = 1f,
)
