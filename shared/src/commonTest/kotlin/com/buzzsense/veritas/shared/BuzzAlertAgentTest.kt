package com.buzzsense.veritas.shared

import ai.koog.prompt.executor.clients.google.GoogleModels
import com.buzzsense.veritas.shared.agent.BuzzAlertEngine
import com.buzzsense.veritas.shared.audio.AudioClip
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class BuzzAlertAgentTest {

    private val model = GoogleModels.Gemini2_5Flash

    private fun engine(mosquito: Float, aedes: Float, vararg tiers: ProviderTier) = BuzzAlertEngine(
        detector = WingbeatDetector(FixedWingbeatModel(mosquito, aedes), DominantFrequencyBaseline(550f..700f)),
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
        assertTrue(llm.prompts.single().toString().contains("likely Aedes aegypti: YES"))
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

    @Test
    fun noProviderConfiguredStillGivesFixedChecklist() = runTest {
        val out = engine(0.9f, 0.8f).run(clip(), Language.ES)
        val ready = assertIs<AgentOutcome.AlertReady>(out)
        assertEquals(ProviderTierId.FIXED_FALLBACK, ready.trace.provider)
        assertEquals(Language.ES, ready.alert.language)
    }
}
