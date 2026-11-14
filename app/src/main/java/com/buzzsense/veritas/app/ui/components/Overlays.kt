package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import com.buzzsense.veritas.app.ui.theme.SheetShape

/** 40x4 grey handle at the top of every sheet. */
@Composable
fun BuzzDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = BuzzTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(BuzzTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

/**
 * Sheet body layout: title, subtitle, free content, then pinned actions.
 * Used inside [BuzzModalBottomSheet] and directly in previews.
 */
@Composable
fun BuzzSheetContent(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuzzTheme.spacing.margin)
            .padding(bottom = BuzzTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BuzzTheme.typography.headlineSmall, color = BuzzTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.outline)
            }
        }
        content()
        if (actions != null) {
            Column(
                modifier = Modifier.padding(top = BuzzTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm),
                content = actions,
            )
        }
    }
}

/**
 * Flat modal sheet: opaque canvas, 24dp top corners, no tonal elevation,
 * ink scrim (no blur).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuzzModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = BuzzTheme.colorScheme.surfaceContainerLowest,
        contentColor = BuzzTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = BuzzTheme.colorScheme.scrim.copy(alpha = 0.45f),
        dragHandle = { BuzzDragHandle() },
    ) {
        Column(Modifier.navigationBarsPadding(), content = content)
    }
}

/** Flat confirmation dialog (e.g. delete a recording). */
@Composable
fun BuzzAlertDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = BuzzTheme.shapes.large,
        containerColor = BuzzTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        title = { Text(title, style = BuzzTheme.typography.headlineSmall) },
        text = { Text(text, style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            BuzzButton(
                text = confirmLabel,
                onClick = onConfirm,
                variant = if (destructive) BuzzButtonVariant.Destructive else BuzzButtonVariant.Primary,
            )
        },
        dismissButton = { BuzzTextButton(text = dismissLabel, onClick = onDismiss) },
    )
}

@BuzzPreviews
@Composable
private fun BuzzSheetContentPreview() {
    BuzzPreviewContainer {
        Column(
            Modifier.background(BuzzTheme.colorScheme.surfaceContainerLowest, SheetShape),
        ) {
            BuzzDragHandle()
            BuzzSheetContent(
                title = "Recording tips",
                subtitle = "Hold the phone within 30 cm of the mosquito in a quiet room.",
                actions = {
                    BuzzButton(text = "Got it", onClick = {}, variant = BuzzButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
                },
            ) {
                BuzzHighlightedText("Turn off fans: they mask the buzz.", highlight = "fans")
            }
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzAlertDialogPreview() {
    BuzzPreviewContainer {
        BuzzAlertDialog(
            title = "Delete recording?",
            text = "This removes the result from History. It cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {},
            dismissLabel = "Cancel",
            onDismiss = {},
            destructive = true,
        )
    }
}
