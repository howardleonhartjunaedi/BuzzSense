package com.buzzsense.veritas.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.Verdict

/**
 * The four outcomes the Result screen can show. Each carries icon + words +
 * color, so color is never the only signal.
 */
enum class ResultKind(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val icon: ImageVector,
    val chipTone: IconChipTone,
    val statusTone: StatusTone,
) {
    LikelyAedes(R.string.result_aedes_title, R.string.result_aedes_body, BuzzIcons.Warning, IconChipTone.Hazard, StatusTone.Hazard),
    NotDengue(R.string.result_not_dengue_title, R.string.result_not_dengue_body, BuzzIcons.Check, IconChipTone.Brand, StatusTone.Safe),
    NotSure(R.string.result_not_sure_title, R.string.result_not_sure_body, BuzzIcons.Help, IconChipTone.Watch, StatusTone.Watch),
    NoMosquito(R.string.result_none_title, R.string.result_none_body, BuzzIcons.VolumeOff, IconChipTone.Neutral, StatusTone.Neutral),
    /** Speech gate: voices were heard, so the clip was deleted before the detector ran. */
    VoiceHeard(R.string.result_voice_title, R.string.result_voice_body, BuzzIcons.MicOff, IconChipTone.Watch, StatusTone.Watch),
    ;

    companion object {
        /** Maps the two-stage detector output to a result. Unsure in either stage wins. */
        fun from(detection: DetectionResult): ResultKind = when {
            detection.mosquito == Verdict.NO -> NoMosquito
            detection.mosquito == Verdict.UNSURE || detection.aedesAegypti == Verdict.UNSURE -> NotSure
            detection.aedesAegypti == Verdict.YES -> LikelyAedes
            else -> NotDengue
        }
    }
}

/** Color that tints the headline, bar and chip for this result. */
val ResultKind.accentColor: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        ResultKind.LikelyAedes -> BuzzTheme.colors.resultHazard
        ResultKind.NotDengue -> BuzzTheme.colors.resultSafe
        ResultKind.NotSure -> BuzzTheme.colors.resultWatch
        ResultKind.NoMosquito -> BuzzTheme.colorScheme.outline
        ResultKind.VoiceHeard -> BuzzTheme.colors.resultWatch
    }

/** Full-screen result block: chip, display headline, big two-tone %, bar. */
@Composable
fun BuzzResultHero(
    kind: ResultKind,
    confidence: Float?,
    modifier: Modifier = Modifier,
    peakHz: Float? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.lg)) {
        BuzzIconChip(kind.icon, tone = kind.chipTone, size = 28.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(kind.title), style = BuzzTheme.typography.displayMedium, color = BuzzTheme.colorScheme.primary)
            Text(stringResource(kind.body), style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.outline)
        }
        BuzzResultPanel(kind = kind, confidence = confidence, peakHz = peakHz)
    }
}

/**
 * Stitch result panel: a mist card with the species/peak row, the big two-tone confidence
 * and its bar. Parts with no data are left out; the panel is skipped when nothing is known.
 */
@Composable
fun BuzzResultPanel(
    kind: ResultKind,
    confidence: Float?,
    modifier: Modifier = Modifier,
    peakHz: Float? = null,
) {
    if (confidence == null && peakHz == null) return
    BuzzCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BuzzStatusTag(stringResource(kind.title), tone = kind.statusTone, modifier = Modifier.weight(1f, fill = false))
            Box(Modifier.weight(1f))
            if (peakHz != null) {
                BuzzMetricChip(
                    label = stringResource(R.string.result_peak),
                    value = "${peakHz.toInt()} Hz",
                    tone = MetricTone.Neutral,
                    modifier = Modifier.padding(start = BuzzTheme.spacing.sm),
                )
            }
        }
        if (confidence != null) {
            Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
                BuzzTwoToneText(
                    value = (confidence * 100).toInt().toString(),
                    unit = "%",
                    style = BuzzTheme.data.hero,
                    unitStyle = BuzzTheme.typography.displayMedium,
                    valueColor = BuzzTheme.colorScheme.primary,
                    unitColor = BuzzTheme.colorScheme.outline,
                )
                BuzzEyebrow(stringResource(R.string.result_confidence))
                BuzzConfidenceBar(progress = confidence, color = kind.accentColor, trackColor = BuzzTheme.colorScheme.surfaceContainerHighest)
            }
        }
    }
}

/** Compact result card for history and summaries. */
@Composable
fun BuzzResultCard(
    kind: ResultKind,
    confidence: Float,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val percent = (confidence * 100).toInt()
    BuzzCard(modifier = modifier, onClick = onClick, verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.Top) {
            BuzzIconChip(kind.icon, tone = kind.chipTone)
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                BuzzEyebrow(stringResource(R.string.result_confidence))
                BuzzTwoToneText(value = percent.toString(), unit = "%")
            }
        }
        Text(
            stringResource(kind.title),
            style = BuzzTheme.typography.titleLarge,
            color = if (kind == ResultKind.NotSure) BuzzTheme.colorScheme.onSurface else kind.accentColor,
        )
        Text(description, style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            BuzzConfidenceBar(progress = confidence, color = kind.accentColor, modifier = Modifier.weight(1f))
            Text("$percent%", style = BuzzTheme.data.tabularSmall, color = BuzzTheme.colorScheme.onSurface)
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzResultHeroPreview() {
    BuzzPreviewContainer {
        BuzzResultHero(kind = ResultKind.LikelyAedes, confidence = 0.82f, peakHz = 584f)
    }
}

@BuzzPreviews
@Composable
private fun BuzzResultHeroNoConfidencePreview() {
    BuzzPreviewContainer {
        BuzzResultHero(kind = ResultKind.NotDengue, confidence = null, peakHz = 448f)
    }
}

@BuzzPreviews
@Composable
private fun BuzzResultCardPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuzzResultCard(ResultKind.LikelyAedes, 0.88f, "Wingbeat near 580 Hz matches the Aedes profile.")
            BuzzResultCard(ResultKind.NotDengue, 0.92f, "Sounds like Culex, the common house mosquito.")
            BuzzResultCard(ResultKind.NotSure, 0.46f, "Signal faint or noisy. Record again, closer.")
            BuzzResultCard(ResultKind.NoMosquito, 0.90f, "No wingbeat in this clip.")
        }
    }
}
