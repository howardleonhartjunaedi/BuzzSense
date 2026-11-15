package com.buzzsense.veritas.app

import com.buzzsense.veritas.app.agent.EngineUnavailableException
import com.buzzsense.veritas.app.ui.components.ResultKind
import com.buzzsense.veritas.app.ui.detect.AgentOutcomeMapper
import com.buzzsense.veritas.app.ui.detect.DetectStep
import com.buzzsense.veritas.app.ui.detect.DetectViewModel
import com.buzzsense.veritas.app.ui.detect.DetectionRunner
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.Alert
import com.buzzsense.veritas.shared.model.AlertAction
import com.buzzsense.veritas.shared.model.AlertDraft
import com.buzzsense.veritas.shared.model.DISCLAIMER_EN
import com.buzzsense.veritas.shared.model.DecisionTrace
import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.ProviderTierId
import com.buzzsense.veritas.shared.model.Urgency
import com.buzzsense.veritas.shared.model.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun detection(mosquito: Verdict, aedes: Verdict, aedesScore: Float? = 0.8f) = DetectionResult(
    mosquito = mosquito, aedesAegypti = aedes, mosquitoScore = 0.9f, aedesScore = aedesScore,
    dominantFrequencyHz = 600f, baselineAedes = Verdict.NO, windowCount = 8, mosquitoWindowCount = 8,
)

private val chunk = GuidanceChunk("kemenkes-en-dengue-3m-plus-01", "Kemenkes", "https://example.org/3m", "3M Plus", Language.EN, "Drain containers.")

private fun alertReady(kind: Verdict = Verdict.YES) = AgentOutcome.AlertReady(
    Alert(
        draft = AlertDraft(
            "An Aedes aegypti mosquito was heard indoors.",
            listOf(
                AlertAction("Empty and scrub water containers.", chunk.id),
                AlertAction("Recycle old tyres that hold rain.", chunk.id),
                AlertAction("Get a test at the clinic if anyone has a fever.", "not-retrieved"),
            ),
            Urgency.HIGH,
        ),
        citations = mapOf(chunk.id to chunk),
        disclaimer = DISCLAIMER_EN,
        language = Language.EN,
    ),
    DecisionTrace(detection = detection(Verdict.YES, kind), provider = ProviderTierId.GEMINI),
)

class AgentOutcomeMapperTest {

    @Test
    fun alertBecomesCitedChecklistRows() {
        val ui = AgentOutcomeMapper.map(alertReady())
        assertEquals(ResultKind.LikelyAedes, ui.kind)
        assertEquals(0.8f, ui.confidence!!, 1e-6f)
        assertEquals(600f, ui.peakHz!!, 0f)
        val alert = ui.alert!!
        assertEquals(ProviderTierId.GEMINI, alert.provider)
        assertEquals("https://example.org/3m", alert.officialUrl)
        // Same chunk cited twice still gives distinct row ids.
        assertEquals(3, alert.entries.map { it.id }.toSet().size)
        assertEquals("Kemenkes · 3M Plus", alert.entries[0].source)
        assertNull("an uncited row shows no source", alert.entries[2].source)
        assertEquals(listOf(BuzzIcons.WaterDrop, BuzzIcons.Recycling, BuzzIcons.MedicalServices), alert.entries.map { it.icon })
    }

    @Test
    fun bareModeAlertIsMarkedUncited() {
        val bare = alertReady().let { it.copy(alert = it.alert.copy(citations = emptyMap())) }
        val alert = AgentOutcomeMapper.map(bare).alert!!
        assertEquals(false, alert.cited)
        assertNull(alert.officialUrl)
        assertTrue(alert.entries.all { it.source == null })
    }

    @Test
    fun localMessagesMapToResultsWithoutChecklist() {
        val noMosquito = AgentOutcome.LocalMessage("No mosquito heard.", DecisionTrace(detection = detection(Verdict.NO, Verdict.NO)))
        val unsure = AgentOutcome.LocalMessage("Not sure.", DecisionTrace(detection = detection(Verdict.YES, Verdict.UNSURE)))
        val speech = AgentOutcome.LocalMessage("We heard voices.", DecisionTrace(detection = null))
        assertEquals(ResultKind.NoMosquito, AgentOutcomeMapper.map(noMosquito).kind)
        assertEquals(ResultKind.NotSure, AgentOutcomeMapper.map(unsure).kind)
        assertEquals(ResultKind.VoiceHeard, AgentOutcomeMapper.map(speech).kind)
        assertTrue(listOf(noMosquito, unsure, speech).all { AgentOutcomeMapper.map(it).alert == null })
        // No wingbeat heard (or unsure): no peak is shown.
        assertTrue(listOf(noMosquito, unsure, speech).all { AgentOutcomeMapper.map(it).peakHz == null })
    }

    @Test
    fun notAedesShowsNoConfidence() {
        // A low Aedes vote (0.09 on an unfamiliar Tanzania recording) must not appear as "91% sure".
        val ui = AgentOutcomeMapper.map(
            AgentOutcome.LocalMessage("x", DecisionTrace(detection = detection(Verdict.YES, Verdict.NO, aedesScore = 0.09f))),
        )
        assertEquals(ResultKind.NotDengue, ui.kind)
        assertNull(ui.confidence)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetectViewModelTest {

    private class FakeRunner(
        private val outcome: () -> AgentOutcome = { alertReady() },
        private val recordFails: Boolean = false,
    ) : DetectionRunner {
        var lastClip: AudioClip? = null
        val clips = mutableListOf<AudioClip>()
        var played = 0
        override suspend fun record(seconds: Int, onProgress: (Float) -> Unit): AudioClip {
            if (recordFails) error("microphone is busy")
            onProgress(1f)
            return AudioClip(FloatArray(8_000 * seconds) { 0.1f }, 8_000).also { lastClip = it; clips += it }
        }
        override fun stopRecording() = Unit
        override suspend fun analyze(clip: AudioClip, language: Language): AgentOutcome = outcome()
        override suspend fun play(clip: AudioClip) {
            check(!clip.isWiped) { "played a wiped clip" }
            played++
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun recordThenAnalyzeShowsTheAgentsResult() = runTest {
        val runner = FakeRunner()
        val vm = DetectViewModel(runner, Language.EN)
        vm.onRecord()
        // Recording ends on the Review step; the clip waits in memory until the user decides.
        val review = vm.state.value.step as DetectStep.Review
        assertEquals(5f, review.durationSeconds, 0f)
        assertEquals(8_000, review.sampleRateHz)
        assertFalse(runner.lastClip!!.isWiped)
        vm.onAnalyze()
        val step = vm.state.value.step as DetectStep.Result
        assertEquals(ResultKind.LikelyAedes, step.detection.kind)
        assertEquals(3, step.detection.alert!!.entries.size)
        assertEquals(1f, vm.recordingProgress.value, 0f)
        assertTrue("audio must not outlive the run", runner.lastClip!!.isWiped)
        // "Record again" goes back to the Detect home screen; it does not start recording by itself.
        vm.onBackHome()
        assertEquals(DetectStep.Home, vm.state.value.step)
    }

    @Test
    fun missingAssetsShowUnavailableAndStillWipeTheClip() = runTest {
        val runner = FakeRunner(outcome = { throw EngineUnavailableException(listOf("assets/silero_vad.onnx")) })
        val vm = DetectViewModel(runner, Language.EN)
        vm.onRecord()
        vm.onAnalyze()
        assertEquals(DetectStep.Unavailable(listOf("assets/silero_vad.onnx")), vm.state.value.step)
        assertTrue(runner.lastClip!!.isWiped)
        vm.onBackHome()
        assertEquals(DetectStep.Home, vm.state.value.step)
    }

    @Test
    fun recordAgainOnReviewWipesTheFirstClip() = runTest {
        val runner = FakeRunner()
        val vm = DetectViewModel(runner, Language.EN)
        vm.onRecord()
        vm.onRecordAgain()
        assertEquals(2, runner.clips.size)
        assertTrue("the discarded clip is wiped", runner.clips[0].isWiped)
        assertFalse(runner.clips[1].isWiped)
        assertTrue(vm.state.value.step is DetectStep.Review)
    }

    @Test
    fun backFromReviewWipesTheClip() = runTest {
        val runner = FakeRunner()
        val vm = DetectViewModel(runner, Language.EN)
        vm.onRecord()
        vm.onBackHome()
        assertEquals(DetectStep.Home, vm.state.value.step)
        assertTrue(runner.lastClip!!.isWiped)
        // Nothing left to analyze or play.
        vm.onAnalyze()
        vm.onTogglePlay()
        assertEquals(DetectStep.Home, vm.state.value.step)
        assertEquals(0, runner.played)
    }

    @Test
    fun playbackReadsTheInMemoryClipAndEnds() = runTest {
        val runner = FakeRunner()
        val vm = DetectViewModel(runner, Language.EN)
        vm.onRecord()
        vm.onTogglePlay()
        assertEquals(1, runner.played)
        // The fake finishes at once, so the step is back to not playing.
        assertEquals(false, (vm.state.value.step as DetectStep.Review).playing)
        vm.onAnalyze()
        assertTrue(runner.lastClip!!.isWiped)
    }

    @Test
    fun microphoneFailureShowsFailed() = runTest {
        val vm = DetectViewModel(FakeRunner(recordFails = true), Language.EN)
        vm.onRecord()
        assertEquals(DetectStep.Failed, vm.state.value.step)
    }

    @Test
    fun deviceLanguagePicksSpanishOnlyForSpanish() {
        assertEquals(Language.ES, DetectViewModel.deviceLanguage(java.util.Locale.forLanguageTag("es-PR")))
        assertEquals(Language.EN, DetectViewModel.deviceLanguage(java.util.Locale.forLanguageTag("id-ID")))
    }
}
