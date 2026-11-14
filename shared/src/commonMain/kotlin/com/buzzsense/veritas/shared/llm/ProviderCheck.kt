package com.buzzsense.veritas.shared.llm

import ai.koog.prompt.dsl.prompt

/** Outcome of a one-message test call, shown next to the key field. */
sealed interface KeyCheck {
    data class Ok(val model: String) : KeyCheck
    data class Failed(val kind: FailureKind, val message: String) : KeyCheck
}

/**
 * Send one tiny text prompt through this tier's Koog executor to confirm the key
 * and model work. Nothing about the user's recordings is sent.
 */
suspend fun ProviderTier.check(): KeyCheck = try {
    executor.execute(
        prompt("key-check") { user("Reply with the single word OK.") },
        model,
        emptyList(),
    )
    KeyCheck.Ok(model.id)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    KeyCheck.Failed(FailureClassifier.classify(e), FailureClassifier.redactSecrets(readableError(e)).take(200))
}

private val PROVIDER_MESSAGE = Regex(""""message"\s*:\s*"([^"]+)"""")

/** Prefer the provider's own error text (e.g. "API key not valid") over Koog's wrapper lines. */
internal fun readableError(e: Throwable): String {
    val raw = e.message ?: return e::class.simpleName.orEmpty()
    PROVIDER_MESSAGE.find(raw)?.let { return it.groupValues[1] }
    return raw.lineSequence().map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("Error from client") } ?: raw
}
