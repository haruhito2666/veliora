package com.veliora.ui.screens.investigations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.veliora.app
import com.veliora.data.Investigation
import com.veliora.data.InvestigationDepth
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.GoalComposerSheet
import com.veliora.ui.components.GradientButton
import com.veliora.ui.components.InvestigationCard
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.theme.Spacing
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
// INVESTIGATIONS — all investigations, active and past. One of the three
// top-level destinations (Home · Investigations · Comms).
// Data comes from the backend via VelioraRepository + Mapper.
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun InvestigationsScreen(
    orgId: String,
    onInvestigationClick: (String) -> Unit,
    onNewInvestigationCreated: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val app = remember { context.app }
    val scope = rememberCoroutineScope()

    var showComposer by remember { mutableStateOf(false) }
    var investigations by remember { mutableStateOf<List<Investigation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(orgId) {
        loading = true
        error = null
        runCatching { app.repository.investigations(orgId) }
            .onSuccess { dtos -> investigations = dtos.map { Mapper.toInvestigation(it) } }
            .onFailure { e -> error = e.message ?: "Failed to load investigations" }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = Spacing.lg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxxl + Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ── Scrollable header — moves with content, one canvas ───────
            item {
                ScrollableHeader(
                    title = "Investigations",
                    subtitle = "The organization investigates, debates, and decides. You watch.",
                )
            }
            item {
                SectionHeader(title = "Active", count = investigations.size)
            }
            when {
                loading -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                investigations.isEmpty() -> {
                    item {
                        EmptyState(
                            icon = "▣",
                            title = "No investigations yet",
                            subtitle = error ?: "Give the organization its first goal.",
                        )
                    }
                }
                else -> {
                    items(investigations, key = { it.id }) { investigation ->
                        InvestigationCard(
                            investigation = investigation,
                            onClick = { onInvestigationClick(investigation.id) },
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.lg))
                GradientButton(
                    text = "New Investigation",
                    onClick = { showComposer = true },
                    icon = Icons.Default.Add,
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }
    }

    GoalComposerSheet(
        visible = showComposer,
        onDismiss = { showComposer = false },
        onCreate = { goal, depth ->
            showComposer = false
            val depthParam = when (depth) {
                InvestigationDepth.QUICK -> "quick"
                InvestigationDepth.STANDARD -> "standard"
                InvestigationDepth.DEEP -> "deep"
            }
            scope.launch {
                val invId = app.orgStore.createInvestigation(goal, depthParam)
                if (invId != null) {
                    onNewInvestigationCreated(invId)
                }
            }
        },
    )
}
