package com.veliora.ui.screens.department

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veliora.app
import com.veliora.data.Department
import com.veliora.data.OrgTask
import com.veliora.data.Worker
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.LiveOrgEffect
import com.veliora.ui.components.LivePulse
import com.veliora.ui.components.ProgressBar
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Green500
import com.veliora.ui.theme.LocalDeptColors
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate100
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

@Composable
fun DepartmentDetailScreen(
    orgId: String,
    deptId: String,
    onWorkerClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = remember { context.app }
    val store = app.orgStore
    val liveOrg by store.organization.collectAsStateWithLifecycle()

    var department by remember { mutableStateOf<Department?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LiveOrgEffect(orgId, store)

    LaunchedEffect(orgId, deptId) {
        loading = true
        error = null
        runCatching { app.repository.organization(orgId) }
            .onSuccess { snap ->
                department = Mapper.toOrganization(snap, snap.dna)
                    .departments
                    .find { it.id == deptId }
            }
            .onFailure { e -> error = e.message ?: "Failed to load organization" }
        loading = false
    }

    val data = liveOrg?.departments?.find { it.id == deptId } ?: department
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
                        title = "Department not found",
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
                            actions = {
                                if (data.isAlive) {
                                    LivePulse(alive = true, size = 8)
                                }
                            },
                        )
                    }

                    // ── Stats row ─────────────────────────────────────────────
                    item { DepartmentStatsRow(data) }

                    // ── Now ───────────────────────────────────────────────────
                    item {
                        SectionHeader(title = "Now", isLive = data.isAlive)
                        VelioraCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = data.currentActivity,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Slate900,
                                modifier = Modifier.padding(Spacing.mdLg),
                            )
                        }
                    }

                    // ── Workers ───────────────────────────────────────────────
                    if (data.workers.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Workers", count = data.workers.size)
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                data.workers.forEach { worker ->
                                    WorkerRow(
                                        worker = worker,
                                        isAlive = data.isAlive,
                                        onClick = { onWorkerClick(worker.id) },
                                    )
                                }
                            }
                        }
                    }

                    // ── Current Tasks ─────────────────────────────────────────
                    if (data.tasks.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Current Tasks", count = data.tasks.size)
                            VelioraCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(Spacing.mdLg)) {
                                    data.tasks.forEach { task ->
                                        TaskRow(task)
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

// ── Stats Row ──────────────────────────────────────────────────────────

@Composable
private fun DepartmentStatsRow(department: Department) {
    VelioraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.smMd, vertical = Spacing.mdLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItem(
                value = "${department.trust}%",
                label = "TRUST",
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(Slate100),
            )
            StatItem(
                value = "${department.workerCount}",
                label = "WORKERS",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Slate900,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate400,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Worker Row ─────────────────────────────────────────────────────────

@Composable
private fun WorkerRow(
    worker: Worker,
    isAlive: Boolean,
    onClick: () -> Unit,
) {
    VelioraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.mdLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = worker.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate900,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = worker.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (isAlive) LivePulse(alive = true, size = 6)
                    Text(
                        text = worker.currentActivity,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "${worker.trust}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (worker.trust >= 90) Green500 else Slate500,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = Slate400,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Task Row ───────────────────────────────────────────────────────────

@Composable
private fun TaskRow(task: OrgTask) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate700,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (task.progress == -1) {
                Text(
                    text = "done",
                    style = MaterialTheme.typography.labelMedium,
                    color = Green500,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = "${task.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate500,
                )
            }
        }
        if (task.progress != -1) {
            ProgressBar(progress = task.progress)
        }
    }
}
