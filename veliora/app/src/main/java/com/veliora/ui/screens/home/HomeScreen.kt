package com.veliora.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veliora.app
import com.veliora.data.CaspianThread
import com.veliora.data.Organization
import com.veliora.data.api.OrgLite
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.BrandGradientSoft
import com.veliora.ui.components.CaspianThreadRow
import com.veliora.ui.components.DepartmentCard
import com.veliora.ui.components.DnaChips
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.GoalComposerSheet
import com.veliora.ui.components.GradientButton
import com.veliora.ui.components.HealthRing
import com.veliora.ui.components.InvestigationCard
import com.veliora.ui.components.LiveOrgEffect
import com.veliora.ui.components.LivePulse
import com.veliora.ui.components.HomeSkeleton
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.TextLink
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Green500
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing
import com.veliora.ui.theme.White
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
// HOME SCREEN — the living organization dashboard (real data).
// Header → org → Departments → Investigations → Communications → Evolution.
// Data comes from the API + live WebSocket stream (OrgStore).
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(
    orgId: String,
    onDepartmentClick: (String) -> Unit,
    onInvestigationClick: (String) -> Unit,
    onCommsClick: () -> Unit,
    onEvolutionClick: () -> Unit,
    onConstitutionClick: () -> Unit,
    onOrgSwitch: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val app = remember { context.app }
    val store = app.orgStore
    val scope = rememberCoroutineScope()
    val org by store.organization.collectAsStateWithLifecycle()
    val investigations by store.investigations.collectAsStateWithLifecycle()
    val loadError by store.error.collectAsStateWithLifecycle()
    val actionError by store.actionError.collectAsStateWithLifecycle()
    var caspianThreads by remember { mutableStateOf<List<CaspianThread>>(emptyList()) }
    var switchOrgs by remember { mutableStateOf<List<OrgLite>>(emptyList()) }
    var showComposer by remember { mutableStateOf(false) }
    var showOrgSwitcher by remember { mutableStateOf(false) }

    LiveOrgEffect(orgId, store)

    LaunchedEffect(orgId) {
        try {
            caspianThreads = app.repository.communications(orgId).map { Mapper.toCaspianThread(it) }
            switchOrgs = app.repository.organizations().data
        } catch (_: Exception) {
        }
    }

    val loadedOrg = org
    if (loadedOrg == null) {
        if (loadError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    icon = "⚠",
                    title = "Could not load organization",
                    subtitle = loadError,
                )
                GradientButton(
                    text = "Retry",
                    onClick = { store.retry() },
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                HomeSkeleton(modifier = Modifier.align(Alignment.Center))
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xxxl + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val visibleError = loadError ?: actionError
            if (visibleError != null) {
                item {
                    VelioraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
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
            }
            item {
                HomeHeader(
                    org = loadedOrg,
                    onOrgClick = { showOrgSwitcher = true },
                )
            }

            item {
                OrgHeader(
                    org = loadedOrg,
                    onEvolutionClick = onEvolutionClick,
                    onConstitutionClick = onConstitutionClick,
                )
            }

            item {
                SectionHeader(title = "Departments", isLive = loadedOrg.isAlive)
            }
            if (loadedOrg.departments.isEmpty()) {
                item {
                    EmptyState(
                        icon = "◌",
                        title = "Organization is forming",
                        subtitle = "Departments will appear once the org seeds its leadership.",
                    )
                }
            } else {
                items(loadedOrg.departments, key = { it.id }) { department ->
                    DepartmentCard(
                        department = department,
                        onClick = { onDepartmentClick(department.id) },
                    )
                }
            }

            item {
                SectionHeader(title = "Active Investigations", count = investigations.size)
            }
            if (investigations.isEmpty()) {
                item {
                    EmptyState(
                        icon = "▣",
                        title = "No investigations yet",
                        subtitle = "Give the organization its first goal below.",
                    )
                }
            } else {
                items(investigations, key = { it.id }) { investigation ->
                    InvestigationCard(
                        investigation = investigation,
                        onClick = { onInvestigationClick(investigation.id) },
                    )
                }
            }

            item {
                SectionHeader(title = "External Communications", count = caspianThreads.size)
            }
            if (caspianThreads.isEmpty()) {
                item {
                    EmptyState(
                        icon = "✉",
                        title = "No external communications",
                        subtitle = "Caspian traffic will land here.",
                    )
                }
            } else {
                items(caspianThreads.take(2), key = { it.id }) { thread ->
                    CaspianThreadRow(thread = thread, onClick = onCommsClick)
                }
                item {
                    TextLink(text = "View All Communications", onClick = onCommsClick)
                }
            }

            // ── Primary Action ─────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(Spacing.xl))
                GradientButton(
                    text = "New Investigation",
                    onClick = { showComposer = true },
                    icon = Icons.Default.Add,
                )
            }
        }
    }

    GoalComposerSheet(
        visible = showComposer,
        onDismiss = { showComposer = false },
        onCreate = { goal, depth ->
            showComposer = false
            scope.launch {
                val invId = store.createInvestigation(goal, depth.name.lowercase())
                if (invId != null) onInvestigationClick(invId)
            }
        },
    )

    OrgSwitcherSheet(
        visible = showOrgSwitcher,
        currentOrgId = loadedOrg.id,
        orgs = switchOrgs,
        onDismiss = { showOrgSwitcher = false },
        onOrgSelected = { selectedOrgId ->
            showOrgSwitcher = false
            if (selectedOrgId != loadedOrg.id) onOrgSwitch(selectedOrgId)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrgSwitcherSheet(
    visible: Boolean,
    currentOrgId: String,
    orgs: List<OrgLite>,
    onDismiss: () -> Unit,
    onOrgSelected: (String) -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Switch Organization",
                style = MaterialTheme.typography.titleLarge,
                color = Slate900,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            orgs.forEach { org ->
                val isCurrent = org.organizationId == currentOrgId
                VelioraCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCurrent) Modifier.border(2.dp, Indigo500, RoundedCornerShape(Radii.lg))
                            else Modifier
                        ),
                    onClick = { onOrgSelected(org.organizationId) },
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.mdLg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        HealthRing(health = org.health.toInt(), ringSize = 44)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                text = org.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Slate900,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${org.template} template · Health ${org.health.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                        }
                        if (isCurrent) {
                            Text(
                                text = "✓ Current",
                                style = MaterialTheme.typography.labelMedium,
                                color = Indigo500,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    org: Organization,
    onOrgClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOrgClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = org.name,
                style = MaterialTheme.typography.titleLarge,
                color = Slate900,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OrgHeader(
    org: Organization,
    onEvolutionClick: () -> Unit,
    onConstitutionClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg, bottom = Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(color = BrandGradientSoft)
                .padding(Spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = org.name,
                            style = MaterialTheme.typography.displaySmall,
                            color = Slate900,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            LivePulse(alive = org.isAlive, size = 6)
                            Text(
                                text = org.template,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "${org.health}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = Slate900,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "HEALTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500,
                        )
                    }
                }
                DnaChips(dna = org.dna)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (org.isAlive) "● Live" else "● Paused",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (org.isAlive) Green500 else Slate400,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextLink(
                        text = "Evolution",
                        onClick = onEvolutionClick,
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    TextLink(
                        text = "View Constitution",
                        onClick = onConstitutionClick,
                    )
                }
            }
        }
    }
}
