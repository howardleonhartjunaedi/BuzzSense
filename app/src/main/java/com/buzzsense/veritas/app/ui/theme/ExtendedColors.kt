package com.buzzsense.veritas.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors Material 3 has no slot for: the three result states, the
 * mustard focal accent and the muted unit tone of two-tone readouts.
 */
@Immutable
data class BuzzColors(
    /** Forest green: no Aedes / safe / cleared. */
    val resultSafe: Color,
    val onResultSafe: Color,
    /** Warm orange: not sure, ambient activity, retest. */
    val resultWatch: Color,
    val onResultWatch: Color,
    /** Alert red: likely Aedes aegypti. */
    val resultHazard: Color,
    val onResultHazard: Color,
    /** Mustard: focal metrics, active listening, highlight behind one word. */
    val accent: Color,
    val onAccent: Color,
    /** Muted tone for units ("Hz", "%") and secondary halves of two-tone text. */
    val unit: Color,
    /** Track behind confidence bars, progress rings and checkboxes. */
    val track: Color,
)

internal val LightBuzzColors = BuzzColors(
    resultSafe = BuzzPalette.ForestGreen,
    onResultSafe = BuzzPalette.CanvasLight,
    resultWatch = BuzzPalette.WarmOrange,
    onResultWatch = BuzzPalette.CanvasLight,
    resultHazard = BuzzPalette.AlertRed,
    onResultHazard = BuzzPalette.CanvasLight,
    accent = BuzzPalette.Mustard,
    onAccent = BuzzPalette.OnTertiaryFixed,
    unit = BuzzPalette.Neutral10.copy(alpha = 0.6f),
    track = BuzzPalette.Neutral94,
)

internal val DarkBuzzColors = BuzzColors(
    resultSafe = BuzzPalette.ForestGreenDark,
    onResultSafe = BuzzPalette.OnSecondaryFixed,
    resultWatch = BuzzPalette.WarmOrangeDark,
    onResultWatch = BuzzPalette.OnTertiaryFixed,
    resultHazard = BuzzPalette.AlertRedDark,
    onResultHazard = BuzzPalette.OnErrorDark,
    accent = BuzzPalette.TertiaryFixedDim,
    onAccent = BuzzPalette.OnTertiaryFixed,
    unit = BuzzPalette.DarkOnSurface.copy(alpha = 0.6f),
    track = BuzzPalette.DarkContainerHigh,
)

val LocalBuzzColors = staticCompositionLocalOf { LightBuzzColors }
