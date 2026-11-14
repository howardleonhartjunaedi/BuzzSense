package com.buzzsense.veritas.shared.detector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * `feature_spec.json` written by `Audio File/pipeline/build_rag.py`. It is the single
 * source of truth for the acoustic features; never hard-code these numbers.
 */
@Serializable
data class FeatureSpec(
    val sr: Int,
    @SerialName("clip_samples") val clipSamples: Int,
    @SerialName("n_fft") val nFft: Int,
    val hop: Int,
    @SerialName("n_mels") val nMels: Int,
    val fmin: Double,
    val fmax: Double,
    @SerialName("log_eps") val logEps: Double = 1e-10,
    /** 40x40 projector that removes each clip's smooth (cubic) spectral envelope. */
    @SerialName("tilt_resid") val tiltResid: List<List<Float>>,
    /** Train-split mean and std of the raw 80-d features. */
    val mu: List<Float>,
    val sd: List<Float>,
    val version: String = "unversioned",
) {
    val embeddingDim: Int get() = 2 * nMels

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun fromJson(text: String): FeatureSpec = json.decodeFromString(serializer(), text)
    }
}

/**
 * Kotlin port of `build_rag.features()`; same code on the phone and in the JVM eval.
 * Verified against Python output by `FeatureParityTest`.
 *
 * Per 0.62 s window at 8 kHz:
 *  - log-mel (natural log) with a symmetric Hann window, gain-normalised by subtracting the global mean;
 *  - raw = [tilt_resid · mean_t(logmel), std_t(logmel)]  (80-d);
 *  - embedding = L2( (raw - mu) / sd ), the vector stored in `clips_vec`;
 *  - f0 = argmax over 150..1199 Hz of the harmonic sum (h = 1..4) of a 16384-point spectrum.
 */
class AcousticFeatures(val spec: FeatureSpec) {

    private val hann = symmetricHann(spec.nFft)
    private val mel: Array<FloatArray> = melFilterBank()
    private val tilt: Array<FloatArray> = Array(spec.nMels) { r -> FloatArray(spec.nMels) { c -> spec.tiltResid[r][c] } }
    private val mu = spec.mu.toFloatArray()
    private val sd = spec.sd.toFloatArray()

    init {
        require(spec.mu.size == spec.embeddingDim && spec.sd.size == spec.embeddingDim) { "mu/sd must be ${spec.embeddingDim}-d" }
    }

    /**
     * Cut a recording into non-overlapping windows of `clip_samples` at `sr`, skipping
     * silent windows (max |x| < 1e-5) like the HumBugDB indexing step.
     * Record at [FeatureSpec.sr] on the phone: the linear resampler here is only a fallback
     * and is not bit-identical to SciPy's `resample_poly`.
     */
    fun windows(samples: FloatArray, sampleRateHz: Int): List<FloatArray> {
        val x = if (sampleRateHz == spec.sr) samples else resampleLinear(samples, sampleRateHz, spec.sr)
        val n = spec.clipSamples
        return (0 until x.size / n).map { i -> x.copyOfRange(i * n, (i + 1) * n) }
            .filter { w -> w.maxOf { kotlin.math.abs(it) } >= 1e-5f }
    }

    /** The 80-d raw features (before z-scoring), `raw` in `build_rag.features`. */
    fun raw(window: FloatArray): FloatArray {
        require(window.size == spec.clipSamples) { "window must be ${spec.clipSamples} samples" }
        val x = centred(window)
        val nFrames = 1 + (x.size - spec.nFft) / spec.hop
        val bins = spec.nFft / 2 + 1
        val lm = Array(nFrames) { DoubleArray(spec.nMels) }
        val re = DoubleArray(spec.nFft)
        val im = DoubleArray(spec.nFft)
        val power = DoubleArray(bins)
        var total = 0.0
        for (f in 0 until nFrames) {
            val off = f * spec.hop
            for (i in 0 until spec.nFft) { re[i] = x[off + i] * hann[i]; im[i] = 0.0 }
            fft(re, im)
            for (k in 0 until bins) power[k] = re[k] * re[k] + im[k] * im[k]
            for (m in 0 until spec.nMels) {
                val fb = mel[m]
                var acc = 0.0
                for (k in 0 until bins) acc += fb[k] * power[k]
                val v = ln(acc + spec.logEps)
                lm[f][m] = v
                total += v
            }
        }
        val gmean = total / (nFrames * spec.nMels)
        val mean = DoubleArray(spec.nMels)
        val std = DoubleArray(spec.nMels)
        for (m in 0 until spec.nMels) {
            var s = 0.0
            for (f in 0 until nFrames) s += lm[f][m] - gmean
            mean[m] = s / nFrames
            var v = 0.0
            for (f in 0 until nFrames) v += (lm[f][m] - gmean - mean[m]).pow(2)
            std[m] = sqrt(v / nFrames) // population std, as numpy
        }
        val out = FloatArray(spec.embeddingDim)
        for (r in 0 until spec.nMels) {
            var acc = 0.0
            for (c in 0 until spec.nMels) acc += tilt[r][c] * mean[c]
            out[r] = acc.toFloat()
        }
        for (m in 0 until spec.nMels) out[spec.nMels + m] = std[m].toFloat()
        return out
    }

    /** The normalised embedding stored in `clips_vec` (cosine distance). */
    fun embedding(window: FloatArray): FloatArray {
        val raw = raw(window)
        val e = FloatArray(raw.size) { (raw[it] - mu[it]) / sd[it] }
        var norm = 0.0
        e.forEach { norm += it * it }
        val n = (sqrt(norm) + 1e-9).toFloat()
        for (i in e.indices) e[i] /= n
        return e
    }

    /** Wingbeat fundamental by harmonic sum, `f0_hz` in the manifest. */
    fun f0Hz(window: FloatArray): Float {
        val x = centred(window)
        val w = symmetricHann(x.size)
        val re = DoubleArray(F0_NFFT)
        val im = DoubleArray(F0_NFFT)
        for (i in x.indices) re[i] = x[i] * w[i]
        fft(re, im)
        val bins = F0_NFFT / 2 + 1
        val s = DoubleArray(bins) { re[it] * re[it] + im[it] * im[it] }
        val df = spec.sr.toDouble() / F0_NFFT
        var best = F0_LO
        var bestScore = Double.NEGATIVE_INFINITY
        var f0 = F0_LO
        while (f0 < F0_HI) {
            var score = 0.0
            for (h in 1..4) score += interp(f0 * h, df, s)
            if (score > bestScore) { bestScore = score; best = f0 }
            f0 += 1.0
        }
        return best.toFloat()
    }

    // ------------------------------------------------------------------ helpers

    private fun centred(window: FloatArray): DoubleArray {
        // build_rag works in float32: subtract the float32 mean.
        var s = 0.0
        window.forEach { s += it }
        val mean = (s / window.size).toFloat()
        return DoubleArray(window.size) { (window[it] - mean).toDouble() }
    }

    /** np.interp on a uniform grid; clamps outside like numpy. */
    private fun interp(freq: Double, df: Double, s: DoubleArray): Double {
        val pos = freq / df
        if (pos <= 0) return s[0]
        if (pos >= s.lastIndex) return s[s.lastIndex]
        val i = pos.toInt()
        val t = pos - i
        return s[i] * (1 - t) + s[i + 1] * t
    }

    private fun melFilterBank(): Array<FloatArray> {
        fun hz2mel(f: Double) = 2595.0 * log10(1 + f / 700)
        fun mel2hz(m: Double) = 700.0 * (10.0.pow(m / 2595) - 1)
        val bins = spec.nFft / 2 + 1
        val lo = hz2mel(spec.fmin)
        val hi = hz2mel(spec.fmax)
        val pts = DoubleArray(spec.nMels + 2) { mel2hz(lo + (hi - lo) * it / (spec.nMels + 1)) }
        return Array(spec.nMels) { i ->
            val l = pts[i]; val c = pts[i + 1]; val r = pts[i + 2]
            FloatArray(bins) { k ->
                val b = k * spec.sr.toDouble() / spec.nFft
                max(0.0, min((b - l) / (c - l), (r - b) / (r - c))).toFloat()
            }
        }
    }

    companion object {
        private const val F0_NFFT = 16384
        private const val F0_LO = 150.0
        private const val F0_HI = 1200.0

        /** numpy.hanning: 0.5 - 0.5 cos(2πn / (N - 1)). */
        fun symmetricHann(n: Int): DoubleArray =
            if (n == 1) doubleArrayOf(1.0) else DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }

        fun resampleLinear(x: FloatArray, from: Int, to: Int): FloatArray {
            if (from == to || x.isEmpty()) return x.copyOf()
            val ratio = from.toDouble() / to
            return FloatArray((x.size / ratio).toInt()) { i ->
                val src = i * ratio
                val i0 = src.toInt().coerceAtMost(x.lastIndex)
                val i1 = (i0 + 1).coerceAtMost(x.lastIndex)
                val t = (src - i0).toFloat()
                x[i0] * (1 - t) + x[i1] * t
            }
        }

        /** In-place iterative radix-2 FFT (size must be a power of two). */
        internal fun fft(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            require(n and (n - 1) == 0) { "FFT size must be a power of two" }
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val half = len / 2
                val ang = -2 * PI / len
                for (k in 0 until half) {
                    val wr = cos(ang * k); val wi = sin(ang * k)
                    var i = k
                    while (i < n) {
                        val a = i + half
                        val vr = re[a] * wr - im[a] * wi
                        val vi = re[a] * wi + im[a] * wr
                        re[a] = re[i] - vr; im[a] = im[i] - vi
                        re[i] += vr; im[i] += vi
                        i += len
                    }
                }
                len = len shl 1
            }
        }
    }
}
