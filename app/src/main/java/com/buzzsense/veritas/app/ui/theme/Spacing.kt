package com.buzzsense.veritas.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** DESIGN.md spacing tokens. All values are 4dp multiples. */
@Immutable
data class BuzzSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 40.dp,
    /** Column gutter on phones. */
    val gutter: Dp = 16.dp,
    /** Outer canvas margin on phones. */
    val margin: Dp = 20.dp,
    val marginTablet: Dp = 32.dp,
    val marginDesktop: Dp = 48.dp,
    /** Max content width on desktop/workstation layouts. */
    val maxContentWidth: Dp = 1120.dp,
)

val LocalBuzzSpacing = staticCompositionLocalOf { BuzzSpacing() }
