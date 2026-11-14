package com.buzzsense.veritas.shared.audio

/**
 * A recorded clip held only in memory (guardrail "Audio stays local").
 *
 * It is never written to disk or sent anywhere. Call [wipe] as soon as features
 * have been extracted; the detector node does this before the graph continues.
 */
class AudioClip(samples: FloatArray, val sampleRateHz: Int) {
    private var data: FloatArray? = samples

    val durationSeconds: Float get() = (data?.size ?: 0).toFloat() / sampleRateHz

    val isWiped: Boolean get() = data == null

    /** Read access for the feature extractor and VAD only. */
    fun samples(): FloatArray = checkNotNull(data) { "Audio clip was already wiped" }

    /** Zero and drop the samples. Safe to call more than once. */
    fun wipe() {
        data?.fill(0f)
        data = null
    }

    companion object {
        const val RECORD_SECONDS = 3
    }
}
