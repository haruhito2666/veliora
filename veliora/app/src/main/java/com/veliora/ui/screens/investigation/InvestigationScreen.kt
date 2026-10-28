package com.veliora.ui.screens.investigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veliora.app
import com.veliora.data.DepartmentStatus
import com.veliora.data.DeptState
import com.veliora.data.Evidence
import com.veliora.data.Investigation
import com.veliora.data.InvestigationPhase
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.EvidenceRow
import com.veliora.ui.components.GradientButton
import com.veliora.ui.components.LiveOrgEffect
import com.veliora.ui.components.PhaseIndicator
import com.veliora.ui.components.ProgressBar
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.StatusBadge
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Indigo50
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.LocalDeptColors
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ═══════════════════════════════════════════════════════════════════════
// InvestigationScreen — the flagship investigation view.
// A living dashboard: the user watches the org investigate in real time.
// Data comes from the backend via VelioraRepository + Mapper.
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun InvestigationScreen(
    orgId: String,
    investigationId: String,
    onReportClick: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember { context.app }
    val store = app.orgStore
    val liveInvestigations by store.investigations.collectAsStateWithLifecycle()
    val departmentStatuses by store.departmentStatuses.collectAsStateWithLifecycle()
    val storeError by store.error.collectAsStateWithLifecycle()

    var investigation by remember { mutableStateOf<Investigation?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var evidenceFeed by remember { mutableStateOf<List<Evidence>>(emptyList()) }
    var evidenceError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LiveOrgEffect(orgId, store)

    LaunchedEffect(orgId, investigationId, reloadKey) {
        loading = true
        error = null
        runCatching { app.repository.investigation(orgId, investigationId) }
            .onSuccess { dto -> investigation = Mapper.toInvestigation(dto) }
            .onFailure { e -> error = e.message ?: "Failed to load investigation" }
        loading = false
    }

    LaunchedEffect(orgId, investigationId, reloadKey) {
        while (isActive) {
            runCatching { app.repository.evidence(orgId, investigationId) }
                .onSuccess {
                    evidenceFeed = it.map(Mapper::toEvidence)
                    evidenceError = null
                }
                .onFailure { e ->
                    if (evidenceFeed.isEmpty()) {
                        evidenceError = e.message ?: "Failed to load evidence"
                    }
                }
            val terminal = store.investigations.value
                .find { it.id == investigationId }
                ?.isTerminal == true
            if (terminal) break
            delay(1_000)
        }
    }

    val liveInvestigation = liveInvestigations.find { it.id == investigationId }
    val data = (liveInvestigation ?: investigation)?.let { inv ->
        if (departmentStatuses.isNotEmpty() && !inv.isTerminal) {
            inv.copy(departmentStatuses = departmentStatuses)
        } else {
            inv
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            loading && data == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            data == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(
                        icon = "▣",
                        title = "Investigation unavailable",
                        subtitle = error ?: storeError ?: "This investigation could not be loaded.",
                    )
                    Button(
                        onClick = {
                            store.clearActionError()
                            store.retry()
                            reloadKey += 1
                        },
                    ) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                val displayDeptStatuses = data.departmentStatuses

                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.xs,
                        bottom = Spacing.xl,
                    ),
                ) {
                    // ── Header ────────────────────────────────────────────────────
                    // Scrolls with the page — the first item on the canvas, so the
                    // whole screen reads as one surface instead of a fixed bar
                    // layered over the content.
                    item {
                        ScrollableHeader(title = "Investigation")
                    }

                    // ── Goal + Phase + Progress (the hero block) ─────────────────
                    // Sits directly on the background — the goal reads like a
                    // question being asked, which is exactly what it is.
                    item {
                        Column {
                            Text(
                                text = data.goal,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Slate900,
                                modifier = Modifier.padding(bottom = Spacing.sm),
                            )
                            Spacer(Modifier.height(Spacing.lg))
                            if (data.phase == InvestigationPhase.CANCELLED ||
                                data.phase == InvestigationPhase.FAILED
                            ) {
                                StatusBadge(
                                    text = data.phase.label.uppercase(),
                                    color = if (data.phase == InvestigationPhase.FAILED) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Slate500
                                    },
                                )
                            } else {
                                PhaseIndicator(
                                    currentPhase = data.phase,
                                    phaseProgress = data.phaseProgress,
                                )
                                Spacer(Modifier.height(Spacing.lg))
                                // Progress bar + % — one baseline-aligned row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.smMd),
                                ) {
                                    ProgressBar(
                                        progress = data.progress,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${data.progress}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Indigo500,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            if (data.elapsedMinutes > 0 || data.etaMinutes > 0) {
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    text = "Elapsed ${data.elapsedMinutes}m · ETA ${data.etaMinutes}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                )
                            }
                        }
                    }

                    // ── Department Status ────────────────────────────────────────
                    // One card holding all department rows. Each row is color-coded
                    // by department via LocalDeptColors.
                    if (displayDeptStatuses.isEmpty()) {
                        item {
                            EmptyState(
                                icon = "◌",
                                title = "No department status yet",
                                subtitle = "Live department status will appear when the backend reports it.",
                            )
                        }
                    } else {
                        item {
                            SectionHeader(title = "Department Status", count = displayDeptStatuses.size)
                            VelioraCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(Spacing.mdLg)) {
                                    displayDeptStatuses.forEach { status ->
                                        DepartmentStatusRow(status)
                                    }
                                }
                            }
                        }
                    }

                    // ── Evidence Feed ────────────────────────────────────────────
                    // Streaming evidence. The SectionHeader carries the live pulse;
                    // the newest item gets a subtle indigo highlight.
                    item {
                        SectionHeader(title = "Evidence Feed", isLive = evidenceFeed.isNotEmpty())
                        VelioraCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(Spacing.mdLg)) {
                                if (evidenceFeed.isEmpty()) {
                                    EmptyState(
                                        icon = "◌",
                                        title = "No evidence yet",
                                        subtitle = evidenceError
                                            ?: "Evidence appears as departments report it.",
                                    )
                                } else {
                                    evidenceFeed.forEachIndexed { index, evidence ->
                                        val isNewest = index == evidenceFeed.lastIndex
                                        Box(
                                            // Vertical padding only — horizontal stays
                                            // flush so the highlighted row's content
                                            // aligns with every other feed row, while
                                            // the extra height keeps the rounded band
                                            // clear of the text.
                                            modifier = if (isNewest)
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(Radii.sm))
                                                    .background(Indigo50)
                                                    .padding(vertical = Spacing.sm)
                                            else Modifier
                                        ) {
                                            EvidenceRow(evidence = evidence)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── View Board Report ────────────────────────────────────────
                    // The report only exists after a successful completion.
                    item {
                        Spacer(Modifier.height(Spacing.xl))
                        when {
                            data.phase == InvestigationPhase.REPORT && data.isTerminal ->
                                GradientButton(
                                    text = "View Board Report",
                                    onClick = onReportClick,
                                )
                            data.phase == InvestigationPhase.CANCELLED ||
                                data.phase == InvestigationPhase.FAILED ->
                                Text(
                                    text = "This investigation ended without a board report.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                )
                            else ->
                                GradientButton(
                                    text = "Report available when complete",
                                    onClick = {},
                                    enabled = false,
                                )
                        }
                    }

                }
            }
        }
    }

}

// ── Department Status Row ───────────────────────────────────────────────
// Department name (left, color-coded dot + name) + state glyph + activity
// (right). A simple Row, used inside the Department Status VelioraCard.

@Composable
private fun DepartmentStatusRow(status: DepartmentStatus) {
    val deptColors = LocalDeptColors.current
    val deptColor = deptColors.forName(status.departmentName)
    val stateColor = when (status.state) {
        DeptState.WORKING, DeptState.IMPLEMENTING, DeptState.COORDINATING -> Indigo500
        DeptState.WAITING, DeptState.QUEUED -> Slate400
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Color-coded department dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(deptColor)
        )
        // Department name — flexible column; ellipsizes instead of
        // wrapping so the glyph + activity columns stay on one line.
        Text(
            text = status.departmentName,
            style = MaterialTheme.typography.titleMedium,
            color = Slate900,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // State glyph — fixed-width column keeps glyphs aligned across rows
        Text(
            text = status.state.glyph,
            fontSize = 14.sp,
            color = stateColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center,
        )
        // Current activity — weighted like the name so a long activity
        // can never starve the flexible name column of width
        Text(
            text = status.activity,
            style = MaterialTheme.typography.bodySmall,
            color = Slate500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
