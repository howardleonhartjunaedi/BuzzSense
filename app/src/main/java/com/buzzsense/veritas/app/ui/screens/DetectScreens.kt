package com.buzzsense.veritas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.components.BuzzActionCard
import com.buzzsense.veritas.app.ui.components.BuzzButton
import com.buzzsense.veritas.app.ui.components.BuzzDisclaimer
import com.buzzsense.veritas.app.ui.components.BuzzEyebrow
import com.buzzsense.veritas.app.ui.components.BuzzMessageState
import com.buzzsense.veritas.app.ui.components.BuzzMicButton
import com.buzzsense.veritas.app.ui.components.BuzzPreviewContainer
import com.buzzsense.veritas.app.ui.components.BuzzProgressIndicator
import com.buzzsense.veritas.app.ui.components.BuzzScreenHeader
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzSoundLevelBars
import com.buzzsense.veritas.app.ui.components.BuzzTimerReadout
import com.buzzsense.veritas.app.ui.components.IconChipTone
import com.buzzsense.veritas.app.ui.components.MicButtonState
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/** Stitch "02 Detect Home" and "11 Detect Home Mic Off". */
@Composable
fun DetectHomeScreen(
    micAvailable: Boolean,
    onRecord: () -> Unit,
    onTips: () -> Unit,
    onAllowMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = BuzzTheme.spacing.margin)
            .padding(top = BuzzTheme.spacing.xl, bottom = BuzzTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BuzzScreenHeader(
            eyebrow = stringResource(R.string.detect_eyebrow),
            title = stringResource(R.string.detect_title),
            subtitle = stringResource(R.string.detect_subtitle),
            centered = true,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (micAvailable) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BuzzMicButton(
                        state = MicButtonState.Idle,
                        onClick = onRecord,
                        contentDescription = stringResource(R.string.mic_start),
                    )
                    Spacer(Modifier.height(BuzzTheme.spacing.lg))
                    Text(stringResource(R.string.detect_tap_to_record), style = BuzzTheme.typography.titleLarge, color = BuzzTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.detect_band_hint),
                        style = BuzzTheme.typography.labelMedium,
                        color = BuzzTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = BuzzTheme.spacing.xs),
                    )
                }
            } else {
                BuzzMessageState(
                    icon = BuzzIcons.MicOff,
                    title = stringResource(R.string.mic_off_title),
                    body = stringResource(R.string.mic_off_body),
                    tone = IconChipTone.Neutral,
                    actions = {
                        BuzzButton(text = stringResource(R.string.mic_off_action), onClick = onAllowMic, modifier = Modifier.fillMaxWidth())
                    },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
            BuzzActionCard(title = stringResource(R.string.detect_tips), icon = BuzzIcons.Lightbulb, onClick = onTips)
            BuzzDisclaimer()
        }
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
            Spacer(Modifier.height(BuzzTheme.spacing.xl))
            BuzzTimerReadout(elapsedSeconds = elapsedSeconds, totalSeconds = totalSeconds)
            Spacer(Modifier.height(BuzzTheme.spacing.xl))
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

/** Stitch "09 Analyzing". */
@Composable
fun AnalyzingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.lg, Alignment.CenterVertically),
        ) {
            BuzzProgressIndicator()
            BuzzScreenHeader(
                title = stringResource(R.string.analyzing_title),
                subtitle = stringResource(R.string.analyzing_body),
                centered = true,
            )
        }
        BuzzDisclaimer()
    }
}

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

@BuzzScreenPreviews
@Composable
private fun RecordingScreenPreview() {
    BuzzPreviewContainer { RecordingScreen(elapsedSeconds = 3, totalSeconds = 5, progress = { 0.6f }, onStop = {}) }
}

@BuzzScreenPreviews
@Composable
private fun AnalyzingScreenPreview() {
    BuzzPreviewContainer { AnalyzingScreen() }
}
