package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/** State of one row in a [BuzzProgressStep] list. */
enum class StepState { Done, Active, Pending }

/** Analyzing checklist row: icon, label, then a check (done), spinner (active) or nothing. */
@Composable
fun BuzzProgressStep(
    label: String,
    icon: ImageVector,
    state: StepState,
    modifier: Modifier = Modifier,
    stateDescription: String = state.name,
) {
    val labelColor = if (state == StepState.Pending) BuzzTheme.colorScheme.outline else BuzzTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { this.stateDescription = stateDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = BuzzTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        Text(label, style = BuzzTheme.typography.titleMedium, color = labelColor, modifier = Modifier.weight(1f))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.Done -> Box(
                    Modifier
                        .size(24.dp)
                        .background(BuzzTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BuzzIcons.Check, contentDescription = null, tint = BuzzTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                }
                StepState.Active -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = BuzzTheme.colorScheme.primary,
                    trackColor = BuzzTheme.colors.track,
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round,
                )
                StepState.Pending -> Unit
            }
        }
    }
}

/** Fixed bar heights (0..1) for the static waveform preview; decorative, not measured audio. */
@Immutable
data class WaveformShape(val levels: List<Float>, val highlight: IntRange = IntRange.EMPTY)

internal val DefaultWaveformShape = WaveformShape(
    levels = listOf(0.25f, 0.38f, 0.5f, 0.38f, 0.75f, 0.88f, 0.62f, 1f, 0.75f, 0.5f, 0.88f, 1f, 0.62f, 0.38f, 0.5f, 0.25f, 0.62f, 0.38f, 0.25f, 0.12f),
    highlight = 4..12,
)

/** Thin vertical bars; [WaveformShape.highlight] bars in forest green, the rest neutral. */
@Composable
fun BuzzWaveform(
    shape: WaveformShape,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
) {
    val on = BuzzTheme.colorScheme.secondary
    val off = BuzzTheme.colorScheme.outlineVariant
    Canvas(modifier.height(height)) {
        val n = shape.levels.size
        if (n == 0) return@Canvas
        val bar = 3.dp.toPx()
        val gap = ((size.width - bar * n) / (n - 1).coerceAtLeast(1)).coerceAtLeast(1f)
        shape.levels.forEachIndexed { i, level ->
            val h = (size.height * level.coerceIn(0.1f, 1f))
            drawRoundRect(
                color = if (i in shape.highlight) on else off,
                topLeft = Offset(i * (bar + gap), (size.height - h) / 2),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2),
            )
        }
    }
}

/**
 * "Recording ready" player row: round play/pause button, waveform preview and duration.
 * Playback stays in memory; this component only reports taps.
 */
@Composable
fun BuzzPlaybackRow(
    playing: Boolean,
    onTogglePlay: () -> Unit,
    duration: String,
    playLabel: String,
    pauseLabel: String,
    modifier: Modifier = Modifier,
    waveform: WaveformShape = DefaultWaveformShape,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BuzzCard(
        modifier = modifier,
        contentPadding = PaddingValues(BuzzTheme.spacing.md),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressAlpha(interactionSource)
                    .clip(CircleShape)
                    .background(BuzzTheme.colorScheme.surfaceContainerHighest)
                    .clickable(interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onTogglePlay)
                    .semantics { contentDescription = if (playing) pauseLabel else playLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) BuzzIcons.Pause else BuzzIcons.PlayArrow,
                    contentDescription = null,
                    tint = BuzzTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
                )
            }
            BuzzWaveform(waveform, Modifier.weight(1f))
            Text(duration, style = BuzzTheme.data.tabularSmall, color = BuzzTheme.colorScheme.onSurface)
        }
    }
}

/** One bar of a [BuzzFrequencyCompare] chart. */
@Immutable
data class FrequencyBar(val label: String, val level: Float, val highlighted: Boolean = false)

/**
 * Walkthrough illustration: a few tall bars with a frequency label under each; the
 * highlighted bar fills in forest green. Illustrative only, not a measurement.
 */
@Composable
fun BuzzFrequencyCompare(
    bars: List<FrequencyBar>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 144.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.lg, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .width(48.dp)
                        .height(barHeight * bar.level.coerceIn(0.2f, 1f))
                        .background(BuzzTheme.colorScheme.surfaceContainerHighest, BuzzTheme.shapes.small),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight * bar.level.coerceIn(0.2f, 1f) * 0.8f)
                            .background(
                                if (bar.highlighted) BuzzTheme.colorScheme.secondary else BuzzTheme.colorScheme.outline.copy(alpha = 0.8f),
                                BuzzTheme.shapes.extraSmall,
                            ),
                    )
                }
                Text(
                    bar.label,
                    style = BuzzTheme.typography.labelSmall,
                    color = if (bar.highlighted) BuzzTheme.colorScheme.secondary else BuzzTheme.colorScheme.outline,
                )
            }
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzProgressStepPreview() {
    BuzzPreviewContainer {
        BuzzCard(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BuzzProgressStep("Sound", BuzzIcons.Mic, StepState.Done)
            BuzzProgressStep("Picture of the sound", BuzzIcons.GraphicEq, StepState.Active)
            BuzzProgressStep("AI model", BuzzIcons.AutoAwesome, StepState.Pending)
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzPlaybackRowPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuzzPlaybackRow(playing = false, onTogglePlay = {}, duration = "0:05", playLabel = "Play", pauseLabel = "Pause")
            BuzzPlaybackRow(playing = true, onTogglePlay = {}, duration = "0:05", playLabel = "Play", pauseLabel = "Pause")
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzFrequencyComparePreview() {
    BuzzPreviewContainer {
        BuzzCard {
            BuzzFrequencyCompare(
                listOf(
                    FrequencyBar("Culex", 0.66f),
                    FrequencyBar("Aedes", 1f, highlighted = true),
                    FrequencyBar("Anopheles", 0.78f),
                ),
            )
        }
    }
}
