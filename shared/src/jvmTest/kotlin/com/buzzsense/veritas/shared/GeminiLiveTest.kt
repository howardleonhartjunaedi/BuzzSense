package com.buzzsense.veritas.shared

import com.buzzsense.veritas.shared.agent.BuzzAlertEngine
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.detector.AcousticFeatures
import com.buzzsense.veritas.shared.detector.DominantFrequencyBaseline
import com.buzzsense.veritas.shared.detector.WingbeatDetector
import com.buzzsense.veritas.shared.guardrails.CanaryToken
import com.buzzsense.veritas.shared.guardrails.HealthSafetyValidator
import com.buzzsense.veritas.shared.llm.CloudProviders
import com.buzzsense.veritas.shared.llm.KeyCheck
import com.buzzsense.veritas.shared.llm.ProviderChain
import com.buzzsense.veritas.shared.llm.check
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.ProviderTierId
import com.buzzsense.veritas.shared.rag.GuidanceRetriever
import com.buzzsense.veritas.shared.model.Verdict
import com.buzzsense.veritas.shared.rag.GuidanceDb
import com.buzzsense.veritas.shared.rag.HybridRetriever
import com.buzzsense.veritas.shared.rag.PrecomputedQueryEmbedder
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Live calls to the Gemini API (Google AI Studio key) through Koog's GoogleLLMClient.
 * Skipped unless GEMINI_API_KEY is set. Only synthetic labels, weather and the three
 * test guidance chunks are sent; no audio leaves the JVM.
 */
class GeminiLiveTest {

    private val key: String? = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }

    @Test
    fun aiStudioKeyAnswersThroughKoog() = runTest(timeout = 2.minutes) {
        val apiKey = key ?: return@runTest
        val result = CloudProviders.gemini(apiKey).check()
        assertIs<KeyCheck.Ok>(result, "Gemini key check failed: $result")
    }

    @Test
    fun geminiComposesAValidatedCitedAlert() = runTest(timeout = 2.minutes) {
        val apiKey = key ?: return@runTest
        val engine = BuzzAlertEngine(
            detector = WingbeatDetector(AcousticFeatures(TEST_SPEC), FixedClassifier(0.9f, 0.8f), DominantFrequencyBaseline(425f..809f)),
            weather = FixedWeather,
            retriever = GuidanceRetriever { CHUNKS },
            chunkLookup = { ids -> CHUNKS.filter { it.id in ids } },
            providers = ProviderChain(listOf(CloudProviders.gemini(apiKey))),
        )
        val out = engine.run(AudioClip(toneSamples(), 8_000), Language.EN)
        val ready = assertIs<AgentOutcome.AlertReady>(out, "expected an alert, got $out")
        assertEquals(ProviderTierId.GEMINI, ready.trace.provider, "fell back instead of answering: ${ready.trace}")
        assertTrue(HealthSafetyValidator().validate(ready.alert.draft, CHUNKS, CanaryToken()).isEmpty())
        println("Gemini draft: ${ready.alert.draft}")
    }

    /** Not Aedes → the malaria branch of the query, against the real guidance.db from tools/guidance. */
    @Test
    fun geminiWritesAMalariaChecklistFromTheRealCorpus() = runTest(timeout = 2.minutes) {
        val apiKey = key ?: return@runTest
        val db = File("../app/src/main/assets/guidance.db").takeIf { it.exists() } ?: return@runTest
        GuidanceDb.open(db.absolutePath).use { store ->
            val engine = BuzzAlertEngine(
                detector = WingbeatDetector(AcousticFeatures(TEST_SPEC), FixedClassifier(0.9f, 0.05f), DominantFrequencyBaseline(425f..809f)),
                weather = FixedWeather,
                retriever = HybridRetriever(store, PrecomputedQueryEmbedder(store)),
                chunkLookup = store::chunks,
                providers = ProviderChain(listOf(CloudProviders.gemini(apiKey))),
            )
            val ready = assertIs<AgentOutcome.AlertReady>(engine.run(AudioClip(toneSamples(), 8_000), Language.EN))
            assertEquals(Verdict.NO, ready.trace.detection?.aedesAegypti)
            assertEquals(ProviderTierId.GEMINI, ready.trace.provider, "fell back instead of answering: ${ready.trace.events}")
            assertTrue(ready.trace.retrieved.any { it.id == "cochrane-en-malaria-itn-01" }, "net guidance not retrieved")
            println("Malaria draft: ${ready.alert.draft}")
        }
    }
}
