package com.buzzsense.veritas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.buzzsense.veritas.app.ui.BuzzSenseRoot
import com.buzzsense.veritas.app.ui.components.BuzzScreenPreviews
import com.buzzsense.veritas.app.ui.components.BuzzSegmentedControl
import com.buzzsense.veritas.app.ui.gallery.ComponentGallery
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import com.buzzsense.veritas.app.ui.theme.BuzzsenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuzzsenseTheme { BuzzSenseRoot() }
        }
    }
}

/**
 * Prototype launcher: the component gallery, the Detect flow built from
 * those components, and the Gemini key setup.
 */
@Composable
private fun DesignShowcase() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(BuzzTheme.colorScheme.background)
            // Also consumes the inset, so the app flow's top bar doesn't pad it twice.
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        BuzzSegmentedControl(
            options = listOf("Components", "App flow", "Gemini key"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = BuzzTheme.spacing.margin, vertical = BuzzTheme.spacing.sm),
        )
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ComponentGallery()
                1 -> BuzzSenseRoot()
                else -> GeminiKeySetup(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(BuzzTheme.spacing.margin),
                )
            }
        }
    }
}

@BuzzScreenPreviews
@Composable
private fun DesignShowcasePreview() {
    BuzzsenseTheme { DesignShowcase() }
}
