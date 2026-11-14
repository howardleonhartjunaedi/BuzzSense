package com.buzzsense.veritas.shared.detector

import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.Verdict

/** `class3` labels used by the acoustic store. */
object Class3 {
    const val AEDES = "aedes"
    const val CULEX = "culex"
    const val OTHER = "other_mosquito"
    const val NONE = "none"
    val ALL = listOf(AEDES, CULEX, OTHER, NONE)
}

/**
 * Per-window class shares (0..1, summing to 1) for embeddings from [AcousticFeatures].
 * [KnnClassifier] is the pipeline's detector; a CNN exported later can implement this too.
 */
fun interface WindowClassifier {
    suspend fun classify(embeddings: List<FloatArray>): List<Map<String, Float>>
}

/** k nearest train-split clips in the acoustic store vote with their `class3` (k = 15 as in the eval). */
class KnnClassifier(private val store: AcousticStore, private val k: Int = 15) : WindowClassifier {
    override suspend fun classify(embeddings: List<FloatArray>): List<Map<String, Float>> =
        embeddings.map { e ->
            val nn = store.nearest(e, k)
            if (nn.isEmpty()) mapOf(Class3.NONE to 1f)
            else nn.groupingBy { it.class3 }.eachCount().mapValues { it.value.toFloat() / nn.size }
        }
}

/**
 * Score bands picked on the validation domains, never on test.
 * Scores inside [low, high) return Unsure instead of a guess.
 * Stage 2 favours recall on Aedes aegypti, so its band sits lower.
 */
data class Thresholds(
    val mosquitoLow: Float = 0.40f,
    val mosquitoHigh: Float = 0.60f,
    val aedesLow: Float = 0.30f,
    val aedesHigh: Float = 0.50f,
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
 * Two-stage detector on 0.62 s windows (`feature_spec.json`):
 *  1. mosquito vs background: score = 1 - share of `none` neighbours, averaged over windows;
 *  2. Aedes vs other mosquitoes, only on windows stage 1 called mosquito:
 *     score = share of `aedes` among the mosquito neighbours, averaged over those windows.
 */
class WingbeatDetector(
    private val features: AcousticFeatures,
    private val classifier: WindowClassifier,
    private val baseline: DominantFrequencyBaseline,
    private val thresholds: Thresholds = Thresholds(),
) {
    suspend fun detect(clip: AudioClip): DetectionResult {
        val windows = features.windows(clip.samples(), clip.sampleRateHz)
        if (windows.isEmpty()) return tooShort()

        val shares = classifier.classify(windows.map(features::embedding))
        val f0 = windows.map(features::f0Hz)

        val mosquitoPerWindow = shares.map { 1f - (it[Class3.NONE] ?: 0f) }
        val mosquitoScore = mosquitoPerWindow.average().toFloat()
        val mosquitoVerdict = thresholds.mosquito(mosquitoScore)
        val mosquitoIdx = mosquitoPerWindow.indices.filter { thresholds.mosquito(mosquitoPerWindow[it]) == Verdict.YES }

        var aedesScore: Float? = null
        val aedesVerdict = when {
            mosquitoVerdict == Verdict.YES && mosquitoIdx.isNotEmpty() -> {
                val s = mosquitoIdx.map { i -> (shares[i][Class3.AEDES] ?: 0f) / mosquitoPerWindow[i].coerceAtLeast(1e-6f) }
                    .average().toFloat()
                aedesScore = s
                thresholds.aedes(s)
            }
            mosquitoVerdict == Verdict.NO -> Verdict.NO
            else -> Verdict.UNSURE
        }

        val f0Clip = median(if (mosquitoIdx.isNotEmpty()) mosquitoIdx.map { f0[it] } else f0)
        val votes = Class3.ALL.associateWith { c -> shares.map { it[c] ?: 0f }.average().toFloat() }

        return DetectionResult(
            mosquito = mosquitoVerdict,
            aedesAegypti = aedesVerdict,
            mosquitoScore = mosquitoScore,
            aedesScore = aedesScore,
            dominantFrequencyHz = f0Clip,
            baselineAedes = if (mosquitoVerdict == Verdict.YES) baseline.classify(f0Clip) else Verdict.NO,
            windowCount = windows.size,
            mosquitoWindowCount = mosquitoIdx.size,
            classVotes = votes,
        )
    }

    private fun tooShort() = DetectionResult(
        mosquito = Verdict.UNSURE,
        aedesAegypti = Verdict.UNSURE,
        mosquitoScore = 0f,
        aedesScore = null,
        dominantFrequencyHz = null,
        baselineAedes = Verdict.NO,
        windowCount = 0,
        mosquitoWindowCount = 0,
    )

    private fun median(v: List<Float>): Float? = if (v.isEmpty()) null else v.sorted()[v.size / 2]
}
