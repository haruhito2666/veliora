package com.veliora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veliora.data.EvolutionType
import com.veliora.ui.theme.EvoHire
import com.veliora.ui.theme.EvoFire
import com.veliora.ui.theme.EvoPromote
import com.veliora.ui.theme.EvoSplit
import com.veliora.ui.theme.EvoMerge
import com.veliora.ui.theme.EvoPolicy
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate100
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

/**
 * A simple before→after org chart diff.
 *
 * - Node = a department/worker.
 * - Added nodes are green, removed red, rewired/kept amber.
 * - Edges are drawn between a manager and its reports.
 *
 * Built on Canvas — no external graph dependency.
 */
@Composable
fun OrgChartDiff(
    before: List<Pair<String, Color>>,
    after: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrgChartColumn(label = "BEFORE", nodes = before)
            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = Slate400,
            )
            OrgChartColumn(label = "AFTER", nodes = after)
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LegendDot(EvoHire, "Added")
            LegendDot(EvoFire, "Removed")
            LegendDot(EvoPromote, "Rewired")
        }
    }
}

@Composable
private fun OrgChartColumn(label: String, nodes: List<Pair<String, Color>>) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.lg))
            .background(Slate100)
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate500,
            fontWeight = FontWeight.Bold,
        )
        nodes.forEach { (name, color) ->
            ChartNode(name = name, color = color)
        }
    }
}

@Composable
private fun ChartNode(name: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.md))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = Slate900,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate500,
        )
    }
}

/** Map an evolution event to its before/after chart. */
fun chartForEvent(
    type: EvolutionType,
    description: String,
): Pair<List<Pair<String, Color>>, List<Pair<String, Color>>> = when (type) {
    EvolutionType.SPLIT -> {
        val before = listOf("Engineering" to Indigo500)
        val after = listOf(
            "Engineering" to EvoHire,
            "DevOps" to EvoHire,
        )
        before to after
    }
    EvolutionType.MERGE -> {
        val before = listOf(
            "Research" to Indigo500,
            "Marketing" to Indigo500,
        )
        val after = listOf("Insights" to EvoHire)
        before to after
    }
    EvolutionType.HIRE -> {
        val before = listOf("Research" to Indigo500)
        val after = listOf("Research" to Indigo500, "Sam Patel" to EvoHire)
        before to after
    }
    EvolutionType.FIRE -> {
        val before = listOf("Finance" to Indigo500, "Temp-Analyst" to Indigo500)
        val after = listOf("Finance" to Indigo500)
        before to after
    }
    EvolutionType.PROMOTE -> {
        val before = listOf("Research" to Indigo500, "Dr. Lin" to Indigo500)
        val after = listOf("Research" to Indigo500, "Dr. Lin" to EvoPromote)
        before to after
    }
    EvolutionType.POLICY -> {
        val before = listOf("Marketing" to Indigo500)
        val after = listOf("Marketing" to Indigo500)
        before to after
    }
}
