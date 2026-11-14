package com.buzzsense.veritas.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * DESIGN.md "rounded" tokens:
 * - extraSmall 4dp (sm): status tags, stop glyph
 * - small 8dp (DEFAULT): buttons, inputs, icon chips
 * - medium 12dp (md): metric tiles
 * - large 16dp (lg): cards, waveform monitors, result modules
 * - extraLarge 24dp (xl): sheets and docks (top edge)
 */
internal val BuzzShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Top-only rounding for modal sheets. */
internal val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
