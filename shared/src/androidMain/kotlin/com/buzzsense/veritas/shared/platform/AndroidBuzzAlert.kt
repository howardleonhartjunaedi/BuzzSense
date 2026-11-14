package com.buzzsense.veritas.shared.platform

import android.content.Context
import com.buzzsense.veritas.shared.agent.BuzzAlertEngine
import com.buzzsense.veritas.shared.context.ForecastRiskSource
import com.buzzsense.veritas.shared.context.NoForecast
import com.buzzsense.veritas.shared.context.OpenMeteoWeather
import com.buzzsense.veritas.shared.detector.AcousticFeatures
import com.buzzsense.veritas.shared.detector.AcousticStore
import com.buzzsense.veritas.shared.detector.DominantFrequencyBaseline
import com.buzzsense.veritas.shared.detector.FeatureSpec
import com.buzzsense.veritas.shared.detector.KnnClassifier
import com.buzzsense.veritas.shared.detector.WingbeatDetector
import com.buzzsense.veritas.shared.guardrails.SpeechGate
import com.buzzsense.veritas.shared.llm.buildProviderChain
import com.buzzsense.veritas.shared.onnx.SileroVad
import com.buzzsense.veritas.shared.rag.GuidanceDb
import com.buzzsense.veritas.shared.rag.HybridRetriever
import com.buzzsense.veritas.shared.rag.PrecomputedQueryEmbedder
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Wires the shared engine for the Android app. Expected files in the app's
 * `src/main/assets/`:
 *
 * - `feature_spec.json`      — copied from `Audio File/data/rag/` (must match the store)
 * - `jumantik_rag.sqlite`    — acoustic store from `build_rag.py` (kNN detector)
 * - `guidance.db`            — prevention-guidance corpus (FTS5 + vectors + query vectors)
 * - `silero_vad.onnx`        — speech rejection
 *
 * Required: `libvec0.so` (sqlite-vec) in the app's `src/main/jniLibs/<abi>/`; the
 * acoustic store cannot be searched without it. Optional: a `.litertlm` model in
 * `filesDir/llm/` for the on-device tier.
 */
object AndroidBuzzAlert {

    fun create(
        context: Context,
        /** f0 band fitted on the train split (eval_retrieval.py: 10th-90th percentile of Aedes f0). */
        baselineBandHz: ClosedFloatingPointRange<Float> = 425f..809f,
        risk: ForecastRiskSource = NoForecast,
    ): BuzzAlertEngine {
        val app = context.applicationContext

        val vecLib = File(app.applicationInfo.nativeLibraryDir, "libvec0.so").takeIf { it.exists() }?.absolutePath
            ?: error("libvec0.so (sqlite-vec) missing from jniLibs: the acoustic store needs it")
        val store = GuidanceDb.open(copyAsset(app, "guidance.db").absolutePath, vecLib)
        val spec = FeatureSpec.fromJson(app.assets.open("feature_spec.json").use { it.readBytes().decodeToString() })
        val acoustic = AcousticStore.open(copyAsset(app, "jumantik_rag.sqlite").absolutePath, vecLib)

        val http = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val providers = buildProviderChain(
            keys = KeystoreApiKeyStore(app).providerKeys(),
            onDevice = OnDeviceLlm.tier(app),
            network = AndroidNetworkStatus(app),
        )

        return BuzzAlertEngine(
            detector = WingbeatDetector(
                features = AcousticFeatures(spec),
                classifier = KnnClassifier(acoustic),
                baseline = DominantFrequencyBaseline(baselineBandHz),
            ),
            weather = OpenMeteoWeather(http),
            retriever = HybridRetriever(store, PrecomputedQueryEmbedder(store)),
            chunkLookup = store::chunks,
            providers = providers,
            speechGate = SpeechGate(SileroVad(asset(app, "silero_vad.onnx"))),
            risk = risk,
        )
    }

    private fun asset(context: Context, name: String): ByteArray = context.assets.open(name).use { it.readBytes() }

    /** SQLite needs a real file path; copy the read-only DB out of the APK once. */
    private fun copyAsset(context: Context, name: String): File {
        val out = File(context.noBackupFilesDir, name)
        if (!out.exists()) context.assets.open(name).use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }
}
