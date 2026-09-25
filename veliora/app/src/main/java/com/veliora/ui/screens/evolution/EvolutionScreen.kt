package com.veliora.ui.screens.evolution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import com.veliora.app
import com.veliora.data.EvolutionEvent
import com.veliora.data.EvolutionType
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.EvolutionEventRow
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.theme.Indigo600
import com.veliora.ui.theme.Spacing

@Composable
fun EvolutionScreen(
    orgId: String,
    onEventClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember { context.app }

    var events by remember { mutableStateOf<List<EvolutionEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(orgId) {
        loading = true
        error = null
        runCatching { app.repository.evolutionEvents(orgId) }
            .onSuccess { dtos ->
                events = dtos.map { Mapper.toEvolutionEvent(it) }.sortedBy { it.daysAgo ?: Int.MAX_VALUE }
            }
            .onFailure { e -> error = e.message ?: "Failed to load evolution events" }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 24dp horizontal alignment — the SectionHeader carries its own top spacing
            contentPadding = PaddingValues(
                start = Spacing.lg,
                top = Spacing.xs,
                end = Spacing.lg,
                bottom = Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Header — scrolls with the page
            item {
                ScrollableHeader(
                    title = "Evolution History",
                    subtitle = "last 30d",
                )
            }

            // Section header
            item {
                SectionHeader(title = "Org Changes", count = events.size)
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
                events.isEmpty() -> {
                    // Empty timeline — nothing changed in the window
                    item {
                        EmptyState(
                            icon = "◌",
                            title = "No evolution events",
                            subtitle = error ?: "The org has been steady — nothing changed in the last 30 days.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> {
                    // Timeline of real evolution events.
                    items(events, key = { it.id }) { event ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            EvolutionEventRow(event = event)
                            if (event.type == EvolutionType.SPLIT || event.type == EvolutionType.MERGE) {
                                TextButton(
                                    onClick = { onEventClick(event.id) },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("View Diff", color = Indigo600)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
