package com.veliora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Color Scheme — 60/30/10 ───────────────────────────────────────────
// 60% #FFF5F8 canvas · 30% #F9DFDF + #90CAF9 brand · 10% #0F3040 accent.
private val VelioraColorScheme = lightColorScheme(
    primary = BrandDark,           // 10% — buttons, selected states
    onPrimary = White,
    primaryContainer = BrandPink,  // 30% — chips, soft containers
    onPrimaryContainer = BrandDark,
    secondary = BrandBlue,         // 30% — brand blue accents
    onSecondary = BrandDark,
    secondaryContainer = BrandBlueSoft,
    onSecondaryContainer = BrandDark,
    tertiary = Blue500,
    onTertiary = White,
    tertiaryContainer = Blue50,
    onTertiaryContainer = Color(0xFF1E40AF),
    error = Red500,
    onError = White,
    errorContainer = Red50,
    onErrorContainer = Red500,
    background = Slate50,          // 60% — the canvas
    onBackground = Slate900,
    surface = White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate200,
    outlineVariant = Slate100,
)

// ── Department Color Local ────────────────────────────────────────────
// Allows any composable to look up the accent color for a department
// without prop-drilling. Set at the org level, consumed by cards.
data class DeptColors(
    val ceo: Color = DeptCEO,
    val research: Color = DeptResearch,
    val engineering: Color = DeptEngineering,
    val finance: Color = DeptFinance,
    val marketing: Color = DeptMarketing,
    val legal: Color = DeptLegal,
    // Light tint variants — the card fill; the deep color is the text.
    val ceoLight: Color = DeptCEOLight,
    val researchLight: Color = DeptResearchLight,
    val engineeringLight: Color = DeptEngineeringLight,
    val financeLight: Color = DeptFinanceLight,
    val marketingLight: Color = DeptMarketingLight,
    val legalLight: Color = DeptLegalLight,
) {
    fun forName(name: String): Color = when {
        name.contains("CEO", ignoreCase = true) -> ceo
        name.contains("Research", ignoreCase = true) -> research
        name.contains("Engineering", ignoreCase = true) || name.contains("Eng", ignoreCase = true) -> engineering
        name.contains("Finance", ignoreCase = true) || name.contains("Fin", ignoreCase = true) -> finance
        name.contains("Marketing", ignoreCase = true) || name.contains("Mkt", ignoreCase = true) -> marketing
        name.contains("Legal", ignoreCase = true) -> legal
        else -> Slate400
    }

    /** Light tint for a department name — used as the card fill. */
    fun lightForName(name: String): Color = when {
        name.contains("CEO", ignoreCase = true) -> ceoLight
        name.contains("Research", ignoreCase = true) -> researchLight
        name.contains("Engineering", ignoreCase = true) || name.contains("Eng", ignoreCase = true) -> engineeringLight
        name.contains("Finance", ignoreCase = true) || name.contains("Fin", ignoreCase = true) -> financeLight
        name.contains("Marketing", ignoreCase = true) || name.contains("Mkt", ignoreCase = true) -> marketingLight
        name.contains("Legal", ignoreCase = true) -> legalLight
        else -> Slate100
    }
}

val LocalDeptColors = staticCompositionLocalOf { DeptColors() }

@Composable
fun VelioraTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDeptColors provides DeptColors(),
        LocalDnaTokens provides DnaTokens(),
    ) {
        MaterialTheme(
            colorScheme = VelioraColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
