package com.buzzsense.veritas.shared.agent

import com.buzzsense.veritas.shared.guardrails.CanaryToken
import com.buzzsense.veritas.shared.guardrails.UntrustedFence
import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.RiskLevel
import com.buzzsense.veritas.shared.model.Weather
import kotlin.math.roundToInt

/** Prompt text for the compose node. Inputs are labels, scores, weather and fenced guidance — no audio. */
internal object AlertPrompt {

    fun system(language: Language, fence: UntrustedFence, canary: CanaryToken): String = buildString {
        appendLine("You write short dengue-prevention checklists for a family in San Juan, Puerto Rico, who just heard a mosquito indoors.")
        appendLine("Rules:")
        appendLine("- Use ONLY the guidance documents provided. Every action must cite the id of the document it came from.")
        appendLine("- Give 3 to 5 concrete actions the family can do today at home.")
        appendLine("- Never diagnose anyone. Never mention medicines, treatments, doses or pills.")
        appendLine("- If the documents mention symptoms, the only allowed advice is to contact a health professional.")
        appendLine("- Write in ${if (language == Language.ES) "Spanish (Puerto Rico)" else "English"}. Plain words, no jargon.")
        appendLine(fence.systemNotice())
        append(canary.systemLine())
    }

    fun user(
        detection: DetectionResult,
        weather: Weather?,
        risk: RiskLevel?,
        chunks: List<GuidanceChunk>,
        fence: UntrustedFence,
    ): String = buildString {
        appendLine("Detection (on-device model):")
        appendLine("- mosquito heard: ${detection.mosquito} (score ${pct(detection.mosquitoScore)})")
        appendLine("- likely Aedes aegypti: ${detection.aedesAegypti}${detection.aedesScore?.let { " (score ${pct(it)})" } ?: ""}")
        if (weather != null) {
            appendLine("Weather today in ${weather.city}: ${weather.temperatureC}°C, humidity ${weather.relativeHumidityPct}%, rain ${weather.precipitationMm} mm.")
        }
        risk?.let { appendLine("This week's outbreak risk level: $it") }
        appendLine()
        appendLine("Guidance documents:")
        appendLine(fence.wrap(chunks))
        appendLine()
        append("Allowed chunk ids: ${chunks.joinToString { it.id }}")
    }

    private fun pct(x: Float) = "${(x * 100).roundToInt()}%"
}
