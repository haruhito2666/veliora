package com.veliora.ui.screens.evolution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.veliora.app
import com.veliora.data.EvolutionEvent
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

/**
 * Details for one real evolution event.
 *
 * The former screen synthesized a fake before/after org chart. The backend
 * contract does not carry before/after snapshots, so this view only renders
 * the recorded event fields and leaves structure visualization to a future
 * data model that actually supplies it.
 */
@Composable
fun EvolutionEventScreen(
    orgId: String,
    eventId: String,
) {
    val context = LocalContext.current
    val app = remember { context.app }

    var event by remember { mutableStateOf<EvolutionEvent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(orgId, eventId, reloadKey) {
        loading = true
        error = null
        runCatching { app.repository.evolutionEvents(orgId) }
            .onSuccess { dtos ->
                event = dtos.map { Mapper.toEvolutionEvent(it) }.find { it.id == eventId }
            }
            .onFailure { e -> error = e.message ?: "Failed to load evolution event" }
        loading = false
    }

    val data = event
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            loading && data == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            data == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    icon = "◌",
                    title = "Event not found",
                    subtitle = error ?: "This evolution event could not be loaded.",
                )
                Button(onClick = { reloadKey += 1 }) {
                    Text("Retry")
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.xs,
                    bottom = Spacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    ScrollableHeader(
                        title = "Evolution Event",
                        subtitle = data.description,
                        actions = {
                            TextButton(onClick = { reloadKey += 1 }) {
                                Text("Refresh")
                            }
                        },
                    )
                }
                item {
                    SectionHeader(title = "Change")
                    VelioraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = data.type.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = Slate500,
                            )
                            Text(
                                text = data.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Slate900,
                            )
                            if (data.status.isNotBlank()) {
                                Text(
                                    text = "Status: ${data.status}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                )
                            }
                        }
                    }
                }
                item {
                    SectionHeader(title = "Reason")
                    VelioraCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = data.reason.ifBlank { "No rationale was recorded." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate700,
                            modifier = Modifier.padding(Spacing.mdLg),
                        )
                    }
                }
                data.daysAgo?.let { days ->
                    item {
                        Text(
                            text = "Recorded $days days ago",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                        )
                    }
                }
            }
        }
    }
}
