package com.veliora.ui.screens.instance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.errorMessage
import com.clerk.api.network.serialization.onFailure
import com.clerk.api.network.serialization.onSuccess
import com.veliora.app
import com.veliora.data.api.OrgLite
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun InstanceScreen(
    onOpenOrg: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.app }
    val scope = rememberCoroutineScope()
    val user by Clerk.userFlow.collectAsStateWithLifecycle()
    var organizations by remember { mutableStateOf<List<OrgLite>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var signOutError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        error = null
        runCatching { app.repository.organizations().data }
            .onSuccess { organizations = it }
            .onFailure { error = it.message ?: "Failed to load this instance" }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.lg, Spacing.xs, Spacing.lg, Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { ScrollableHeader(title = "Instance") }
            item {
                VelioraCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.mdLg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = Green500, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(Spacing.md))
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    "Signed in with Clerk",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Slate900,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    user?.primaryEmailAddress?.emailAddress ?: user?.id ?: "Unknown user",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate500,
                                )
                            }
                        }
                        Text(
                            "Organizations are scoped to this Clerk account. Signing out returns to the Clerk sign-in screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500,
                        )
                        signOutError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                signOutError = null
                                scope.launch {
                                    Clerk.auth.signOut()
                                        .onSuccess { app.orgStore.clear() }
                                        .onFailure {
                                            signOutError = it.errorMessage
                                        }
                                }
                            },
                        ) {
                            Text("Sign out")
                        }
                    }
                }
            }
            item { SectionHeader(title = "Organizations", count = organizations.size) }
            when {
                loading -> item {
                    Column(Modifier.fillMaxWidth().padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                    }
                }
                organizations.isEmpty() -> item {
                    EmptyState("◌", "No organizations", error ?: "Create one from the organization selector.")
                }
                else -> items(organizations, key = { it.organizationId }) { org ->
                    VelioraCard(Modifier.fillMaxWidth(), onClick = { onOpenOrg(org.organizationId) }) {
                        Row(Modifier.fillMaxWidth().padding(Spacing.mdLg), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(org.name, style = MaterialTheme.typography.titleMedium, color = Slate900, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${org.template} · ${org.state}", style = MaterialTheme.typography.bodySmall, color = Slate400, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Filled.ChevronRight, "Open organization", tint = Slate400)
                        }
                    }
                }
            }
            item {
                Text(
                    "Veliora 1.0 · MIT licensed open source",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate700,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        }
    }
}
