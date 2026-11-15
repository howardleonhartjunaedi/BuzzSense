package com.buzzsense.veritas.shared.agent

import com.buzzsense.veritas.shared.guardrails.CanaryToken
import com.buzzsense.veritas.shared.guardrails.UntrustedFence
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.json.JsonStructure
import com.buzzsense.veritas.shared.model.AlertAction
import com.buzzsense.veritas.shared.model.AlertDraft
import com.buzzsense.veritas.shared.model.Urgency
import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.RiskLevel
import com.buzzsense.veritas.shared.model.Weather
import kotlin.math.roundToInt

/** Prompt text for the compose node. Inputs are labels, scores, weather and fenced guidance — no audio. */
internal object AlertPrompt {

    fun system(language: Language, fence: UntrustedFence, canary: CanaryToken): String = buildString {
        appendLine("You write short mosquito-bite prevention checklists (dengue and malaria) for a family who just heard a mosquito indoors.")
        appendLine("Rules:")
        appendLine("- If the mosquito is likely Aedes aegypti, focus on dengue: day-time bites and water containers.")
        appendLine("- If it is not Aedes aegypti, it may be a night-biting malaria mosquito: prefer nets at night, standing water and testing for fever, when the documents support them.")
        appendLine("- Never say that anyone has or will get a disease; a buzz only shows a mosquito is present.")
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

    /**
     * Structured output in Koog's Manual mode: the JSON schema and one example go into the
     * prompt as text. Koog would otherwise pick Native mode from the *session* model (Gemini)
     * and send a Google-format schema in `params.schema` to whichever tier answers. LiteRT
     * ignores that parameter, so Gemma got no format at all, and Claude/OpenAI got a schema
     * written for Google. Text instructions work on every tier; the validator still checks.
     */
    val structuredRequest: StructuredRequestConfig<AlertDraft> = StructuredRequestConfig(
        default = StructuredRequest.Manual(
            JsonStructure.create<AlertDraft>(
                examples = listOf(
                    AlertDraft(
                        summary = "One short sentence on what was heard and why it matters today.",
                        actions = listOf(
                            AlertAction("A concrete step the family can take today.", "id-of-the-document-it-came-from"),
                            AlertAction("Another step from the documents.", "id-of-the-document-it-came-from"),
                            AlertAction("A third step from the documents.", "id-of-the-document-it-came-from"),
                        ),
                        urgency = Urgency.MEDIUM,
                    ),
                ),
            ),
        ),
    )
}
