package com.buzzsense.veritas.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.buzzsense.veritas.app.R
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Font families from DESIGN.md: Space Grotesk (display, data, small labels)
 * and Hanken Grotesk (body, titles).
 *
 * Both are bundled as single variable TTFs from google/fonts (SIL OFL 1.1,
 * licenses in assets/licenses). Each weight is an instance of the `wght`
 * axis, so one file serves every weight (variable fonts need API 26+,
 * which is the app's minSdk).
 */
@OptIn(ExperimentalTextApi::class) // Font(resId, weight, variationSettings)
private fun variableFamily(resId: Int, vararg weights: FontWeight): FontFamily =
    FontFamily(
        weights.map { weight ->
            Font(
                resId = resId,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

/** Space Grotesk ships weights 300–700. */
val SpaceGrotesk: FontFamily = variableFamily(
    R.font.space_grotesk,
    FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

/** Hanken Grotesk ships weights 100–900; the app uses 400–800. */
val HankenGrotesk: FontFamily = variableFamily(
    R.font.hanken_grotesk,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
)

/** Tabular numerals so readouts like "620 Hz" do not jitter while they change. */
private const val TABULAR_NUMS = "tnum"

/** Material 3 type scale mapped from the DESIGN.md tokens. */
internal val BuzzTypography = Typography(
    // display-lg
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.03).em,
    ),
    // display-lg-mobile
    displayMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em,
    ),
    displaySmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em,
    ),
    // headline-lg
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em,
    ),
    // headline-lg-mobile
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em,
    ),
    // headline-md
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    // title-md
    titleLarge = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // body-lg
    bodyLarge = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    // body-md
    bodyMedium = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    // Button labels
    labelLarge = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    // label-md
    labelMedium = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em,
    ),
    // label-sm
    labelSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.06.em,
    ),
)

/** Data styles with no Material 3 slot. Both use tabular numerals. */
@Immutable
data class BuzzDataTypography(
    /** data-tabular-lg: 28/32 Space Grotesk Medium. */
    val tabularLarge: TextStyle,
    /** data-tabular-sm: 16/20 Space Grotesk Medium. */
    val tabularSmall: TextStyle,
    /** Hero readout (confidence %, timer): display-lg with tabular numerals. */
    val hero: TextStyle,
)

internal val DefaultBuzzDataTypography = BuzzDataTypography(
    tabularLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR_NUMS,
    ),
    tabularSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_NUMS,
    ),
    hero = BuzzTypography.displayLarge.copy(fontFeatureSettings = TABULAR_NUMS),
)

val LocalBuzzDataTypography = staticCompositionLocalOf { DefaultBuzzDataTypography }
