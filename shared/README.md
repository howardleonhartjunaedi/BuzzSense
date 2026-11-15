# `:shared` — BuzzAlert agent module (Koog 1.3.0)

Kotlin Multiplatform module (Android + JVM) with everything that is not UI:
the Koog agent and its strategy graph, the two-stage wingbeat detector (kNN over
the pipeline's acoustic store), on-device RAG, guardrails and provider failover.
The Compose UI in `:app` reaches it through `AppContainer` → `DetectViewModel` (see `app/.../ui/detect/`); underneath, it is one call:

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
              GuidanceQueryBuilder (query from detection + weather only; adds malaria terms when not Aedes),
              PrecomputedQueryEmbedder
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
jvmTest/      FeatureParityTest (+ real-store kNN when env vars set), GeminiLiveTest (when GEMINI_API_KEY set),
              GuidanceDbParityTest (when app/src/main/assets/guidance.db exists)
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
`compose` via `requestLLMStructured(AlertPrompt.structuredRequest)` (Koog Manual mode) and never sees audio.

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

## On-device model (tier 3, LiteRT)

Koog's LiteRT client runs Gemma 4 E2B, the file `gemma-4-E2B-it.litertlm` (2.59 GB) from
Hugging Face `litert-community/gemma-4-E2B-it-litert-lm`, under Google's Gemma terms. The
app never downloads it by itself. Until the file is present, `OnDeviceLlm.tier()` returns
null and an offline run goes straight to the fixed checklist.

- **In the app:** `OnDeviceLlm.install(context, uri)` copies a file the user picked (for
  example from `ActivityResultContracts.OpenDocument`) into `filesDir/llm/`, through a temp
  file so a failed copy leaves nothing behind. `isInstalled()` and `remove()` are there for a
  settings screen. No screen calls them yet.
- **Debug build, from a laptop:**
  ```bash
  adb push gemma-4-E2B-it.litertlm /data/local/tmp/
  adb shell run-as com.buzzsense.veritas.app mkdir -p files/llm
  adb shell run-as com.buzzsense.veritas.app cp /data/local/tmp/gemma-4-E2B-it.litertlm files/llm/
  ```

Structured output uses Koog's Manual mode (`AlertPrompt.structuredRequest`): the JSON
schema and an example go in the prompt as text, and Koog strips any markdown fences
before parsing. Native mode was the old default, and it broke this tier, because LiteRT
ignores `params.schema`. On a phone, the tier is checked only by
`onDeviceTierGetsFormatInstructionsAndItsJsonIsParsed`, using a stand-in model on the JVM.

## Files the app must ship (`app/src/main/assets/`)

`feature_spec.json` (from `Audio File/data/rag/`) and the slim train-split `jumantik_rag.sqlite`
(from `Audio File/pipeline/export_slim_store.py`, 127 MB, git-ignored),
`guidance.db` (built by `tools/guidance/build_guidance_db.py`, see its README), `silero_vad.onnx` (Silero VAD v6.2.3, MIT). The app manifest declares `INTERNET` and
`ACCESS_NETWORK_STATE` (cloud tiers, Open-Meteo, offline detection). Required: `libvec0.so` (sqlite-vec 0.1.9, MIT/Apache-2.0, built 16 KB page-aligned by `tools/sqlite-vec/build_android.sh`) in
`app/src/main/jniLibs/<abi>/`, extracted on install (`useLegacyPackaging`). Licences are in `assets/licenses/`. Optional: the Gemma `.litertlm` model in `filesDir/llm/` (see above)
(on-device tier). Record at 8 kHz so no resampling is needed. The guidance DB schema is
documented on `GuidanceStore`; it also stores the embedding of every templated
query, so the phone does not need a SentencePiece tokenizer.

## Still to do by the team

- Try `GeminiKeySetup` on a phone (only checked on the JVM so far); move it to a real settings screen.
- Real chunk ids in `FallbackChecklist` (currently placeholders).
- Dengue guidance pages (WHO, CDC, PAHO, PR Health; EN + ES) for `guidance.db`: today it holds 17 English
  malaria passages from the team's Indonesia review and one dengue passage (3M Plus).
- `Thresholds` picked on validation domains (defaults are placeholders).

This project is a prototype and is not medical advice.
