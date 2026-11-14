package com.buzzsense.veritas.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.app.ui.theme.BuzzTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** What the hero mic shows. */
enum class MicButtonState {
    /** Ink disc with a mic glyph on a mist halo. */
    Idle,

    /** Stop glyph inside a forest-green progress ring. */
    Recording,

    /** Mic permission missing or mic busy: muted disc, mic-off glyph. */
    Unavailable,
}

/**
 * 120dp hero microphone on a 156dp footprint. [progress] is a lambda so the
 * ring updates in the draw phase without recomposing the button.
 */
@Composable
fun BuzzMicButton(
    state: MicButtonState,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 0f },
) {
    val scheme = BuzzTheme.colorScheme
    val ringColor = scheme.secondary
    val trackColor = BuzzTheme.colors.track
    val haloColor = scheme.surfaceContainerLow
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(156.dp)
            .drawBehind {
                when (state) {
                    MicButtonState.Recording -> {
                        val stroke = 4.dp.toPx()
                        val inset = stroke / 2 + 4.dp.toPx()
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        val topLeft = Offset(inset, inset)
                        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress().coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    else -> drawCircle(haloColor)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val enabled = state != MicButtonState.Unavailable
        Box(
            modifier = Modifier
                .size(120.dp)
                .pressAlpha(interactionSource, pressedAlpha = 0.75f)
                .clip(CircleShape)
                .background(if (enabled) scheme.primary else scheme.surfaceContainerHighest)
                .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                MicButtonState.Idle -> Icon(
                    BuzzIcons.Mic, contentDescription = null, tint = scheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
                MicButtonState.Recording -> Box(
                    Modifier
                        .size(24.dp)
                        .background(scheme.onPrimary, BuzzTheme.shapes.extraSmall),
                )
                MicButtonState.Unavailable -> Icon(
                    BuzzIcons.MicOff, contentDescription = null, tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

/**
 * Animated forest-green sound-level bars shown while listening. The phase is
 * read only inside the Canvas draw block.
 */
@Composable
fun BuzzSoundLevelBars(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    barCount: Int = 9,
) {
    val color = BuzzTheme.colorScheme.secondary
    val idleColor = BuzzTheme.colors.track
    val transition = rememberInfiniteTransition(label = "soundLevel")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(
        modifier = modifier
            .width((barCount * 10).dp)
            .height(32.dp),
    ) {
        val barWidth = 4.dp.toPx()
        val gap = (size.width - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
        val minH = 6.dp.toPx()
        val maxH = size.height
        val p = if (active) phase.value else 0f
        repeat(barCount) { i ->
            // Deterministic per-bar offset gives an organic, non-uniform rhythm.
            val offset = i * 1.7f
            val level = if (active) abs(sin(p + offset)) else 0f
            val h = minH + (maxH - minH) * level
            drawRoundRect(
                color = if (active) color else idleColor,
                topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzMicButtonPreview() {
    BuzzPreviewContainer {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BuzzMicButton(MicButtonState.Idle, onClick = {}, contentDescription = "Start recording")
            BuzzMicButton(MicButtonState.Recording, onClick = {}, contentDescription = "Stop", progress = { 0.6f })
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzMicButtonUnavailablePreview() {
    BuzzPreviewContainer {
        BuzzMicButton(MicButtonState.Unavailable, onClick = {}, contentDescription = "Microphone is off")
    }
}

@BuzzPreviews
@Composable
private fun BuzzSoundLevelBarsPreview() {
    BuzzPreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BuzzSoundLevelBars(active = true)
            BuzzSoundLevelBars(active = false)
        }
    }
}
