# `:shared` — BuzzAlert agent module (Koog 1.3.0)

Kotlin Multiplatform module (Android + JVM) with everything that is not UI:
the Koog agent and its strategy graph, the two-stage wingbeat detector wrapper,
on-device RAG, guardrails and provider failover. The Compose UI in `:app` calls
one function:

```kotlin
val engine = AndroidBuzzAlert.create(context, baselineBandHz = 450f..620f) // band fitted on training sites
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
| ONNX Runtime | 1.32.0 | wingbeat CNN, Silero VAD, e5 embedder |

## Layout

```
commonMain/
  agent/      BuzzAlertEngine (entry point), BuzzAlertStrategy (Koog graph),
              BuzzAlertTools (read-only Koog Tools + ToolGate), AlertPrompt, FallbackChecklist
  detector/   LogMelExtractor (pure Kotlin, same code on phone + laptop),
              WingbeatDetector (2 stages + Unsure band), DominantFrequencyBaseline
  rag/        GuidanceStore (SQLite FTS5 + sqlite-vec/brute-force), HybridRetriever (RRF k=60),
              GuidanceQueryBuilder (query from detection + weather only), PrecomputedQueryEmbedder
  guardrails/ SpeechGate, UntrustedFence, CanaryToken, ToolPermissions,
              HealthSafetyValidator, AlarmFatigueGuard
  llm/        FailoverPromptExecutor + ProviderChain (Gemini → Claude/OpenAI → LiteRT),
              FailureClassifier, CloudProviders
  context/    OpenMeteoWeather (fixed San Juan coordinates), ForecastRiskSource (stretch)
  model/      DetectionResult, AlertDraft (structured output), AgentOutcome, DecisionTrace
androidMain/  AndroidBuzzAlert (wiring), OnDeviceLlm (Koog LiteRT tier), KeystoreApiKeyStore,
              AndroidNetworkStatus
onnxShared/   OnnxWingbeatModel, SileroVad, OnnxE5Embedder (compiled into android + jvm)
jvmMain/      JvmBuzzAlert + eval 7 (retrieval) command
commonTest/   guardrail/RRF/failover unit tests + end-to-end graph runs with scripted providers
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
| 3 consecutive failures | circuit breaker skips the tier for 60 s |

`forcePrimaryFailure = true` makes tier 1 fail so failover can be shown in the demo.
The answering tier is in `DecisionTrace.provider` for the "How it decided" panel.

## Files the app must ship (`app/src/main/assets/`)

`guidance.db`, `wingbeat_stage1.onnx`, `wingbeat_stage2.onnx`, `silero_vad.onnx`.
Optional: `libvec0.so` in `app/src/main/jniLibs/<abi>/` (sqlite-vec) and a
`.litertlm` model in `filesDir/llm/` (on-device tier). The guidance DB schema is
documented on `GuidanceStore`; it also stores the embedding of every templated
query, so the phone does not need a SentencePiece tokenizer.

## Still to do by the team

- Real chunk ids in `FallbackChecklist` (currently placeholders).
- The corpus build script (chunk → embed with e5 → write `guidance.db`).
- Threshold values in `Thresholds` and the baseline band, picked on validation sites.
- `FeatureConfig` must match the Colab notebook exactly (eval 6 checks parity).

This project is a prototype and is not medical advice.
