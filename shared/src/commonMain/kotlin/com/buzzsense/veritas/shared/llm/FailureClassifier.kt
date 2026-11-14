package com.buzzsense.veritas.shared.llm

import ai.koog.http.client.KoogHttpClientException

/** The error classes from the failover table in the architecture plan. */
enum class FailureKind {
    /** Timeout, connection lost, HTTP 5xx, HTTP 429: retry once with jittered backoff, then next provider. */
    TRANSIENT,

    /** HTTP 401/403: skip this provider for the session. */
    AUTH,

    /** HTTP 400, unsupported structured output: next provider, no retry. */
    BAD_REQUEST,

    /** Airplane mode / DNS failure: go straight to the on-device model. */
    NO_NETWORK,
}

object FailureClassifier {

    private val STATUS_IN_MESSAGE = Regex("""Status code:\s*(\d{3})""")

    private val NO_NETWORK_TYPES = setOf(
        "UnknownHostException", "UnresolvedAddressException", "NoRouteToHostException",
        "ConnectException", "UnknownServiceException",
    )
    private val TIMEOUT_TYPES = setOf(
        "SocketTimeoutException", "HttpRequestTimeoutException", "ConnectTimeoutException",
        "TimeoutCancellationException", "InterruptedIOException", "EOFException", "IOException",
    )

    fun classify(error: Throwable): FailureKind {
        val chain = generateSequence(error) { it.cause.takeIf { c -> c !== it } }.take(10).toList()

        chain.firstNotNullOfOrNull { (it as? KoogHttpClientException)?.statusCode }?.let { return fromStatus(it) }
        chain.firstNotNullOfOrNull { t -> t.message?.let { STATUS_IN_MESSAGE.find(it)?.groupValues?.get(1)?.toInt() } }
            ?.let { return fromStatus(it) }

        val names = chain.mapNotNull { it::class.simpleName }
        return when {
            names.any { it in NO_NETWORK_TYPES } -> FailureKind.NO_NETWORK
            names.any { it in TIMEOUT_TYPES } -> FailureKind.TRANSIENT
            chain.any { it is kotlinx.serialization.SerializationException || it is IllegalArgumentException } ->
                FailureKind.BAD_REQUEST
            else -> FailureKind.TRANSIENT
        }
    }

    /** Gemini sends the key as a `?key=` query parameter. */
    private val KEY_QUERY_PARAM = Regex("""([?&]key=)[^&\s"']+""")

    private val BARE_KEYS = listOf(
        Regex("""\bAIza[0-9A-Za-z_\-]{20,}"""),        // Google API key
        Regex("""\bAQ\.[0-9A-Za-z_\-]{20,}"""),        // Google AI Studio key
        Regex("""\bsk-[0-9A-Za-z_\-]{16,}"""),         // Anthropic (sk-ant-…) / OpenAI
    )

    /**
     * Provider error messages can echo the request URL, which for Gemini holds the
     * API key. Run every message through this before it reaches a trace, log or UI.
     */
    fun redactSecrets(text: String): String =
        BARE_KEYS.fold(KEY_QUERY_PARAM.replace(text) { it.groupValues[1] + "REDACTED" }) { acc, re ->
            re.replace(acc, "REDACTED")
        }

    fun fromStatus(status: Int): FailureKind = when (status) {
        401, 403 -> FailureKind.AUTH
        408, 429 -> FailureKind.TRANSIENT
        in 500..599 -> FailureKind.TRANSIENT
        in 400..499 -> FailureKind.BAD_REQUEST
        else -> FailureKind.TRANSIENT
    }
}
