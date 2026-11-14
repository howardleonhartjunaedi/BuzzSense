package com.buzzsense.veritas.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/** Monolithic checkbox: flat subdued square unchecked, solid fill + glyph checked. */
@Composable
fun BuzzCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fill = if (checked) BuzzTheme.colors.resultSafe else BuzzTheme.colorScheme.surfaceContainerHighest
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(48.dp) // touch target
            .then(toggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(if (enabled) fill else fill.copy(alpha = 0.4f), BuzzTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(BuzzIcons.Check, contentDescription = null, tint = BuzzTheme.colors.onResultSafe, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Prevention checklist row: canvas disc with a forest icon, the action text,
 * and a [BuzzCheckbox]. The whole row toggles.
 */
@Composable
fun BuzzChecklistItem(
    text: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BuzzTheme.shapes.small)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(start = BuzzTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(BuzzTheme.colorScheme.surfaceContainerLowest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = BuzzTheme.colors.resultSafe, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = BuzzTheme.typography.titleMedium, color = BuzzTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(supporting, style = BuzzTheme.typography.labelMedium, color = BuzzTheme.colorScheme.outline)
            }
        }
        // Row owns the toggle semantics, so the box is visual only.
        BuzzCheckbox(checked = checked, onCheckedChange = null)
    }
}

/** Segmented control on a pale track; the selected segment is a canvas block. */
@Composable
fun BuzzSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BuzzTheme.colorScheme.surfaceContainerHigh, BuzzTheme.shapes.small)
            .padding(BuzzTheme.spacing.xs)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                text = label,
                style = BuzzTheme.typography.titleSmall,
                color = if (selected) BuzzTheme.colorScheme.onSurface else BuzzTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(BuzzTheme.shapes.extraSmall)
                    .background(if (selected) BuzzTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(index) },
                        role = Role.Tab,
                        interactionSource = interactionSource,
                        indication = null,
                    )
                    .padding(vertical = 10.dp),
            )
        }
    }
}

/** Walkthrough dots; the active dot stretches into a mustard bar. */
@Composable
fun BuzzPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            val width by animateDpAsState(if (active) 24.dp else 8.dp, label = "dotWidth")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .background(
                        if (active) BuzzTheme.colors.accent else BuzzTheme.colorScheme.surfaceContainerHigh,
                        CircleShape,
                    ),
            )
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzChecklistPreview() {
    BuzzPreviewContainer {
        val checked = remember { mutableStateOf(setOf(0)) }
        BuzzCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "Drain water containers" to BuzzIcons.WaterDrop,
                "Cover water storage" to BuzzIcons.Shield,
                "Recycle items that hold water" to BuzzIcons.Recycling,
            ).forEachIndexed { i, (text, icon) ->
                BuzzChecklistItem(
                    text = text,
                    icon = icon,
                    checked = i in checked.value,
                    onCheckedChange = { on -> checked.value = if (on) checked.value + i else checked.value - i },
                    supporting = if (i == 0) "WHO · Dengue prevention" else null,
                )
            }
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzCheckboxPreview() {
    BuzzPreviewContainer {
        Row {
            BuzzCheckbox(checked = false, onCheckedChange = {})
            BuzzCheckbox(checked = true, onCheckedChange = {})
            BuzzCheckbox(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzSegmentedControlPreview() {
    BuzzPreviewContainer {
        var selected by remember { mutableIntStateOf(0) }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BuzzSegmentedControl(listOf("Acoustic 5s", "Spectrum", "Harmonics"), selected, { selected = it })
            BuzzPageIndicator(pageCount = 4, currentPage = 1)
        }
    }
}
