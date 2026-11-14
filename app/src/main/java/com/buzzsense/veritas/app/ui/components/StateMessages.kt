package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Centered status message for blocking states: Microphone is off, You're
 * offline, Something went wrong, Too short, History empty.
 */
@Composable
fun BuzzMessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: IconChipTone = IconChipTone.Neutral,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuzzTheme.spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md),
    ) {
        BuzzIconChip(icon, tone = tone, size = 48.dp)
        Text(title, style = BuzzTheme.typography.headlineSmall, color = BuzzTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text(body, style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actions != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = BuzzTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm),
                content = actions,
            )
        }
    }
}

/** Indeterminate forest-green spinner on the neutral track (Analyzing, Loading). */
@Composable
fun BuzzProgressIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(56.dp),
        color = BuzzTheme.colorScheme.secondary,
        trackColor = BuzzTheme.colors.track,
        strokeWidth = 4.dp,
        strokeCap = StrokeCap.Round,
    )
}

@BuzzPreviews
@Composable
private fun BuzzMessageStatePreview() {
    BuzzPreviewContainer {
        BuzzMessageState(
            icon = BuzzIcons.MicOff,
            title = "Microphone is off",
            body = "BuzzSense needs the microphone to hear the buzz. Audio never leaves your phone.",
            actions = {
                BuzzButton(text = "Allow microphone", onClick = {}, modifier = Modifier.fillMaxWidth())
                BuzzTextButton(text = "Not now", onClick = {}, modifier = Modifier.fillMaxWidth())
            },
        )
    }
}

@BuzzPreviews
@Composable
private fun BuzzProgressIndicatorPreview() {
    BuzzPreviewContainer {
        BuzzProgressIndicator()
    }
}
