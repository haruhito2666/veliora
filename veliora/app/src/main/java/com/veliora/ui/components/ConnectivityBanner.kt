package com.veliora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veliora.ui.theme.Amber50
import com.veliora.ui.theme.Amber500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate100
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Spacing

/** Connectivity state for the offline/live banners. */
enum class ConnectionState {
    LIVE,
    RECONNECTING,
    OFFLINE,
    STALLED,
}

private data class BannerSpec(
    val icon: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val bg: Color,
)

/**
 * Banner for offline / reconnecting / stalled states (22-mobile-ux §18).
 */
@Composable
fun ConnectivityBanner(
    state: ConnectionState,
    modifier: Modifier = Modifier,
    lastKnownTime: String = "12:04",
) {
    if (state == ConnectionState.LIVE) return

    val spec = when (state) {
        ConnectionState.RECONNECTING -> BannerSpec(
            icon = "⟳",
            title = "RECONNECTING",
            subtitle = "Attempting to restore live updates…",
            color = Amber500,
            bg = Amber50,
        )
        ConnectionState.OFFLINE -> BannerSpec(
            icon = "○",
            title = "OFFLINE — showing $lastKnownTime state",
            subtitle = "Live updates paused. You can keep browsing cached data.",
            color = Slate500,
            bg = Slate100,
        )
        ConnectionState.STALLED -> BannerSpec(
            icon = "⚠",
            title = "ORG STALLED — engine may be down",
            subtitle = "No heartbeat detected for a while.",
            color = Amber500,
            bg = Amber50,
        )
        ConnectionState.LIVE -> return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(spec.bg)
            .padding(horizontal = Spacing.md, vertical = Spacing.smMd),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = spec.icon, color = spec.color, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.labelMedium,
                color = spec.color,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = spec.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Slate700,
            )
        }
    }
}
