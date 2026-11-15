package com.buzzsense.veritas.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.buzzsense.veritas.app.ui.screens.OnboardingFlow
import com.buzzsense.veritas.app.ui.screens.SplashScreen
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buzzsense.veritas.app.appContainer
import com.buzzsense.veritas.app.ui.detect.DetectViewModel
import com.buzzsense.veritas.app.ui.settings.SettingsViewModel
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme

private const val SPLASH_MS = 1_200L

/**
 * Entry point for setContent: splash, first-run onboarding, then theme + mic permission + [DetectViewModel] + [BuzzSenseApp].
 * Use as `setContent { BuzzSenseRoot() }` in MainActivity.
 */
@Composable
fun BuzzSenseRoot(modifier: Modifier = Modifier) {
    BuzzsenseTheme {
        val context = LocalContext.current
        val viewModel: DetectViewModel = viewModel(factory = DetectViewModel.factory(context.appContainer))
        val state by viewModel.state.collectAsStateWithLifecycle()
        val progress = viewModel.recordingProgress.collectAsStateWithLifecycle()
        val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context.appContainer))
        val settings by settingsViewModel.state.collectAsStateWithLifecycle()

        var micGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            micGranted = it
        }
        val requestMic = { launcher.launch(Manifest.permission.RECORD_AUDIO) }

        // Splash once per process start, then the walkthrough until it has been finished once.
        var splashDone by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(SPLASH_MS)
            splashDone = true
        }
        val onboarded by context.appContainer.settings.onboarded.collectAsStateWithLifecycle()
        if (!splashDone) {
            SplashScreen(modifier)
            return@BuzzsenseTheme
        }
        if (!onboarded) {
            OnboardingFlow(
                micGranted = micGranted,
                onRequestMic = requestMic,
                onFinished = context.appContainer.settings::setOnboarded,
                modifier = modifier,
            )
            return@BuzzsenseTheme
        }
        val versionName = remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        BuzzSenseApp(
            versionName = versionName,
            state = state,
            recordingProgress = { progress.value },
            micAvailable = micGranted,
            onRequestMic = requestMic,
            actions = viewModel,
            onOpenUrl = { url ->
                // Citations are public guidance pages; open them in the user's browser.
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                }
            },
            modifier = modifier,
            settings = settings,
            settingsActions = settingsViewModel,
        )
    }
}
