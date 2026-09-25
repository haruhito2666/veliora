package com.veliora.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════
// VELIORA BRAND PALETTE — 60 / 30 / 10
// ═══════════════════════════════════════════════════════════════════════
// 60% — #FFF5F8  dominant canvas (backgrounds, large surfaces)
// 30% — #F9DFDF  brand pink  · #90CAF9 brand blue (containers, chips,
//       brand surfaces — one color at a time, never gradients)
// 10% — #0F3040  accent (primary text, buttons, links, selected states)
// ═══════════════════════════════════════════════════════════════════════

// ── Brand ────────────────────────────────────────────────────────────
val BrandPink = Color(0xFFF9DFDF)      // 30% — warm brand hue
val BrandBlue = Color(0xFF90CAF9)      // 30% — cool brand hue
val BrandBlueSoft = Color(0xFFDCECFC)  // light blue container tint
val BrandDark = Color(0xFF0F3040)      // 10% — deep teal-navy accent
val BrandDarkSoft = Color(0xFF84A3B2)  // muted variant of the accent

// ── Accent (10%) ─────────────────────────────────────────────────────
// Kept under the historical "Indigo" names so every existing
// reference (buttons, links, selected states, glyphs) picks up the
// brand accent without touching call sites.
val Indigo500 = BrandDark   // action glyphs, fills, accents
val Indigo600 = BrandDark   // primary buttons, links, selected
val Indigo50 = BrandPink    // soft containers, chips, highlights

// ── Neutrals (60% family) ─────────────────────────────────────────────
// Slate tinted toward the warm canvas; text steps stay readable.
val Slate900 = BrandDark    // primary text — the 10% anchor
val Slate700 = Color(0xFF3C4553)  // body text
val Slate600 = Color(0xFF525C6B)
val Slate500 = Color(0xFF6B7584)  // secondary text
val Slate400 = Color(0xFF9AA3B0)  // tertiary text / icons
val Slate300 = Color(0xFFE6D4D8)  // disabled / hint
val Slate200 = Color(0xFFF3DEE2)  // outline / divider
val Slate100 = Color(0xFFFBE9EC)  // surface variant (soft pink)
val Slate50 = Color(0xFFFFF5F8)   // background — the 60% canvas
val White = Color(0xFFFFFFFF)     // surface / card

// ── Status ───────────────────────────────────────────────────────────
// Functional only — never decorative.
val Green500 = Color(0xFF22C55E) // success / healthy / survived
val Green50 = Color(0xFFF0FDF4)
val Amber500 = Color(0xFFF59E0B) // warning / debating / medium risk
val Amber50 = Color(0xFFFFFBEB)
val Red500 = Color(0xFFEF4444)   // error / fire / high risk
val Red50 = Color(0xFFFEF2F2)
val Blue500 = Color(0xFF3B82F6)  // info / research
val Blue50 = Color(0xFFEFF6FF)

// ── Department Colors ────────────────────────────────────────────────
// Each department keeps a distinguishable accent.
val DeptCEO = BrandDark           // the top of the org — brand accent
val DeptResearch = Color(0xFF3B82F6) // Blue
val DeptEngineering = Color(0xFF8B5CF6) // Violet
val DeptFinance = Color(0xFF14B8A6) // Teal
val DeptMarketing = Color(0xFFF59E0B) // Amber
val DeptLegal = Color(0xFFEF4444)    // Red

// ── Department Colors — Light Variants ───────────────────────────────
// Soft tints of the accents above; used as fully-filled card surfaces
// with the deep accent as the on-card text color.
val DeptCEOLight = Color(0xFFCDDEE8)        // soft slate-teal
val DeptResearchLight = Color(0xFFDBEAFE)   // light blue
val DeptEngineeringLight = Color(0xFFDDD6FE) // light violet
val DeptFinanceLight = Color(0xFFCCFBF1)    // light teal
val DeptMarketingLight = Color(0xFFFDE68A)  // light amber
val DeptLegalLight = Color(0xFFFECACA)      // light red

// ── Channel Colors (Caspian) ──────────────────────────────────────────
val ChannelEmail = Color(0xFF3B82F6)
val ChannelTelegram = Color(0xFF0EA5E9)
val ChannelSlack = Color(0xFF8B5CF6)
val ChannelDiscord = Color(0xFF5865F2)
val ChannelWhatsApp = Color(0xFF22C55E)

// ── Evolution Event Colors ────────────────────────────────────────────
val EvoHire = Color(0xFF22C55E)     // green
val EvoFire = Color(0xFFEF4444)     // red
val EvoPromote = Color(0xFFF59E0B)  // amber
val EvoSplit = Color(0xFF8B5CF6)    // violet
val EvoMerge = BrandDark            // brand accent
val EvoPolicy = Color(0xFF64748B)   // slate
