package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Two-tone tabular readout ("620 Hz", "82%", "0:03 / 0:05"): the value in
 * the content color, the unit right after it in the muted unit tone.
 */
@Composable
fun BuzzTwoToneText(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    style: TextStyle = BuzzTheme.data.tabularLarge,
    unitStyle: TextStyle? = null,
    valueColor: Color = BuzzTheme.colorScheme.onSurface,
    unitColor: Color = BuzzTheme.colors.unit,
) {
    val text = remember(value, unit, valueColor, unitColor, unitStyle) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = valueColor)) { append(value) }
            val unitSpan = unitStyle?.toSpanStyle()?.copy(color = unitColor) ?: SpanStyle(color = unitColor)
            withStyle(unitSpan) { append(unit) }
        }
    }
    Text(text = text, style = style, modifier = modifier)
}

/** Recording timer "0:03 / 0:05" in hero size. */
@Composable
fun BuzzTimerReadout(
    elapsedSeconds: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
) {
    BuzzTwoToneText(
        value = formatSeconds(elapsedSeconds),
        unit = " / ${formatSeconds(totalSeconds)}",
        style = BuzzTheme.data.hero,
        unitColor = BuzzTheme.colorScheme.outlineVariant,
        modifier = modifier,
    )
}

private fun formatSeconds(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * Flat 6dp confidence bar. [progress] is 0..1. Drawn in one draw block (no
 * nested layouts), so it is cheap inside lists.
 */
@Composable
fun BuzzConfidenceBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = BuzzTheme.colors.track,
) {
    val clamped = progress.coerceIn(0f, 1f)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) }
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(trackColor, cornerRadius = radius)
                if (clamped > 0f) {
                    drawRoundRect(color, size = Size(size.width * clamped, size.height), cornerRadius = radius)
                }
            },
    )
}

/** Uppercase analytical label ("SAMPLE DURATION") over a tabular value. */
@Composable
fun BuzzMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    valueColor: Color = BuzzTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier
            .background(BuzzTheme.colorScheme.surfaceContainerLow, BuzzTheme.shapes.medium)
            .padding(BuzzTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xs),
    ) {
        BuzzEyebrow(label)
        BuzzTwoToneText(value = value, unit = unit, style = BuzzTheme.data.tabularSmall, valueColor = valueColor)
    }
}

/** Tone for [BuzzMetricChip] values. */
enum class MetricTone { Accent, Safe, Neutral }

/**
 * Compact two-tone acoustic chip: parameter name at 50% opacity, value in
 * saturated mustard or forest green.
 */
@Composable
fun BuzzMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: MetricTone = MetricTone.Accent,
) {
    val valueColor = when (tone) {
        MetricTone.Accent -> BuzzTheme.colors.accent
        MetricTone.Safe -> BuzzTheme.colors.resultSafe
        MetricTone.Neutral -> BuzzTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .background(BuzzTheme.colorScheme.surfaceContainerLow, BuzzTheme.shapes.extraSmall)
            .padding(horizontal = BuzzTheme.spacing.sm, vertical = BuzzTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            style = BuzzTheme.typography.labelSmall,
            color = BuzzTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(value.uppercase(), style = BuzzTheme.typography.labelSmall.copy(fontSize = 12.sp), color = valueColor)
    }
}

@BuzzPreviews
@Composable
private fun BuzzTwoToneTextPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuzzTwoToneText(value = "620", unit = " Hz")
            BuzzTwoToneText(value = "82", unit = "%", style = BuzzTheme.data.hero, unitStyle = BuzzTheme.typography.displayMedium)
            BuzzTimerReadout(elapsedSeconds = 3, totalSeconds = 5)
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzConfidenceBarPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuzzConfidenceBar(0.82f, BuzzTheme.colors.resultHazard)
            BuzzConfidenceBar(0.92f, BuzzTheme.colors.resultSafe)
            BuzzConfidenceBar(0.46f, BuzzTheme.colors.resultWatch)
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzMetricPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BuzzMetricTile("Sample duration", "4.2", unit = " s", modifier = Modifier.weight(1f))
                BuzzMetricTile("Peak", "480", unit = " Hz", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BuzzMetricChip("Peak", "480 Hz")
                BuzzMetricChip("Windows", "5/5", tone = MetricTone.Safe)
                BuzzMetricChip("Level", "48 dB", tone = MetricTone.Neutral, modifier = Modifier.width(120.dp))
            }
        }
    }
}
