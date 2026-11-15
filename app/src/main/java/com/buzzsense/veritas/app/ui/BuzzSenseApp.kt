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
import com.buzzsense.veritas.app.ui.components.BuzzBottomNavBar
import com.buzzsense.veritas.app.ui.components.BuzzButton
import com.buzzsense.veritas.app.ui.components.BuzzButtonVariant
import com.buzzsense.veritas.app.ui.components.BuzzHighlightedText
import com.buzzsense.veritas.app.ui.components.BuzzMessageState
import com.buzzsense.veritas.app.ui.components.BuzzModalBottomSheet
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzSheetContent
import com.buzzsense.veritas.app.ui.components.BuzzTopAppBar
import com.buzzsense.veritas.app.ui.components.BuzzTopBarAction
import com.buzzsense.veritas.app.ui.components.IconChipTone
import com.buzzsense.veritas.app.ui.components.ResultKind
import com.buzzsense.veritas.app.ui.components.rememberBuzzNavItems
import com.buzzsense.veritas.app.ui.detect.AlertUi
import com.buzzsense.veritas.app.ui.detect.DetectStep
import com.buzzsense.veritas.app.ui.detect.DetectUiState
import com.buzzsense.veritas.app.ui.detect.DetectViewModel
import com.buzzsense.veritas.app.ui.detect.DetectionUi
import com.buzzsense.veritas.app.ui.screens.AnalyzingScreen
import com.buzzsense.veritas.app.ui.screens.DetectHomeScreen
import com.buzzsense.veritas.app.ui.screens.RecordingScreen
import com.buzzsense.veritas.app.ui.screens.ResultScreen
import com.buzzsense.veritas.app.ui.screens.SampleChecklist
import com.buzzsense.veritas.app.ui.screens.WhatToDoNowSheet
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme

/** Everything the Detect flow can ask the app to do; implemented by [DetectViewModel]. */
interface DetectActions {
    fun onRecord()
    fun onStop()
    fun onRecordAgain()
    fun onBackHome()
}

/**
 * App shell: flat top bar, bottom nav, Detect flow. Stateless for the flow itself: it renders
 * [state] from [DetectViewModel] and reports intents through [actions]. Only UI-local state
 * (selected tab, open sheets) lives here.
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
) {
    val navItems = rememberBuzzNavItems()
    var selectedTab by rememberSaveable { mutableStateOf(navItems.first().key) }
    var showTips by remember { mutableStateOf(false) }
    var openAlert by remember { mutableStateOf<AlertUi?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BuzzTheme.colorScheme.background,
        topBar = {
            BuzzTopAppBar(actions = {
                BuzzTopBarAction(BuzzIcons.History, stringResource(R.string.action_history), onClick = {})
            })
        },
        bottomBar = {
            BuzzBottomNavBar(items = navItems, selectedKey = selectedTab, onSelect = { selectedTab = it.key })
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        if (selectedTab != "detect") {
            Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BuzzMessageState(
                    icon = BuzzIcons.MenuBook,
                    title = stringResource(R.string.tab_soon_title),
                    body = stringResource(R.string.tab_soon_body),
                )
            }
            return@Scaffold
        }
        when (val step = state.step) {
            DetectStep.Home -> DetectHomeScreen(
                micAvailable = micAvailable,
                onRecord = actions::onRecord,
                onTips = { showTips = true },
                onAllowMic = onRequestMic,
                modifier = contentModifier,
            )
            DetectStep.Recording -> RecordingStep(
                progress = recordingProgress,
                onStop = actions::onStop,
                modifier = contentModifier,
            )
            DetectStep.Analyzing -> AnalyzingScreen(modifier = contentModifier)
            is DetectStep.Result -> ResultScreen(
                kind = step.detection.kind,
                confidence = step.detection.confidence,
                showWhatToDo = step.detection.alert != null,
                onWhatToDo = { openAlert = step.detection.alert },
                onRecordAgain = actions::onRecordAgain,
                modifier = contentModifier,
            )
            is DetectStep.Unavailable -> MessageStep(
                title = stringResource(R.string.engine_unavailable_title),
                body = stringResource(R.string.engine_unavailable_body, step.missing.joinToString(", ")),
                button = stringResource(R.string.action_back),
                onButton = actions::onBackHome,
                modifier = contentModifier,
            )
            DetectStep.Failed -> MessageStep(
                title = stringResource(R.string.run_failed_title),
                body = stringResource(R.string.run_failed_body),
                button = stringResource(R.string.action_try_again),
                onButton = actions::onRecordAgain,
                modifier = contentModifier,
            )
        }
    }

    if (showTips) {
        BuzzModalBottomSheet(onDismissRequest = { showTips = false }) {
            BuzzSheetContent(
                title = stringResource(R.string.tips_title),
                actions = {
                    BuzzButton(
                        text = stringResource(R.string.tips_got_it),
                        onClick = { showTips = false },
                        variant = BuzzButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            ) {
                BuzzHighlightedText(stringResource(R.string.tips_body), highlight = stringResource(R.string.tips_highlight))
            }
        }
    }
    openAlert?.let { alert ->
        WhatToDoNowSheet(
            entries = alert.entries,
            subtitle = "${alert.summary} ${stringResource(R.string.todo_review_hint)}",
            onDismiss = { openAlert = null },
            onOpenOfficialInfo = alert.officialUrl?.let { url -> { onOpenUrl(url) } },
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
    override fun onRecordAgain() = Unit
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
            DetectUiState(DetectStep.Result(DetectionUi(ResultKind.LikelyAedes, 0.82f, alert))),
            { 0f }, micAvailable = true, onRequestMic = {}, actions = PreviewActions, onOpenUrl = {},
        )
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
