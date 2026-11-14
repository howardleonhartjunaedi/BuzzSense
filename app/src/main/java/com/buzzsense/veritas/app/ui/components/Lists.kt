package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Activity / settings row. Rows separate by space, never by divider lines;
 * [selected] highlights with a flat container block.
 */
@Composable
fun BuzzListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = if (onClick != null) {
        Modifier
            .pressAlpha(interactionSource)
            .clip(BuzzTheme.shapes.small)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(clickable)
            .background(
                if (selected) BuzzTheme.colorScheme.surfaceContainer else Color.Transparent,
                BuzzTheme.shapes.small,
            )
            .padding(vertical = BuzzTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(headline, style = BuzzTheme.typography.titleSmall, color = BuzzTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(supporting, style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** One mist card holding several [BuzzListRow]s with `space-md` rhythm. */
@Composable
fun BuzzGroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BuzzCard(
        modifier = modifier,
        contentPadding = PaddingValues(BuzzTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm),
        content = content,
    )
}

/** Trailing value chip ("5 sec"). */
@Composable
fun BuzzValueChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = BuzzTheme.data.tabularSmall,
        color = BuzzTheme.colorScheme.onSurface,
        modifier = modifier
            .background(BuzzTheme.colorScheme.surfaceContainerHigh, BuzzTheme.shapes.small)
            .padding(horizontal = BuzzTheme.spacing.md, vertical = BuzzTheme.spacing.xs),
    )
}

/** Trailing chevron for navigation rows. */
@Composable
fun BuzzChevron(modifier: Modifier = Modifier) {
    Icon(BuzzIcons.ChevronRight, contentDescription = null, tint = BuzzTheme.colorScheme.outline, modifier = modifier)
}

/** Flat switch: forest-green track when on, no outline. */
@Composable
fun BuzzSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = BuzzTheme.colors.onResultSafe,
            checkedTrackColor = BuzzTheme.colors.resultSafe,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = BuzzTheme.colorScheme.surfaceContainerLowest,
            uncheckedTrackColor = BuzzTheme.colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@BuzzPreviews
@Composable
private fun BuzzGroupedListPreview() {
    BuzzPreviewContainer {
        var filter by remember { mutableStateOf(true) }
        BuzzGroupedList {
            BuzzListRow(
                headline = "Sample duration",
                supporting = "Standard acoustic window",
                trailing = { BuzzValueChip("5 sec") },
            )
            BuzzListRow(
                headline = "Filter ambient noise",
                supporting = "Fans and road traffic",
                trailing = { BuzzSwitch(checked = filter, onCheckedChange = { filter = it }) },
            )
            BuzzListRow(
                headline = "Today, 19:42",
                supporting = "Likely Aedes aegypti · 82%",
                selected = true,
                onClick = {},
                leading = { BuzzIconChip(BuzzIcons.Warning, tone = IconChipTone.Hazard) },
                trailing = { BuzzChevron() },
            )
            BuzzListRow(
                headline = "Yesterday, 07:10",
                supporting = "No mosquito heard",
                onClick = {},
                leading = {
                    BuzzIconChip(BuzzIcons.Check, tone = IconChipTone.Neutral, modifier = Modifier.clip(CircleShape))
                },
                trailing = { BuzzChevron() },
            )
        }
    }
}
