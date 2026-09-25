package com.veliora.ui.screens.evolution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.veliora.app
import com.veliora.data.EvolutionEvent
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.OrgChartDiff
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.components.chartForEvent
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Spacing

/** Before→after org chart diff for an evolution event (22-mobile-ux §12.2). */
@Composable
fun OrgChartDiffScreen(
    orgId: String,
    eventId: String,
) {
    val context = LocalContext.current
    val app = remember { context.app }

    var event by remember { mutableStateOf<EvolutionEvent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(orgId, eventId) {
        loading = true
        error = null
        runCatching { app.repository.evolutionEvents(orgId) }
            .onSuccess { dtos -> event = dtos.map { Mapper.toEvolutionEvent(it) }.find { it.id == eventId } }
            .onFailure { e -> error = e.message ?: "Failed to load evolution events" }
        loading = false
    }

    val data = event
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            data == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = "◌",
                        title = "Event not found",
                        subtitle = error ?: "This evolution event could not be loaded.",
                    )
                }
            }
            else -> {
                val (before, after) = chartForEvent(data.type, data.description)
                LazyColumn(
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
                            title = "Org Chart Diff",
                            subtitle = data.description,
                        )
                    }
                    item {
                        SectionHeader(title = "Structure Change")
                        VelioraCard(modifier = Modifier.fillMaxWidth()) {
                            OrgChartDiff(
                                before = before,
                                after = after,
                                modifier = Modifier.padding(Spacing.mdLg),
                            )
                        }
                    }
                    item {
                        SectionHeader(title = "Trigger")
                        VelioraCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = data.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate700,
                                modifier = Modifier.padding(Spacing.mdLg),
                            )
                        }
                    }
                }
            }
        }
    }
}
