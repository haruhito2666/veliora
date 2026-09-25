package com.veliora.ui.screens.createorg

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veliora.app
import com.veliora.data.api.TemplateSummary
import com.veliora.ui.components.GradientButton
import com.veliora.ui.components.ScrollableHeader
import com.veliora.ui.components.VelioraCard
import com.veliora.ui.theme.Indigo500
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun CreateOrgScreen(onLaunch: (String) -> Unit) {
    val context = LocalContext.current
    val app = remember { context.app }
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<List<TemplateSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(1) }
    var selectedTemplateIndex by remember { mutableStateOf(-1) }
    var orgName by remember { mutableStateOf("") }
    var orgMission by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            templates = app.repository.templates().templates
            if (templates.isNotEmpty()) selectedTemplateIndex = 0
        } catch (e: Exception) {
            error = e.message ?: "Failed to load templates"
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (step == 1) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.smMd),
            ) {
                item {
                    ScrollableHeader(
                        title = "Create Organization",
                    )
                }
                item {
                    Text(
                        text = "Choose a template to start from",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                    )
                }
                itemsIndexed(templates) { index, template ->
                    val isSelected = index == selectedTemplateIndex
                    VelioraCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) Modifier.border(2.dp, Indigo500, RoundedCornerShape(Radii.lg))
                                else Modifier
                            ),
                        onClick = { selectedTemplateIndex = index },
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.mdLg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Slate900,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = template.departments.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                            Text(
                                text = template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400,
                            )
                        }
                    }
                }
                if (selectedTemplateIndex >= 0) {
                    item {
                        Button(
                            onClick = { step = 2 },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radii.lg),
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.xs)
                    .verticalScroll(rememberScrollState()),
            ) {
                ScrollableHeader(
                    title = "Mission & Identity",
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                Text(
                    text = "Name the organization and optionally override its mission",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500,
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                OutlinedTextField(
                    value = orgName,
                    onValueChange = { orgName = it },
                    label = { Text("Organization Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                OutlinedTextField(
                    value = orgMission,
                    onValueChange = { orgMission = it },
                    label = { Text("Mission") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                GradientButton(
                    text = if (creating) "Creating…" else "Launch Organization",
                    onClick = {
                        if (!creating) {
                            creating = true
                            scope.launch {
                                try {
                                    val template = templates.getOrNull(selectedTemplateIndex)?.id ?: "startup"
                                    val name = orgName.ifBlank { "My ${template.replaceFirstChar { it.uppercase() }} Society" }
                                    val resp = app.repository.createOrganization(name, template, orgMission.trim())
                                    onLaunch(resp.organizationId)
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to create organization"
                                } finally {
                                    creating = false
                                }
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }
}
