package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.buzzsense.veritas.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/** Horizontally scrolling single-choice topic filter (Learn). */
@Composable
fun BuzzFilterChips(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                text = label,
                style = BuzzTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = if (selected) BuzzTheme.colorScheme.onPrimary else BuzzTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .pressAlpha(interactionSource)
                    .clip(BuzzTheme.shapes.small)
                    .background(if (selected) BuzzTheme.colorScheme.primary else BuzzTheme.colorScheme.surfaceContainerLow)
                    .selectable(selected, interactionSource = interactionSource, indication = null, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = BuzzTheme.spacing.sm),
            )
        }
    }
}

/**
 * Learn article card: a header block (photo, or a large faint icon when there is none, with
 * the topic tag and an outward arrow), then source line, title, two-line summary and a link label.
 */
@Composable
fun BuzzArticleCard(
    tag: String,
    meta: String,
    title: String,
    summary: String,
    linkLabel: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: Painter? = null,
) {
    BuzzCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(144.dp)
                .background(BuzzTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (image != null) {
                Image(
                    painter = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = BuzzTheme.colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center),
                )
            }
            BuzzStatusTag(tag, modifier = Modifier.padding(12.dp).align(Alignment.TopStart))
            Box(
                Modifier
                    .padding(12.dp)
                    .size(28.dp)
                    .background(BuzzTheme.colorScheme.surfaceContainerLowest, CircleShape)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BuzzIcons.NorthEast, contentDescription = null, tint = BuzzTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.padding(BuzzTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(meta, style = BuzzTheme.typography.labelMedium, color = BuzzTheme.colorScheme.outline)
            Text(title, style = BuzzTheme.typography.titleMedium, color = BuzzTheme.colorScheme.onSurface)
            Text(
                summary,
                style = BuzzTheme.typography.bodyMedium,
                color = BuzzTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = BuzzTheme.spacing.xs),
            ) {
                Text(linkLabel, style = BuzzTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = BuzzTheme.colors.resultSafe)
                Icon(BuzzIcons.ChevronRight, contentDescription = null, tint = BuzzTheme.colors.resultSafe, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Credits-style card: headline + trailing outline icon, body text, then optional chips. */
@Composable
fun BuzzInfoCard(
    title: String,
    icon: ImageVector,
    body: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    BuzzCard(modifier = modifier, onClick = onClick, verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(title, style = BuzzTheme.typography.headlineSmall, color = BuzzTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(icon, contentDescription = null, tint = BuzzTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
        Text(body, style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.onSurfaceVariant)
        if (footer != null) {
            Column(Modifier.padding(top = BuzzTheme.spacing.sm), content = footer)
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzFilterChipsPreview() {
    BuzzPreviewContainer {
        var selected by remember { mutableIntStateOf(0) }
        BuzzFilterChips(listOf("All topics", "Dengue (Aedes)", "Malaria (Anopheles)", "Acoustic AI"), selected, { selected = it })
    }
}

@BuzzPreviews
@Composable
private fun BuzzArticleCardPreview() {
    BuzzPreviewContainer {
        BuzzArticleCard(
            tag = "Dengue vector",
            meta = "WHO · 3 min read",
            title = "Detecting Aedes aegypti: the main dengue mosquito",
            summary = "White markings, daytime biting and a high-pitched wingbeat. Why early detection matters.",
            linkLabel = "Read the fact sheet",
            icon = BuzzIcons.GraphicEq,
            onClick = {},
            image = painterResource(R.drawable.learn_aedes),
        )
    }
}

@BuzzPreviews
@Composable
private fun BuzzInfoCardPreview() {
    BuzzPreviewContainer {
        BuzzInfoCard(title = "Acoustic data", icon = BuzzIcons.GraphicEq, body = "HumBugDB (University of Oxford), BioDCASE") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BuzzMetricChip("Wingbeat", "300–800 Hz", tone = MetricTone.Safe)
            }
        }
    }
}
