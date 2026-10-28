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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veliora.app
import com.veliora.data.InvestigationDepth
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.GoalComposerSheet
import com.veliora.ui.components.GradientButton
import com.veliora.ui.components.InvestigationCard
import com.veliora.ui.components.LiveOrgEffect
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
    val store = app.orgStore
    val investigations by store.investigations.collectAsStateWithLifecycle()
    val loading by store.loading.collectAsStateWithLifecycle()
    val error by store.error.collectAsStateWithLifecycle()
    val actionError by store.actionError.collectAsStateWithLifecycle()

    var showComposer by remember { mutableStateOf(false) }

    LiveOrgEffect(orgId, store)

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
            val visibleError = error ?: actionError
            if (visibleError != null && investigations.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = visibleError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = {
                                store.clearActionError()
                                store.retry()
                            },
                        ) {
                            Text("Retry")
                        }
                    }
                }
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
                            subtitle = visibleError ?: "Give the organization its first goal.",
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
