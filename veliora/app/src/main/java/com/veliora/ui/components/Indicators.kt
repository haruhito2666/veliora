package com.veliora.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veliora.data.InvestigationPhase
import com.veliora.data.VoteTally
import com.veliora.ui.theme.Amber50
import com.veliora.ui.theme.Amber500
import com.veliora.ui.theme.Green50
import com.veliora.ui.theme.Green500
import com.veliora.ui.theme.Indigo50
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Red50
import com.veliora.ui.theme.Red500
import com.veliora.ui.theme.Slate100
import com.veliora.ui.theme.Slate200
import com.veliora.ui.theme.Slate300
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

// ── PhaseIndicator ───────────────────────────────────────────────────
// Horizontal strip: ◐ Gathering ── ◑ Debate ── ● Voting ── ◉ Report
// Current phase is highlighted; past phases are filled; future are gray.

@Composable
fun PhaseIndicator(
    currentPhase: InvestigationPhase,
    phaseProgress: Int = 0,
    modifier: Modifier = Modifier,
) {
    val phases = listOf(
        InvestigationPhase.GATHERING,
        InvestigationPhase.DEBATE,
        InvestigationPhase.VOTING,
        InvestigationPhase.REPORT,
    )
    val currentIndex = phases.indexOf(currentPhase).coerceAtLeast(0)

    // Box so the connector line can sit at the circle's vertical center
    // (the labels below would otherwise shift it). Circles live in
    // equal-weight columns → perfectly centered and evenly spaced.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Connector track — spans exactly from the first circle's center
        // (maxWidth/8) to the last circle's center (maxWidth - maxWidth/8)
        // so it never overshoots the outer circles. Vertically centered on
        // the 32dp circle row: 4dp tall, starting at y = 14dp (even rule).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = maxWidth / 8f, end = maxWidth / 8f)
                .offset(y = 14.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .background(Slate200)
        )
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                phases.forEach { phase ->
                    val isCurrent = phase == currentPhase
                    val isPast = phases.indexOf(phase) < currentIndex
                    val phaseColor = when {
                        isCurrent -> Indigo500
                        isPast -> Green500
                        else -> Slate300
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) Indigo50 else phaseColor.copy(alpha = 0.08f))
                                .semantics {
                                    contentDescription = phase.label
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = phase.glyph,
                                fontSize = 16.sp,
                                color = phaseColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = phase.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) Slate900 else Slate400,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private const val CIRCLE_SIZE = 32

// ── TrustBar ─────────────────────────────────────────────────────────
// Trust percentage + mini sparkline (simplified as bars).

@Composable
fun TrustBar(
    trust: Int,
    history: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val trustColor = when {
        trust >= 90 -> Green500
        trust >= 75 -> Indigo500
        else -> Amber500
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Percentage
        Text(
            text = "$trust%",
            style = MaterialTheme.typography.titleLarge,
            color = trustColor,
            fontWeight = FontWeight.Bold,
        )
        // Sparkline
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val maxVal = history.maxOrNull() ?: 100
                val minVal = history.minOrNull() ?: 0
                val range = (maxVal - minVal).coerceAtLeast(1)
                history.takeLast(12).forEach { value ->
                    // Round to the nearest EVEN dp — the app's spacing rule
                    val raw = ((value - minVal).toFloat() / range * 24f).coerceIn(4f, 28f)
                    val evenHeight = ((raw / 2f).toInt() * 2).coerceIn(4, 28).dp
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(evenHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(trustColor.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

// ── VoteTallyBar ────────────────────────────────────────────────────
// Animated vote bar with threshold line. Green = for, Red = against,
// Gray = abstain. Threshold line is dashed.

@Composable
fun VoteTallyBar(
    tally: VoteTally,
    modifier: Modifier = Modifier,
) {
    val total = tally.total.coerceAtLeast(1)
    val animatedFor by animateFloatAsState(
        targetValue = tally.forCount.toFloat() / total,
        animationSpec = tween(600),
        label = "forAnim",
    )
    val animatedAgainst by animateFloatAsState(
        targetValue = tally.againstCount.toFloat() / total,
        animationSpec = tween(600),
        label = "againstAnim",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Bar — BoxWithConstraints so the threshold line scales with width
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(Radii.sm))
                .background(Slate100),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // For
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFor)
                        .height(28.dp)
                        .background(if (tally.thresholdMet) Green500 else Indigo500)
                )
                // Against
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedAgainst)
                        .height(28.dp)
                        .background(Red500)
                )
            }
            // Threshold line — positioned at (threshold × real width)
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * tally.threshold)
                    .width(2.dp)
                    .height(28.dp)
                    .background(Slate900)
            )
        }
        // Counts
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            VoteCount(label = "For", count = tally.forCount, color = if (tally.thresholdMet) Green500 else Indigo500)
            VoteCount(label = "Against", count = tally.againstCount, color = Red500)
            VoteCount(label = "Abstain", count = tally.abstainCount, color = Slate400)
        }
        if (tally.thresholdMet) {
            Text(
                text = "DECISION REACHED — threshold met",
                style = MaterialTheme.typography.labelSmall,
                color = Green500,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun VoteCount(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.labelSmall,
            color = Slate700,
        )
    }
}

// ── ConfidenceBar ────────────────────────────────────────────────────
// Horizontal confidence bar for the report.

@Composable
fun ConfidenceBar(
    confidence: Int,
    modifier: Modifier = Modifier,
) {
    val color = when {
        confidence >= 80 -> Green500
        confidence >= 60 -> Indigo500
        else -> Amber500
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .background(Slate100)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(confidence / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(color)
            )
        }
        Text(
            text = "$confidence% confidence",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── RiskBadge ────────────────────────────────────────────────────────
// Small colored badge for risk levels.

@Composable
fun RiskBadge(
    level: com.veliora.data.RiskLevel?,
    modifier: Modifier = Modifier,
) {
    val (color, bg, label) = when (level) {
        com.veliora.data.RiskLevel.HIGH -> Triple(Red500, Red50, "HIGH")
        com.veliora.data.RiskLevel.MEDIUM -> Triple(Amber500, Amber50, "MEDIUM")
        com.veliora.data.RiskLevel.LOW -> Triple(Green500, Green50, "LOW")
        null -> Triple(Slate500, Slate100, "UNRATED")
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(bg)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
