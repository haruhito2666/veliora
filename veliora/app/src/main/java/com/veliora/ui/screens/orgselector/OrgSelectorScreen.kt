package com.veliora.ui.screens.orgselector

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veliora.app
import com.veliora.data.api.OrgLite
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.HealthRing
import com.veliora.ui.components.LivePulse
import com.veliora.ui.components.OrganizationListSkeleton
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

@Composable
fun OrgSelectorScreen(
    onOrgSelected: (String) -> Unit,
    onCreateOrg: () -> Unit,
    onInstanceClick: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember { context.app }
    var organizations by remember { mutableStateOf<List<OrgLite>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        try {
            organizations = app.repository.organizations(force = true).data
        } catch (e: Exception) {
            error = e.message ?: "Failed to load organizations"
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.smMd),
        ) {
            item {
                ScrollableHeader(
                    title = "Organizations",
                    actions = {
                        IconButton(onClick = { reloadKey += 1 }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh organizations",
                                tint = Slate700,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = onInstanceClick) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = "Profile and instance information",
                                tint = Slate700,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    },
                )
            }
            when {
                loading -> item {
                    OrganizationListSkeleton()
                }
                error != null -> item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(icon = "⚠", title = "Could not load", subtitle = error!!)
                        TextButton(
                            onClick = {
                                app.repository.retrySofuu()
                                reloadKey += 1
                            },
                        ) {
                            Text("Retry")
                        }
                    }
                }
                organizations.isEmpty() -> item {
                    VelioraCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            EmptyState(
                                icon = "◌",
                                title = "No organizations yet",
                                subtitle = "Create your first organization to begin.",
                            )
                            Text(
                                "Getting started",
                                style = MaterialTheme.typography.titleMedium,
                                color = Slate900,
                                fontWeight = FontWeight.SemiBold,
                            )
                            listOf(
                                "1. Create a Startup Society.",
                                "2. Launch an investigation with a goal.",
                                "3. Watch departments, debates, and the board report.",
                            ).forEach { step ->
                                Text(
                                    step,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                )
                            }
                        }
                    }
                }
                else -> itemsIndexed(organizations) { _, org ->
                    val isCurrent = org.organizationId == app.orgStore.activeOrgId
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
                        ) {
                            HealthRing(health = org.health.toInt(), ringSize = 56)
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    if (org.state == "active") LivePulse(alive = true, size = 6)
                                    Text(
                                        text = org.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Slate900,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                Text(
                                    text = org.template,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = org.state,
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400,
                            )
                            Spacer(modifier = Modifier.width(Spacing.smMd))
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Open",
                                tint = Slate400,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            item {
                VelioraCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCreateOrg,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.mdLg),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Organization",
                            tint = Indigo500,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "New Organization",
                            style = MaterialTheme.typography.titleMedium,
                            color = Indigo500,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
