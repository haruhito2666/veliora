package com.veliora.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veliora.data.CaspianThread
import com.veliora.data.ChallengeStatus
import com.veliora.data.Department
import com.veliora.data.Evidence
import com.veliora.data.EvolutionEvent
import com.veliora.data.EvolutionType
import com.veliora.data.Investigation
import com.veliora.ui.theme.Amber500
import com.veliora.ui.theme.EvoFire
import com.veliora.ui.theme.EvoHire
import com.veliora.ui.theme.EvoMerge
import com.veliora.ui.theme.EvoPolicy
import com.veliora.ui.theme.EvoPromote
import com.veliora.ui.theme.EvoSplit
import com.veliora.ui.theme.Green500
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Indigo50
import com.veliora.ui.theme.LocalDeptColors
import com.veliora.ui.theme.LocalDnaTokens
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Red500
import com.veliora.ui.theme.Slate100
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing
import com.veliora.ui.theme.White

// ── VelioraCard ──────────────────────────────────────────────────────
// Base card style — white surface, 16dp radius, subtle shadow.
// All content cards inherit this look.

@Composable
fun VelioraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // DNA-driven elevation — high-hierarchy orgs get shadowed cards.
    val dnaTokens = LocalDnaTokens.current
    val base = Modifier
        .clip(RoundedCornerShape(Radii.lg))
        .shadow(elevation = dnaTokens.cardElevation, shape = RoundedCornerShape(Radii.lg))
        .background(White)
    Box(
        modifier = if (onClick != null) base.then(modifier).clickable { onClick() } else base.then(modifier),
    ) {
        content()
    }
}

// ── DepartmentCard ───────────────────────────────────────────────────
// Row in the departments list on Home. Left color stripe + name +
// activity + trust. Generous internal padding.

@Composable
fun DepartmentCard(
    department: Department,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deptColors = LocalDeptColors.current
    val cardColor = deptColors.lightForName(department.name)
    val accentColor = deptColors.forName(department.name)

    // Fill = department progress (0-100): white on the left, progressively
    // filled with the department color to the right. Animated so a progress
    // change visibly "fills" the card. Driven ONLY by `department.progress`.
    val fillFraction = (department.progress.coerceIn(0, 100)) / 100f
    val animatedFill by animateFloatAsState(
        targetValue = fillFraction,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "deptCardFill",
    )
    // The headline number mirrors the fill, so the % visibly rises as the
    // color fills — the two are one and the same signal.
    val animatedProgress by animateIntAsState(
        targetValue = department.progress.coerceIn(0, 100),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "deptCardProgress",
    )

    VelioraCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        // The card surface is white; a gradient fill sweeps in from the
        // left edge up to `animatedFill`, so the color "fills" the card
        // as the department's progress grows.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to cardColor,
                            animatedFill to cardColor,
                            (animatedFill + 0.001f).coerceAtMost(1f) to White,
                            1f to White,
                        ),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = department.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$animatedProgress%",
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (department.isAlive) LivePulse(alive = true, size = 8)
                        Text(
                            text = department.currentActivity,
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "♥ ${department.trust} · ${department.workerCount} workers",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.smMd))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = accentColor.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── InvestigationCard ───────────────────────────────────────────────
// Row in the active investigations list. Phase glyph + goal + progress
// + department chips + stats.

@Composable
fun InvestigationCard(
    investigation: Investigation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VelioraCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.mdLg),
            verticalArrangement = Arrangement.spacedBy(Spacing.smMd),
        ) {
            // Phase glyph (fixed box, baseline-aligned with the goal) + goal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.smMd),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(Indigo50),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = investigation.phase.glyph,
                        fontSize = 14.sp,
                        color = Indigo500,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = investigation.goal,
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate900,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            // Progress — bar and its % share one baseline-aligned row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.smMd),
            ) {
                ProgressBar(
                    progress = investigation.progress,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${investigation.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Indigo500,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(34.dp),
                    textAlign = TextAlign.End,
                )
            }
            // Phase label + stats — one aligned chip row
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatChip(
                    text = investigation.phase.label,
                    color = Indigo500,
                )
            }
        }
    }
}

// ── EvidenceRow ─────────────────────────────────────────────────────
// Single evidence item in the evidence feed. Timestamp + dept + type +
// description + challenge status.

@Composable
fun EvidenceRow(
    evidence: Evidence,
    modifier: Modifier = Modifier,
) {
    val deptColors = LocalDeptColors.current
    val deptColor = deptColors.forName(evidence.department)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.smMd),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smMd),
    ) {
        // Timestamp — fixed column so descriptions align across rows
        Text(
            text = evidence.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = Slate400,
            modifier = Modifier.width(44.dp).padding(top = 2.dp),
        )
        // Department — pill with fixed min width keeps dept codes aligned
        Text(
            text = evidence.department.take(3),
            style = MaterialTheme.typography.labelSmall,
            color = deptColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .width(40.dp)
                .clip(RoundedCornerShape(Radii.sm))
                .background(deptColor.copy(alpha = 0.1f))
                .padding(horizontal = Spacing.xs, vertical = 2.dp),
            textAlign = TextAlign.Center,
        )
        // Description
        Text(
            text = evidence.description,
            style = MaterialTheme.typography.bodySmall,
            color = Slate700,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Challenge status — fixed slot so text never shifts under the glyph
        if (evidence.challengeStatus != null) {
            Text(
                text = evidence.challengeStatus.glyph,
                fontSize = 14.sp,
                color = when (evidence.challengeStatus) {
                    ChallengeStatus.CHALLENGED -> Red500
                    ChallengeStatus.REBUTTED -> Amber500
                    ChallengeStatus.SURVIVED -> Green500
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(18.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── CaspianThreadRow ─────────────────────────────────────────────────
// External communication row. Channel icon + dept + recipient + message
// + reply indicator.

@Composable
fun CaspianThreadRow(
    thread: CaspianThread,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VelioraCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.mdLg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ChannelIcon(channel = thread.channel)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = thread.department,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalDeptColors.current.forName(thread.department),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "→ ${thread.recipient}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = thread.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate700,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (thread.hasReply) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "↩",
                            fontSize = 12.sp,
                            color = Green500,
                        )
                        Text(
                            text = thread.replySummary ?: thread.reply ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Green500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "· ${thread.timestamp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400,
                        )
                    }
                } else if (thread.isAwaitingReply) {
                    Text(
                        text = "awaiting reply · ${thread.timestamp}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400,
                    )
                }
            }
        }
    }
}

// ── EvolutionEventRow ───────────────────────────────────────────────
// Org change row. Type glyph + description + reason + time.

@Composable
fun EvolutionEventRow(
    event: EvolutionEvent,
    modifier: Modifier = Modifier,
) {
    val typeColor = when (event.type) {
        EvolutionType.HIRE -> EvoHire
        EvolutionType.FIRE -> EvoFire
        EvolutionType.PROMOTE -> EvoPromote
        EvolutionType.SPLIT -> EvoSplit
        EvolutionType.MERGE -> EvoMerge
        EvolutionType.POLICY -> EvoPolicy
    }

    VelioraCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.mdLg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Type icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(typeColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = event.type.glyph,
                    fontSize = 18.sp,
                    color = typeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = event.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate900,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "Reason: ${event.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
                Text(
                    text = event.daysAgo?.let { "$it days ago" } ?: "Time unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                )
            }
        }
    }
}

// ── ProgressBar ──────────────────────────────────────────────────────
// Thin progress bar. Minimal — just a track + fill.

@Composable
fun ProgressBar(
    progress: Int,
    modifier: Modifier = Modifier,
    trackHeight: Int = 4,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight.dp)
            .clip(RoundedCornerShape(Radii.pill))
            .background(Slate100)
    ) {
        // Brand pink fill — one solid brand color
        Box(
            modifier = Modifier
                .fillMaxWidth(progress / 100f)
                .height(trackHeight.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .background(BrandGradientStrong)
        )
    }
}

// ── StatChip ─────────────────────────────────────────────────────────
// Small stat pill: "68%", "4 meetings", "12 evidence"

@Composable
fun StatChip(
    text: String,
    color: Color = Slate500,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = if (color == Indigo500) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(Slate100)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
