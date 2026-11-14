# AGENTS.md (App Leaf)

Gradle needs a JDK: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (Git Bash) before any command.

## Commands
| Intent | Command | Notes |
|:---|:---|:---|
| Test shared module | `./gradlew :shared:jvmTest` | Fast; runs `commonTest` on the JVM, incl. full graph runs with scripted providers |
| Parity + real-store kNN | `JUMANTIK_RAG_DIR="O:/Veritas AI/Audio File/data/rag" SQLITE_VEC_LIB="O:/Veritas AI/Audio File/.venv/Lib/site-packages/sqlite_vec/vec0" ./gradlew :shared:jvmTest` | Without the env vars the real-store test is skipped |
| Live Gemini check | `GEMINI_API_KEY=<AI Studio key> ./gradlew :shared:jvmTest` | Runs `GeminiLiveTest` (key check + one full graph run); skipped without the env var. Never write the key to a file |
| Build guidance.db | `cd tools/guidance && .venv/Scripts/python.exe -I build_guidance_db.py` | Writes `app/src/main/assets/guidance.db`; setup and source format in `tools/guidance/README.md`. Then run `:shared:jvmTest` (`GuidanceDbParityTest`) |
| Compile Android side | `./gradlew :shared:compileAndroidMain` | Catches LiteRT/Keystore/ONNX Android code |
| Build app | `./gradlew :app:assembleDebug` | APK in `app/build/outputs/apk/debug/` |
| Retrieval eval (eval 7) | Run `com.buzzsense.veritas.shared.eval.EvalHarnessKt` (jvmMain) with args `<artifactsDir> <queries.json> <results.jsonl>` | No Gradle run task yet; appends rows to the results table |

If Gradle dies with "paging file is too small", the machine is out of commit memory: close Android Studio's Gradle daemon or retry with `-Dorg.gradle.jvmargs=-Xmx1536m`.

## Koog (1.3.0) rules
- Verify every Koog API against the 1.3.0 sources before using it; docs and examples online are often 0.x. Pass `promptExecutor =` and set `maxAgentIterations` explicitly.
- Stable modules use version `1.3.0`; beta modules (Google client, LiteRT client) use `1.3.0-beta`. Keep both in `gradle/libs.versions.toml`.
- One `AIAgent` per detection run (`BuzzAlertEngine.run`); share the `ProviderChain`, detector and stores.
- The strategy graph is fixed: detect → context → retrieve → compose → validate. Detect, context and retrieve stay deterministic. The LLM runs only in `compose`, via `requestLLMStructured<AlertDraft>()`, with `tools = emptyList()`.
- Every path must reach `nodeFinish`. No mosquito, Unsure or speech must end the graph before any LLM call.
- New tools are read-only Koog `Tool`s, called through `ToolGate`, and must be added to the node allowlist in `ToolPermissions`. Never add a share, message or write tool.
- Tier 1 is `CloudProviders.gemini` (default `GoogleModels.Gemini3_5FlashLite`; 2.5 models return 404 for new keys). Check a model with `GeminiLiveTest` before changing the default.
- Provider error text reaches traces and UI only through `FailureClassifier.redactSecrets` (Gemini puts the key in the URL).
- Provider errors go through `FailureClassifier`; a failed validation calls `rejectLastAnswer()` so the retry uses the next tier. All tiers failing ends in `FallbackChecklist`, never an exception to the UI.

## local-constraints
- `commonMain` must not use Android or JVM-only APIs. Android-only code goes in `androidMain`; ONNX code lives in `src/onnxShared/` and is compiled into both `androidMain` and `jvmMain`.
- Detector features come only from `FeatureSpec` (`feature_spec.json`); `AcousticFeatures` must keep passing `FeatureParityTest`. When the spec changes, copy the new spec and re-export `src/jvmTest/resources/parity_fixture.json` with `Audio File/pipeline/export_parity_fixture.py`.
- API keys enter the app only through `GeminiKeySetup` (`:app`) → `KeystoreApiKeyStore`; on a laptop only through env vars. No key in code, Gradle files, tests or logs.
- `app/src/main/AndroidManifest.xml` must keep `INTERNET` and `ACCESS_NETWORK_STATE`; without them every cloud tier fails and `AndroidNetworkStatus` cannot see connectivity.
- `AudioClip` stays in memory and is wiped in `finally`; never write audio to disk, logs or `DecisionTrace`.
- Guidance chunk ids are citations: never rename a shipped id. Corpus text carries no medicine, prophylaxis or dosing; `build_guidance_db.py` mirrors `HealthSafetyValidator.MEDICINE`, so change both together. A change to `GuidanceQueryBuilder` needs a rebuilt `guidance.db` (`GuidanceDbParityTest` fails otherwise).
- `GuidanceStore` uses the bundled SQLite driver: FTS5 is always there, sqlite-vec only when `addExtension` succeeds. Keep the brute-force fallback working.
- Add a `commonTest` case for every new guardrail, failover rule or graph edge. Detector tests go in `AcousticFeaturesTest`, not in `GuardrailsAndRetrievalTest`.
- Tests that call a real provider live in `jvmTest`, read the key from an env var and return early without it (see `GeminiLiveTest`).
- Commit on the current branch (`master`); do not create branches unless asked.
- Do not commit `MainActivity.kt` demo edits, `build/`, `.kotlin/` or per-user `.idea` files.

## Known gaps (update when fixed)
- `Thresholds` defaults are placeholders until picked on validation domains.
- The full acoustic store (~350 MB) is too large for an APK; a train-split slim store is not built yet.
- `guidance.db` holds 17 English malaria passages (Indonesia review); only one (3M Plus) covers dengue. WHO/CDC/PAHO/PR Health dengue pages in English and Spanish are still to add.
- Retrieval: when the mosquito is not Aedes, `GuidanceQueryBuilder` adds malaria terms (nets, night, fever test); the fever-test passage still misses the top 5 for those queries. Tune it with eval 7, not by editing passage text.
- `FallbackChecklist` chunk ids are placeholders until the dengue pages are in `guidance.db`.
