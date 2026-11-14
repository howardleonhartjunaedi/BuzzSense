package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.R
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/** Uppercase label-sm "analytical ceiling" above a value or headline. */
@Composable
fun BuzzEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BuzzTheme.colorScheme.outline,
) {
    Text(text = text.uppercase(), style = BuzzTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** Eyebrow + headline + optional supporting text. */
@Composable
fun BuzzScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    centered: Boolean = false,
    titleStyle: TextStyle = BuzzTheme.typography.headlineMedium,
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xs),
    ) {
        if (eyebrow != null) BuzzEyebrow(eyebrow)
        Text(title, style = titleStyle, color = BuzzTheme.colorScheme.primary, textAlign = align)
        if (subtitle != null) {
            Text(
                subtitle,
                style = BuzzTheme.typography.bodyMedium,
                color = BuzzTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
            )
        }
    }
}

/**
 * The prototype disclaimer. AGENTS.md requires this exact line on result and
 * write-up surfaces, so the default text comes from R.string.disclaimer.
 */
@Composable
fun BuzzDisclaimer(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.disclaimer),
) {
    Text(
        text = text,
        style = BuzzTheme.typography.labelMedium,
        color = BuzzTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuzzTheme.spacing.md),
    )
}

/** Body text with one word set on a mustard highlight. */
@Composable
fun BuzzHighlightedText(
    text: String,
    highlight: String,
    modifier: Modifier = Modifier,
    style: TextStyle = BuzzTheme.typography.bodyLarge,
) {
    val accent = BuzzTheme.colors.accent
    val onAccent = BuzzTheme.colors.onAccent
    val annotated = remember(text, highlight, accent, onAccent) {
        buildAnnotatedString {
            val start = text.indexOf(highlight)
            if (start < 0 || highlight.isEmpty()) {
                append(text)
            } else {
                append(text.substring(0, start))
                withStyle(SpanStyle(background = accent, color = onAccent, fontWeight = FontWeight.Medium)) {
                    append(" $highlight ")
                }
                append(text.substring(start + highlight.length))
            }
        }
    }
    Text(annotated, style = style, color = BuzzTheme.colorScheme.onSurface, modifier = modifier)
}

/** Tone of a [BuzzStatusTag]. */
enum class StatusTone { Safe, Watch, Hazard, Neutral }

/**
 * Square-cornered status tag (never a pill, per DESIGN.md "Shapes").
 */
@Composable
fun BuzzStatusTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
) {
    val buzz = BuzzTheme.colors
    val scheme = BuzzTheme.colorScheme
    val (container, content) = when (tone) {
        StatusTone.Safe -> buzz.resultSafe to buzz.onResultSafe
        StatusTone.Watch -> buzz.resultWatch to buzz.onResultWatch
        StatusTone.Hazard -> buzz.resultHazard to buzz.onResultHazard
        StatusTone.Neutral -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    Text(
        text = text.uppercase(),
        style = BuzzTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, BuzzTheme.shapes.extraSmall)
            .padding(horizontal = BuzzTheme.spacing.sm, vertical = BuzzTheme.spacing.xs),
    )
}

@BuzzPreviews
@Composable
private fun BuzzScreenHeaderPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BuzzScreenHeader(
                eyebrow = "Detect",
                title = "Is it a dengue mosquito?",
                subtitle = "Record the buzz for five seconds.",
                centered = true,
            )
            BuzzScreenHeader(eyebrow = "Result", title = "Likely Aedes aegypti", titleStyle = BuzzTheme.typography.displayMedium)
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzTextBitsPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuzzHighlightedText("Targeted monitoring for dengue transmission zones.", highlight = "dengue")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BuzzStatusTag("Safe", tone = StatusTone.Safe)
                BuzzStatusTag("Retest", tone = StatusTone.Watch)
                BuzzStatusTag("Aedes", tone = StatusTone.Hazard)
                BuzzStatusTag("Draft")
            }
            BuzzDisclaimer()
        }
    }
}
