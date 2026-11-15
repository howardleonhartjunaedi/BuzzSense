# AGENTS.md (App Leaf)

Gradle needs a JDK: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (Git Bash) before any command.

## Commands
| Intent | Command | Notes |
|:---|:---|:---|
| Test shared module | `./gradlew :shared:jvmTest` | Fast; runs `commonTest` on the JVM, incl. full graph runs with scripted providers |
| Parity + real-store kNN | `JUMANTIK_RAG_DIR="O:/Veritas AI/Audio File/data/rag" SQLITE_VEC_LIB="O:/Veritas AI/Audio File/.venv/Lib/site-packages/sqlite_vec/vec0" ./gradlew :shared:jvmTest` | Without the env vars the real-store test is skipped |
| Live Gemini check | `GEMINI_API_KEY=<AI Studio key> ./gradlew :shared:jvmTest` | Runs `GeminiLiveTest` (key check + one full graph run); skipped without the env var. Never write the key to a file |
| Build guidance.db | `cd tools/guidance && .venv/Scripts/python.exe -I build_guidance_db.py` | Writes `app/src/main/assets/guidance.db`; setup and source format in `tools/guidance/README.md`. Then run `:shared:jvmTest` (`GuidanceDbParityTest`) |
| Bundle detector files | `cd "../Audio File" && .venv/Scripts/python.exe -I "../App Folder/pipeline/export_slim_store.py" --rag data/rag --out data/rag_slim`, then copy `data/rag_slim/jumantik_rag.sqlite` to `app/src/main/assets/` | Train split only, same neighbours as the full store; 127 MB, git-ignored. `BundledAssetsTest` (needs `SQLITE_VEC_LIB`) runs the shipped files on real clips |
| Rebuild `libvec0.so` | `tools/sqlite-vec/build_android.sh` | sqlite-vec 0.1.9 for 4 ABIs, 16 KB page-aligned, with NDK r28+ (`ANDROID_NDK_HOME` or the SDK's `ndk/`); keep the version equal to the one that built the stores |
| On-device bundle check | `./gradlew :app:connectedDebugAndroidTest` (device or emulator attached) | `BundledEngineTest`: `create()` loads the bundle and runs the graph on Android |
| Compile Android side | `./gradlew :shared:compileAndroidMain` | Catches LiteRT/Keystore/ONNX Android code |
| Build app | `./gradlew :app:assembleDebug` | APK in `app/build/outputs/apk/debug/` |
| Retrieval eval (eval 7) | Run `com.buzzsense.veritas.shared.eval.EvalHarnessKt` (jvmMain) with args `<artifactsDir> <queries.json> <results.jsonl>` | No Gradle run task yet; appends rows to the results table |

If Gradle dies with "paging file is too small", the machine is out of commit memory: close Android Studio's Gradle daemon or retry with `-Dorg.gradle.jvmargs=-Xmx1536m`.

## Koog (1.3.0) rules
- Verify every Koog API against the 1.3.0 sources before using it; docs and examples online are often 0.x. Pass `promptExecutor =` and set `maxAgentIterations` explicitly.
- Stable modules use version `1.3.0`; beta modules (Google client, LiteRT client) use `1.3.0-beta`. Keep both in `gradle/libs.versions.toml`.
- One `AIAgent` per detection run (`BuzzAlertEngine.run`); share the `ProviderChain`, detector and stores.
- The strategy graph is fixed: detect → context → retrieve → compose → validate. Detect, context and retrieve stay deterministic. The LLM runs only in `compose`, via `requestLLMStructured(AlertPrompt.structuredRequest)`, with `tools = emptyList()`.
- `run(useGuidance = false)` is the bare-LLM mode (Settings switch, eval 8 baseline): retrieve is skipped and only the citation check is dropped; schema, diagnosis, medicine and canary checks still run. Keep it that way.
- Keep structured output in Koog Manual mode (schema as text). Native mode is chosen from the session model (Gemini) and its `params.schema` reaches every tier: LiteRT ignores it and Claude/OpenAI get a Google-format schema. `onDeviceTierGetsFormatInstructionsAndItsJsonIsParsed` guards this.
- Every path must reach `nodeFinish`. No mosquito, Unsure or speech must end the graph before any LLM call.
- New tools are read-only Koog `Tool`s, called through `ToolGate`, and must be added to the node allowlist in `ToolPermissions`. Never add a share, message or write tool.
- Tier 1 is `CloudProviders.gemini` (default `GoogleModels.Gemini3_5FlashLite`; 2.5 models return 404 for new keys). Check a model with `GeminiLiveTest` before changing the default.
- Provider error text reaches traces and UI only through `FailureClassifier.redactSecrets` (Gemini puts the key in the URL).
- Provider errors go through `FailureClassifier`; a failed validation calls `rejectLastAnswer()` so the retry uses the next tier. All tiers failing ends in `FallbackChecklist`, never an exception to the UI.

## UI and design system (strict)
Every UI you create or change in `:app` must use the BuzzSense design system. Sources of truth: `DESIGN.md` and the Stitch project "Remix of BuzzSense Mosquito Detector" (id 9130902123267424033; the 13 screens the app ships). Code: tokens in `ui/theme/`, components in `ui/components/`, browsable in `ui/gallery/ComponentGallery.kt` (in the app: Credits → Design system).
- Read theme values only through `BuzzTheme.*` (`colorScheme`, `typography`, `shapes`, `spacing`, `colors`, `data`). Outside `ui/theme/`, never write `Color(0x…)`, raw `dp`/`sp` for spacing or type, or a `FontFamily`.
- Build screens only from `Buzz*` components. Do not use raw Material 3 `Button`, `Card`, `TopAppBar`, `NavigationBar`, `Checkbox`, `Switch`, `TextField`, `ModalBottomSheet` or `AlertDialog` in screens; their `Buzz*` wrappers exist.
- If a pattern is missing, first add a `Buzz*` component in `ui/components/` (stateless, `modifier: Modifier = Modifier` as the first optional parameter, slots for content), then use it in the screen. Do not style one-off composables inside a screen.
- Icons come from `BuzzIcons`; add new ones there as vector paths. No icon library.
- Keep the flat system: no shadows or tonal elevation, borders, divider lines, gradients or ripples (tap feedback is `Modifier.pressAlpha`). Status tags are square-cornered, never pills.
- Result colors (`BuzzColors.resultSafe/Watch/Hazard`) are used only through `ResultKind` and always with an icon and words, never color alone.
- User-facing text goes in `res/values/strings.xml`. Result and write-up screens show `BuzzDisclaimer`.
- Every new component gets `@BuzzPreviews` (light + dark) and a sample in `ComponentGallery`; every new screen gets `@BuzzScreenPreviews`.
- Ask before adding or changing a token (color, type style, shape, spacing). A token change updates `DESIGN.md` and `ui/theme/` together; spacing stays on 4dp multiples.

## local-constraints
- App wiring: `BuzzSenseApplication` → `AppContainer` (one engine, built off the main thread, rebuilt after `onKeysChanged()`) → `DetectViewModel` (flow state as `StateFlow`) → stateless `BuzzSenseApp`. Screens never touch the engine, recorder or Keystore; agent output reaches the UI only through `AgentOutcomeMapper`. Test flow changes in `app/src/test` with a fake `DetectionRunner`.
- `commonMain` must not use Android or JVM-only APIs. Android-only code goes in `androidMain`; ONNX code lives in `src/onnxShared/` and is compiled into both `androidMain` and `jvmMain`.
- Detector features come only from `FeatureSpec` (`feature_spec.json`); `AcousticFeatures` must keep passing `FeatureParityTest`. When the spec changes, copy the new spec and re-export `src/jvmTest/resources/parity_fixture.json` with `pipeline/export_parity_fixture.py`.
- API keys enter the app only through Settings (`SettingsViewModel`) → `KeystoreApiKeyStore`; on a laptop only through env vars. No key in code, Gradle files, tests or logs.
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
- LiteRT tier: never run on a phone yet, and no screen calls `OnDeviceLlm.install()` (Gemma file is 2.59 GB; `adb` route in `shared/README.md`). Tier 2 (Claude/OpenAI) has no key screen and no live test.
- Speech gate: rejects when >= 25% of the clip is speech-like (train/val mosquito clips max 18%, TTS speech min 44%). Not yet checked on real household speech or live buzz through a phone mic; each run logs its numbers to Logcat (tag `BuzzSense`, stage `speech-check`).
- `guidance.db` holds 56 English malaria chunks: the full Indonesia review (`sources/malaria_indonesia_review.md`, chunked by structure, coverage-checked) plus the 17 older curated passages. Only 3M Plus and the malaria-vs-dengue rows cover dengue. WHO/CDC/PAHO/PR Health dengue pages in English and Spanish are still to add. The review and the curated passages overlap, so near-duplicates can share a top 5.
- Retrieval: `DetectionResult.group` picks the query and prompt branch: Aedes (aegypti or albopictus; class3 does not split them), Culex (night bites, nets, screens, no malaria) or Anopheles/other (adds malaria terms and fever testing). The corpus has no Culex-specific page, so Culex queries retrieve general net/screen/repellent passages from malaria sources. A query change means rebuilding `guidance.db` (48 queries now). Tune retrieval with eval 7, not by editing passage text. `LiveGeminiBatchEvalTest` (androidTest, needs a saved key and the git-ignored `eval40_8k.f32`) runs 40 recordings through the whole chain.
- `FallbackChecklist` chunk ids are placeholders until the dengue pages are in `guidance.db`.
- UI: all 13 Stitch screens are built (splash, 3 walkthroughs, mic permission, Detect home, recording, recording ready, analyzing, results, what to do now, Learn, Credits); Settings is reached from Credits. The logo and Learn photos are Stitch-generated images in `res/drawable-nodpi/` (`brand_logo`, `learn_*`). The Analyzing rows follow a fixed order (the engine reports no progress). "Upload a file instead" on Mic Permission is not built. The Detect flow runs the real engine with the bundled files; a build without the git-ignored `jumantik_rag.sqlite` shows "Detector not installed" (`AndroidBuzzAlert.missingFiles`). Checked on an API 37 emulator (`BundledEngineTest`); not yet on a phone with a real mosquito. No review/share screen, "How it decided" panel or history yet.
