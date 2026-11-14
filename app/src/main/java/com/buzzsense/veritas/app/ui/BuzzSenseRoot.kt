package com.buzzsense.veritas.app.ui

import android.Manifest
import android.content.pm.PackageManager
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
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme

/**
 * Entry point for setContent: theme + mic permission + [BuzzSenseApp].
 * Use as `setContent { BuzzSenseRoot() }` in MainActivity.
 */
@Composable
fun BuzzSenseRoot(modifier: Modifier = Modifier) {
    BuzzsenseTheme {
        val context = LocalContext.current
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
            micAvailable = micGranted,
            onRequestMic = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
            modifier = modifier,
        )
    }
}
