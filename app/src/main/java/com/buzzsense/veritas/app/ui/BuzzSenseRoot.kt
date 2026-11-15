package com.buzzsense.veritas.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme

/**
 * Entry point for setContent: theme + mic permission + [DetectViewModel] + [BuzzSenseApp].
 * Use as `setContent { BuzzSenseRoot() }` in MainActivity.
 */
@Composable
fun BuzzSenseRoot(modifier: Modifier = Modifier) {
    BuzzsenseTheme {
        val context = LocalContext.current
        val viewModel: DetectViewModel = viewModel(factory = DetectViewModel.factory(context.appContainer))
        val state by viewModel.state.collectAsStateWithLifecycle()
        val progress = viewModel.recordingProgress.collectAsStateWithLifecycle()

        var micGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            micGranted = it
        }
        BuzzSenseApp(
            state = state,
            recordingProgress = { progress.value },
            micAvailable = micGranted,
            onRequestMic = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
            actions = viewModel,
            onOpenUrl = { url ->
                // Citations are public guidance pages; open them in the user's browser.
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                }
            },
            modifier = modifier,
        )
    }
}
