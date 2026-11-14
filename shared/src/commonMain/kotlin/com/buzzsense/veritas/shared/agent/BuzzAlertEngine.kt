package com.buzzsense.veritas.shared.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.context.ForecastRiskSource
import com.buzzsense.veritas.shared.context.NoForecast
import com.buzzsense.veritas.shared.context.WeatherSource
import com.buzzsense.veritas.shared.detector.WingbeatDetector
import com.buzzsense.veritas.shared.guardrails.HealthSafetyValidator
import com.buzzsense.veritas.shared.guardrails.SpeechGate
import com.buzzsense.veritas.shared.guardrails.ToolPermissions
import com.buzzsense.veritas.shared.llm.AllProvidersFailedException
import com.buzzsense.veritas.shared.llm.ProviderChain
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.TraceEvent
import com.buzzsense.veritas.shared.rag.GuidanceRetriever
import com.buzzsense.veritas.shared.rag.GuidanceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Entry point used by the Android app and the JVM eval harness.
 *
 * ```kotlin
 * val outcome = engine.run(clip, Language.ES)
 * when (outcome) {
 *     is AgentOutcome.LocalMessage -> showMessage(outcome.message)
 *     is AgentOutcome.AlertReady   -> openReview(outcome.alert, outcome.trace)
 * }
 * ```
 *
 * Wiring helpers: `AndroidBuzzAlert.create(...)` (Android) and `JvmBuzzAlert.create(...)` (eval harness).
 *
 * One Koog [AIAgent] is created per run (the agent is stateful); the provider
 * chain, detector and stores are shared.
 */
class BuzzAlertEngine(
    private val detector: WingbeatDetector,
    private val weather: WeatherSource,
    private val retriever: GuidanceRetriever,
    /** Resolves the fixed checklist's chunk ids to citations (usually [GuidanceStore.chunks]). */
    private val chunkLookup: suspend (List<String>) -> List<GuidanceChunk>,
    private val providers: ProviderChain?,
    private val speechGate: SpeechGate? = null,
    private val risk: ForecastRiskSource = NoForecast,
    private val validator: HealthSafetyValidator = HealthSafetyValidator(),
    private val fallback: FallbackChecklist = FallbackChecklist(),
    private val permissions: ToolPermissions = ToolPermissions.BUZZALERT,
    private val maxAgentIterations: Int = 20,
) {
    /**
     * @param forcePrimaryFailure demo/test flag that makes the primary provider fail, to show failover.
     */
    suspend fun run(clip: AudioClip, language: Language, forcePrimaryFailure: Boolean = false): AgentOutcome {
        val events = mutableListOf<TraceEvent>()
        val executor = providers?.newRun(forcePrimaryFailure)
        val tools = BuzzAlertTools(detector, weather, risk, retriever)
        val deps = RunDeps(
            tools = tools,
            gate = ToolGate(permissions, events),
            speechGate = speechGate,
            executor = executor,
            validator = validator,
            fallback = fallback,
            lookupChunks = chunkLookup,
            events = events,
        )
        val agent = AIAgent(
            promptExecutor = executor ?: NoProviderExecutor,
            agentConfig = AIAgentConfig(
                prompt = prompt("buzzalert") {},
                model = providers?.primaryModel ?: PLACEHOLDER_MODEL,
                maxAgentIterations = maxAgentIterations,
            ),
            strategy = buzzAlertStrategy(deps),
            toolRegistry = tools.registry,
        )
        return try {
            agent.run(AlertRequest(clip, language))
        } finally {
            clip.wipe() // guardrail "Audio stays local": never outlives the run
        }
    }

    private companion object {
        /** Only used when no provider is configured; the graph then always takes the fallback path. */
        val PLACEHOLDER_MODEL: LLModel = GoogleModels.Gemini2_5Flash
    }
}

/** Executor used when the user configured no provider at all: every call fails into the fixed checklist. */
internal object NoProviderExecutor : PromptExecutor() {
    private fun fail(): Nothing = throw AllProvidersFailedException(emptyList())
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant = fail()
    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = flow { fail() }
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = fail()
    override fun close() = Unit
}
