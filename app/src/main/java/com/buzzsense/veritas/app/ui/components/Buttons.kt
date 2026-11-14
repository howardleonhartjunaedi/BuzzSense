package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import androidx.compose.runtime.CompositionLocalProvider

/** Button fills from DESIGN.md "Buttons" and the Stitch style guide. */
enum class BuzzButtonVariant {
    /** Ink black fill, white text. One per screen. */
    Primary,

    /** Mist grey fill, ink text. */
    Secondary,

    /** Forest green fill, white text. Save / confirm a safe action. */
    Brand,

    /** Alert red fill, white text. Delete and other destructive actions. */
    Destructive,
}

@Immutable
private data class ButtonColors(val container: Color, val content: Color)

@Composable
private fun BuzzButtonVariant.colors(enabled: Boolean): ButtonColors {
    val scheme = BuzzTheme.colorScheme
    val buzz = BuzzTheme.colors
    val base = when (this) {
        BuzzButtonVariant.Primary -> ButtonColors(scheme.primary, scheme.onPrimary)
        BuzzButtonVariant.Secondary -> ButtonColors(scheme.surfaceContainerHigh, scheme.onSurface)
        BuzzButtonVariant.Brand -> ButtonColors(buzz.resultSafe, buzz.onResultSafe)
        BuzzButtonVariant.Destructive -> ButtonColors(buzz.resultHazard, buzz.onResultHazard)
    }
    return if (enabled) base else ButtonColors(base.container.copy(alpha = 0.3f), base.content.copy(alpha = 0.6f))
}

/** 52dp matches the Stitch screens and clears the 48dp touch target. */
val BuzzButtonHeight: Dp = 52.dp

/**
 * Flat filled button. Slot version: [content] is laid out in a centered row
 * with the label text style and content color already provided.
 */
@Composable
fun BuzzButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BuzzButtonVariant = BuzzButtonVariant.Primary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = variant.colors(enabled)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = BuzzButtonHeight)
            .pressAlpha(interactionSource)
            .clip(BuzzTheme.shapes.small)
            .background(colors.container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = BuzzTheme.spacing.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.content,
            LocalTextStyle provides BuzzTheme.typography.labelLarge,
        ) {
            content()
        }
    }
}

/** Text + optional leading icon convenience overload. */
@Composable
fun BuzzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BuzzButtonVariant = BuzzButtonVariant.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    BuzzButton(onClick = onClick, modifier = modifier, variant = variant, enabled = enabled) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(BuzzTheme.spacing.sm))
        }
        Text(text = text, textAlign = TextAlign.Center)
    }
}

/** Quiet grey text action ("Record again", "Skip"). No container. */
@Composable
fun BuzzTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = BuzzTheme.colorScheme.outline,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = BuzzTheme.typography.bodyMedium,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        textAlign = TextAlign.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .pressAlpha(interactionSource, pressedAlpha = 0.75f)
            .clip(BuzzTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = BuzzTheme.spacing.md, vertical = 14.dp),
    )
}

/** Forest green inline link with a trailing arrow ("Official dengue info"). */
@Composable
fun BuzzLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .pressAlpha(interactionSource)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xs),
    ) {
        Text(text, style = BuzzTheme.typography.titleLarge, color = BuzzTheme.colors.resultSafe)
        Icon(
            BuzzIcons.ArrowForward,
            contentDescription = null,
            tint = BuzzTheme.colors.resultSafe,
            modifier = Modifier.size(18.dp),
        )
    }
}

@BuzzPreviews
@Composable
private fun BuzzButtonPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BuzzButtonVariant.entries.forEach { variant ->
                BuzzButton(text = variant.name, onClick = {}, variant = variant, modifier = Modifier.fillMaxWidth())
                BuzzButton(
                    text = "${variant.name} (disabled)",
                    onClick = {},
                    variant = variant,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BuzzButton(text = "Share", onClick = {}, leadingIcon = BuzzIcons.Share, modifier = Modifier.fillMaxWidth())
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzTextButtonPreview() {
    BuzzPreviewContainer {
        Column {
            BuzzTextButton(text = "Record again", onClick = {}, modifier = Modifier.fillMaxWidth())
            BuzzTextButton(text = "Skip (disabled)", onClick = {}, enabled = false)
            BuzzLinkButton(text = "Official dengue info", onClick = {})
        }
    }
}
