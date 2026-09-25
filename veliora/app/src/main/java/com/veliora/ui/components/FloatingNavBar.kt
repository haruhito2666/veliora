package com.veliora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veliora.ui.theme.BrandDark
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Spacing
import com.veliora.ui.theme.White

// ═══════════════════════════════════════════════════════════════════════
// FLOATING NAV BAR — the app's primary navigation.
//
// A long rounded-rectangle pill that floats above the content with
// three destinations: Home · Investigations · Comms. Selected item is
// tinted indigo; unselected is slate. Generous whitespace inside so the
// bar feels light, not heavy.
// ═══════════════════════════════════════════════════════════════════════

data class FloatingNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun FloatingNavBar(
    items: List<FloatingNavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.md, bottom = Spacing.xs),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(24.dp),
                    // Inline shadow tint — Slate900 (0x0F172A) at 0x14/0x1A alpha,
                    // the app's ink. Kept inline: shadow colors are not theme slots.
                    ambientColor = Color(0x140F172A),
                    spotColor = Color(0x1A0F172A),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(White)
                .padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(
                            color = if (selected) BrandGradientStrong
                            else Color.Transparent
                        )
                        .clickable { onNavigate(item.route) }
                        .padding(vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) BrandDark else Slate400,
                        modifier = Modifier.height(22.dp),
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) BrandDark else Slate400,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
