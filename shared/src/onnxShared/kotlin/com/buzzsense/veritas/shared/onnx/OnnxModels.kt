package com.buzzsense.veritas.shared.onnx

// Compiled twice, once per target (androidMain + jvmMain both add this folder),
// against onnxruntime-android and onnxruntime respectively. Same ai.onnxruntime API.

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.buzzsense.veritas.shared.guardrails.VoiceActivityDetector
import com.buzzsense.veritas.shared.rag.Embedder
import java.nio.LongBuffer
import kotlin.math.sqrt

private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

private fun session(bytes: ByteArray): OrtSession =
    env.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })

/**
 * Silero VAD v5/v6 (ONNX; the app ships v6.2.3), run on 512-sample chunks at 16 kHz plus 64 samples of context.
 * Inputs: `input` [1, 576], `state` [2, 1, 128], `sr` int64; outputs: prob [1, 1], new state.
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
                // As the reference OnnxWrapper (v5+): each 512-sample chunk is preceded by the
                // previous chunk's last 64 samples (zeros for the first), so the model sees 576.
                val input = FloatArray(CONTEXT + CHUNK)
                if (off >= CONTEXT) audio.copyInto(input, 0, off - CONTEXT, off)
                audio.copyInto(input, CONTEXT, off, off + CHUNK)
                val chunk = arrayOf(input)
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

    private companion object {
        const val CHUNK = 512
        const val CONTEXT = 64
    }
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
            // The Hugging Face ONNX export also requires token_type_ids (all zeros, single segment),
            // as tools/guidance/build_guidance_db.py feeds it.
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size)), shape).use { typeT ->
                val inputs = buildMap {
                    put("input_ids", idT)
                    put("attention_mask", maskT)
                    if ("token_type_ids" in s.inputNames) put("token_type_ids", typeT)
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
    }

    override fun close() = s.close()
}

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
