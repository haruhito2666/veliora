package com.veliora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.veliora.ui.theme.Radii
import com.veliora.ui.theme.Slate100
import com.veliora.ui.theme.Spacing

/**
 * Lightweight placeholders shown while real on-device data is loading. They
 * keep layout stable instead of replacing whole screens with a spinner.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(Slate100),
    )
}

@Composable
fun OrgCardSkeleton(
    modifier: Modifier = Modifier,
) {
    VelioraCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.mdLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(modifier = Modifier.size(56.dp).clip(CircleShape))
            Spacer(Modifier.width(Spacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.45f).height(18.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.28f).height(12.dp))
            }
            SkeletonBox(modifier = Modifier.width(48.dp).height(14.dp))
        }
    }
}

@Composable
fun OrganizationListSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.smMd)) {
        repeat(3) { OrgCardSkeleton() }
    }
}

@Composable
fun HomeSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(132.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.35f).height(16.dp))
        repeat(3) {
            VelioraCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(Spacing.mdLg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(modifier = Modifier.size(44.dp).clip(CircleShape))
                    Spacer(Modifier.width(Spacing.md))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp))
                        SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp))
                    }
                }
            }
        }
    }
}
