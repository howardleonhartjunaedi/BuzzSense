package com.buzzsense.veritas.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.buzzsense.veritas.app.ui.components.ResultKind
import com.buzzsense.veritas.app.ui.components.rememberBuzzNavItems
import com.buzzsense.veritas.app.ui.screens.AnalyzingScreen
import com.buzzsense.veritas.app.ui.screens.DetectHomeScreen
import com.buzzsense.veritas.app.ui.screens.RecordingScreen
import com.buzzsense.veritas.app.ui.screens.ResultScreen
import com.buzzsense.veritas.app.ui.screens.SampleChecklist
import com.buzzsense.veritas.app.ui.screens.WhatToDoNowSheet
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme
import kotlinx.coroutines.delay

/** Steps of the Detect tab. */
@Immutable
sealed interface DetectStep {
    data object Home : DetectStep
    data object Recording : DetectStep
    data object Analyzing : DetectStep
    data class Result(val kind: ResultKind, val confidence: Float?) : DetectStep
}

private const val CLIP_SECONDS = 5

/**
 * App shell: flat top bar, bottom nav, Detect flow.
 *
 * The recorder and BuzzAlertEngine are not connected yet. [onRunDetection]
 * is the seam: it is called when a recording finishes and must return the
 * result to show. The default returns a fixed demo result so the UI can be
 * walked end to end without the mic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuzzSenseApp(
    micAvailable: Boolean,
    onRequestMic: () -> Unit,
    modifier: Modifier = Modifier,
    onRunDetection: suspend () -> DetectStep.Result = {
        delay(1200)
        DetectStep.Result(ResultKind.LikelyAedes, 0.82f)
    },
) {
    val navItems = rememberBuzzNavItems()
    var selectedTab by rememberSaveable { mutableStateOf(navItems.first().key) }
    var step by remember { mutableStateOf<DetectStep>(DetectStep.Home) }
    var showTips by remember { mutableStateOf(false) }
    var showChecklist by remember { mutableStateOf(false) }

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
        when (val current = step) {
            DetectStep.Home -> DetectHomeScreen(
                micAvailable = micAvailable,
                onRecord = { step = DetectStep.Recording },
                onTips = { showTips = true },
                onAllowMic = onRequestMic,
                modifier = contentModifier,
            )
            DetectStep.Recording -> RecordingStep(
                onFinished = { step = DetectStep.Analyzing },
                modifier = contentModifier,
            )
            DetectStep.Analyzing -> {
                LaunchedEffect(Unit) { step = onRunDetection() }
                AnalyzingScreen(modifier = contentModifier)
            }
            is DetectStep.Result -> ResultScreen(
                kind = current.kind,
                confidence = current.confidence,
                onWhatToDo = { showChecklist = true },
                onRecordAgain = { step = DetectStep.Recording },
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
    if (showChecklist) {
        WhatToDoNowSheet(
            entries = SampleChecklist,
            onDismiss = { showChecklist = false },
            onOpenOfficialInfo = {},
        )
    }
}

/** Drives the 5 s countdown. Progress lives in a float state read only by the ring. */
@Composable
private fun RecordingStep(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val totalMs = CLIP_SECONDS * 1000L
        val start = System.currentTimeMillis()
        while (true) {
            val t = System.currentTimeMillis() - start
            progress = (t.toFloat() / totalMs).coerceAtMost(1f)
            elapsed = (t / 1000).toInt().coerceAtMost(CLIP_SECONDS)
            if (t >= totalMs) break
            delay(16)
        }
        onFinished()
    }
    RecordingScreen(
        elapsedSeconds = elapsed,
        totalSeconds = CLIP_SECONDS,
        progress = { progress },
        onStop = onFinished,
        modifier = modifier,
    )
}

@BuzzScreenPreviews
@Composable
private fun BuzzSenseAppPreview() {
    BuzzsenseTheme {
        BuzzSenseApp(micAvailable = true, onRequestMic = {})
    }
}
