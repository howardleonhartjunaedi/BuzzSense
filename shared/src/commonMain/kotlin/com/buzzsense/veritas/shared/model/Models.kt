package com.buzzsense.veritas.shared.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Three-way answer shown on the Result screen. Unsure always comes with "record again, closer". */
@Serializable
enum class Verdict { YES, NO, UNSURE }

@Serializable
enum class Language(val code: String) { EN("en"), ES("es") }

/** Output of the two-stage detector. Only labels and scores ever reach the LLM, never audio. */
@Serializable
data class DetectionResult(
    /** Stage 1: is a mosquito audible in the clip? */
    val mosquito: Verdict,
    /** Stage 2: is it Aedes aegypti? NO when stage 1 is not YES. */
    val aedesAegypti: Verdict,
    /** Mean stage-1 score over all 1 s windows. */
    val mosquitoScore: Float,
    /** Mean stage-2 score over windows stage 1 called mosquito; null if none. */
    val aedesScore: Float?,
    /** Wingbeat fundamental (Hz, harmonic sum, median over windows) for the spectrogram marker. */
    val dominantFrequencyHz: Float?,
    /** Answer of the dominant-frequency rule, shown next to the CNN in "How it decided". */
    val baselineAedes: Verdict,
    val windowCount: Int,
    val mosquitoWindowCount: Int,
    /** Mean kNN vote share per class3 label over all windows, for "How it decided". */
    val classVotes: Map<String, Float> = emptyMap(),
) {
    /**
     * Which advice fits: the Aedes verdict first, then, for a mosquito that is not Aedes, the kNN
     * votes decide between Culex (night biter, no malaria) and Anopheles/other (possible malaria).
     * "Aedes" covers Aedes aegypti and Aedes albopictus: the store's class3 label does not split them.
     */
    val group: MosquitoGroup
        get() = when {
            aedesAegypti == Verdict.YES -> MosquitoGroup.AEDES
            (classVotes["culex"] ?: 0f) > (classVotes["other_mosquito"] ?: 0f) -> MosquitoGroup.CULEX
            else -> MosquitoGroup.ANOPHELES_OR_OTHER
        }
}

/** Mosquito group used for the guidance query and the prompt (see [DetectionResult.group]). */
enum class MosquitoGroup { AEDES, CULEX, ANOPHELES_OR_OTHER }

@Serializable
data class Weather(
    val city: String,
    val temperatureC: Double,
    val relativeHumidityPct: Double,
    val precipitationMm: Double,
    val observedAt: String,
)

@Serializable
enum class RiskLevel { LOW, MEDIUM, HIGH }

/** One retrieved chunk from the public-guidance corpus (WHO, CDC, PAHO, PR Dept. of Health). */
@Serializable
data class GuidanceChunk(
    val id: String,
    val source: String,
    val url: String,
    val title: String,
    val language: Language,
    val text: String,
)

@Serializable
enum class Urgency { LOW, MEDIUM, HIGH }

// ---------------------------------------------------------------------------
// Structured LLM output (Koog requestLLMStructured<AlertDraft>)
// ---------------------------------------------------------------------------

@Serializable
@LLMDescription("One prevention action for the family, tied to the guidance chunk it came from.")
data class AlertAction(
    @property:LLMDescription("Short imperative action a parent can do today, e.g. 'Empty standing water from plant saucers'.")
    val text: String,
    @property:LLMDescription("The id of the retrieved guidance chunk that supports this action. Must be one of the provided ids.")
    @SerialName("chunk_id")
    val chunkId: String,
)

@Serializable
@LLMDescription("Prevention alert for a family that just heard a mosquito indoors.")
data class AlertDraft(
    @property:LLMDescription("One sentence: what was detected and why it matters today. No diagnosis, no medicine.")
    val summary: String,
    @property:LLMDescription("Three to five prevention actions, each citing a provided chunk id.")
    val actions: List<AlertAction>,
    @property:LLMDescription("How soon the family should act: LOW, MEDIUM or HIGH.")
    val urgency: Urgency,
)

/** Which tier produced the alert text; shown in the "How it decided" panel. */
@Serializable
enum class ProviderTierId { GEMINI, SECOND_API, ON_DEVICE, FIXED_FALLBACK }

/** Guardrail or failover event, shown in the "How it decided" panel. */
@Serializable
data class TraceEvent(val stage: String, val message: String)

/** Everything the "How it decided" panel needs. Contains no audio. */
@Serializable
data class DecisionTrace(
    val detection: DetectionResult? = null,
    val weather: Weather? = null,
    val risk: RiskLevel? = null,
    val retrieved: List<GuidanceChunk> = emptyList(),
    val provider: ProviderTierId? = null,
    val events: List<TraceEvent> = emptyList(),
)

/** Alert handed to the Review screen. The parent may edit or remove any action before sharing. */
@Serializable
data class Alert(
    val draft: AlertDraft,
    val citations: Map<String, GuidanceChunk>,
    val disclaimer: String,
    val language: Language,
)

/** Final output of the agent graph. */
@Serializable
sealed interface AgentOutcome {
    val trace: DecisionTrace

    /** No mosquito, Unsure, or speech: a short local message, no LLM call was made. */
    @Serializable
    data class LocalMessage(val message: String, override val trace: DecisionTrace) : AgentOutcome

    /** Alert ready for the parent's review. Nothing is shared without their tap. */
    @Serializable
    data class AlertReady(val alert: Alert, override val trace: DecisionTrace) : AgentOutcome
}

const val DISCLAIMER_EN = "This project is a prototype and is not medical advice."
const val DISCLAIMER_ES = "Este proyecto es un prototipo y no constituye consejo médico."

fun disclaimerFor(language: Language) = if (language == Language.ES) DISCLAIMER_ES else DISCLAIMER_EN
