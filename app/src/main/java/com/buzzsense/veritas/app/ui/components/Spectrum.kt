package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Normalized spectrum bins (0..1) for display only, plus the bins that
 * hold the wingbeat peak. Built from labels/scores the detector already
 * exposes; never from raw audio kept after the run.
 */
@Immutable
data class SpectrumData(
    val bins: List<Float>,
    val peakBins: IntRange = IntRange.EMPTY,
)

/**
 * Flat vector frequency bars. Peak bins in forest green, the rest in a
 * neutral tone. No grid ticks, no gradient fill.
 */
@Composable
fun BuzzSpectrumBars(
    data: SpectrumData,
    modifier: Modifier = Modifier,
    height: Dp = 112.dp,
) {
    val peak = BuzzTheme.colorScheme.secondary
    val base = BuzzTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val count = data.bins.size
        if (count == 0) return@Canvas
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val radius = CornerRadius(4.dp.toPx())
        data.bins.forEachIndexed { i, v ->
            val h = size.height * v.coerceIn(0.04f, 1f)
            drawRoundRect(
                color = if (i in data.peakBins) peak else base,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }
    }
}

/** Mist card with eyebrow, peak chip and the bars. */
@Composable
fun BuzzSpectrumCard(
    title: String,
    peakLabel: String,
    data: SpectrumData,
    modifier: Modifier = Modifier,
) {
    BuzzCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BuzzEyebrow(title, modifier = Modifier.weight(1f))
            BuzzMetricChip(label = "Peak", value = peakLabel, tone = MetricTone.Safe)
        }
        BuzzSpectrumBars(data)
    }
}

internal val SampleSpectrum = SpectrumData(
    bins = listOf(0.3f, 0.45f, 0.38f, 0.58f, 0.92f, 0.8f, 0.5f, 0.34f, 0.22f, 0.4f),
    peakBins = 4..5,
)

@BuzzPreviews
@Composable
private fun BuzzSpectrumPreview() {
    BuzzPreviewContainer {
        BuzzSpectrumCard(title = "Spectral analysis", peakLabel = "480 Hz", data = SampleSpectrum)
    }
}
