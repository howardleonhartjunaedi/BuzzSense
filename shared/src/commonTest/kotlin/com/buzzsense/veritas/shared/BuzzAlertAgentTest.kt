package com.buzzsense.veritas.shared

import ai.koog.prompt.executor.clients.google.GoogleModels
import com.buzzsense.veritas.shared.agent.BuzzAlertEngine
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.detector.AcousticFeatures
import com.buzzsense.veritas.shared.detector.DominantFrequencyBaseline
import com.buzzsense.veritas.shared.detector.WingbeatDetector
import com.buzzsense.veritas.shared.llm.FailoverPolicy
import com.buzzsense.veritas.shared.llm.ProviderChain
import com.buzzsense.veritas.shared.llm.ProviderTier
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.DISCLAIMER_EN
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.ProviderTierId
import com.buzzsense.veritas.shared.model.Verdict
import com.buzzsense.veritas.shared.rag.GuidanceRetriever
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class BuzzAlertAgentTest {

    private val model = GoogleModels.Gemini3_5FlashLite

    private fun engine(mosquito: Float, aedes: Float, vararg tiers: ProviderTier) = BuzzAlertEngine(
        detector = WingbeatDetector(AcousticFeatures(TEST_SPEC), FixedClassifier(mosquito, aedes), DominantFrequencyBaseline(425f..809f)),
        weather = FixedWeather,
        retriever = GuidanceRetriever { CHUNKS },
        chunkLookup = { ids -> CHUNKS.filter { it.id in ids } },
        providers = if (tiers.isEmpty()) null else ProviderChain(tiers.toList(), policy = FailoverPolicy(retryBaseDelay = Duration.ZERO)),
    )

    private fun clip() = AudioClip(toneSamples(), 8_000)

    @Test
    fun noMosquitoStopsBeforeAnyLlmCall() = runTest {
        val llm = ScriptedExecutor({ goodDraftJson() })
        val c = clip()
        val out = engine(0.1f, 0.9f, ProviderTier(ProviderTierId.GEMINI, llm, model)).run(c, Language.EN)
        assertIs<AgentOutcome.LocalMessage>(out)
        assertTrue(llm.prompts.isEmpty())
        assertTrue(c.isWiped, "audio must not outlive the run")
    }

    @Test
    fun aedesDetectionProducesCitedAlertWithDisclaimer() = runTest {
        val llm = ScriptedExecutor({ goodDraftJson() })
        val out = engine(0.9f, 0.8f, ProviderTier(ProviderTierId.GEMINI, llm, model)).run(clip(), Language.EN)
        val ready = assertIs<AgentOutcome.AlertReady>(out)
        assertEquals(Verdict.YES, ready.trace.detection?.aedesAegypti)
        assertEquals(ProviderTierId.GEMINI, ready.trace.provider)
        assertEquals(DISCLAIMER_EN, ready.alert.disclaimer)
        assertTrue(ready.alert.draft.actions.all { it.chunkId in ready.alert.citations })
        // Audio never reaches the model: only labels, weather and fenced guidance.
        val prompt = llm.prompts.single().toString()
        assertTrue(prompt.contains("likely Aedes (aegypti or albopictus): YES"), prompt)
        assertTrue(prompt.contains("mosquito group: Aedes"), prompt)
    }

    @Test
    fun primaryFailureFailsOverToSecondProvider() = runTest {
        val primary = ScriptedExecutor({ error("Status code: 401 bad key") })
        val second = ScriptedExecutor({ goodDraftJson() })
        val out = engine(
            0.9f, 0.8f,
            ProviderTier(ProviderTierId.GEMINI, primary, model),
            ProviderTier(ProviderTierId.SECOND_API, second, model),
        ).run(clip(), Language.EN)
        assertEquals(ProviderTierId.SECOND_API, assertIs<AgentOutcome.AlertReady>(out).trace.provider)
    }

    @Test
    fun unsafeAnswerIsRetriedOnNextProviderThenFallsBack() = runTest {
        val unsafe = goodDraftJson().replace("Use an EPA-registered repellent.", "Give ibuprofen 200 mg.")
        val primary = ScriptedExecutor({ unsafe })
        val second = ScriptedExecutor({ unsafe })
        val out = engine(
            0.9f, 0.8f,
            ProviderTier(ProviderTierId.GEMINI, primary, model),
            ProviderTier(ProviderTierId.SECOND_API, second, model),
        ).run(clip(), Language.EN)
        val ready = assertIs<AgentOutcome.AlertReady>(out)
        assertEquals(1, primary.prompts.size)
        assertEquals(1, second.prompts.size)
        assertEquals(ProviderTierId.FIXED_FALLBACK, ready.trace.provider)
    }

    /**
     * The on-device LiteRT tier ignores a native response schema, so the request must carry
     * the format as text. Here Gemini is rejected (401) and a Gemma-like model answers with
     * fenced JSON, as small local models tend to.
     */
    @Test
    fun onDeviceTierGetsFormatInstructionsAndItsJsonIsParsed() = runTest {
        val gemini = ScriptedExecutor({ error("Status code: 401 API key not valid") })
        val gemma = ScriptedExecutor({ "```json\n" + goodDraftJson() + "\n```" })
        val out = engine(
            0.9f, 0.8f,
            ProviderTier(ProviderTierId.GEMINI, gemini, model),
            ProviderTier(ProviderTierId.ON_DEVICE, gemma, ON_DEVICE_MODEL, requiresNetwork = false),
        ).run(clip(), Language.EN)
        val ready = assertIs<AgentOutcome.AlertReady>(out, "expected an alert, got $out")
        assertEquals(ProviderTierId.ON_DEVICE, ready.trace.provider)
        val sent = gemma.prompts.single()
        assertEquals(null, sent.params.schema, "LiteRT ignores params.schema; the format must be in the messages")
        assertTrue(sent.toString().contains("chunk_id"), "format instructions missing from the on-device prompt")
    }

    @Test
    fun bareModeSkipsRetrievalAndAcceptsUncitedActions() = runTest {
        val llm = ScriptedExecutor({ goodDraftJson(listOf("", "", "")) })
        val bare = BuzzAlertEngine(
            detector = WingbeatDetector(AcousticFeatures(TEST_SPEC), FixedClassifier(0.9f, 0.8f), DominantFrequencyBaseline(425f..809f)),
            weather = FixedWeather,
            retriever = GuidanceRetriever { error("retrieval must not run when guidance is off") },
            chunkLookup = { emptyList() },
            providers = ProviderChain(listOf(ProviderTier(ProviderTierId.GEMINI, llm, model)), policy = FailoverPolicy(retryBaseDelay = Duration.ZERO)),
        )
        val ready = assertIs<AgentOutcome.AlertReady>(bare.run(clip(), Language.EN, useGuidance = false))
        assertEquals(ProviderTierId.GEMINI, ready.trace.provider)
        assertTrue(ready.alert.citations.isEmpty())
        assertTrue(ready.trace.events.any { it.stage == "retrieve" && it.message.startsWith("skipped") })
        val sent = llm.prompts.single().toString()
        assertFalse(sent.contains("Guidance documents:"), "no documents are sent in bare mode")
        assertEquals(DISCLAIMER_EN, ready.alert.disclaimer)
    }

    @Test
    fun noProviderConfiguredStillGivesFixedChecklist() = runTest {
        val out = engine(0.9f, 0.8f).run(clip(), Language.ES)
        val ready = assertIs<AgentOutcome.AlertReady>(out)
        assertEquals(ProviderTierId.FIXED_FALLBACK, ready.trace.provider)
        assertEquals(Language.ES, ready.alert.language)
    }
}
