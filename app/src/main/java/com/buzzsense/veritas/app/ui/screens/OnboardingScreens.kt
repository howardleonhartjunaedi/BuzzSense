package com.buzzsense.veritas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.components.BuzzBackButton
import com.buzzsense.veritas.app.ui.components.BuzzButton
import com.buzzsense.veritas.app.ui.components.BuzzCard
import com.buzzsense.veritas.app.ui.components.BuzzEyebrow
import com.buzzsense.veritas.app.ui.components.BuzzFrequencyCompare
import com.buzzsense.veritas.app.ui.components.BuzzIconChip
import com.buzzsense.veritas.app.ui.components.BuzzListRow
import com.buzzsense.veritas.app.ui.components.BuzzLogo
import com.buzzsense.veritas.app.ui.components.BuzzMetricChip
import com.buzzsense.veritas.app.ui.components.BuzzPageIndicator
import com.buzzsense.veritas.app.ui.components.BuzzPreviewContainer
import com.buzzsense.veritas.app.ui.components.BuzzScreenColumn
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzStatusDot
import com.buzzsense.veritas.app.ui.components.BuzzStatusTag
import com.buzzsense.veritas.app.ui.components.BuzzStepBadge
import com.buzzsense.veritas.app.ui.components.BuzzTextButton
import com.buzzsense.veritas.app.ui.components.BuzzTopAppBar
import com.buzzsense.veritas.app.ui.components.FrequencyBar
import com.buzzsense.veritas.app.ui.components.IconChipTone
import com.buzzsense.veritas.app.ui.components.MetricTone
import com.buzzsense.veritas.app.ui.components.StatusTone
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme
import kotlinx.coroutines.launch

/** Stitch "01 Splash": wordmark on the dark canvas, in both light and dark mode. */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    BuzzsenseTheme(darkTheme = true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BuzzTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
                BuzzLogo(size = 80.dp)
                Text(
                    stringResource(R.string.brand_name),
                    style = BuzzTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = BuzzTheme.colorScheme.onSurface,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = BuzzTheme.spacing.lg)
                    .size(4.dp)
                    .background(BuzzTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

private const val WALKTHROUGH_PAGES = 3

/**
 * Stitch "02–04 Walkthrough": three swipeable pages with a mustard step badge, dots and
 * Next / Get started. [onFinish] runs on Get started and on Skip.
 */
@Composable
fun WalkthroughScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { WALKTHROUGH_PAGES }
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BuzzTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BuzzTopAppBar(
            actions = { BuzzTextButton(text = stringResource(R.string.action_skip), onClick = onFinish) },
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            BuzzScreenColumn {
                BuzzStepBadge(stringResource(R.string.onboarding_step, page + 1, WALKTHROUGH_PAGES))
                Spacer(Modifier.height(48.dp))
                when (page) {
                    0 -> WalkthroughFingerprint()
                    1 -> WalkthroughRecord()
                    else -> WalkthroughHelper()
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.lg),
        ) {
            BuzzPageIndicator(pageCount = WALKTHROUGH_PAGES, currentPage = pager.currentPage)
            val last = pager.currentPage == WALKTHROUGH_PAGES - 1
            BuzzButton(
                text = stringResource(if (last) R.string.action_get_started else R.string.action_next),
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WalkthroughHeadline(title: Int, body: Int) {
    Text(stringResource(title), style = BuzzTheme.typography.headlineMedium, color = BuzzTheme.colorScheme.primary)
    Text(
        stringResource(body),
        style = BuzzTheme.typography.bodyLarge,
        color = BuzzTheme.colorScheme.outline,
        modifier = Modifier.padding(top = BuzzTheme.spacing.sm),
    )
}

/** Page 1: each species has its own pitch. Illustrative bars, labelled as such. */
@Composable
private fun WalkthroughFingerprint() {
    WalkthroughHeadline(R.string.walk1_title, R.string.walk1_body)
    BuzzCard(modifier = Modifier.padding(top = BuzzTheme.spacing.xl)) {
        BuzzEyebrow(stringResource(R.string.walk1_card_label))
        BuzzFrequencyCompare(
            listOf(
                FrequencyBar(stringResource(R.string.walk1_bar_culex), 0.66f),
                FrequencyBar(stringResource(R.string.walk1_bar_aedes), 1f, highlighted = true),
                FrequencyBar(stringResource(R.string.walk1_bar_anopheles), 0.78f),
            ),
            barHeight = 128.dp,
        )
        Text(stringResource(R.string.walk1_card_note), style = BuzzTheme.typography.labelMedium, color = BuzzTheme.colorScheme.outline)
    }
}

/** Page 2: how to record. */
@Composable
private fun WalkthroughRecord() {
    WalkthroughHeadline(R.string.walk2_title, R.string.walk2_body)
    BuzzCard(modifier = Modifier.padding(top = BuzzTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.md)) {
        listOf(
            BuzzIcons.Smartphone to R.string.walk2_row_distance,
            BuzzIcons.VolumeOff to R.string.walk2_row_quiet,
            BuzzIcons.Timer to R.string.walk2_row_still,
        ).forEach { (icon, text) ->
            BuzzListRow(headline = stringResource(text), leading = { BuzzIconChip(icon, size = 28.dp) })
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BuzzTheme.spacing.md, start = BuzzTheme.spacing.xs, end = BuzzTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuzzStatusDot(stringResource(R.string.walk2_status), modifier = Modifier.weight(1f))
        BuzzMetricChip(stringResource(R.string.detect_sensor_label), stringResource(R.string.detect_sensor_value), tone = MetricTone.Safe)
    }
}

/** Page 3: a helper, not a doctor. */
@Composable
private fun WalkthroughHelper() {
    WalkthroughHeadline(R.string.walk3_title, R.string.walk3_body)
    BuzzCard(modifier = Modifier.padding(top = BuzzTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.lg)) {
        BuzzStatusTag(stringResource(R.string.walk3_card_tag))
        Text(stringResource(R.string.walk3_card_text), style = BuzzTheme.typography.titleLarge, color = BuzzTheme.colorScheme.primary)
        Row {
            Column(Modifier.weight(1f)) {
                BuzzEyebrow(stringResource(R.string.walk3_role_label))
                Text(stringResource(R.string.walk3_role_value), style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                BuzzEyebrow(stringResource(R.string.walk3_output_label))
                Text(stringResource(R.string.walk3_output_value), style = BuzzTheme.data.tabularSmall, color = BuzzTheme.colorScheme.secondary)
            }
        }
    }
}

/**
 * Stitch "05 Mic Permission". [onAllow] asks the system for RECORD_AUDIO; [onNotNow] goes on
 * without it (Detect Home asks again on the first tap).
 */
@Composable
fun MicPermissionScreen(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuzzScreenColumn(
        modifier = modifier
            .background(BuzzTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BuzzBackButton(onClick = onBack, label = stringResource(R.string.action_back))
        Spacer(Modifier.height(48.dp))
        BuzzIconChip(BuzzIcons.Mic, tone = IconChipTone.Brand, size = 64.dp)
        Spacer(Modifier.height(BuzzTheme.spacing.lg))
        Text(stringResource(R.string.mic_perm_title), style = BuzzTheme.typography.headlineMedium, color = BuzzTheme.colorScheme.primary)
        Text(
            stringResource(R.string.mic_perm_body),
            style = BuzzTheme.typography.bodyLarge,
            color = BuzzTheme.colorScheme.outline,
            modifier = Modifier.padding(top = BuzzTheme.spacing.sm, bottom = BuzzTheme.spacing.xl),
        )
        BuzzCard(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
                Icon(BuzzIcons.VerifiedUser, contentDescription = null, tint = BuzzTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                BuzzEyebrow(stringResource(R.string.mic_perm_card_title), color = BuzzTheme.colorScheme.secondary)
            }
            Text(stringResource(R.string.mic_perm_card_body), style = BuzzTheme.typography.bodyMedium, color = BuzzTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm), modifier = Modifier.padding(top = BuzzTheme.spacing.xs)) {
                BuzzStatusTag(stringResource(R.string.mic_perm_chip_band), tone = StatusTone.Neutral)
                BuzzStatusTag(stringResource(R.string.mic_perm_chip_device), tone = StatusTone.Neutral)
            }
        }
        Spacer(Modifier.weight(1f).height(BuzzTheme.spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.sm)) {
            BuzzButton(text = stringResource(R.string.mic_off_action), onClick = onAllow, modifier = Modifier.fillMaxWidth())
            BuzzTextButton(text = stringResource(R.string.action_not_now), onClick = onNotNow, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Steps of first-run onboarding. */
private enum class OnboardingStep { Walkthrough, MicPermission }

/**
 * First-run flow: walkthrough, then the mic permission screen if the mic is not granted yet.
 * [onFinished] marks onboarding done; it runs once the mic is granted or the user picks Not now.
 */
@Composable
fun OnboardingFlow(
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Walkthrough) }
    when (step) {
        OnboardingStep.Walkthrough -> WalkthroughScreen(
            onFinish = { if (micGranted) onFinished() else step = OnboardingStep.MicPermission },
            modifier = modifier,
        )
        OnboardingStep.MicPermission -> {
            if (micGranted) {
                LaunchedEffect(Unit) { onFinished() }
            }
            MicPermissionScreen(
                onAllow = onRequestMic,
                onNotNow = onFinished,
                onBack = { step = OnboardingStep.Walkthrough },
                modifier = modifier,
            )
        }
    }
}

@BuzzScreenPreviews
@Composable
private fun SplashScreenPreview() {
    SplashScreen()
}

@BuzzScreenPreviews
@Composable
private fun WalkthroughScreenPreview() {
    BuzzsenseTheme { WalkthroughScreen(onFinish = {}) }
}

@BuzzScreenPreviews
@Composable
private fun WalkthroughPagesPreview() {
    BuzzPreviewContainer {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(360.dp)) { WalkthroughRecord() }
        }
    }
}

@BuzzScreenPreviews
@Composable
private fun WalkthroughHelperPreview() {
    BuzzPreviewContainer { Column { WalkthroughHelper() } }
}

@BuzzScreenPreviews
@Composable
private fun MicPermissionScreenPreview() {
    BuzzsenseTheme { MicPermissionScreen(onAllow = {}, onNotNow = {}, onBack = {}) }
}
