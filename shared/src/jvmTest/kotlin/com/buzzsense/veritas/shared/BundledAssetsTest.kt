package com.buzzsense.veritas.shared

import com.buzzsense.veritas.shared.agent.BuzzAlertEngine
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.detector.AcousticFeatures
import com.buzzsense.veritas.shared.detector.AcousticStore
import com.buzzsense.veritas.shared.detector.DominantFrequencyBaseline
import com.buzzsense.veritas.shared.detector.FeatureSpec
import com.buzzsense.veritas.shared.detector.KnnClassifier
import com.buzzsense.veritas.shared.detector.WingbeatDetector
import com.buzzsense.veritas.shared.guardrails.SpeechGate
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.onnx.SileroVad
import com.buzzsense.veritas.shared.rag.GuidanceDb
import com.buzzsense.veritas.shared.rag.HybridRetriever
import com.buzzsense.veritas.shared.rag.PrecomputedQueryEmbedder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The files the app ships, loaded from app/src/main/assets the way AndroidBuzzAlert loads
 * them, run on the real dataset clips in parity_fixture.json. Needs SQLITE_VEC_LIB and the
 * slim jumantik_rag.sqlite (git-ignored; see Audio File/pipeline/export_slim_store.py).
 */
class BundledAssetsTest {

    private val assets = File("../app/src/main/assets")
    private val vecLib: String? = System.getenv("SQLITE_VEC_LIB")

    private fun ready() = vecLib != null && REQUIRED.all { File(assets, it).isFile }

    /** Each fixture case is one 0.62 s window; repeat it to a 2.5 s clip like a short recording. */
    private fun cases(): List<Pair<String, FloatArray>> {
        val root = Json.parseToJsonElement(javaClass.getResource("/parity_fixture.json")!!.readText()).jsonObject
        return root["cases"]!!.jsonArray.map { c ->
            val o = c.jsonObject
            val window = o["samples"]!!.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
            o["class3"]!!.jsonPrimitive.content to FloatArray(window.size * 4) { window[it % window.size] }
        }
    }

    @Test
    fun sileroDoesNotMistakeMosquitoAudioForSpeech() = runTest(timeout = 2.minutes) {
        if (!ready()) return@runTest
        SileroVad(File(assets, "silero_vad.onnx").readBytes()).use { vad ->
            for ((label, samples) in cases()) {
                val p = vad.speechProbability(samples, 8_000)
                println("silero speech probability for $label: $p")
                assertTrue(p < 0.5f, "$label clip would be deleted as speech (p=$p)")
            }
            assertTrue(vad.speechProbability(FloatArray(16_000), 8_000) < 0.5f, "silence flagged as speech")
        }
    }

    @Test
    fun bundledEngineRunsEveryFixtureClipThroughTheDetector() = runTest(timeout = 2.minutes) {
        if (!ready()) return@runTest
        val spec = FeatureSpec.fromJson(File(assets, "feature_spec.json").readText())
        val acoustic = AcousticStore.open(File(assets, "jumantik_rag.sqlite").absolutePath, vecLib!!)
        val guidance = GuidanceDb.open(File(assets, "guidance.db").absolutePath, vecLib)
        SileroVad(File(assets, "silero_vad.onnx").readBytes()).use { vad ->
            val engine = BuzzAlertEngine(
                detector = WingbeatDetector(AcousticFeatures(spec), KnnClassifier(acoustic), DominantFrequencyBaseline(425f..809f)),
                weather = FixedWeather,
                retriever = HybridRetriever(guidance, PrecomputedQueryEmbedder(guidance)),
                chunkLookup = guidance::chunks,
                providers = null, // no LLM: alerts take the fixed checklist
                speechGate = SpeechGate(vad),
            )
            for ((label, samples) in cases()) {
                val out = engine.run(AudioClip(samples, 8_000), Language.EN)
                val d = assertNotNull(out.trace.detection, "$label: no detection (speech gate fired?)")
                val kind = if (out is AgentOutcome.AlertReady) "alert" else "local message"
                println("$label -> mosquito ${d.mosquito}, aedes ${d.aedesAegypti} (votes ${d.classVotes}) -> $kind")
            }
        }
    }

    private companion object {
        val REQUIRED = listOf("feature_spec.json", "jumantik_rag.sqlite", "guidance.db", "silero_vad.onnx")
    }
}
