package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/** Mustard "1 of 3" walkthrough badge. Softly squared, never a pill (DESIGN.md "Shapes"). */
@Composable
fun BuzzStepBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = BuzzTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = BuzzTheme.colors.onAccent,
        modifier = modifier
            .background(BuzzTheme.colors.accent, BuzzTheme.shapes.extraSmall)
            .padding(horizontal = BuzzTheme.spacing.sm, vertical = 2.dp),
    )
}

/** "● ACOUSTIC GUARD ACTIVE": a small status dot with an uppercase label. */
@Composable
fun BuzzStatusDot(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color = BuzzTheme.colorScheme.secondary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(dotColor, CircleShape),
        )
        BuzzEyebrow(text)
    }
}

/** Plain label chip ("Kotlin / Compose") on a canvas fill, for use inside mist cards. */
@Composable
fun BuzzTextChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = BuzzTheme.typography.labelSmall,
        color = BuzzTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(BuzzTheme.colorScheme.surfaceContainerLowest, BuzzTheme.shapes.extraSmall)
            .padding(horizontal = 10.dp, vertical = BuzzTheme.spacing.xs),
    )
}

@BuzzPreviews
@Composable
private fun BuzzBadgesPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuzzStepBadge("1 of 3")
            BuzzStatusDot("Audio stays on this phone")
            BuzzCard {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BuzzTextChip("Kotlin / Compose")
                    BuzzTextChip("ONNX Runtime")
                }
            }
        }
    }
}
