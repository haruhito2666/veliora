package com.veliora.ui.screens.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veliora.app
import com.veliora.data.Decision
import com.veliora.data.Worker
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.LiveOrgEffect
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.TrustBar
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Blue500
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

@Composable
fun WorkerDetailScreen(
    orgId: String,
    workerId: String,
) {
    val context = LocalContext.current
    val app = remember { context.app }
    val store = app.orgStore
    val liveOrg by store.organization.collectAsStateWithLifecycle()

    var worker by remember { mutableStateOf<Worker?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LiveOrgEffect(orgId, store)

    LaunchedEffect(orgId, workerId) {
        loading = true
        error = null
        runCatching { app.repository.organization(orgId) }
            .onSuccess { snap ->
                worker = snap.workers
                    .map { Mapper.toWorker(it) }
                    .find { it.id == workerId }
            }
            .onFailure { e -> error = e.message ?: "Failed to load organization" }
        loading = false
    }

    val data = liveOrg?.departments
        ?.flatMap { it.workers }
        ?.find { it.id == workerId }
        ?: worker
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        when {
            loading && data == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            data == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = "Worker not found",
                        subtitle = error,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.xs,
                        bottom = Spacing.xxl,
                    ),
                ) {
                    // ── Header ────────────────────────────────────────────────
                    item {
                        ScrollableHeader(
                            title = data.name,
                            subtitle = data.role,
                        )
                    }

                    // ── Trust ─────────────────────────────────────────────────
                    item {
                        SectionHeader(title = "Trust")
                        VelioraCard(modifier = Modifier.fillMaxWidth()) {
                            TrustBar(
                                trust = data.trust,
                                history = data.trustHistory,
                                modifier = Modifier.padding(Spacing.mdLg),
                            )
                        }
                    }

                    // ── Influence ────────────────────────────────────────────
                    item {
                        SectionHeader(title = "Influence")
                        VelioraCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = data.influence,
                                style = MaterialTheme.typography.titleLarge,
                                color = Slate900,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(Spacing.mdLg),
                            )
                        }
                    }

                    // ── Current ───────────────────────────────────────────────
                    item {
                        SectionHeader(title = "Current")
                        VelioraCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = data.currentActivity,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Slate900,
                                modifier = Modifier.padding(Spacing.mdLg),
                            )
                        }
                    }

                    // ── Recent Decisions ──────────────────────────────────────
                    if (data.decisions.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Recent Decisions", count = data.decisions.size)
                            VelioraCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(Spacing.mdLg)) {
                                    data.decisions.forEach { decision ->
                                        DecisionRow(decision)
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}

// ── Decision Row ───────────────────────────────────────────────────────

@Composable
private fun DecisionRow(decision: Decision) {
    val linkColor = if (decision.linkType == "meeting") Indigo500 else Blue500
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = decision.text,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate700,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = decision.linkType,
            style = MaterialTheme.typography.labelSmall,
            color = linkColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(Radii.sm))
                .background(linkColor.copy(alpha = 0.1f))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
