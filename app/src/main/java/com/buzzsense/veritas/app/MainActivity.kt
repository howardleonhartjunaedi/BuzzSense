package com.buzzsense.veritas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.buzzsense.veritas.app.ui.BuzzSenseRoot
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme

/**
 * Opens the app: splash, first-run onboarding, then Detect · Learn · Credits. The design
 * system gallery is in Credits → Design system.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuzzsenseTheme { BuzzSenseRoot() }
        }
    }
}
