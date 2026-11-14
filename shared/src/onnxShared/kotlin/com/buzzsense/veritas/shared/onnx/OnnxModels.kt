package com.buzzsense.veritas.shared.onnx

// Compiled twice, once per target (androidMain + jvmMain both add this folder),
// against onnxruntime-android and onnxruntime respectively. Same ai.onnxruntime API.

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.buzzsense.veritas.shared.detector.LogMelWindow
import com.buzzsense.veritas.shared.detector.WingbeatModel
import com.buzzsense.veritas.shared.guardrails.VoiceActivityDetector
import com.buzzsense.veritas.shared.rag.Embedder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.math.sqrt

private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

private fun session(bytes: ByteArray): OrtSession =
    env.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })

/**
 * The two exported CNNs. Expected ONNX signature (export from the Colab notebook):
 * input  `logmel` float32 [N, 1, frames, nMels]
 * output `logit`  float32 [N, 1]   (a sigmoid is applied here)
 */
class OnnxWingbeatModel(stage1Onnx: ByteArray, stage2Onnx: ByteArray) : WingbeatModel, AutoCloseable {
    private val stage1 = session(stage1Onnx)
    private val stage2 = session(stage2Onnx)

    override suspend fun mosquitoScores(windows: List<LogMelWindow>) = score(stage1, windows)
    override suspend fun aedesScores(windows: List<LogMelWindow>) = score(stage2, windows)

    private fun score(s: OrtSession, windows: List<LogMelWindow>): FloatArray {
        if (windows.isEmpty()) return FloatArray(0)
        val frames = windows[0].frames
        val mels = windows[0].nMels
        val buf = FloatBuffer.allocate(windows.size * frames * mels)
        windows.forEach { buf.put(it.values) }
        buf.rewind()
        OnnxTensor.createTensor(env, buf, longArrayOf(windows.size.toLong(), 1, frames.toLong(), mels.toLong())).use { input ->
            s.run(mapOf(s.inputNames.first() to input)).use { out ->
                @Suppress("UNCHECKED_CAST")
                val logits = out[0].value as Array<FloatArray>
                return FloatArray(windows.size) { i -> sigmoid(logits[i][0]) }
            }
        }
    }

    override fun close() { stage1.close(); stage2.close() }
}

/**
 * Silero VAD v5 (ONNX), run on 512-sample chunks at 16 kHz.
 * Inputs: `input` [1, 512], `state` [2, 1, 128], `sr` int64; outputs: prob [1, 1], new state.
 */
class SileroVad(onnx: ByteArray) : VoiceActivityDetector, AutoCloseable {
    private val s = session(onnx)

    override suspend fun speechProbability(samples: FloatArray, sampleRateHz: Int): Float {
        val audio = resampleLinear(samples, sampleRateHz, 16_000)
        var state = Array(2) { Array(1) { FloatArray(128) } }
        var maxProb = 0f
        OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(16_000)), longArrayOf()).use { sr ->
            var off = 0
            while (off + CHUNK <= audio.size) {
                val chunk = arrayOf(audio.copyOfRange(off, off + CHUNK))
                OnnxTensor.createTensor(env, chunk).use { input ->
                    OnnxTensor.createTensor(env, state).use { st ->
                        s.run(mapOf("input" to input, "state" to st, "sr" to sr)).use { out ->
                            @Suppress("UNCHECKED_CAST")
                            val p = (out[0].value as Array<FloatArray>)[0][0]
                            @Suppress("UNCHECKED_CAST")
                            state = out[1].value as Array<Array<FloatArray>>
                            if (p > maxProb) maxProb = p
                        }
                    }
                }
                off += CHUNK
            }
        }
        return maxProb
    }

    override fun close() = s.close()

    private companion object { const val CHUNK = 512 }
}

/**
 * multilingual-e5-small in ONNX. Tokenization (XLM-R SentencePiece) is supplied by
 * the caller via [tokenize] → (input_ids, attention_mask). Mean-pooled, L2-normalized.
 * The app can instead use the precomputed query vectors in the guidance DB, since
 * every query is built from a fixed template (see `PrecomputedQueryEmbedder`).
 */
class OnnxE5Embedder(
    onnx: ByteArray,
    private val tokenize: (String) -> Pair<LongArray, LongArray>,
    override val dimensions: Int = 384,
) : Embedder, AutoCloseable {
    private val s = session(onnx)

    override suspend fun embed(text: String, isQuery: Boolean): FloatArray {
        val (ids, mask) = tokenize((if (isQuery) "query: " else "passage: ") + text)
        val shape = longArrayOf(1, ids.size.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { idT ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape).use { maskT ->
                val inputs = buildMap {
                    put("input_ids", idT)
                    put("attention_mask", maskT)
                }
                s.run(inputs).use { out ->
                    @Suppress("UNCHECKED_CAST")
                    val hidden = (out[0].value as Array<Array<FloatArray>>)[0]
                    val pooled = FloatArray(dimensions)
                    var count = 0f
                    hidden.forEachIndexed { t, vec ->
                        if (mask[t] == 1L) { for (d in 0 until dimensions) pooled[d] += vec[d]; count++ }
                    }
                    var norm = 0f
                    for (d in 0 until dimensions) { pooled[d] /= count; norm += pooled[d] * pooled[d] }
                    norm = sqrt(norm).coerceAtLeast(1e-12f)
                    for (d in 0 until dimensions) pooled[d] /= norm
                    return pooled
                }
            }
        }
    }

    override fun close() = s.close()
}

private fun sigmoid(x: Float) = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()

private fun resampleLinear(x: FloatArray, from: Int, to: Int): FloatArray {
    if (from == to || x.isEmpty()) return x
    val ratio = from.toDouble() / to
    return FloatArray((x.size / ratio).toInt()) { i ->
        val src = i * ratio
        val i0 = src.toInt().coerceAtMost(x.lastIndex)
        val i1 = (i0 + 1).coerceAtMost(x.lastIndex)
        val t = (src - i0).toFloat()
        x[i0] * (1 - t) + x[i1] * t
    }
}
