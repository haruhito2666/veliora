package com.veliora.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.veliora.data.sofuu.ConnectionProfile
import com.veliora.data.sofuu.ConnectionStore
import com.veliora.data.sofuu.InferenceMode
import com.veliora.data.sofuu.McpServer
import com.veliora.data.sofuu.McpServerStore
import com.veliora.data.sofuu.RuntimeEnv
import com.veliora.data.sofuu.RuntimeSettings
import com.veliora.data.sofuu.SecretStore
import com.veliora.ui.components.BrandGradientStrong
import com.veliora.ui.components.GradientButton
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.SectionHeader
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.BrandDark
import com.veliora.ui.theme.BrandPink
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

/**
 * The app's configuration hub: inference mode, saved model connections, web
 * search, HTTP MCP tool servers, and advanced runtime behavior.
 *
 * Connection/integration edits persist immediately. Runtime settings persist
 * on Save and apply on the next app launch; web-search env values apply right
 * away because Sofuu reads them per call.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(RuntimeSettings.load(context)) }
    var connections by remember { mutableStateOf(ConnectionStore.load(context)) }
    var servers by remember { mutableStateOf(McpServerStore.load(context)) }
    var webKey by remember {
        mutableStateOf(webSecret(context, settings.webEngine))
    }
    var editingConnection by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showConnectionEditor by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<McpServer?>(null) }
    var showServerEditor by remember { mutableStateOf(false) }
    var stepDelay by remember { mutableStateOf(settings.stepDelayMs.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun selectMode(next: InferenceMode) {
        settings = settings.copy(mode = next)
        if (next != InferenceMode.SCRIPTED && settings.connectionId == null) {
            settings = settings.copy(connectionId = connections.firstOrNull()?.id)
        }
        message = null
        error = null
    }

    fun saveSettings() {
        val delay = stepDelay.toIntOrNull()?.coerceIn(0, 2_000) ?: settings.stepDelayMs
        val next = settings.copy(stepDelayMs = delay)
        RuntimeSettings.save(context, next)
        RuntimeEnv.applyWebSettings(context, next)
        settings = next
        message = "Settings saved. Close and reopen the app to apply them."
    }

    fun saveConnection(profile: ConnectionProfile, apiKey: String) {
        ConnectionStore.upsert(context, profile)
        ConnectionStore.setApiKey(context, profile.id, apiKey)
        connections = ConnectionStore.load(context)
        if (settings.connectionId == null && settings.mode != InferenceMode.SCRIPTED) {
            settings = settings.copy(connectionId = profile.id)
        }
        showConnectionEditor = false
        editingConnection = null
    }

    fun saveServer(server: McpServer) {
        McpServerStore.upsert(context, server)
        servers = McpServerStore.load(context)
        showServerEditor = false
        editingServer = null
    }

    fun saveWebKey() {
        val secretName = when (settings.webEngine) {
            RuntimeSettings.WEB_BRAVE -> "web:brave"
            RuntimeSettings.WEB_TAVILY -> "web:tavily"
            else -> return
        }
        SecretStore.put(context, secretName, webKey.trim())
        webKey = webKey.trim()
        RuntimeEnv.applyWebSettings(context, settings)
        message = "Web search settings applied."
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
            contentPadding = PaddingValues(
                top = Spacing.xs,
                // The floating nav bar overlays this screen, so leave enough
                // tail room for the bar + system navigation + breathing room.
                bottom = Spacing.xxxl + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smMd),
        ) {
            item {
                ScrollableHeader(
                    title = "Settings",
                    subtitle = "Inference, connections, and integrations",
                )
            }

            // ── Inference ────────────────────────────────────────────────
            item { SectionHeader(title = "Inference") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    InferenceMode.entries.forEach { option ->
                        val selected = option == settings.mode
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) BrandDark else Slate700,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Radii.pill))
                                .background(
                                    if (selected) BrandGradientStrong
                                    else BrandPink.copy(alpha = 0.4f)
                                )
                                .clickable { selectMode(option) }
                                .padding(vertical = Spacing.smMd),
                        )
                    }
                }
            }
            item {
                Text(
                    text = when (settings.mode) {
                        InferenceMode.SCRIPTED ->
                            "Deterministic fixtures. No model connection is used."
                        InferenceMode.CLOUD ->
                            "Use an OpenAI-compatible cloud service. Select or add a connection below."
                        InferenceMode.LOCAL ->
                            "Use a local OpenAI-compatible server such as Ollama or llama.cpp."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
            }

            // ── Connections ──────────────────────────────────────────────
            item { SectionHeader(title = "Connections", count = connections.size) }
            if (connections.isEmpty()) {
                item {
                    VelioraCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                "No saved connections",
                                style = MaterialTheme.typography.titleMedium,
                                color = Slate900,
                            )
                            Text(
                                "Add a cloud or local model service, then select it for Cloud or Local mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                        }
                    }
                }
            } else {
                items(connections.size) { index ->
                    val connection = connections[index]
                    val active = connection.id == settings.connectionId
                    VelioraCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (active && settings.mode != InferenceMode.SCRIPTED) {
                                    Modifier.border(2.dp, Indigo500, RoundedCornerShape(Radii.lg))
                                } else {
                                    Modifier
                                }
                            ),
                        onClick = {
                            settings = settings.copy(connectionId = connection.id)
                        },
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    connection.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Slate900,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (active && settings.mode != InferenceMode.SCRIPTED) {
                                    Text(
                                        "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Indigo500,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                "${connection.provider} · ${connection.model}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                            if (connection.baseUrl.isNotBlank()) {
                                Text(
                                    connection.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate400,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                TextButton(onClick = {
                                    editingConnection = connection
                                    showConnectionEditor = true
                                }) { Text("Edit") }
                                TextButton(onClick = {
                                    ConnectionStore.delete(context, connection.id)
                                    connections = ConnectionStore.load(context)
                                    if (settings.connectionId == connection.id) {
                                        settings = settings.copy(connectionId = null)
                                    }
                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = {
                    editingConnection = null
                    showConnectionEditor = true
                }) {
                    Text("+ Add connection", color = Indigo500)
                }
            }

            // ── Integrations ─────────────────────────────────────────────
            item { SectionHeader(title = "Integrations") }
            item {
                VelioraCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.mdLg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Text(
                            "Web search",
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate900,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            listOf(
                                RuntimeSettings.WEB_DUCKDUCKGO to "DuckDuckGo",
                                RuntimeSettings.WEB_BRAVE to "Brave",
                                RuntimeSettings.WEB_TAVILY to "Tavily",
                            ).forEach { (value, label) ->
                                val selected = settings.webEngine == value
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) BrandDark else Slate700,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(Radii.pill))
                                        .background(
                                            if (selected) BrandGradientStrong
                                            else BrandPink.copy(alpha = 0.4f)
                                        )
                                        .clickable {
                                            settings = settings.copy(webEngine = value)
                                            webKey = webSecret(context, value)
                                        }
                                        .padding(vertical = Spacing.sm),
                                )
                            }
                        }
                        if (settings.webEngine != RuntimeSettings.WEB_DUCKDUCKGO) {
                            OutlinedTextField(
                                value = webKey,
                                onValueChange = { webKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("${settings.webEngine.replaceFirstChar { it.uppercase() }} API key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            TextButton(onClick = { saveWebKey() }) {
                                Text("Save web key")
                            }
                        } else {
                            Text(
                                "DuckDuckGo needs no API key.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                        }
                    }
                }
            }

            item {
                VelioraCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.mdLg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Tool servers (MCP)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Slate900,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${servers.count { it.enabled }} active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500,
                            )
                        }
                        if (servers.isEmpty()) {
                            Text(
                                "Add an HTTP MCP server to give agents extra tools.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                        } else {
                            servers.forEach { server ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            server.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Slate900,
                                        )
                                        Text(
                                            server.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate400,
                                            maxLines = 1,
                                        )
                                    }
                                    Switch(
                                        checked = server.enabled,
                                        onCheckedChange = { enabled ->
                                            saveServer(server.copy(enabled = enabled))
                                        },
                                    )
                                    TextButton(onClick = {
                                        editingServer = server
                                        showServerEditor = true
                                    }) { Text("Edit") }
                                }
                            }
                        }
                        TextButton(onClick = {
                            editingServer = null
                            showServerEditor = true
                        }) {
                            Text("+ Add tool server", color = Indigo500)
                        }
                    }
                }
            }

            // ── Advanced ─────────────────────────────────────────────────
            item { SectionHeader(title = "Advanced") }
            item {
                VelioraCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.mdLg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        OutlinedTextField(
                            value = stepDelay,
                            onValueChange = { value ->
                                stepDelay = value.filter(Char::isDigit).take(4)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Step delay (ms)") },
                            supportingText = { Text("Use 1000–2000 ms to watch investigation phases.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Use tools during investigations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate900,
                                )
                                Text(
                                    "Experimental. Runs departments as Sofuu agents that can use web and MCP tools.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                )
                            }
                            Switch(
                                checked = settings.useAgents,
                                onCheckedChange = { settings = settings.copy(useAgents = it) },
                                enabled = settings.mode != InferenceMode.SCRIPTED,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Fall back to scripted on provider failure",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate900,
                                )
                                Text(
                                    "Off for real models so provider errors stay visible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                )
                            }
                            Switch(
                                checked = settings.fallbackToScripted,
                                onCheckedChange = { settings = settings.copy(fallbackToScripted = it) },
                            )
                        }
                    }
                }
            }

            error?.let {
                item {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            message?.let {
                item {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                    )
                }
            }

            item {
                Text(
                    "Runtime settings apply after you close and reopen the app. Web search settings apply immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
            }
            item {
                GradientButton(
                    text = "Save settings",
                    onClick = { saveSettings() },
                )
            }
        }
    }

    if (showConnectionEditor) {
        ConnectionEditorSheet(
            initial = editingConnection,
            onDismiss = {
                showConnectionEditor = false
                editingConnection = null
            },
            onSave = { profile, apiKey -> saveConnection(profile, apiKey) },
        )
    }

    if (showServerEditor) {
        McpServerEditorSheet(
            initial = editingServer,
            onDismiss = {
                showServerEditor = false
                editingServer = null
            },
            onSave = { server -> saveServer(server) },
        )
    }
}

private fun webSecret(context: android.content.Context, engine: String): String {
    val name = when (engine) {
        RuntimeSettings.WEB_BRAVE -> "web:brave"
        RuntimeSettings.WEB_TAVILY -> "web:tavily"
        else -> return ""
    }
    return SecretStore.get(context, name).orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionEditorSheet(
    initial: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile, String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var provider by remember { mutableStateOf(initial?.provider.orEmpty()) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var apiKey by remember {
        mutableStateOf(initial?.let { ConnectionStore.apiKey(context, it.id) }.orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                if (initial == null) "Add connection" else "Edit connection",
                style = MaterialTheme.typography.headlineSmall,
                color = Slate900,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("Provider") },
                placeholder = { Text("openai, anthropic, river, …", color = Slate400) },
                supportingText = { Text("Use openai for OpenAI-compatible APIs; other names are custom.") },
                singleLine = true,
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                singleLine = true,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (API root, or full chat URL for custom)") },
                placeholder = { Text("https://api.example.com/v1", color = Slate400) },
                singleLine = true,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            GradientButton(
                text = "Save connection",
                onClick = {
                    val cleanProvider = provider.trim().lowercase()
                    val cleanBaseUrl = baseUrl.trim().trimEnd('/')
                    when {
                        name.trim().isBlank() -> error = "Name is required."
                        cleanProvider.isBlank() -> error = "Provider is required."
                        model.trim().isBlank() -> error = "Model is required."
                        cleanProvider !in setOf("openai", "anthropic") && cleanBaseUrl.isBlank() ->
                            error = "Base URL is required for a custom provider."
                        else -> onSave(
                            ConnectionProfile(
                                id = initial?.id ?: ConnectionStore.newId(),
                                name = name.trim(),
                                provider = cleanProvider,
                                model = model.trim(),
                                baseUrl = cleanBaseUrl,
                            ),
                            apiKey.trim(),
                        )
                    }
                },
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpServerEditorSheet(
    initial: McpServer?,
    onDismiss: () -> Unit,
    onSave: (McpServer) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                if (initial == null) "Add tool server" else "Edit tool server",
                style = MaterialTheme.typography.headlineSmall,
                color = Slate900,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("MCP URL") },
                placeholder = { Text("https://mcp.example.com/mcp", color = Slate400) },
                supportingText = { Text("HTTP MCP only. Android cannot run stdio servers.") },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Enabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate900,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            GradientButton(
                text = "Save tool server",
                onClick = {
                    val cleanUrl = url.trim()
                    when {
                        name.trim().isBlank() -> error = "Name is required."
                        !cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://") ->
                            error = "Enter an http:// or https:// MCP URL."
                        else -> onSave(
                            McpServer(
                                id = initial?.id ?: McpServerStore.newId(),
                                name = name.trim(),
                                url = cleanUrl.trimEnd('/'),
                                enabled = enabled,
                            ),
                        )
                    }
                },
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
        }
    }
}
