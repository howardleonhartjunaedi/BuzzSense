package com.buzzsense.veritas.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.buzzsense.veritas.app.ui.BuzzSenseRoot

/**
 * Debug-only entry that runs the new BuzzSense UI without touching
 * MainActivity. Launch with:
 * adb shell am start -n com.buzzsense.veritas.app/.debug.DesignPreviewActivity
 */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BuzzSenseRoot() }
    }
}
