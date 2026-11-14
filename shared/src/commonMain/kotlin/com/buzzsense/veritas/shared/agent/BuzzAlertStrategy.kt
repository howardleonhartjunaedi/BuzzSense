package com.buzzsense.veritas.shared.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.prompt
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.guardrails.CanaryToken
import com.buzzsense.veritas.shared.guardrails.HealthSafetyValidator
import com.buzzsense.veritas.shared.guardrails.SpeechGate
import com.buzzsense.veritas.shared.guardrails.ToolPermissions.Companion.NODE_COMPOSE
import com.buzzsense.veritas.shared.guardrails.ToolPermissions.Companion.NODE_CONTEXT
import com.buzzsense.veritas.shared.guardrails.ToolPermissions.Companion.NODE_DETECT
import com.buzzsense.veritas.shared.guardrails.ToolPermissions.Companion.NODE_RETRIEVE
import com.buzzsense.veritas.shared.guardrails.ToolPermissions.Companion.NODE_VALIDATE
import com.buzzsense.veritas.shared.guardrails.UntrustedFence
import com.buzzsense.veritas.shared.llm.AllProvidersFailedException
import com.buzzsense.veritas.shared.llm.FailoverPromptExecutor
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.Alert
import com.buzzsense.veritas.shared.model.AlertDraft
import com.buzzsense.veritas.shared.model.DecisionTrace
import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.ProviderTierId
import com.buzzsense.veritas.shared.model.RiskLevel
import com.buzzsense.veritas.shared.model.TraceEvent
import com.buzzsense.veritas.shared.model.Verdict
import com.buzzsense.veritas.shared.model.Weather
import com.buzzsense.veritas.shared.model.disclaimerFor
import com.buzzsense.veritas.shared.rag.GuidanceQueryBuilder
import kotlinx.coroutines.CancellationException

/** Agent input: the in-memory clip and the language the parent chose. */
class AlertRequest(val clip: AudioClip, val language: Language)

// Typed values passed along the graph edges.
internal sealed interface DetectStep {
    data object SpeechRejected : DetectStep
    data class Detected(val result: DetectionResult) : DetectStep
}

internal data class ContextBundle(val detection: DetectionResult, val weather: Weather?, val risk: RiskLevel?)
internal data class ComposeInput(val context: ContextBundle, val chunks: List<GuidanceChunk>)

internal sealed interface ComposeResult {
    val input: ComposeInput
    data class Draft(override val input: ComposeInput, val draft: AlertDraft) : ComposeResult
    data class ParseFailed(override val input: ComposeInput, val reason: String) : ComposeResult
    data class NoProvider(override val input: ComposeInput) : ComposeResult
}

internal sealed interface Checked {
    data class Valid(val input: ComposeInput, val draft: AlertDraft) : Checked
    data class Retry(val input: ComposeInput) : Checked
    data class Fallback(val input: ComposeInput, val reason: String) : Checked
}

/** Everything one run of the graph needs. Built fresh per run (one agent per alert). */
internal class RunDeps(
    val tools: BuzzAlertTools,
    val gate: ToolGate,
    val speechGate: SpeechGate?,
    val executor: FailoverPromptExecutor?,
    val validator: HealthSafetyValidator,
    val fallback: FallbackChecklist,
    val lookupChunks: suspend (List<String>) -> List<GuidanceChunk>,
    val events: MutableList<TraceEvent>,
    val maxComposeAttempts: Int = 2,
)

/**
 * The fixed BuzzAlert strategy graph:
 *
 * ```
 * start → detect ─(no mosquito / unsure / speech)→ localMessage → finish
 *            └─(mosquito heard)→ context → retrieve → compose → validate ─(ok)→ finishAlert → finish
 *                                                       ↑          ├─(invalid, retry once on next provider)
 *                                                       └──────────┘
 *                                                                  └─(still invalid / no provider)→ fallback → finish
 * ```
 *
 * Detector and retrieval are deterministic nodes; the LLM only writes the alert
 * from what they return, and only after the confidence gate passed.
 */
internal fun buzzAlertStrategy(deps: RunDeps): AIAgentGraphStrategy<AlertRequest, AgentOutcome> =
    strategy<AlertRequest, AgentOutcome>("buzzalert") {
        val fence = UntrustedFence()
        val canary = CanaryToken()
        var language = Language.EN
        var attempts = 0
        var detected: DetectionResult? = null

        fun trace(ctx: ContextBundle? = null, chunks: List<GuidanceChunk> = emptyList(), provider: ProviderTierId? = null) =
            DecisionTrace(
                detection = detected,
                weather = ctx?.weather,
                risk = ctx?.risk,
                retrieved = chunks,
                provider = provider,
                events = deps.events.toList() + deps.executor?.events.orEmpty(),
            )

        // 1. Detect: speech rejection, then the two-stage CNN. The clip is wiped either way.
        val detect by node<AlertRequest, DetectStep>(NODE_DETECT) { req ->
            language = req.language
            try {
                if (deps.speechGate?.rejectIfSpeech(req.clip) == true) {
                    deps.events += TraceEvent("speech-rejection", "voice detected; clip deleted")
                    return@node DetectStep.SpeechRejected
                }
                deps.tools.clipHolder.clip = req.clip
                val result = deps.gate.call(NODE_DETECT, deps.tools.detect, NoArgs)
                detected = result
                DetectStep.Detected(result)
            } finally {
                req.clip.wipe()
                deps.tools.clipHolder.clip = null
            }
        }

        val localMessage by node<DetectStep, AgentOutcome>("local_message") { step ->
            deps.events += TraceEvent("confidence-gate", "graph stopped before any LLM call")
            AgentOutcome.LocalMessage(LocalMessages.forStep(step, language), trace())
        }

        // 2. Context: weather (fixed city, no GPS) and the optional outbreak risk level.
        val context by node<DetectStep, ContextBundle>(NODE_CONTEXT) { step ->
            val detection = (step as DetectStep.Detected).result
            val weather = runCatching { deps.gate.call(NODE_CONTEXT, deps.tools.weatherToday, NoArgs) }
                .onFailure { if (it is CancellationException) throw it; deps.events += TraceEvent("context", "weather unavailable: ${it.message}") }
                .getOrNull()
            val risk = runCatching { deps.gate.call(NODE_CONTEXT, deps.tools.riskLevel, NoArgs).level }.getOrNull()
            ContextBundle(detection, weather, risk)
        }

        // 3. Retrieve: query built from detection + weather only, never from free text.
        val retrieve by node<ContextBundle, ComposeInput>(NODE_RETRIEVE) { ctx ->
            val query = GuidanceQueryBuilder.build(ctx.detection, ctx.weather, ctx.risk, language)
            val chunks = deps.gate.call(NODE_RETRIEVE, deps.tools.searchGuidance, GuidanceQuery(query)).chunks
            deps.events += TraceEvent("retrieve", "${chunks.size} chunks for query \"$query\"")
            ComposeInput(ctx, chunks)
        }

        // 4. Compose: the only LLM call. Structured output, no tools, fenced documents.
        val compose by node<ComposeInput, ComposeResult>(NODE_COMPOSE) { input ->
            attempts++
            if (deps.executor == null || input.chunks.isEmpty()) return@node ComposeResult.NoProvider(input)
            try {
                val response = llm.writeSession {
                    tools = emptyList()
                    rewritePrompt { old ->
                        prompt(old.id, old.params) {
                            system(AlertPrompt.system(language, fence, canary))
                            user(AlertPrompt.user(input.context.detection, input.context.weather, input.context.risk, input.chunks, fence))
                        }
                    }
                    requestLLMStructured<AlertDraft>()
                }
                response.fold(
                    onSuccess = { ComposeResult.Draft(input, it.data) },
                    onFailure = { ComposeResult.ParseFailed(input, it.message ?: "unparseable structured output") },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AllProvidersFailedException) {
                ComposeResult.NoProvider(input)
            }
        }

        // 5. Validate: schema, citations, no diagnosis/medicine, canary. One retry, then fallback.
        val validate by node<ComposeResult, Checked>(NODE_VALIDATE) { result ->
            when (result) {
                is ComposeResult.NoProvider -> Checked.Fallback(result.input, "no LLM provider available")
                is ComposeResult.ParseFailed -> retryOrFallback(result.input, result.reason, attempts, deps)
                is ComposeResult.Draft -> {
                    val issues = deps.validator.validate(result.draft, result.input.chunks, canary)
                    if (issues.isEmpty()) Checked.Valid(result.input, result.draft)
                    else retryOrFallback(result.input, issues.joinToString("; ") { it.reason }, attempts, deps)
                }
            }
        }

        val finishAlert by node<Checked, AgentOutcome>("finish_alert") { checked ->
            val valid = checked as Checked.Valid
            val alert = Alert(
                draft = valid.draft,
                citations = valid.input.chunks.associateBy { it.id },
                disclaimer = disclaimerFor(language),
                language = language,
            )
            AgentOutcome.AlertReady(alert, trace(valid.input.context, valid.input.chunks, deps.executor?.lastTier))
        }

        val fallback by node<Checked, AgentOutcome>("fallback") { checked ->
            val fb = checked as Checked.Fallback
            deps.events += TraceEvent("fallback", "fixed checklist shown: ${fb.reason}")
            val draft = deps.fallback.draft(language)
            val cited = deps.lookupChunks(deps.fallback.chunkIds(language))
            val alert = Alert(draft, cited.associateBy { it.id }, disclaimerFor(language), language)
            AgentOutcome.AlertReady(alert, trace(fb.input.context, fb.input.chunks, ProviderTierId.FIXED_FALLBACK))
        }

        edge(nodeStart forwardTo detect)
        edge(detect forwardTo localMessage onCondition { !it.passesConfidenceGate() })
        edge(detect forwardTo context onCondition { it.passesConfidenceGate() })
        edge(localMessage forwardTo nodeFinish)
        edge(context forwardTo retrieve)
        edge(retrieve forwardTo compose)
        edge(compose forwardTo validate)
        edge(validate forwardTo finishAlert onCondition { it is Checked.Valid })
        edge(validate forwardTo compose onCondition { it is Checked.Retry } transformed { (it as Checked.Retry).input })
        edge(validate forwardTo fallback onCondition { it is Checked.Fallback })
        edge(finishAlert forwardTo nodeFinish)
        edge(fallback forwardTo nodeFinish)
    }

/** Guardrail "Confidence gate": a mosquito must be heard and stage 2 must not be Unsure. */
internal fun DetectStep.passesConfidenceGate(): Boolean =
    this is DetectStep.Detected && result.mosquito == Verdict.YES && result.aedesAegypti != Verdict.UNSURE

private fun retryOrFallback(input: ComposeInput, reason: String, attempts: Int, deps: RunDeps): Checked {
    deps.events += TraceEvent("health-safety", "answer rejected: $reason")
    return if (attempts < deps.maxComposeAttempts) {
        deps.executor?.rejectLastAnswer(reason)
        Checked.Retry(input)
    } else {
        Checked.Fallback(input, reason)
    }
}

internal object LocalMessages {
    fun forStep(step: DetectStep, language: Language): String {
        val es = language == Language.ES
        return when {
            step is DetectStep.SpeechRejected ->
                if (es) "Escuchamos voces, así que borramos la grabación. Grabe otra vez solo el mosquito."
                else "We heard voices, so the recording was deleted. Please record again with only the mosquito."
            step is DetectStep.Detected && step.result.mosquito == Verdict.NO ->
                if (es) "No se escuchó un mosquito. Si aún oye el zumbido, acerque el teléfono y grabe otra vez."
                else "No mosquito heard. If you still hear buzzing, hold the phone closer and record again."
            else ->
                if (es) "No estamos seguros. Grabe otra vez, más cerca del mosquito."
                else "Not sure. Record again, closer to the mosquito."
        }
    }
}
