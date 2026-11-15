package com.buzzsense.veritas.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.buzzsense.veritas.app.R
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import com.buzzsense.veritas.app.ui.components.BuzzBackButton
import com.buzzsense.veritas.app.ui.components.BuzzBottomNavBar
import com.buzzsense.veritas.app.ui.components.BuzzTabs
import com.buzzsense.veritas.app.ui.components.BuzzButton
import com.buzzsense.veritas.app.ui.components.BuzzMessageState
import com.buzzsense.veritas.app.ui.components.BuzzModalBottomSheet
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzTopAppBar
import com.buzzsense.veritas.app.ui.components.IconChipTone
import com.buzzsense.veritas.app.ui.components.ResultKind
import com.buzzsense.veritas.app.ui.components.rememberBuzzNavItems
import com.buzzsense.veritas.app.ui.detect.AlertUi
import com.buzzsense.veritas.app.ui.detect.DetectStep
import com.buzzsense.veritas.app.ui.detect.DetectUiState
import com.buzzsense.veritas.app.ui.detect.DetectViewModel
import com.buzzsense.veritas.app.ui.detect.DetectionUi
import com.buzzsense.veritas.app.ui.screens.AnalyzingScreen
import com.buzzsense.veritas.app.ui.screens.CreditsScreen
import com.buzzsense.veritas.app.ui.screens.LearnScreen
import com.buzzsense.veritas.app.ui.screens.RecordingTipsContent
import com.buzzsense.veritas.app.ui.screens.ReviewScreen
import com.buzzsense.veritas.app.ui.screens.DetectHomeScreen
import com.buzzsense.veritas.app.ui.screens.RecordingScreen
import com.buzzsense.veritas.app.ui.screens.ResultScreen
import com.buzzsense.veritas.app.ui.screens.SampleChecklist
import com.buzzsense.veritas.app.ui.screens.SettingsScreen
import com.buzzsense.veritas.app.ui.settings.SettingsActions
import com.buzzsense.veritas.app.ui.settings.SettingsUiState
import com.buzzsense.veritas.shared.model.ProviderTierId
import com.buzzsense.veritas.app.ui.screens.WhatToDoNowSheet
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme

/** Everything the Detect flow can ask the app to do; implemented by [DetectViewModel]. */
interface DetectActions {
    fun onRecord()
    fun onStop()
    fun onBackHome()
    /** Review step: send the clip to the engine. */
    fun onAnalyze() = Unit
    /** Review step: drop the clip and record a new one. */
    fun onRecordAgain() = Unit
    /** Review step: play or pause the in-memory clip. */
    fun onTogglePlay() = Unit
}

/**
 * App shell (Stitch "3 Tabs"): flat top bar, Detect · Learn · Credits, and the Detect flow.
 * Stateless for the flow itself: it renders [state] from [DetectViewModel] and reports intents
 * through [actions]. Only UI-local state (selected tab, open sheets, Settings page) lives here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuzzSenseApp(
    state: DetectUiState,
    recordingProgress: () -> Float,
    micAvailable: Boolean,
    onRequestMic: () -> Unit,
    actions: DetectActions,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    settings: SettingsUiState = SettingsUiState(),
    settingsActions: SettingsActions? = null,
    versionName: String = "",
) {
    val navItems = rememberBuzzNavItems()
    var selectedTab by rememberSaveable { mutableStateOf(BuzzTabs.DETECT) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTips by remember { mutableStateOf(false) }
    var openAlert by remember { mutableStateOf<AlertUi?>(null) }

    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BuzzTheme.colorScheme.background,
        topBar = {
            if (selectedTab == BuzzTabs.CREDITS && showSettings) {
                BuzzTopAppBar(
                    navigation = { BuzzBackButton(onClick = { showSettings = false }) },
                    title = { Text(stringResource(R.string.settings_title), style = BuzzTheme.typography.titleLarge) },
                )
            } else {
                BuzzTopAppBar()
            }
        },
        bottomBar = {
            BuzzBottomNavBar(
                items = navItems,
                selectedKey = selectedTab,
                onSelect = {
                    selectedTab = it.key
                    showSettings = false
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (selectedTab) {
            BuzzTabs.LEARN -> LearnScreen(onOpenUrl = onOpenUrl, modifier = contentModifier)
            BuzzTabs.CREDITS ->
                if (showSettings && settingsActions != null) {
                    SettingsScreen(settings, settingsActions, modifier = contentModifier)
                } else {
                    CreditsScreen(
                        versionName = versionName,
                        onOpenSettings = { showSettings = true },
                        modifier = contentModifier,
                    )
                }
            else -> DetectTab(
                step = state.step,
                recordingProgress = recordingProgress,
                micAvailable = micAvailable,
                onRequestMic = onRequestMic,
                onTips = { showTips = true },
                onWhatToDo = { openAlert = it },
                actions = actions,
                modifier = contentModifier,
            )
        }
    }

    if (showTips) {
        BuzzModalBottomSheet(onDismissRequest = { showTips = false }) {
            RecordingTipsContent(onDone = { showTips = false })
        }
    }
    openAlert?.let { alert ->
        WhatToDoNowSheet(
            entries = alert.entries,
            subtitle = "${alert.summary} " + stringResource(
                if (alert.cited || alert.provider == ProviderTierId.FIXED_FALLBACK) R.string.todo_review_hint else R.string.todo_uncited_hint,
            ),
            onDismiss = { openAlert = null },
            onOpenOfficialInfo = alert.officialUrl?.let { url -> { onOpenUrl(url) } },
        )
    }
}

/** The Detect tab: one screen per [DetectStep]. */
@Composable
private fun DetectTab(
    step: DetectStep,
    recordingProgress: () -> Float,
    micAvailable: Boolean,
    onRequestMic: () -> Unit,
    onTips: () -> Unit,
    onWhatToDo: (AlertUi) -> Unit,
    actions: DetectActions,
    modifier: Modifier = Modifier,
) {
    // Back from any Detect step returns to the Detect home screen (and wipes a pending clip).
    BackHandler(enabled = step != DetectStep.Home, onBack = actions::onBackHome)
    when (step) {
        DetectStep.Home -> DetectHomeScreen(
            micAvailable = micAvailable,
            onRecord = actions::onRecord,
            onTips = onTips,
            onAllowMic = onRequestMic,
            modifier = modifier,
        )
        DetectStep.Recording -> RecordingStep(
            progress = recordingProgress,
            onStop = actions::onStop,
            modifier = modifier,
        )
        is DetectStep.Review -> ReviewScreen(
            durationSeconds = step.durationSeconds,
            sampleRateHz = step.sampleRateHz,
            playing = step.playing,
            onTogglePlay = actions::onTogglePlay,
            onAnalyze = actions::onAnalyze,
            onRecordAgain = actions::onRecordAgain,
            modifier = modifier,
        )
        DetectStep.Analyzing -> AnalyzingScreen(modifier = modifier)
        is DetectStep.Result -> ResultScreen(
            kind = step.detection.kind,
            confidence = step.detection.confidence,
            showWhatToDo = step.detection.alert != null,
            onWhatToDo = { step.detection.alert?.let(onWhatToDo) },
            // Back to the Detect home screen; the user starts the next recording from there.
            onRecordAgain = actions::onBackHome,
            peakHz = step.detection.peakHz,
            modifier = modifier,
        )
        is DetectStep.Unavailable -> MessageStep(
            title = stringResource(R.string.engine_unavailable_title),
            body = stringResource(R.string.engine_unavailable_body, step.missing.joinToString(", ")),
            button = stringResource(R.string.action_back),
            onButton = actions::onBackHome,
            modifier = modifier,
        )
        DetectStep.Failed -> MessageStep(
            title = stringResource(R.string.run_failed_title),
            body = stringResource(R.string.run_failed_body),
            button = stringResource(R.string.action_try_again),
            onButton = actions::onBackHome,
            modifier = modifier,
        )
    }
}

/** Countdown driven by the recorder's real progress; only the ring and the seconds redraw. */
@Composable
private fun RecordingStep(progress: () -> Float, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val elapsed by remember(progress) {
        derivedStateOf { (progress() * DetectViewModel.CLIP_SECONDS).toInt().coerceAtMost(DetectViewModel.CLIP_SECONDS) }
    }
    RecordingScreen(
        elapsedSeconds = elapsed,
        totalSeconds = DetectViewModel.CLIP_SECONDS,
        progress = progress,
        onStop = onStop,
        modifier = modifier,
    )
}

@Composable
private fun MessageStep(title: String, body: String, button: String, onButton: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(BuzzTheme.spacing.margin), contentAlignment = Alignment.Center) {
        BuzzMessageState(
            icon = BuzzIcons.Info,
            title = title,
            body = body,
            tone = IconChipTone.Watch,
            actions = { BuzzButton(text = button, onClick = onButton, modifier = Modifier.fillMaxWidth()) },
        )
    }
}

private object PreviewActions : DetectActions {
    override fun onRecord() = Unit
    override fun onStop() = Unit
    override fun onBackHome() = Unit
}

@BuzzScreenPreviews
@Composable
private fun BuzzSenseAppPreview() {
    BuzzsenseTheme {
        BuzzSenseApp(DetectUiState(), { 0f }, micAvailable = true, onRequestMic = {}, actions = PreviewActions, onOpenUrl = {})
    }
}

@BuzzScreenPreviews
@Composable
private fun BuzzSenseAppResultPreview() {
    val alert = AlertUi("A mosquito that may be Aedes aegypti was heard indoors.", SampleChecklist, "https://www.cdc.gov/", null)
    BuzzsenseTheme {
        BuzzSenseApp(
            DetectUiState(DetectStep.Result(DetectionUi(ResultKind.LikelyAedes, 0.82f, alert, peakHz = 584f))),
            { 0f }, micAvailable = true, onRequestMic = {}, actions = PreviewActions, onOpenUrl = {},
        )
    }
}

@BuzzScreenPreviews
@Composable
private fun BuzzSenseAppReviewPreview() {
    BuzzsenseTheme {
        BuzzSenseApp(DetectUiState(DetectStep.Review(5f, 8_000)), { 1f }, micAvailable = true, onRequestMic = {}, actions = PreviewActions, onOpenUrl = {})
    }
}

@BuzzScreenPreviews
@Composable
private fun BuzzSenseAppUnavailablePreview() {
    BuzzsenseTheme {
        BuzzSenseApp(
            DetectUiState(DetectStep.Unavailable(listOf("assets/silero_vad.onnx", "jniLibs/<abi>/libvec0.so"))),
            { 0f }, micAvailable = true, onRequestMic = {}, actions = PreviewActions, onOpenUrl = {},
        )
    }
}
