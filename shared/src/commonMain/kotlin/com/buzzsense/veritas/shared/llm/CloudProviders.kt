package com.buzzsense.veritas.shared.llm

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import com.buzzsense.veritas.shared.model.ProviderTierId

/**
 * Cloud tiers built from the keys the user pasted on first launch (kept in the
 * Android Keystore, see `KeystoreApiKeyStore`). No key is compiled into the app.
 * Only text crosses the network: labels, scores, weather and guidance — never audio.
 */
object CloudProviders {

    private val defaultFactory: KoogHttpClient.Factory by lazy { KtorKoogHttpClient.Factory() }

    /** Tier 1: Gemini API on the free tier, with the user's own key. */
    fun gemini(
        apiKey: String,
        model: LLModel = GoogleModels.Gemini2_5Flash,
        httpClientFactory: KoogHttpClient.Factory = defaultFactory,
    ) = ProviderTier(
        id = ProviderTierId.GEMINI,
        executor = MultiLLMPromptExecutor(GoogleLLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)),
        model = model,
    )

    /** Tier 2 option: Claude API. */
    fun anthropic(
        apiKey: String,
        model: LLModel = AnthropicModels.Haiku_4_5,
        httpClientFactory: KoogHttpClient.Factory = defaultFactory,
    ) = ProviderTier(
        id = ProviderTierId.SECOND_API,
        executor = MultiLLMPromptExecutor(AnthropicLLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)),
        model = model,
    )

    /** Tier 2 option: OpenAI API. */
    fun openAI(
        apiKey: String,
        model: LLModel = OpenAIModels.Chat.GPT5Mini,
        httpClientFactory: KoogHttpClient.Factory = defaultFactory,
    ) = ProviderTier(
        id = ProviderTierId.SECOND_API,
        executor = MultiLLMPromptExecutor(OpenAILLMClient(apiKey = apiKey, httpClientFactory = httpClientFactory)),
        model = model,
    )
}

/** Keys as the user entered them; blank means "not configured". */
data class ProviderKeys(
    val gemini: String? = null,
    val anthropic: String? = null,
    val openAI: String? = null,
)

/**
 * Assemble the chain in plan order: Gemini, second API (Claude preferred, else
 * OpenAI), then the on-device tier if the platform provides one.
 */
fun buildProviderChain(
    keys: ProviderKeys,
    onDevice: ProviderTier?,
    network: NetworkStatus,
    policy: FailoverPolicy = FailoverPolicy(),
): ProviderChain? {
    val tiers = buildList {
        keys.gemini?.takeIf { it.isNotBlank() }?.let { add(CloudProviders.gemini(it)) }
        when {
            !keys.anthropic.isNullOrBlank() -> add(CloudProviders.anthropic(keys.anthropic))
            !keys.openAI.isNullOrBlank() -> add(CloudProviders.openAI(keys.openAI))
        }
        onDevice?.let { add(it) }
    }
    // No tier at all: the agent still works and shows the fixed checklist.
    return if (tiers.isEmpty()) null else ProviderChain(tiers, network, policy)
}
