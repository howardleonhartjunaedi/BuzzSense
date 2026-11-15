package com.buzzsense.veritas.shared.guardrails

import com.buzzsense.veritas.shared.audio.AudioClip
import kotlin.math.roundToInt

/** Speech evidence for one clip, from per-chunk VAD probabilities. Numbers only, never audio. */
data class SpeechScore(
    /** Highest per-chunk speech probability, 0..1. */
    val maxProbability: Float,
    /** Share of the clip whose chunks are at or above the VAD's chunk threshold, 0..1. */
    val speechFraction: Float,
    /** Longest unbroken run of speech chunks, in milliseconds. */
    val longestSpeechMs: Int,
) {
    fun describe(): String =
        "${(speechFraction * 100).roundToInt()}% of the clip speech-like, longest run ${longestSpeechMs} ms, " +
            "peak ${(maxProbability * 100).roundToInt()}%"
}

/** Voice-activity detection, e.g. Silero VAD in ONNX (`SileroVad`). */
interface VoiceActivityDetector {
    suspend fun analyze(samples: FloatArray, sampleRateHz: Int): SpeechScore
}

/** Result of [SpeechGate.check]; when [rejected] the clip is already wiped. */
data class SpeechCheck(val rejected: Boolean, val score: SpeechScore)

/**
 * Guardrail "Speech rejection": runs before the detector. If a voice is heard the clip is
 * wiped immediately and the user is asked to record again.
 *
 * The decision uses how much of the clip sounds like speech, not a single peak: a mosquito's
 * tonal whine makes Silero spike on a few 32 ms chunks, so "any chunk ≥ 0.5" deleted up to
 * 20% of real Culex clips. Measured on train/val clips (BioDCASE without D2, HumBugDB) with the
 * shipped Silero v6.2.3 at 8 kHz: mosquito clips reach at most 18% speech-like (one HumBugDB
 * recording that likely holds voices: 56%), spoken sentences at least 44%, alone or over a buzz.
 */
class SpeechGate(
    private val vad: VoiceActivityDetector,
    private val minSpeechFraction: Float = 0.25f,
) {
    suspend fun check(clip: AudioClip): SpeechCheck {
        val score = vad.analyze(clip.samples(), clip.sampleRateHz)
        val rejected = score.speechFraction >= minSpeechFraction
        if (rejected) clip.wipe()
        return SpeechCheck(rejected, score)
    }
}
