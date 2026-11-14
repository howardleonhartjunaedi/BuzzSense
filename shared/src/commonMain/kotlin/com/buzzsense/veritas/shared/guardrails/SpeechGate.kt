package com.buzzsense.veritas.shared.guardrails

import com.buzzsense.veritas.shared.audio.AudioClip

/** Voice-activity detection, e.g. Silero VAD in ONNX (`SileroVad`). */
interface VoiceActivityDetector {
    /** Highest speech probability over the clip, 0..1. */
    suspend fun speechProbability(samples: FloatArray, sampleRateHz: Int): Float
}

/**
 * Guardrail "Speech rejection": runs before the detector. If a voice is heard the
 * clip is wiped immediately and the user is asked to record again.
 */
class SpeechGate(
    private val vad: VoiceActivityDetector,
    private val threshold: Float = 0.5f,
) {
    /** @return true if the clip was rejected (and already wiped). */
    suspend fun rejectIfSpeech(clip: AudioClip): Boolean {
        val p = vad.speechProbability(clip.samples(), clip.sampleRateHz)
        if (p >= threshold) {
            clip.wipe()
            return true
        }
        return false
    }
}
