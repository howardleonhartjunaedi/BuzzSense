# `:shared` — BuzzAlert agent module (Koog 1.3.0)

Kotlin Multiplatform module (Android + JVM) with everything that is not UI:
the Koog agent and its strategy graph, the two-stage wingbeat detector (kNN over
the pipeline's acoustic store), on-device RAG, guardrails and provider failover.
The Compose UI in `:app` calls one function:

```kotlin
val engine = AndroidBuzzAlert.create(context) // f0 baseline band defaults to 425–809 Hz (train split)
when (val out = engine.run(clip, Language.ES, forcePrimaryFailure = demoFlag)) {
    is AgentOutcome.LocalMessage -> showMessage(out.message)          // no / unsure / speech: no LLM call
    is AgentOutcome.AlertReady   -> openReview(out.alert, out.trace)  // parent edits, then shares
}
```

## Versions

| Library | Version | Notes |
| --- | --- | --- |
| Koog core (`koog-agents`, `agents-test`) | 1.3.0 | stable; includes Anthropic + OpenAI clients |
| Koog Google client, LiteRT client | 1.3.0-beta | beta modules are versioned `-beta` |
| Kotlin | 2.4.21 | Koog 1.3.0 needs Kotlin 2.3.10 or newer |
| Ktor | 3.3.3 | matches Koog 1.3.0 |
| androidx.sqlite (bundled) | 2.6.2 | FTS5 built in; `addExtension` loads sqlite-vec |
| ONNX Runtime | 1.32.0 | Silero VAD, e5 embedder |

## Layout

```
commonMain/
  agent/      BuzzAlertEngine (entry point), BuzzAlertStrategy (Koog graph),
              BuzzAlertTools (read-only Koog Tools + ToolGate), AlertPrompt, FallbackChecklist
  detector/   FeatureSpec + AcousticFeatures (Kotlin port of build_rag.features(), read from
              feature_spec.json), AcousticStore + KnnClassifier (sqlite-vec kNN, k=15, train split),
              WingbeatDetector (2 stages + Unsure band), DominantFrequencyBaseline (f0 rule)
  rag/        GuidanceStore (SQLite FTS5 + sqlite-vec/brute-force), HybridRetriever (RRF k=60),
              GuidanceQueryBuilder (query from detection + weather only), PrecomputedQueryEmbedder
  guardrails/ SpeechGate, UntrustedFence, CanaryToken, ToolPermissions,
              HealthSafetyValidator, AlarmFatigueGuard
  llm/        FailoverPromptExecutor + ProviderChain (Gemini → Claude/OpenAI → LiteRT),
              FailureClassifier (+ redactSecrets), CloudProviders, ProviderCheck (one-prompt key check)
  context/    OpenMeteoWeather (fixed San Juan coordinates), ForecastRiskSource (stretch)
  model/      DetectionResult, AlertDraft (structured output), AgentOutcome, DecisionTrace
androidMain/  AndroidBuzzAlert (wiring), OnDeviceLlm (Koog LiteRT tier), KeystoreApiKeyStore,
              AndroidNetworkStatus
onnxShared/   SileroVad, OnnxE5Embedder (compiled into android + jvm)
jvmMain/      JvmBuzzAlert + eval 7 (retrieval) command
commonTest/   AcousticFeaturesTest (f0), GuardrailsAndRetrievalTest (guardrails, RRF, failover, redaction),
              BuzzAlertAgentTest (end-to-end graph runs with scripted providers)
jvmTest/      FeatureParityTest (+ real-store kNN when env vars set), GeminiLiveTest (when GEMINI_API_KEY set)
```

## Strategy graph

```
start → detect ─(no mosquito / Unsure / speech)→ local_message → finish
          └─(heard)→ context → retrieve → compose → validate ─(ok)→ finish_alert → finish
                                            ↑           ├─(invalid: retry once on next provider)
                                            └───────────┘
                                                        └─(still invalid / no provider)→ fallback → finish
```

Detect, context and retrieve are deterministic nodes that call tools through
`ToolGate` (per-node allowlist; compose has no tools). The LLM runs once in
`compose` via `requestLLMStructured<AlertDraft>()` and never sees audio.

## Failover

| Error | Behaviour |
| --- | --- |
| Timeout, 5xx, 429 | retry once with jittered backoff, then next tier |
| 401 / 403 | tier skipped for the rest of the session |
| 400 / unsupported structured output | next tier, no retry |
| Fails validation | `rejectLastAnswer()` → compose retries on the next tier once, then fixed checklist |
| No network | cloud tiers skipped, straight to LiteRT |
| Any error text | passed through `FailureClassifier.redactSecrets` before it reaches `DecisionTrace` or the UI |
| 3 consecutive failures | circuit breaker skips the tier for 60 s |

`forcePrimaryFailure = true` makes tier 1 fail so failover can be shown in the demo.
The answering tier is in `DecisionTrace.provider` for the "How it decided" panel.

## Gemini (Google AI Studio)

Tier 1 uses Koog's `GoogleLLMClient` with the user's own AI Studio key
(https://aistudio.google.com/apikey). On the phone, `GeminiKeySetup` (in `:app`) saves it
with `KeystoreApiKeyStore` and runs `ProviderTier.check()`, one short text prompt. On a laptop,
set `GEMINI_API_KEY` and run `./gradlew :shared:jvmTest`. `GeminiLiveTest` then does a key check and one
full graph run against the real API; without the variable it is skipped. The default model is
`Gemini3_5FlashLite` (about 1 s per call; the 2.5 models return 404 for new keys). Error text is passed through `FailureClassifier.redactSecrets`, because
Koog sends the key as a `?key=` query parameter.

## Files the app must ship (`app/src/main/assets/`)

`feature_spec.json` and `jumantik_rag.sqlite` (from `Audio File/data/rag/`),
`guidance.db`, `silero_vad.onnx`. The app manifest declares `INTERNET` and
`ACCESS_NETWORK_STATE` (cloud tiers, Open-Meteo, offline detection). Required: `libvec0.so` (sqlite-vec) in
`app/src/main/jniLibs/<abi>/`. Optional: a `.litertlm` model in `filesDir/llm/`
(on-device tier). Record at 8 kHz so no resampling is needed. The guidance DB schema is
documented on `GuidanceStore`; it also stores the embedding of every templated
query, so the phone does not need a SentencePiece tokenizer.

## Still to do by the team

- Try `GeminiKeySetup` on a phone (only checked on the JVM so far); move it to a real settings screen.
- Real chunk ids in `FallbackChecklist` (currently placeholders).
- The corpus build script (chunk → embed with e5 → write `guidance.db`).
- `Thresholds` picked on validation domains (defaults are placeholders).
- A smaller train-split store for the APK: the full `jumantik_rag.sqlite` is ~350 MB.

This project is a prototype and is not medical advice.
