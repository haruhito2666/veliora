package com.veliora.ui.screens.comms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.veliora.app
import com.veliora.data.CaspianChannel
import com.veliora.data.CaspianThread
import com.veliora.data.repo.Mapper
import com.veliora.ui.components.CaspianThreadRow
import com.veliora.ui.components.EmptyState
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.theme.Green500
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate100
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Spacing
import com.veliora.ui.theme.White

@Composable
fun CommsScreen(
    orgId: String,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val app = remember { context.app }

    var allThreads by remember { mutableStateOf<List<CaspianThread>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(orgId, reloadKey) {
        loading = true
        error = null
        runCatching { app.repository.communications(orgId) }
            .onSuccess { dtos -> allThreads = dtos.map { Mapper.toCaspianThread(it) } }
            .onFailure { e -> error = e.message ?: "Failed to load communications" }
        loading = false
    }

    var selectedChannel by remember { mutableStateOf<CaspianChannel?>(null) }
    var selectedThread by remember { mutableStateOf<CaspianThread?>(null) }

    val filteredThreads = remember(selectedChannel, allThreads) {
        if (selectedChannel == null) allThreads
        else allThreads.filter { it.channel == selectedChannel }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(contentPadding),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xxxl + Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                ScrollableHeader(
                    title = "External Communications",
                    actions = {
                        TextButton(onClick = { reloadKey += 1 }) {
                            Text("Refresh")
                        }
                    },
                )
            }

            // Channel filter row
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        ChannelFilterChip("All", selectedChannel == null) {
                            selectedChannel = null
                        }
                        CaspianChannel.entries.forEach { channel ->
                            ChannelFilterChip(
                                label = channel.label,
                                selected = selectedChannel == channel,
                                onClick = { selectedChannel = channel },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                }
            }

            // Thread list
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
                filteredThreads.isEmpty() -> {
                    item {
                        EmptyState(
                            icon = "○",
                            title = "No messages",
                            subtitle = error ?: "No threads on this channel yet.",
                        )
                    }
                }
                else -> {
                    items(filteredThreads, key = { it.id }) { thread ->
                        CaspianThreadRow(
                            thread = thread,
                            onClick = { selectedThread = thread },
                        )
                    }
                }
            }
        }
    }

    selectedThread?.let { thread ->
        ThreadDetailDialog(
            thread = thread,
            onClose = { selectedThread = null },
        )
    }
}

// ── ChannelFilterChip ────────────────────────────────────────────────
// Minimal text pill. Indigo when selected, slate when not.

@Composable
private fun ChannelFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) White else Slate500,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(if (selected) Indigo500 else Slate100)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.smMd),
    )
}

// ── ThreadDetailDialog ────────────────────────────────────────────────
// Full card for one thread: channel + recipient, outbound message,
// reply (green) or awaiting state (slate), and timestamp.

@Composable
private fun ThreadDetailDialog(
    thread: CaspianThread,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = thread.channel.icon,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${thread.channel.label} · ${thread.recipient}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = thread.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate700,
                )
                if (thread.hasReply) {
                    Text(
                        text = "↩ Reply: ${thread.reply ?: thread.replySummary ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Green500,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Text(
                        text = "Awaiting reply...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                    )
                }
                Text(
                    text = thread.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
    )
}
