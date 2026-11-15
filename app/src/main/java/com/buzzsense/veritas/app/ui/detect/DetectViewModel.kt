package com.buzzsense.veritas.app.ui.detect

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.buzzsense.veritas.app.agent.AppContainer
import com.buzzsense.veritas.app.agent.EngineUnavailableException
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
    /** Starts building the engine in the background so the first run is not slowed down. */
    suspend fun warmUp() {}
}

/** Real runner: the phone's microphone and the shared Koog [BuzzAlertEngine][com.buzzsense.veritas.shared.agent.BuzzAlertEngine]. */
class EngineDetectionRunner(private val container: AppContainer) : DetectionRunner {
    private val recorder: ClipRecorder = container.newRecorder()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun record(seconds: Int, onProgress: (Float) -> Unit) = recorder.record(seconds, onProgress)
    override fun stopRecording() = recorder.stop()
    override suspend fun analyze(clip: AudioClip, language: Language) = container.engine().run(clip, language)
    override suspend fun warmUp() { container.engine() }
}

/**
 * Owns the Detect flow: record → analyze → result. The UI only renders [state] and
 * [recordingProgress] and calls the intents below.
 *
 * Audio never outlives a run: the engine wipes the clip when it finishes, and this class
 * wipes it itself when the run never reaches the engine.
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

    /** Call only once RECORD_AUDIO is granted (the Home screen asks first). */
    fun startRecording() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _recordingProgress.value = 0f
            setStep(DetectStep.Recording)
            var clip: AudioClip? = null
            try {
                clip = runner.record(CLIP_SECONDS) { _recordingProgress.value = it }
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
                clip?.takeUnless { it.isWiped }?.wipe()
            }
        }
    }

    /** The Stop button: keep what was recorded so far and analyze it. */
    fun stopRecording() = runner.stopRecording()

    fun recordAgain() {
        job?.cancel()
        job = null
        startRecording()
    }

    fun backToHome() {
        job?.cancel()
        job = null
        setStep(DetectStep.Home)
    }

    override fun onRecord() = startRecording()
    override fun onStop() = stopRecording()
    override fun onRecordAgain() = recordAgain()
    override fun onBackHome() = backToHome()

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
