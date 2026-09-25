package com.veliora.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veliora.app
import com.veliora.data.Report
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.BrandGradientSoft
import com.veliora.ui.components.ConfidenceBar
import com.veliora.ui.components.EvidenceRow
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.GoalComposerSheet
import com.veliora.ui.components.RiskBadge
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Green50
import com.veliora.ui.theme.Green500
import com.veliora.ui.theme.Indigo600
import com.veliora.ui.theme.LocalDeptColors
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
// ReportScreen — the board report, the output of an investigation.
// Loads the real report from the API (doc 19 §10, doc 24 §8.2).
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun ReportScreen(
    orgId: String,
    investigationId: String,
    onNewInvestigation: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = remember { context.app }
    val deptColors = LocalDeptColors.current
    var report by remember { mutableStateOf<Report?>(null) }
    var goal by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showComposer by remember { mutableStateOf(false) }
    var composerPrefill by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(investigationId) {
        loading = true
        error = null
        try {
            goal = app.repository.investigation(orgId, investigationId).prompt
            report = Mapper.toReport(app.repository.report(orgId, investigationId))
        } catch (e: Exception) {
            error = e.message ?: "Failed to load report"
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val rep = report
        when {
            loading -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            error != null -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(icon = "⚠", title = "Report unavailable", subtitle = error!!)
            }
            rep == null -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(icon = "◌", title = "No report yet", subtitle = "The report appears once the investigation completes.")
            }
            else -> LazyColumn(
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
                item {
                    ScrollableHeader(title = "Report", subtitle = goal)
                }

                item {
                    SectionHeader(title = "Executive Summary")
                    VelioraCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = rep.executiveSummary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Slate700,
                            modifier = Modifier.padding(Spacing.mdLg),
                        )
                    }
                }

                item {
                    SectionHeader(title = "Recommendation")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(color = BrandGradientSoft)
                            .padding(Spacing.lg),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.smMd)) {
                            Text(
                                text = "RECOMMENDATION",
                                style = MaterialTheme.typography.labelLarge,
                                color = Indigo600,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = rep.recommendation,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Slate900,
                                fontWeight = FontWeight.Bold,
                            )
                            ConfidenceBar(confidence = rep.confidence)
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Dissenting Opinions",
                        count = rep.dissentingOpinions.size,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        rep.dissentingOpinions.forEach { dissent ->
                            val department = dissent.department.ifBlank { "Dissent" }
                            val deptColor = deptColors.forName(department)
                            VelioraCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(Spacing.mdLg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = department,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = deptColor,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Text(
                                        text = dissent.argument,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate700,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Supporting Evidence",
                        count = rep.supportingEvidence.size,
                    )
                    VelioraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = Spacing.mdLg,
                                vertical = Spacing.sm,
                            ),
                        ) {
                            rep.supportingEvidence.forEach { evidence ->
                                EvidenceRow(evidence = evidence)
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Risks",
                        count = rep.risks.size,
                    )
                    VelioraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.smMd),
                        ) {
                            rep.risks.forEach { risk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                ) {
                                    RiskBadge(level = risk.level)
                                    Text(
                                        text = risk.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate700,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Opportunities",
                        count = rep.opportunities.size,
                    )
                    VelioraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.smMd),
                        ) {
                            rep.opportunities.forEach { opportunity ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(Radii.sm))
                                            .background(Green50),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "+",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Green500,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = opportunity,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate700,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Next Steps",
                        count = rep.nextSteps.size,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        rep.nextSteps.forEach { step ->
                            VelioraCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate900,
                                    modifier = Modifier.padding(Spacing.mdLg),
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeader(title = "Full Discussion")
                    VelioraCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = rep.fullDiscussionSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate700,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Discussion transcript opens here")
                                    }
                                },
                            ) {
                                Text(
                                    text = "Open",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Indigo600,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(Spacing.lg)) }
            }
        }
    }

    GoalComposerSheet(
        visible = showComposer,
        initialGoal = composerPrefill,
        title = "Follow-up Investigation",
        onDismiss = { showComposer = false },
        onCreate = { goal, _ ->
            onNewInvestigation(goal)
        },
    )
}
