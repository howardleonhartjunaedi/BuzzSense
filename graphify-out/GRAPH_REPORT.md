# Graph Report - App Folder  (2026-11-14)

## Corpus Check
- 59 files · ~54,634 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 19 file(s) not represented in the graph (top: .xml 10, .properties 3, (none) 2)

## Summary
- 588 nodes · 1299 edges · 23 communities (14 shown, 9 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 34 edges (avg confidence: 0.87)
- Token cost: 124,006 input · 0 output

## Community Hubs (Navigation)
- Koog Strategy Graph
- LLM Provider Clients
- Android & JVM Wiring
- Feature Math & Tests
- Agent Rules (AGENTS.md)
- Acoustic kNN Store
- Agent Engine & Context
- Agent Graph Tests
- Compose UI Shell
- Provider Failover
- ONNX Speech & Embeddings
- Keystore API Keys
- Output Safety Guardrails
- Alarm Fatigue Guard
- Instrumented Test Stub
- Unit Test Stub

## God Nodes (most connected - your core abstractions)
1. `buzzAlertStrategy()` - 29 edges
2. `GuidanceStore` - 28 edges
3. `AcousticFeatures` - 26 edges
4. `GuidanceChunk` - 23 edges
5. `Language` - 22 edges
6. `BuzzAlertEngine` - 20 edges
7. `ProviderChain` - 20 edges
8. `WingbeatDetector` - 18 edges
9. `RiskLevel` - 18 edges
10. `AlertDraft` - 18 edges

## Surprising Connections (you probably didn't know these)
- `One AIAgent per detection run` --rationale_for--> `BuzzAlertEngine`  [EXTRACTED]
  AGENTS.md → shared/src/commonMain/kotlin/com/buzzsense/veritas/shared/agent/BuzzAlertEngine.kt
- `AudioClip in-memory only, wiped in finally` --references--> `DecisionTrace`  [EXTRACTED]
  AGENTS.md → shared/src/commonMain/kotlin/com/buzzsense/veritas/shared/model/Models.kt
- `GuidanceStore bundled SQLite: FTS5 always, sqlite-vec optional, brute-force fallback` --rationale_for--> `GuidanceStore`  [EXTRACTED]
  AGENTS.md → shared/src/commonMain/kotlin/com/buzzsense/veritas/shared/rag/GuidanceStore.kt
- `Fixed strategy graph: detect -> context -> retrieve -> compose -> validate` --rationale_for--> `buzzAlertStrategy()`  [INFERRED]
  AGENTS.md → shared/src/commonMain/kotlin/com/buzzsense/veritas/shared/agent/BuzzAlertStrategy.kt
- `FallbackChecklist placeholder chunk ids; no guidance.db builder` --references--> `FallbackChecklist`  [EXTRACTED]
  AGENTS.md → shared/src/commonMain/kotlin/com/buzzsense/veritas/shared/agent/FallbackChecklist.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **BuzzAlert detect-context-retrieve-compose-validate pipeline** — shared_readme_two_stage_wingbeat_detector, shared_readme_open_meteo_context, shared_readme_on_device_rag, shared_src_commonmain_kotlin_com_buzzsense_veritas_shared_model_models_alertdraft, shared_readme_guardrails, shared_src_commonmain_kotlin_com_buzzsense_veritas_shared_agent_fallbackchecklist_fallbackchecklist [INFERRED 0.85]
- **Provider failover mechanism** — shared_readme_provider_chain_tiers, shared_src_commonmain_kotlin_com_buzzsense_veritas_shared_llm_failureclassifier_failureclassifier, shared_readme_circuit_breaker, shared_readme_failover_table, shared_src_commonmain_kotlin_com_buzzsense_veritas_shared_agent_fallbackchecklist_fallbackchecklist [INFERRED 0.85]
- **Python-Kotlin acoustic feature parity** — agents_feature_parity_rule, shared_src_commonmain_kotlin_com_buzzsense_veritas_shared_detector_acousticfeatures_featurespec, shared_src_commonmain_kotlin_com_buzzsense_veritas_shared_detector_acousticfeatures_acousticfeatures, agents_real_store_knn_parity_test [INFERRED 0.85]

## Communities (23 total, 9 thin omitted)

### Community 0 - "Koog Strategy Graph"
Cohesion: 0.05
Nodes (58): Strategy graph (detect/local_message/context/retrieve/compose/validate/fallback), AlertPrompt, AlertRequest, buzzAlertStrategy(), Checked, ComposeInput, ComposeResult, ContextBundle (+50 more)

### Community 1 - "LLM Provider Clients"
Cohesion: 0.05
Nodes (14): AndroidNetworkStatus, OnDeviceLlm, buildProviderChain(), CloudProviders, FailoverPolicy, FailoverPromptExecutor, NetworkStatus, ProviderChain (+6 more)

### Community 2 - "Android & JVM Wiring"
Cohesion: 0.06
Nodes (22): AndroidBuzzAlert, OpenMeteoWeather, DominantFrequencyBaseline, Class3, KnnClassifier, Thresholds, WindowClassifier, WingbeatDetector (+14 more)

### Community 3 - "Feature Math & Tests"
Cohesion: 0.08
Nodes (5): AcousticFeatures, FeatureSpec, Case, FeatureParityTest, Fixture

### Community 4 - "Agent Rules (AGENTS.md)"
Cohesion: 0.06
Nodes (28): AGENTS.md (App Leaf) agent guide, Commit conventions (master branch, no MainActivity demo edits/build dirs), Gradle build/test commands, FallbackChecklist placeholder chunk ids; no guidance.db builder, Known gaps, local-constraints, Placeholder Thresholds defaults, Parity + real-store kNN test (JUMANTIK_RAG_DIR, SQLITE_VEC_LIB) (+20 more)

### Community 5 - "Acoustic kNN Store"
Cohesion: 0.10
Nodes (5): AcousticStore, Neighbour, GuidanceStore, toFloatArrayLittleEndian(), toLittleEndianBytes()

### Community 6 - "Agent Engine & Context"
Cohesion: 0.10
Nodes (9): BuzzAlertEngine, NoProviderExecutor, Current, ForecastRiskSource, OpenMeteoResponse, ReplayForecastRisk, WeatherSource, AllProvidersFailedException (+1 more)

### Community 7 - "Agent Graph Tests"
Cohesion: 0.12
Nodes (7): BuzzAlertAgentTest, FixedClassifier, FixedWeather, goodDraftJson(), harmonicSamples(), ScriptedExecutor, toneSamples()

### Community 8 - "Compose UI Shell"
Cohesion: 0.10
Nodes (4): Greeting(), GreetingPreview(), MainActivity, BuzzsenseTheme()

### Community 9 - "Provider Failover"
Cohesion: 0.13
Nodes (10): Circuit breaker (3 consecutive failures, 60 s skip), Failover behaviour table, Provider chain tiers: Gemini -> Claude/OpenAI -> LiteRT, FailureClassifier, FailureKind, AUTH, BAD_REQUEST, NO_NETWORK (+2 more)

### Community 10 - "ONNX Speech & Embeddings"
Cohesion: 0.12
Nodes (7): SpeechGate, VoiceActivityDetector, Embedder, OnnxE5Embedder, resampleLinear(), session(), SileroVad

### Community 11 - "Keystore API Keys"
Cohesion: 0.14
Nodes (5): KeystoreApiKeyStore, Provider, ANTHROPIC, GEMINI, OPENAI

### Community 12 - "Output Safety Guardrails"
Cohesion: 0.17
Nodes (8): Diagnosis, HealthSafetyValidator, Medicine, PromptLeak, Schema, UnknownCitation, ValidationIssue, CanaryToken

### Community 13 - "Alarm Fatigue Guard"
Cohesion: 0.18
Nodes (4): AlarmFatigueGuard, InMemoryShareLedger, ShareAdvice, ShareLedger

## Knowledge Gaps
- **33 isolated node(s):** `GEMINI`, `ANTHROPIC`, `OPENAI`, `SpeechRejected`, `TRANSIENT` (+28 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 159 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AcousticFeatures` connect `Feature Math & Tests` to `Provider Failover`, `Android & JVM Wiring`, `Agent Rules (AGENTS.md)`, `Agent Graph Tests`?**
  _High betweenness centrality (0.108) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `buzzAlertStrategy()` (e.g. with `Fixed strategy graph: detect -> context -> retrieve -> compose -> validate` and `Strategy graph (detect/local_message/context/retrieve/compose/validate/fallback)`) actually correct?**
  _`buzzAlertStrategy()` has 6 INFERRED edges - model-reasoned connections that need verification._
- **What connects `GEMINI`, `ANTHROPIC`, `OPENAI` to the rest of the system?**
  _33 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Koog Strategy Graph` be split into smaller, more focused modules?**
  _Cohesion score 0.05297876726448155 - nodes in this community are weakly interconnected._
- **Why does `GuidanceStore` connect `Acoustic kNN Store` to `Android & JVM Wiring`, `Feature Math & Tests`, `Agent Rules (AGENTS.md)`, `Agent Engine & Context`?**
  _High betweenness centrality (0.075) - this node is a cross-community bridge._
- **Should `LLM Provider Clients` be split into smaller, more focused modules?**
  _Cohesion score 0.05300207039337474 - nodes in this community are weakly interconnected._
- **Why does `BuzzAlertEngine` connect `Agent Engine & Context` to `Koog Strategy Graph`, `LLM Provider Clients`, `Android & JVM Wiring`, `Feature Math & Tests`, `Agent Rules (AGENTS.md)`, `Agent Graph Tests`, `ONNX Speech & Embeddings`, `Output Safety Guardrails`?**
  _High betweenness centrality (0.065) - this node is a cross-community bridge._