package com.veliora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.veliora.ui.theme.Indigo600
import com.veliora.ui.theme.Indigo50
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Spacing

/** A "→ trace" pill that collapses the four-tap trace into one guided affordance. */
@Composable
fun TracePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "trace",
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(Indigo50)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.smMd, vertical = Spacing.xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "→",
            style = MaterialTheme.typography.labelSmall,
            color = Indigo600,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Indigo600,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
