package com.buzzsense.veritas.shared.llm

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import com.buzzsense.veritas.shared.model.ProviderTierId
import com.buzzsense.veritas.shared.model.TraceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/** One provider in the chain. [executor] is a Koog executor bound to [model]. */
class ProviderTier(
    val id: ProviderTierId,
    val executor: PromptExecutor,
    val model: LLModel,
    /** False for the on-device LiteRT tier: still usable in airplane mode. */
    val requiresNetwork: Boolean = true,
)

/** Connectivity check (ConnectivityManager on Android, always true on the JVM). */
fun interface NetworkStatus {
    fun isOnline(): Boolean
}

/** Thrown when every tier failed; the agent then shows the fixed checklist. */
class AllProvidersFailedException(val events: List<TraceEvent>) :
    RuntimeException("All LLM providers failed: " + events.joinToString { it.message })

data class FailoverPolicy(
    val retryBaseDelay: Duration = 400.milliseconds,
    /** Stretch "circuit breaker": skip a provider after this many consecutive failures... */
    val breakerThreshold: Int = 3,
    /** ...for this long. */
    val breakerCooldown: Duration = 60.seconds,
)

/**
 * Session-wide provider chain: primary API, second API, then the on-device model.
 * Holds state that must outlive a single alert (auth-skips, circuit breaker).
 * Create one [FailoverPromptExecutor] per agent run with [newRun].
 */
class ProviderChain(
    val tiers: List<ProviderTier>,
    private val network: NetworkStatus = NetworkStatus { true },
    private val policy: FailoverPolicy = FailoverPolicy(),
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
) : AutoCloseable {
    init {
        require(tiers.isNotEmpty()) { "At least one provider tier is required" }
    }

    private val lock = Mutex()
    private val authSkipped = mutableSetOf<ProviderTierId>()
    private val consecutiveFailures = mutableMapOf<ProviderTierId, Int>()
    private val openUntil = mutableMapOf<ProviderTierId, Instant>()

    /** Model handed to Koog's AIAgentConfig; the executor substitutes each tier's own model. */
    val primaryModel: LLModel get() = tiers.first().model

    /** @param forcePrimaryFailure demo/test flag: the first tier always fails, to show failover live. */
    fun newRun(forcePrimaryFailure: Boolean = false) = FailoverPromptExecutor(this, forcePrimaryFailure)

    internal fun isOnline() = network.isOnline()
    internal fun policy() = policy
    internal fun jitter(base: Duration): Duration = base + (base * random.nextDouble())

    internal suspend fun skipReason(tier: ProviderTier): String? = lock.withLock {
        when {
            tier.id in authSkipped -> "auth failed earlier this session"
            (openUntil[tier.id] ?: Instant.DISTANT_PAST) > clock.now() -> "circuit breaker open"
            else -> null
        }
    }

    internal suspend fun onSuccess(tier: ProviderTier) = lock.withLock {
        consecutiveFailures.remove(tier.id)
        openUntil.remove(tier.id)
    }

    internal suspend fun onFailure(tier: ProviderTier, kind: FailureKind) = lock.withLock {
        if (kind == FailureKind.AUTH) authSkipped += tier.id
        val n = (consecutiveFailures[tier.id] ?: 0) + 1
        consecutiveFailures[tier.id] = n
        if (n >= policy.breakerThreshold) openUntil[tier.id] = clock.now() + policy.breakerCooldown
    }

    override fun close() = tiers.forEach { runCatching { it.executor.close() } }
}

/**
 * Koog [PromptExecutor] that wraps the provider chain for ONE agent run.
 *
 * - Transient errors: one retry with jittered backoff, then next tier.
 * - Auth errors: tier skipped for the rest of the session.
 * - Bad request: next tier, no retry.
 * - No network: cloud tiers skipped, straight to on-device.
 * - Invalid answer (schema / citation / safety): the validate node calls
 *   [rejectLastAnswer] so the compose retry goes to the next tier once.
 */
class FailoverPromptExecutor internal constructor(
    private val chain: ProviderChain,
    private val forcePrimaryFailure: Boolean,
) : PromptExecutor() {

    private val rejected = mutableSetOf<ProviderTierId>()
    private val _events = mutableListOf<TraceEvent>()

    /** Tier that produced the most recent successful answer. */
    var lastTier: ProviderTierId? = null
        private set

    val events: List<TraceEvent> get() = _events.toList()

    /** Called by the validate node when an answer failed the health-safety check. */
    fun rejectLastAnswer(reason: String) {
        lastTier?.let {
            rejected += it
            log("failover", "answer from $it rejected ($reason); trying next provider")
        }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant =
        withFailover { tier -> tier.executor.execute(prompt, tier.model, tools) }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = flow {
        // Failover is decided before the first frame; a mid-stream failure surfaces to the caller.
        val tier = withFailover { it }
        emitAll(tier.executor.executeStreaming(prompt, tier.model, tools))
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        withFailover { tier -> tier.executor.moderate(prompt, tier.model) }

    /** The chain is shared by every run; closing a run must not close the provider clients. */
    override fun close() = Unit

    private suspend fun <T> withFailover(call: suspend (ProviderTier) -> T): T {
        val online = chain.isOnline()
        if (!online) log("failover", "no network: skipping cloud providers")

        for ((index, tier) in chain.tiers.withIndex()) {
            if (tier.id in rejected) continue
            if (tier.requiresNetwork && !online) continue
            chain.skipReason(tier)?.let { log("failover", "${tier.id} skipped: $it"); continue }

            var attempt = 0
            while (true) {
                attempt++
                try {
                    if (forcePrimaryFailure && index == 0) error("Status code: 503 (forced failure test flag)")
                    val started = TimeSource.Monotonic.markNow()
                    val result = call(tier)
                    chain.onSuccess(tier)
                    lastTier = tier.id
                    log("llm", "${tier.id} answered (model ${tier.model.id}, ${started.elapsedNow().inWholeMilliseconds} ms)")
                    return result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val kind = FailureClassifier.classify(e)
                    log("failover", "${tier.id} failed ($kind): ${FailureClassifier.redactSecrets(e.message?.lineSequence()?.firstOrNull { it.isNotBlank() }.orEmpty()).take(120)}")
                    if (kind == FailureKind.TRANSIENT && attempt == 1) {
                        delay(chain.jitter(chain.policy().retryBaseDelay))
                        continue
                    }
                    chain.onFailure(tier, kind)
                    break
                }
            }
        }
        throw AllProvidersFailedException(events)
    }

    private fun log(stage: String, message: String) {
        _events += TraceEvent(stage, message)
    }
}
