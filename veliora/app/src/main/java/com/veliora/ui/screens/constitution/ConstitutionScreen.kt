package com.veliora.ui.screens.constitution

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.veliora.app
import com.veliora.data.Amendment
import com.veliora.data.AmendmentStatus
import com.veliora.data.Constitution
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.rememberHapticContext
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.StatusBadge
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Amber500
import com.veliora.ui.theme.Green500
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Indigo50
import com.veliora.ui.theme.Indigo600
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate100
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing
import com.veliora.ui.theme.White
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
// Constitution Screen — the org's governing law.
// Mission · Principles · Core Values · Decision Rules · Amendments.
// Clean, authoritative, generous white space. Data from VelioraRepository.
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun ConstitutionScreen(
    orgId: String,
) {
    val context = LocalContext.current
    val app = remember { context.app }

    var constitution by remember { mutableStateOf<Constitution?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(orgId) {
        loading = true
        error = null
        runCatching { app.repository.constitution(orgId) }
            .onSuccess { dto -> constitution = Mapper.toConstitution(dto) }
            .onFailure { e -> error = e.message ?: "Failed to load constitution" }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val data = constitution
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            data == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = "◉",
                        title = "Constitution unavailable",
                        subtitle = error ?: "No constitution for this organization.",
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        top = Spacing.xs,
                        end = Spacing.lg,
                        bottom = Spacing.lg,
                    ),
                ) {
                    // ── Scrollable header — moves with content, one canvas ───────
                    item {
                        ScrollableHeader(title = "Constitution")
                    }

                    // ── Mission ──────────────────────────────────────────────
                    item {
                        SectionHeader(title = "Mission")
                        VelioraCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = data.mission,
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = Slate700,
                                modifier = Modifier.padding(Spacing.mdLg),
                            )
                        }
                    }

                    // ── Principles ───────────────────────────────────────────
                    item {
                        SectionHeader(title = "Principles", count = data.principles.size)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            data.principles.forEach { principle ->
                                BulletRow(text = principle)
                            }
                        }
                    }

                    // ── Core Values ──────────────────────────────────────────
                    item {
                        SectionHeader(title = "Core Values", count = data.coreValues.size)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            data.coreValues.forEach { value ->
                                ValueChip(value = value)
                            }
                        }
                    }

                    // ── Decision Rules ──────────────────────────────────────
                    item {
                        SectionHeader(title = "Decision Rules", count = data.decisionRules.size)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            data.decisionRules.forEach { rule ->
                                BulletRow(text = rule)
                            }
                        }
                    }

                    // ── Amendments ──────────────────────────────────────────
                    item {
                        SectionHeader(title = "Amendments", count = data.amendments.size)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            data.amendments.forEach { amendment ->
                                AmendmentRow(
                                    text = amendment.text,
                                    daysAgo = amendment.daysAgo,
                                    status = amendment.status,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

// ── Bullet row (principles & decision rules) ──────────────────────────
// A '▸' glyph in the org accent leads each line. Clean and scannable.

@Composable
private fun BulletRow(text: String) {
    VelioraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.mdLg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "▸",
                color = Indigo500,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate700,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Core value chip ───────────────────────────────────────────────────
// Indigo-tinted pill — the org's signature accent on its values.

@Composable
private fun ValueChip(value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(Indigo50)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = Indigo600,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Amendment row ─────────────────────────────────────────────────────
// Amendment text + age on the left, status pill on the right.
// Ratified = green, Debating = amber.

@Composable
private fun AmendmentRow(
    text: String,
    daysAgo: Int,
    status: AmendmentStatus,
) {
    val statusColor = when (status) {
        AmendmentStatus.RATIFIED -> Green500
        AmendmentStatus.DEBATING -> Amber500
    }
    VelioraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.mdLg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate900,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${daysAgo} d ago",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                )
            }
            StatusBadge(text = status.label, color = statusColor)
        }
    }
}
