package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * DESIGN.md tap feedback: an immediate alpha shift, never a ripple, glow or
 * scale. Built on Modifier.Node; the alpha is read inside the layer block, so
 * a press only re-draws the layer, never recomposes or re-measures.
 */
fun Modifier.pressAlpha(
    interactionSource: InteractionSource,
    pressedAlpha: Float = 0.9f,
): Modifier = this then PressAlphaElement(interactionSource, pressedAlpha)

private data class PressAlphaElement(
    val interactionSource: InteractionSource,
    val pressedAlpha: Float,
) : ModifierNodeElement<PressAlphaNode>() {
    override fun create() = PressAlphaNode(interactionSource, pressedAlpha)
    override fun update(node: PressAlphaNode) = node.update(interactionSource, pressedAlpha)
}

private class PressAlphaNode(
    private var interactionSource: InteractionSource,
    private var pressedAlpha: Float,
) : Modifier.Node(), LayoutModifierNode {

    private var activePresses by mutableIntStateOf(0)
    private var collectJob: Job? = null

    override fun onAttach() = collectPresses()

    fun update(source: InteractionSource, alpha: Float) {
        if (alpha != pressedAlpha) {
            pressedAlpha = alpha
            invalidatePlacement()
        }
        if (source != interactionSource) {
            interactionSource = source
            collectPresses()
        }
    }

    private fun collectPresses() {
        collectJob?.cancel()
        activePresses = 0
        collectJob = coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                activePresses = presses.size
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                alpha = if (activePresses > 0) pressedAlpha else 1f
            }
        }
    }
}
