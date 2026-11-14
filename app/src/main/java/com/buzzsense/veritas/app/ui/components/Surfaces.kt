package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Flat mist-grey plane on the canvas: no border, no shadow, 16dp corners.
 * Pass [onClick] to make the whole card tappable (alpha feedback only).
 */
@Composable
fun BuzzCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = BuzzTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(BuzzTheme.spacing.lg),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(BuzzTheme.spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier
            .pressAlpha(interactionSource)
            .clip(BuzzTheme.shapes.large)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier.clip(BuzzTheme.shapes.large)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .background(containerColor)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Fill of a [BuzzIconChip]. */
enum class IconChipTone { Neutral, Brand, Canvas, Hazard, Watch }

/** Square icon chip (8dp corners) used on tiles, rows and result headers. */
@Composable
fun BuzzIconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: IconChipTone = IconChipTone.Neutral,
    size: Dp = 32.dp,
) {
    val scheme = BuzzTheme.colorScheme
    val buzz = BuzzTheme.colors
    val (container, content) = when (tone) {
        IconChipTone.Neutral -> scheme.surfaceContainer to scheme.onSurface
        IconChipTone.Brand -> buzz.resultSafe to buzz.onResultSafe
        IconChipTone.Canvas -> scheme.surfaceContainerLowest to buzz.resultSafe
        IconChipTone.Hazard -> buzz.resultHazard to buzz.onResultHazard
        IconChipTone.Watch -> buzz.resultWatch to buzz.onResultWatch
    }
    Box(
        modifier = modifier
            .size(size)
            .background(container, BuzzTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.6f))
    }
}

/** Tappable mist card row: icon chip, label, chevron ("Tips for a clear recording"). */
@Composable
fun BuzzActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuzzCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(BuzzTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BuzzIconChip(icon)
            Text(
                title,
                style = BuzzTheme.typography.titleMedium,
                color = BuzzTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = BuzzTheme.spacing.md),
            )
            Icon(BuzzIcons.ChevronRight, contentDescription = null, tint = BuzzTheme.colorScheme.outline)
        }
    }
}

/** Grid tile: icon chip, title, subtitle. Use two per row. */
@Composable
fun BuzzTile(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTone: IconChipTone = IconChipTone.Neutral,
    onClick: (() -> Unit)? = null,
) {
    BuzzCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(BuzzTheme.spacing.md),
        verticalArrangement = Arrangement.Top,
    ) {
        BuzzIconChip(icon, tone = iconTone, size = 28.dp)
        Spacer(Modifier.height(BuzzTheme.spacing.md))
        Text(title, style = BuzzTheme.typography.titleSmall, color = BuzzTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(subtitle, style = BuzzTheme.typography.bodySmall, color = BuzzTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzCardPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuzzCard {
                BuzzEyebrow("Spectral analysis")
                Text("Flat mist plane, no border, no shadow.", style = BuzzTheme.typography.bodyMedium)
            }
            BuzzActionCard(title = "Tips for a clear recording", icon = BuzzIcons.Lightbulb, onClick = {})
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzTilePreview() {
    BuzzPreviewContainer {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BuzzTile("How it works", BuzzIcons.GraphicEq, subtitle = "On-device model", iconTone = IconChipTone.Brand, modifier = Modifier.weight(1f))
            BuzzTile("Meet Aedes", BuzzIcons.MenuBook, subtitle = "Know the vector", modifier = Modifier.weight(1f))
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzIconChipPreview() {
    BuzzPreviewContainer {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconChipTone.entries.forEach { BuzzIconChip(BuzzIcons.Shield, tone = it) }
        }
    }
}
