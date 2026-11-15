package com.buzzsense.veritas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.components.BuzzActionCard
import com.buzzsense.veritas.app.ui.components.BuzzButton
import com.buzzsense.veritas.app.ui.components.BuzzCard
import com.buzzsense.veritas.app.ui.components.BuzzDisclaimer
import com.buzzsense.veritas.app.ui.components.BuzzEyebrow
import com.buzzsense.veritas.app.ui.components.BuzzIconChip
import com.buzzsense.veritas.app.ui.components.BuzzListRow
import com.buzzsense.veritas.app.ui.components.BuzzMetricChip
import com.buzzsense.veritas.app.ui.components.BuzzMetricTile
import com.buzzsense.veritas.app.ui.components.BuzzMicButton
import com.buzzsense.veritas.app.ui.components.BuzzPlaybackRow
import com.buzzsense.veritas.app.ui.components.BuzzPreviewContainer
import com.buzzsense.veritas.app.ui.components.BuzzPreviews
import com.buzzsense.veritas.app.ui.components.BuzzProgressStep
import com.buzzsense.veritas.app.ui.components.BuzzScreenColumn
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzSheetContent
import com.buzzsense.veritas.app.ui.components.BuzzSoundLevelBars
import com.buzzsense.veritas.app.ui.components.BuzzTextButton
import com.buzzsense.veritas.app.ui.components.BuzzTimerReadout
import com.buzzsense.veritas.app.ui.components.BuzzWaveform
import com.buzzsense.veritas.app.ui.components.IconChipTone
import com.buzzsense.veritas.app.ui.components.MetricTone
import com.buzzsense.veritas.app.ui.components.MicButtonState
import com.buzzsense.veritas.app.ui.components.StepState
import com.buzzsense.veritas.app.ui.components.WaveformShape
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import kotlinx.coroutines.delay

/**
 * Stitch "06 Detect Home". Without the mic permission the button shows the mic-off state
 * and a tap asks for the permission instead of recording.
 */
@Composable
fun DetectHomeScreen(
    micAvailable: Boolean,
    onRecord: () -> Unit,
    onTips: () -> Unit,
    onAllowMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuzzScreenColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f).heightIn(min = BuzzTheme.spacing.lg))
        BuzzMetricChip(
            label = stringResource(R.string.detect_sensor_label),
            value = stringResource(R.string.detect_sensor_value),
            tone = MetricTone.Safe,
        )
        Spacer(Modifier.height(BuzzTheme.spacing.sm))
        Text(
            stringResource(R.string.detect_title),
            style = BuzzTheme.typography.headlineMedium,
            color = BuzzTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        BuzzMicButton(
            state = if (micAvailable) MicButtonState.Idle else MicButtonState.Unavailable,
            onClick = if (micAvailable) onRecord else onAllowMic,
            contentDescription = stringResource(if (micAvailable) R.string.mic_start else R.string.mic_off_action),
        )
        Text(
            stringResource(if (micAvailable) R.string.detect_tap_to_record else R.string.mic_off_action),
            style = BuzzTheme.typography.bodyMedium,
            color = BuzzTheme.colorScheme.outline,
            modifier = Modifier.padding(top = BuzzTheme.spacing.md),
        )
        Spacer(Modifier.height(48.dp))
        BuzzActionCard(title = stringResource(R.string.detect_tips), icon = BuzzIcons.Lightbulb, onClick = onTips)
        Spacer(Modifier.weight(1f).heightIn(min = BuzzTheme.spacing.lg))
        BuzzDisclaimer()
    }
}

/** Body of the "Recording protocol" tips sheet on Detect Home. */
@Composable
fun RecordingTipsContent(onDone: () -> Unit, modifier: Modifier = Modifier) {
    BuzzSheetContent(
        title = stringResource(R.string.tips_protocol_title),
        modifier = modifier,
        actions = {
            BuzzButton(text = stringResource(R.string.tips_understood), onClick = onDone, modifier = Modifier.fillMaxWidth())
        },
    ) {
        BuzzListRow(
            headline = stringResource(R.string.tips_quiet_title),
            supporting = stringResource(R.string.tips_quiet_body),
            leading = { BuzzIconChip(BuzzIcons.VolumeOff, tone = IconChipTone.Canvas, size = 28.dp) },
        )
        BuzzListRow(
            headline = stringResource(R.string.tips_distance_title),
            supporting = stringResource(R.string.tips_distance_body),
            leading = { BuzzIconChip(BuzzIcons.Straighten, tone = IconChipTone.Canvas, size = 28.dp) },
        )
    }
}

/**
 * Stitch "07 Recording". [progress] is read in the draw phase by the ring;
 * [elapsedSeconds] only changes once per second.
 */
@Composable
fun RecordingScreen(
    elapsedSeconds: Int,
    totalSeconds: Int,
    progress: () -> Float,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BuzzEyebrow(stringResource(R.string.detect_eyebrow))
            Spacer(Modifier.height(48.dp))
            BuzzTimerReadout(elapsedSeconds = elapsedSeconds, totalSeconds = totalSeconds)
            Spacer(Modifier.height(48.dp))
            BuzzMicButton(
                state = MicButtonState.Recording,
                onClick = onStop,
                contentDescription = stringResource(R.string.mic_stop),
                progress = progress,
            )
            Spacer(Modifier.height(BuzzTheme.spacing.lg))
            BuzzSoundLevelBars()
            Spacer(Modifier.height(BuzzTheme.spacing.md))
            Text(
                stringResource(R.string.recording_listening),
                style = BuzzTheme.typography.titleMedium,
                color = BuzzTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        BuzzDisclaimer()
    }
}

/**
 * Stitch "08 Recording ready": the clip waits in memory; the user can play it back, analyze
 * it or record again. Only facts about the clip are shown here (length, sample rate); the
 * wingbeat peak is not known until the detector has run.
 */
@Composable
fun ReviewScreen(
    durationSeconds: Float,
    sampleRateHz: Int,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onAnalyze: () -> Unit,
    onRecordAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = formatDuration(durationSeconds)
    BuzzScreenColumn(
        modifier = modifier,
    ) {
        Text(stringResource(R.string.review_step), style = BuzzTheme.typography.labelMedium, color = BuzzTheme.colorScheme.outline)
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.review_title), style = BuzzTheme.typography.headlineLarge, color = BuzzTheme.colorScheme.primary)
        BuzzMetricChip(
            label = stringResource(R.string.review_chip_label),
            value = stringResource(R.string.review_chip_value),
            tone = MetricTone.Safe,
            modifier = Modifier.padding(top = BuzzTheme.spacing.sm),
        )
        Spacer(Modifier.height(BuzzTheme.spacing.xl))
        BuzzPlaybackRow(
            playing = playing,
            onTogglePlay = onTogglePlay,
            duration = duration,
            playLabel = stringResource(R.string.review_play),
            pauseLabel = stringResource(R.string.review_pause),
        )
        Row(
            modifier = Modifier.padding(top = BuzzTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm),
        ) {
            BuzzMetricTile(stringResource(R.string.review_duration_label), duration, modifier = Modifier.weight(1f))
            BuzzMetricTile(
                stringResource(R.string.review_rate_label),
                (sampleRateHz / 1000).toString(),
                unit = " kHz",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.weight(1f).heightIn(min = BuzzTheme.spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            BuzzButton(text = stringResource(R.string.review_analyze), onClick = onAnalyze, modifier = Modifier.fillMaxWidth())
            BuzzTextButton(text = stringResource(R.string.result_record_again), onClick = onRecordAgain, modifier = Modifier.fillMaxWidth())
            BuzzDisclaimer()
        }
    }
}

private fun formatDuration(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Static detector-band strip for the Analyzing screen. Decorative. */
private val AnalyzingBand = WaveformShape(
    levels = listOf(0.6f, 1f, 0.4f, 0.9f, 0.5f, 1f, 0.5f, 0.25f, 0.25f, 0.25f, 0.25f, 0.25f, 0.25f, 0.25f, 0.25f, 0.25f),
    highlight = 0..6,
)

/**
 * Stitch "09 Analyzing". The engine reports no progress, so the rows follow its fixed order:
 * the sound is captured (done), the spectrogram is quick (done after a moment) and the AI
 * model takes the rest of the time.
 */
@Composable
fun AnalyzingScreen(modifier: Modifier = Modifier) {
    var pictureDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PICTURE_STEP_MS)
        pictureDone = true
    }
    BuzzScreenColumn(
        modifier = modifier,
    ) {
        BuzzEyebrow(stringResource(R.string.detect_eyebrow))
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.analyzing_headline), style = BuzzTheme.typography.headlineMedium, color = BuzzTheme.colorScheme.primary)
        BuzzCard(
            modifier = Modifier.padding(top = BuzzTheme.spacing.xl),
            contentPadding = PaddingValues(horizontal = BuzzTheme.spacing.sm, vertical = BuzzTheme.spacing.xs),
            containerColor = BuzzTheme.colorScheme.surfaceContainerLow,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BuzzWaveform(AnalyzingBand, Modifier.weight(1f), height = 16.dp)
                BuzzEyebrow(
                    "${stringResource(R.string.analyzing_band)} ${stringResource(R.string.detect_sensor_value)}",
                    modifier = Modifier.padding(start = BuzzTheme.spacing.sm),
                )
            }
        }
        BuzzCard(
            modifier = Modifier.padding(top = BuzzTheme.spacing.lg),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val done = stringResource(R.string.step_done)
            val active = stringResource(R.string.step_active)
            val pending = stringResource(R.string.step_pending)
            BuzzProgressStep(stringResource(R.string.analyzing_step_sound), BuzzIcons.Mic, StepState.Done, stateDescription = done)
            BuzzProgressStep(
                stringResource(R.string.analyzing_step_picture),
                BuzzIcons.GraphicEq,
                if (pictureDone) StepState.Done else StepState.Active,
                stateDescription = if (pictureDone) done else active,
            )
            BuzzProgressStep(
                stringResource(R.string.analyzing_step_model),
                BuzzIcons.AutoAwesome,
                if (pictureDone) StepState.Active else StepState.Pending,
                stateDescription = if (pictureDone) active else pending,
            )
        }
        Spacer(Modifier.weight(1f).heightIn(min = BuzzTheme.spacing.xl))
        BuzzDisclaimer()
    }
}

private const val PICTURE_STEP_MS = 800L

@BuzzScreenPreviews
@Composable
private fun DetectHomeScreenPreview() {
    BuzzPreviewContainer { DetectHomeScreen(micAvailable = true, onRecord = {}, onTips = {}, onAllowMic = {}) }
}

@BuzzScreenPreviews
@Composable
private fun DetectHomeMicOffPreview() {
    BuzzPreviewContainer { DetectHomeScreen(micAvailable = false, onRecord = {}, onTips = {}, onAllowMic = {}) }
}

@BuzzPreviews
@Composable
private fun RecordingTipsPreview() {
    BuzzPreviewContainer { RecordingTipsContent(onDone = {}) }
}

@BuzzScreenPreviews
@Composable
private fun RecordingScreenPreview() {
    BuzzPreviewContainer { RecordingScreen(elapsedSeconds = 3, totalSeconds = 5, progress = { 0.6f }, onStop = {}) }
}

@BuzzScreenPreviews
@Composable
private fun ReviewScreenPreview() {
    BuzzPreviewContainer {
        ReviewScreen(5f, 8_000, playing = false, onTogglePlay = {}, onAnalyze = {}, onRecordAgain = {})
    }
}

@BuzzScreenPreviews
@Composable
private fun AnalyzingScreenPreview() {
    BuzzPreviewContainer { AnalyzingScreen() }
}
