package com.veliora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veliora.ui.theme.BrandBlueSoft
import com.veliora.ui.theme.BrandDark
import com.veliora.ui.theme.BrandPink
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate200
import com.veliora.ui.theme.Slate400
import com.veliora.ui.theme.Spacing

// ═══════════════════════════════════════════════════════════════════════
// BRAND SURFACES — one color at a time, never gradients.
// Used sparingly: hero buttons, selected pills, and one or two signature
// surfaces. Everything else stays flat.
// ═══════════════════════════════════════════════════════════════════════

/** Brand pink — buttons, selected pills, progress. */
val BrandGradientStrong: Color = BrandPink

/** Soft brand blue — larger surfaces (banners, avatars, highlight boxes). */
val BrandGradientSoft: Color = BrandBlueSoft

/** Disabled state — flat, muted pink. */
val BrandGradientDisabled: Color = Slate200

/**
 * Primary CTA with the brand gradient fill and dark accent text.
 * Used for the hero actions: New Investigation, Launch, etc.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (enabled) BrandGradientStrong else BrandGradientDisabled,
                shape = RoundedCornerShape(Radii.lg),
            ),
        shape = RoundedCornerShape(Radii.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = BrandDark,
            disabledContentColor = Slate400,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandDark,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
            }
            Text(
                text = text,
                color = BrandDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
        }
    }
}
