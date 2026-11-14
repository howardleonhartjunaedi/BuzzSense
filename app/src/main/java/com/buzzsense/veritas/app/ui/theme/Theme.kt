package com.buzzsense.veritas.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * BuzzSense theme: Material 3 plus the Quiet Acoustic extensions.
 *
 * Dynamic (wallpaper) color is off by default: the result colors carry
 * meaning (red = likely Aedes) and the brand is monochrome. When it is on,
 * only the M3 roles follow the wallpaper; result colors stay fixed.
 */
@Composable
fun BuzzsenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, dynamicColor, context) {
        val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        when {
            supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
            supportsDynamic -> dynamicLightColorScheme(context)
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    }
    val buzzColors = if (darkTheme) DarkBuzzColors else LightBuzzColors

    CompositionLocalProvider(
        LocalBuzzColors provides buzzColors,
        LocalBuzzSpacing provides DefaultSpacing,
        LocalBuzzDataTypography provides DefaultBuzzDataTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BuzzTypography,
            shapes = BuzzShapes,
            content = content,
        )
    }
}

private val DefaultSpacing = BuzzSpacing()

/** Single entry point for theme values in presentation code. */
object BuzzTheme {
    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes

    val colors: BuzzColors
        @Composable @ReadOnlyComposable get() = LocalBuzzColors.current

    val spacing: BuzzSpacing
        @Composable @ReadOnlyComposable get() = LocalBuzzSpacing.current

    val data: BuzzDataTypography
        @Composable @ReadOnlyComposable get() = LocalBuzzDataTypography.current
}
