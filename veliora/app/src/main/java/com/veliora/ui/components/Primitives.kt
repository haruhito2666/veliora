package com.veliora.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veliora.ui.theme.Amber500
import com.veliora.ui.theme.ChannelDiscord
import com.veliora.ui.theme.ChannelEmail
import com.veliora.ui.theme.ChannelSlack
import com.veliora.ui.theme.ChannelTelegram
import com.veliora.ui.theme.ChannelWhatsApp
import com.veliora.ui.theme.Green500
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Indigo600
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Red500
import com.veliora.ui.theme.Slate200
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

// ── LivePulse ────────────────────────────────────────────────────────
// A small pulsing dot. Green = alive, gray = paused/stalled.
// Uses contentDescription for accessibility — never color alone.

@Composable
fun LivePulse(
    alive: Boolean = true,
    size: Int = 8,
    modifier: Modifier = Modifier,
) {
    val color = if (alive) Green500 else Slate400
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (alive) alpha else 0.5f))
            .semantics {
                contentDescription = if (alive) "Organization active" else "Organization paused"
            }
    )
}

// ── SectionHeader ────────────────────────────────────────────────────
// Uppercase label + optional live count badge. Generous top margin
// to create visual breathing room between sections.

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(top = Spacing.xl, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (isLive) LivePulse(alive = true, size = 6)
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Slate500,
        )
        if (count != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(Slate200)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── StatusBadge ─────────────────────────────────────────────────────
// Small pill for LIVE, PAUSED, STALLED states.

@Composable
fun StatusBadge(
    text: String,
    color: Color = Green500,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.smMd, vertical = Spacing.xs),
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
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
    }
}

// ── HealthRing ───────────────────────────────────────────────────────
// Circular progress ring for org health. Minimal — just the ring
// and the number in the center.

@Composable
fun HealthRing(
    health: Int,
    modifier: Modifier = Modifier,
    ringSize: Int = 44,
    strokeWidth: Int = 4,
) {
    val color = when {
        health >= 85 -> Green500
        health >= 60 -> Amber500
        else -> Red500
    }
    // The ring always starts empty (0°) and animates up to the current
    // health on first appearance (e.g. when the app opens on the org list).
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(health) {
        sweep.animateTo(
            targetValue = (health / 100f) * 360f,
            animationSpec = tween(durationMillis = 2200, easing = LinearEasing),
        )
    }

    Box(
        modifier = modifier.size(ringSize.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ringSize.dp)) {
            val stroke = strokeWidth.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            // Track (full circle, light)
            drawArc(
                color = Slate200.copy(alpha = 0.5f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress arc (top-start, clockwise) — sweeps in with the animation
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$health%",
            style = MaterialTheme.typography.labelSmall,
            color = Slate900,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

// ── DnaChips ─────────────────────────────────────────────────────────
// Row of DNA parameter chips. Each shows name + value.
// Minimal — no bars, just text + a subtle dot indicating level.

@Composable
fun DnaChips(
    dna: com.veliora.data.OrgDNA,
    modifier: Modifier = Modifier,
    showAll: Boolean = false,
) {
    val params = if (showAll) dna.toList() else dna.toList().take(4)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        params.forEach { param ->
            DnaChip(name = param.name, value = param.value)
        }
    }
}

@Composable
fun DnaChip(name: String, value: Int) {
    val color = when {
        value >= 75 -> Indigo500
        value >= 50 -> Color(0xFF8B5CF6) // violet — standardized status color
        else -> Slate400
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = Spacing.smMd, vertical = Spacing.xs),
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
            text = "$name $value%",
            style = MaterialTheme.typography.labelSmall,
            color = Slate500,
            fontSize = 11.sp,
        )
    }
}

// ── ChannelIcon ─────────────────────────────────────────────────────
// Small circular icon for Caspian channels.

@Composable
fun ChannelIcon(
    channel: com.veliora.data.CaspianChannel,
    modifier: Modifier = Modifier,
) {
    val color = when (channel) {
        com.veliora.data.CaspianChannel.EMAIL -> ChannelEmail
        com.veliora.data.CaspianChannel.TELEGRAM -> ChannelTelegram
        com.veliora.data.CaspianChannel.SLACK -> ChannelSlack
        com.veliora.data.CaspianChannel.DISCORD -> ChannelDiscord
        com.veliora.data.CaspianChannel.WHATSAPP -> ChannelWhatsApp
    }
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(Radii.sm))
            .background(color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = channel.icon,
            fontSize = 16.sp,
            color = color,
        )
    }
}

// ── ScrollableHeader ─────────────────────────────────────────────────
// Page header that lives INSIDE the scroll content (first item of the
// LazyColumn) instead of a fixed TopAppBar layer. It moves with the
// page as the user scrolls — the page is one canvas, no overlapping
// header background.

@Composable
fun ScrollableHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Slate700,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Slate900,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

// ── TextLink ─────────────────────────────────────────────────────────
// Small indigo text link with a chevron — used for "View All" affordances.

@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Indigo600,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "▸",
            style = MaterialTheme.typography.labelMedium,
            color = Indigo600,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── EmptyState ───────────────────────────────────────────────────────
// For screens with no data yet. Minimal, centered, with generous spacing.

@Composable
fun EmptyState(
    icon: String = "○",
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl)
            .heightIn(min = 160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = icon,
            fontSize = 48.sp,
            color = Slate400,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Slate500,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Slate400,
                textAlign = TextAlign.Center,
            )
        }
    }
}
