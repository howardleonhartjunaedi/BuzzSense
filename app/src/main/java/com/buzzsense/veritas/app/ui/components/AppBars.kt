package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/** "graphic_eq + BuzzSense" wordmark used in the Stitch TopAppBar. */
@Composable
fun BuzzBrandMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm),
    ) {
        Icon(BuzzIcons.GraphicEq, contentDescription = null, tint = BuzzTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.brand_name),
            style = BuzzTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = BuzzTheme.colorScheme.primary,
        )
    }
}

/**
 * Flat top bar: no elevation, no divider, canvas colored. Slots for the
 * leading navigation, the title and trailing actions.
 */
@Composable
fun BuzzTopAppBar(
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit = { BuzzBrandMark() },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BuzzTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = BuzzTheme.spacing.margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) {
            navigation()
            Spacer(Modifier.size(BuzzTheme.spacing.sm))
        }
        Box(Modifier.weight(1f)) { title() }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Back affordance: grey disc + chevron, per the style guide. */
@Composable
fun BuzzBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(BuzzTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                BuzzIcons.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = BuzzTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Top-bar icon action (History, Share). */
@Composable
fun BuzzTopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = contentDescription, tint = BuzzTheme.colorScheme.primary)
    }
}

/** One bottom navigation destination. */
@Immutable
data class BuzzNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Four-item flat bottom bar. The active item is ink colored with a forest
 * green dot beneath its label; others are quiet outline grey.
 */
@Composable
fun BuzzBottomNavBar(
    items: List<BuzzNavItem>,
    selectedKey: String,
    onSelect: (BuzzNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BuzzTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.sm)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEach { item ->
            BuzzNavBarItem(
                item = item,
                selected = item.key == selectedKey,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun BuzzNavBarItem(item: BuzzNavItem, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) BuzzTheme.colorScheme.primary else BuzzTheme.colorScheme.outline
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .pressAlpha(interactionSource)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(horizontal = BuzzTheme.spacing.sm, vertical = BuzzTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, contentDescription = null, tint = tint)
        Text(
            text = item.label,
            style = BuzzTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = BuzzTheme.spacing.xs),
        )
        Box(
            Modifier
                .padding(top = BuzzTheme.spacing.xs)
                .size(4.dp)
                .background(if (selected) BuzzTheme.colorScheme.secondary else Color.Transparent, CircleShape),
        )
    }
}

/** The app's four tabs from the Stitch screens. */
@Composable
fun rememberBuzzNavItems(): List<BuzzNavItem> {
    val detect = stringResource(R.string.nav_detect)
    val learn = stringResource(R.string.nav_learn)
    val map = stringResource(R.string.nav_map)
    val about = stringResource(R.string.nav_about)
    return remember(detect, learn, map, about) {
        listOf(
            BuzzNavItem("detect", detect, BuzzIcons.GraphicEq),
            BuzzNavItem("learn", learn, BuzzIcons.MenuBook),
            BuzzNavItem("map", map, BuzzIcons.NearMe),
            BuzzNavItem("about", about, BuzzIcons.Info),
        )
    }
}

@BuzzPreviews
@Composable
private fun BuzzTopAppBarPreview() {
    BuzzPreviewContainer {
        Column {
            BuzzTopAppBar(actions = {
                BuzzTopBarAction(BuzzIcons.History, "History", onClick = {})
            })
            BuzzTopAppBar(
                navigation = { BuzzBackButton(onClick = {}) },
                title = { Text("Result details", style = BuzzTheme.typography.titleLarge) },
                actions = { BuzzTextButton(text = "Skip", onClick = {}) },
            )
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzBottomNavBarPreview() {
    BuzzPreviewContainer {
        var selected by remember { mutableIntStateOf(0) }
        val items = rememberBuzzNavItems()
        BuzzBottomNavBar(
            items = items,
            selectedKey = items[selected].key,
            onSelect = { selected = items.indexOf(it) },
        )
    }
}
