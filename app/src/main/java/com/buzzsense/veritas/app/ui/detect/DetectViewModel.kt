package com.buzzsense.veritas.app.ui.detect

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.buzzsense.veritas.app.agent.AppContainer
import com.buzzsense.veritas.app.agent.EngineUnavailableException
import com.buzzsense.veritas.app.audio.ClipPlayer
import com.buzzsense.veritas.app.audio.ClipRecorder
import com.buzzsense.veritas.app.ui.DetectActions
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Steps of the Detect tab. */
@Immutable
sealed interface DetectStep {
    data object Home : DetectStep
    data object Recording : DetectStep

    /** Recording finished; the clip waits in memory until the user analyzes or re-records. */
    data class Review(val durationSeconds: Float, val sampleRateHz: Int, val playing: Boolean = false) : DetectStep
    data object Analyzing : DetectStep
    data class Result(val detection: DetectionUi) : DetectStep

    /** The engine cannot start in this build (assets or libvec0.so missing). */
    data class Unavailable(val missing: List<String>) : DetectStep

    /** Recording or the run failed; nothing was kept. */
    data object Failed : DetectStep
}

@Immutable
data class DetectUiState(val step: DetectStep = DetectStep.Home)

/** The two things a detection needs, so [DetectViewModel] can be tested without a phone. */
interface DetectionRunner {
    suspend fun record(seconds: Int, onProgress: (Float) -> Unit): AudioClip
    fun stopRecording()
    suspend fun analyze(clip: AudioClip, language: Language): AgentOutcome
    /** Plays [clip] from memory; returns when it ends. Cancel the caller to stop. */
    suspend fun play(clip: AudioClip) {}
    /** Starts building the engine in the background so the first run is not slowed down. */
    suspend fun warmUp() {}
}

/** Real runner: the phone's microphone and the shared Koog [BuzzAlertEngine][com.buzzsense.veritas.shared.agent.BuzzAlertEngine]. */
class EngineDetectionRunner(private val container: AppContainer) : DetectionRunner {
    private val recorder: ClipRecorder = container.newRecorder()
    private val player = ClipPlayer()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun record(seconds: Int, onProgress: (Float) -> Unit) = recorder.record(seconds, onProgress)
    override fun stopRecording() = recorder.stop()
    override suspend fun play(clip: AudioClip) = player.play(clip.samples(), clip.sampleRateHz)
    override suspend fun analyze(clip: AudioClip, language: Language): AgentOutcome =
        container.engine().run(clip, language, useGuidance = container.settings.useGuidance.value).also { outcome ->
            // Decision trace only (labels, scores, guardrail events): never audio. See it in Logcat, tag "BuzzSense".
            outcome.trace.events.forEach { Log.i(LOG_TAG, "${it.stage}: ${it.message}") }
            outcome.trace.detection?.let { Log.i(LOG_TAG, "detection: $it") }
            outcome.trace.provider?.let { Log.i(LOG_TAG, "alert written by: $it") }
        }

    private companion object { const val LOG_TAG = "BuzzSense" }
    override suspend fun warmUp() { container.engine() }
}

/**
 * Owns the Detect flow: record → review → analyze → result. The UI only renders [state] and
 * [recordingProgress] and calls the intents below.
 *
 * Audio never outlives a run: while Review is shown the clip lives only in memory here (it can
 * be played back, never saved); "Record again", Back and [onCleared] wipe it, and "Analyze"
 * hands it to the engine, which wipes it when the run finishes.
 */
class DetectViewModel(
    private val runner: DetectionRunner,
    private val language: Language = deviceLanguage(),
) : ViewModel(), DetectActions {

    private val _state = MutableStateFlow(DetectUiState())
    val state: StateFlow<DetectUiState> = _state.asStateFlow()

    private val _recordingProgress = MutableStateFlow(0f)
    /** 0..1 while recording; read in a lambda by the progress ring so only the ring redraws. */
    val recordingProgress: StateFlow<Float> = _recordingProgress.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            try {
                runner.warmUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reported when the user records; warming up is best effort.
            }
        }
    }

    /** The recorded clip while the Review step is shown; wiped on every way out of it. */
    private var pendingClip: AudioClip? = null
    private var playJob: Job? = null

    /** Call only once RECORD_AUDIO is granted (the Home screen asks first). */
    fun startRecording() {
        if (job?.isActive == true) return
        discardPending()
        job = viewModelScope.launch {
            _recordingProgress.value = 0f
            setStep(DetectStep.Recording)
            var clip: AudioClip? = null
            try {
                clip = runner.record(CLIP_SECONDS) { _recordingProgress.value = it }
                pendingClip = clip
                clip = null // ownership moves to the Review step
                val review = pendingClip!!
                setStep(DetectStep.Review(review.durationSeconds, review.sampleRateHz))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStep(DetectStep.Failed)
            } finally {
                clip?.takeUnless { it.isWiped }?.wipe()
            }
        }
    }

    /** "Analyze buzz" on the Review step: hands the clip to the engine, which wipes it. */
    fun analyze() {
        val clip = pendingClip ?: return
        if (job?.isActive == true) return
        stopPlayback()
        pendingClip = null
        job = viewModelScope.launch {
            try {
                setStep(DetectStep.Analyzing)
                val outcome = runner.analyze(clip, language)
                setStep(DetectStep.Result(AgentOutcomeMapper.map(outcome)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: EngineUnavailableException) {
                setStep(DetectStep.Unavailable(e.missing))
            } catch (e: Exception) {
                setStep(DetectStep.Failed)
            } finally {
                clip.takeUnless { it.isWiped }?.wipe()
            }
        }
    }

    /** Play / pause on the Review step. Playback reads the in-memory clip only. */
    fun togglePlayback() {
        val clip = pendingClip ?: return
        if (playJob?.isActive == true) {
            stopPlayback()
            return
        }
        playJob = viewModelScope.launch {
            setPlaying(true)
            try {
                runner.play(clip)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Playback is optional; the clip can still be analyzed.
            } finally {
                setPlaying(false)
            }
        }
    }

    /** The Stop button: keep what was recorded so far and go to Review. */
    fun stopRecording() = runner.stopRecording()

    fun backToHome() {
        job?.cancel()
        job = null
        discardPending()
        setStep(DetectStep.Home)
    }

    override fun onCleared() {
        discardPending()
        super.onCleared()
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
    }

    private fun discardPending() {
        stopPlayback()
        pendingClip?.takeUnless { it.isWiped }?.wipe()
        pendingClip = null
    }

    private fun setPlaying(playing: Boolean) = _state.update { s ->
        val step = s.step
        if (step is DetectStep.Review) s.copy(step = step.copy(playing = playing)) else s
    }

    override fun onRecord() = startRecording()
    override fun onStop() = stopRecording()
    override fun onBackHome() = backToHome()
    override fun onAnalyze() = analyze()
    override fun onRecordAgain() = startRecording()
    override fun onTogglePlay() = togglePlayback()

    private fun setStep(step: DetectStep) = _state.update { it.copy(step = step) }

    companion object {
        const val CLIP_SECONDS = 5

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetectViewModel(EngineDetectionRunner(container)) }
        }

        fun deviceLanguage(locale: Locale = Locale.getDefault()): Language =
            if (locale.language == "es") Language.ES else Language.EN
    }
}
