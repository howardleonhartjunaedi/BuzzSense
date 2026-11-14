package com.buzzsense.veritas.shared.detector

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Feature settings. These MUST match the Colab training notebook exactly, or the
 * phone sees different inputs than the model was trained on (eval 6 checks parity).
 */
data class FeatureConfig(
    val sampleRateHz: Int = 8_000,
    val windowSeconds: Float = 1.0f,
    val nFft: Int = 512,
    val hopLength: Int = 128,
    val nMels: Int = 64,
    val fMinHz: Float = 0f,
    val fMaxHz: Float = 4_000f,
    /** Wingbeat band searched by the dominant-frequency rule and the spectrogram marker. */
    val wingbeatBandHz: ClosedFloatingPointRange<Float> = 200f..1_000f,
) {
    init {
        require(nFft > 0 && (nFft and (nFft - 1)) == 0) { "nFft must be a power of two" }
    }

    val windowSamples: Int get() = (sampleRateHz * windowSeconds).toInt()
}

/** A [frames] x [nMels] log-mel patch, row-major, normalized per clip. */
class LogMelWindow(val frames: Int, val nMels: Int, val values: FloatArray)

/** Pure-Kotlin feature pipeline shared by Android and the JVM eval harness. */
class LogMelExtractor(val config: FeatureConfig = FeatureConfig()) {

    private val hann = FloatArray(config.nFft) { i -> (0.5 - 0.5 * cos(2 * PI * i / config.nFft)).toFloat() }
    private val melBank: Array<FloatArray> = buildMelFilterBank()

    /** Linear-interpolation resample to [FeatureConfig.sampleRateHz]. */
    fun resample(samples: FloatArray, fromHz: Int): FloatArray {
        if (fromHz == config.sampleRateHz || samples.isEmpty()) return samples.copyOf()
        val ratio = fromHz.toDouble() / config.sampleRateHz
        val outLen = (samples.size / ratio).toInt()
        return FloatArray(outLen) { i ->
            val src = i * ratio
            val i0 = floor(src).toInt().coerceIn(0, samples.lastIndex)
            val i1 = min(i0 + 1, samples.lastIndex)
            val t = (src - i0).toFloat()
            samples[i0] * (1 - t) + samples[i1] * t
        }
    }

    /** Power spectrum frames for one signal: [frame][nFft/2 + 1]. */
    fun powerSpectrogram(signal: FloatArray): Array<FloatArray> {
        val bins = config.nFft / 2 + 1
        val nFrames = if (signal.size < config.nFft) 0 else 1 + (signal.size - config.nFft) / config.hopLength
        val re = DoubleArray(config.nFft)
        val im = DoubleArray(config.nFft)
        return Array(nFrames) { f ->
            val off = f * config.hopLength
            for (i in 0 until config.nFft) {
                re[i] = (signal[off + i] * hann[i]).toDouble()
                im[i] = 0.0
            }
            fft(re, im)
            FloatArray(bins) { k -> (re[k] * re[k] + im[k] * im[k]).toFloat() }
        }
    }

    /**
     * Full clip -> non-overlapping 1 s log-mel windows, normalized per clip
     * (zero mean, unit variance over the whole clip).
     */
    fun windows(samples: FloatArray, sampleRateHz: Int): List<LogMelWindow> {
        val signal = resample(samples, sampleRateHz)
        val step = config.windowSamples
        if (signal.size < step) return emptyList()
        val raw = (0..signal.size - step step step).map { start ->
            val power = powerSpectrogram(signal.copyOfRange(start, start + step))
            val mel = FloatArray(power.size * config.nMels)
            power.forEachIndexed { f, spec ->
                for (m in 0 until config.nMels) {
                    val filt = melBank[m]
                    var acc = 0f
                    for (k in spec.indices) acc += filt[k] * spec[k]
                    mel[f * config.nMels + m] = 10f * log10(max(acc, 1e-10f))
                }
            }
            LogMelWindow(power.size, config.nMels, mel)
        }
        normalizePerClip(raw)
        return raw
    }

    /** Strongest peak inside the wingbeat band, averaged over the clip; null for silence. */
    fun dominantFrequencyHz(samples: FloatArray, sampleRateHz: Int): Float? {
        val power = powerSpectrogram(resample(samples, sampleRateHz))
        if (power.isEmpty()) return null
        val bins = power[0].size
        val mean = FloatArray(bins)
        power.forEach { frame -> for (k in 0 until bins) mean[k] += frame[k] / power.size }
        val hzPerBin = config.sampleRateHz.toFloat() / config.nFft
        val lo = (config.wingbeatBandHz.start / hzPerBin).toInt().coerceIn(0, bins - 1)
        val hi = (config.wingbeatBandHz.endInclusive / hzPerBin).toInt().coerceIn(lo, bins - 1)
        var best = lo
        for (k in lo..hi) if (mean[k] > mean[best]) best = k
        return if (mean[best] <= 1e-9f) null else best * hzPerBin
    }

    private fun normalizePerClip(windows: List<LogMelWindow>) {
        val n = windows.sumOf { it.values.size }
        if (n == 0) return
        val mean = windows.sumOf { w -> w.values.sumOf { it.toDouble() } } / n
        val variance = windows.sumOf { w -> w.values.sumOf { (it - mean).pow(2) } } / n
        val std = sqrt(variance).coerceAtLeast(1e-6)
        windows.forEach { w -> for (i in w.values.indices) w.values[i] = ((w.values[i] - mean) / std).toFloat() }
    }

    private fun buildMelFilterBank(): Array<FloatArray> {
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
        val bins = config.nFft / 2 + 1
        val melMin = hzToMel(config.fMinHz.toDouble())
        val melMax = hzToMel(config.fMaxHz.toDouble())
        val points = DoubleArray(config.nMels + 2) { i -> melToHz(melMin + (melMax - melMin) * i / (config.nMels + 1)) }
        val binHz = DoubleArray(bins) { k -> k * config.sampleRateHz.toDouble() / config.nFft }
        return Array(config.nMels) { m ->
            val l = points[m]
            val c = points[m + 1]
            val r = points[m + 2]
            FloatArray(bins) { k ->
                val f = binHz[k]
                when {
                    f < l || f > r -> 0f
                    f <= c -> ((f - l) / (c - l)).toFloat()
                    else -> ((r - f) / (r - c)).toFloat()
                }
            }
        }
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val half = len / 2
            for (i in 0 until n step len) {
                for (k in 0 until half) {
                    val wr = cos(ang * k)
                    val wi = sin(ang * k)
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + half] * wr - im[i + k + half] * wi
                    val vi = re[i + k + half] * wi + im[i + k + half] * wr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + half] = ur - vr; im[i + k + half] = ui - vi
                }
            }
            len = len shl 1
        }
    }
}
