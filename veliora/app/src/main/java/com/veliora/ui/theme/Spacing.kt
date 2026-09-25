package com.veliora.ui.theme

import androidx.compose.ui.unit.dp

// ── Spacing System ────────────────────────────────────────────────────
// Generous, deliberate whitespace. The gap between elements is as
// important as the elements themselves. This is what makes the UI
// feel calm, uncluttered, and premium.

object Spacing {
    /** 4dp — tight internal padding (icon + label) */
    val xs = 4.dp

    /** 8dp — small internal padding, chip gaps */
    val sm = 8.dp

    /** 12dp — medium internal padding, card inner spacing */
    val smMd = 12.dp

    /** 16dp — standard padding, card content padding */
    val md = 16.dp

    /** 20dp — comfortable card padding */
    val mdLg = 20.dp

    /** 24dp — section spacing, screen horizontal padding */
    val lg = 24.dp

    /** 32dp — large section gaps */
    val xl = 32.dp

    /** 48dp — extra-large gaps between major sections */
    val xxl = 48.dp

    /** 64dp — top/bottom screen breathing room */
    val xxxl = 64.dp
}

// ── Corner Radii ──────────────────────────────────────────────────────
object Radii {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp   // default card radius
    val xl = 20.dp   // large cards / bottom sheets
    val pill = 1000.dp // badges, chips, pulse (even, per app spacing rule)
}

// ── Elevation ─────────────────────────────────────────────────────────
// Every value is even (divides by 2) — the app's spacing rule.
object Elevation {
    val none = 0.dp
    val sm = 2.dp   // subtle card
    val md = 2.dp   // raised card
    val lg = 4.dp   // bottom sheet
    val xl = 8.dp   // dialog
}
