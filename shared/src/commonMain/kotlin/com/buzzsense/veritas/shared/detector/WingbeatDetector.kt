package com.buzzsense.veritas.shared.detector

import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.Verdict

/**
 * The exported CNNs (ONNX, or LiteRT if you swap the runtime). The ONNX
 * implementation (`OnnxWingbeatModel`) is compiled for Android and the JVM eval harness.
 */
interface WingbeatModel {
    /** Stage 1: P(mosquito) for each window. */
    suspend fun mosquitoScores(windows: List<LogMelWindow>): FloatArray

    /** Stage 2: P(Aedes aegypti | mosquito) for each window. */
    suspend fun aedesScores(windows: List<LogMelWindow>): FloatArray
}

/**
 * Score bands picked on the validation sites, never on test.
 * Scores inside [low, high) return Unsure instead of a guess.
 * Stage 2 favours recall on Aedes aegypti, so its band sits lower.
 */
data class Thresholds(
    val mosquitoLow: Float = 0.35f,
    val mosquitoHigh: Float = 0.65f,
    val aedesLow: Float = 0.30f,
    val aedesHigh: Float = 0.55f,
) {
    fun mosquito(score: Float) = band(score, mosquitoLow, mosquitoHigh)
    fun aedes(score: Float) = band(score, aedesLow, aedesHigh)

    private fun band(s: Float, lo: Float, hi: Float) = when {
        s >= hi -> Verdict.YES
        s < lo -> Verdict.NO
        else -> Verdict.UNSURE
    }
}

/**
 * Two-stage detector:
 *  1. mosquito vs background on every 1 s window;
 *  2. Aedes aegypti vs other species only on windows stage 1 called mosquito,
 *     with window scores averaged over the clip.
 */
class WingbeatDetector(
    private val model: WingbeatModel,
    private val baseline: DominantFrequencyBaseline,
    private val extractor: LogMelExtractor = LogMelExtractor(),
    private val thresholds: Thresholds = Thresholds(),
) {
    suspend fun detect(clip: AudioClip): DetectionResult {
        val samples = clip.samples()
        val windows = extractor.windows(samples, clip.sampleRateHz)
        val dominantHz = extractor.dominantFrequencyHz(samples, clip.sampleRateHz)
        if (windows.isEmpty()) return tooShort(dominantHz)

        val stage1 = model.mosquitoScores(windows)
        val mosquitoMean = stage1.average().toFloat()
        val mosquitoVerdict = thresholds.mosquito(mosquitoMean)
        val mosquitoWindows = windows.filterIndexed { i, _ -> thresholds.mosquito(stage1[i]) == Verdict.YES }

        var aedesScore: Float? = null
        val aedesVerdict = when {
            mosquitoVerdict == Verdict.YES && mosquitoWindows.isNotEmpty() -> {
                val s = model.aedesScores(mosquitoWindows).average().toFloat()
                aedesScore = s
                thresholds.aedes(s)
            }
            mosquitoVerdict == Verdict.NO -> Verdict.NO
            else -> Verdict.UNSURE
        }

        return DetectionResult(
            mosquito = mosquitoVerdict,
            aedesAegypti = aedesVerdict,
            mosquitoScore = mosquitoMean,
            aedesScore = aedesScore,
            dominantFrequencyHz = dominantHz,
            baselineAedes = if (mosquitoVerdict == Verdict.YES) baseline.classify(dominantHz) else Verdict.NO,
            windowCount = windows.size,
            mosquitoWindowCount = mosquitoWindows.size,
        )
    }

    private fun tooShort(dominantHz: Float?) = DetectionResult(
        mosquito = Verdict.UNSURE,
        aedesAegypti = Verdict.UNSURE,
        mosquitoScore = 0f,
        aedesScore = null,
        dominantFrequencyHz = dominantHz,
        baselineAedes = Verdict.NO,
        windowCount = 0,
        mosquitoWindowCount = 0,
    )
}
