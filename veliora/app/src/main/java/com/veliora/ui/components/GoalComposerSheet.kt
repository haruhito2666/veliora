package com.veliora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.veliora.data.InvestigationDepth
import com.veliora.ui.theme.BrandDark
import com.veliora.ui.theme.BrandPink
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Slate500
import com.veliora.ui.theme.Slate700
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.Spacing

// ═══════════════════════════════════════════════════════════════════════
// GOAL COMPOSER — the single place the user types free text to the org.
// A bottom sheet, not a full screen, so context is never lost.
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalComposerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (goal: String, depth: InvestigationDepth) -> Unit,
    initialGoal: String = "",
    title: String = "New Investigation",
) {
    if (!visible) return

    var goal by remember(visible, initialGoal) { mutableStateOf(initialGoal) }
    var depth by remember(visible) { mutableStateOf(InvestigationDepth.STANDARD) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Slate900,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Give the organization a goal — it decides who participates and how.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Should I build…?", color = Slate400) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(Radii.lg),
            )

            // Depth selector — three segmented pills
            DepthSelector(
                selected = depth,
                onSelect = { depth = it },
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            GradientButton(
                text = "Launch Investigation",
                onClick = {
                    if (goal.isNotBlank()) {
                        onCreate(goal.trim(), depth)
                        onDismiss()
                    }
                },
                enabled = goal.isNotBlank(),
            )
        }
    }
}

@Composable
private fun DepthSelector(
    selected: InvestigationDepth,
    onSelect: (InvestigationDepth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        InvestigationDepth.entries.forEach { option ->
            val isSelected = option == selected
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) BrandDark else Slate700,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(
                        color = if (isSelected) BrandGradientStrong
                        else BrandPink.copy(alpha = 0.4f)
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = Spacing.smMd),
            )
        }
    }
}
