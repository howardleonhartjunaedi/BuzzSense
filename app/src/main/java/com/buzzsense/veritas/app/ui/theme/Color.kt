package com.buzzsense.veritas.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Raw palette for the "Quiet Acoustic" system (DESIGN.md + Stitch project
 * "BuzzSense Mosquito Detector"). Presentation code must never read these
 * directly: use MaterialTheme.colorScheme or BuzzTheme.colors.
 */
internal object BuzzPalette {
    // Brand anchors (Stitch overrides)
    val InkBlack = Color(0xFF111214)
    val ForestGreen = Color(0xFF1E4733)
    val Mustard = Color(0xFFE0A838)
    val MistGrey = Color(0xFFF4F5F6)

    // Result states
    val WarmOrange = Color(0xFFD95A2B)
    val AlertRed = Color(0xFFC82D2D)

    // Canvases. Dark canvas is near-black, not #000000, to avoid OLED smear.
    val CanvasLight = Color(0xFFFFFFFF)
    val CanvasDark = Color(0xFF0A0A0B)

    // Light neutrals (Stitch tonal palette)
    val Neutral10 = Color(0xFF191C1D)
    val Neutral30 = Color(0xFF45474A)
    val Neutral50 = Color(0xFF76777B)
    val Neutral80 = Color(0xFFC6C6CA)
    val Neutral90 = Color(0xFFE1E3E4)
    val Neutral92 = Color(0xFFE7E8E9)
    val Neutral94 = Color(0xFFEDEEEF)
    val Neutral95 = Color(0xFFF0F1F2)
    val Neutral20 = Color(0xFF2E3132)

    // Dark neutrals
    val DarkContainerLow = Color(0xFF17181A)
    val DarkContainer = Color(0xFF1E1F21)
    val DarkContainerHigh = Color(0xFF26272A)
    val DarkContainerHighest = Color(0xFF303134)
    val DarkOnSurface = Color(0xFFE3E3E4)
    val DarkOnSurfaceVariant = Color(0xFFC6C6CA)
    val DarkOutline = Color(0xFF909094)

    // Secondary (forest) tones
    val SecondaryContainer = Color(0xFFBDEACE)
    val OnSecondaryContainer = Color(0xFF264F3A)
    val SecondaryFixed = Color(0xFFC0EDD1)
    val SecondaryFixedDim = Color(0xFFA4D1B6)
    val OnSecondaryFixed = Color(0xFF002113)

    // Tertiary (mustard) tones
    val TertiaryFixed = Color(0xFFFFDEA8)
    val TertiaryFixedDim = Color(0xFFF7BD4B)
    val OnTertiaryFixed = Color(0xFF271900)
    val OnTertiaryFixedVariant = Color(0xFF5E4200)

    // Error tones
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF93000A)
    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)

    // Dark result tones (lifted for contrast on near-black)
    val ForestGreenDark = Color(0xFF7FC19C)
    val WarmOrangeDark = Color(0xFFF08A5D)
    val AlertRedDark = Color(0xFFF2726B)
}

internal val LightColorScheme = lightColorScheme(
    primary = BuzzPalette.InkBlack,
    onPrimary = BuzzPalette.CanvasLight,
    primaryContainer = Color(0xFF1B1C1E),
    onPrimaryContainer = Color(0xFFC7C6C9),
    inversePrimary = Color(0xFFC7C6C9),
    secondary = BuzzPalette.ForestGreen,
    onSecondary = BuzzPalette.CanvasLight,
    secondaryContainer = BuzzPalette.SecondaryContainer,
    onSecondaryContainer = BuzzPalette.OnSecondaryContainer,
    tertiary = BuzzPalette.Mustard,
    onTertiary = BuzzPalette.OnTertiaryFixed,
    tertiaryContainer = BuzzPalette.TertiaryFixed,
    onTertiaryContainer = BuzzPalette.OnTertiaryFixedVariant,
    error = BuzzPalette.AlertRed,
    onError = BuzzPalette.CanvasLight,
    errorContainer = BuzzPalette.ErrorContainer,
    onErrorContainer = BuzzPalette.OnErrorContainer,
    background = BuzzPalette.CanvasLight,
    onBackground = BuzzPalette.Neutral10,
    surface = BuzzPalette.CanvasLight,
    onSurface = BuzzPalette.Neutral10,
    surfaceVariant = BuzzPalette.Neutral90,
    onSurfaceVariant = BuzzPalette.Neutral30,
    surfaceTint = Color.Transparent, // flat system: no tonal elevation tint
    inverseSurface = BuzzPalette.Neutral20,
    inverseOnSurface = BuzzPalette.Neutral95,
    outline = BuzzPalette.Neutral50,
    outlineVariant = BuzzPalette.Neutral80,
    scrim = BuzzPalette.InkBlack,
    surfaceBright = BuzzPalette.CanvasLight,
    surfaceDim = Color(0xFFD9DADB),
    surfaceContainerLowest = BuzzPalette.CanvasLight,
    surfaceContainerLow = BuzzPalette.MistGrey,
    surfaceContainer = BuzzPalette.Neutral94,
    surfaceContainerHigh = BuzzPalette.Neutral92,
    surfaceContainerHighest = BuzzPalette.Neutral90,
)

internal val DarkColorScheme = darkColorScheme(
    primary = BuzzPalette.CanvasLight,
    onPrimary = BuzzPalette.InkBlack,
    primaryContainer = Color(0xFFE3E2E4),
    onPrimaryContainer = Color(0xFF1B1C1E),
    inversePrimary = BuzzPalette.InkBlack,
    secondary = BuzzPalette.SecondaryFixedDim,
    onSecondary = BuzzPalette.OnSecondaryFixed,
    secondaryContainer = BuzzPalette.OnSecondaryContainer,
    onSecondaryContainer = BuzzPalette.SecondaryFixed,
    tertiary = BuzzPalette.TertiaryFixedDim,
    onTertiary = BuzzPalette.OnTertiaryFixed,
    tertiaryContainer = BuzzPalette.OnTertiaryFixedVariant,
    onTertiaryContainer = BuzzPalette.TertiaryFixed,
    error = BuzzPalette.ErrorDark,
    onError = BuzzPalette.OnErrorDark,
    errorContainer = BuzzPalette.ErrorContainerDark,
    onErrorContainer = BuzzPalette.ErrorContainer,
    background = BuzzPalette.CanvasDark,
    onBackground = BuzzPalette.DarkOnSurface,
    surface = BuzzPalette.CanvasDark,
    onSurface = BuzzPalette.DarkOnSurface,
    surfaceVariant = BuzzPalette.DarkContainerHighest,
    onSurfaceVariant = BuzzPalette.DarkOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = BuzzPalette.DarkOnSurface,
    inverseOnSurface = BuzzPalette.Neutral20,
    outline = BuzzPalette.DarkOutline,
    outlineVariant = BuzzPalette.Neutral30,
    scrim = Color.Black,
    surfaceBright = BuzzPalette.DarkContainerHighest,
    surfaceDim = BuzzPalette.CanvasDark,
    surfaceContainerLowest = BuzzPalette.CanvasDark,
    surfaceContainerLow = BuzzPalette.DarkContainerLow,
    surfaceContainer = BuzzPalette.DarkContainer,
    surfaceContainerHigh = BuzzPalette.DarkContainerHigh,
    surfaceContainerHighest = BuzzPalette.DarkContainerHighest,
)
