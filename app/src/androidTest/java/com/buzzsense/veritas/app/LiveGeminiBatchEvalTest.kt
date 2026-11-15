package com.buzzsense.veritas.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.ProviderTierId
import com.buzzsense.veritas.shared.platform.AndroidBuzzAlert
import com.buzzsense.veritas.shared.platform.KeystoreApiKeyStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The app's real chain on 40 recordings (10 Aedes, 10 Culex, 10 other mosquito, 10 background),
 * sampled at random from BioDCASE validation (sites D1/D3/D5, not in the app's store) and HumBugDB
 * windows the store does not hold. Built by the pipeline into androidTest assets (eval40*.f32/json).
 * Gemini is called only when the detector hears a mosquito: at most one request per recording,
 * plus a retry if a draft fails validation, 4 s apart. One JSON line per run goes to Logcat (tag
 * GeminiBatchEval) for the report. Skipped without a saved Gemini key.
 */
@RunWith(AndroidJUnit4::class)
class LiveGeminiBatchEvalTest {

    @Test
    fun fortyRecordingsThroughTheWholeChain() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val target = inst.targetContext
        assumeTrue("no Gemini key saved in Settings", KeystoreApiKeyStore(target).load(KeystoreApiKeyStore.Provider.GEMINI) != null)

        val meta = JSONObject(inst.context.assets.open("eval40.json").use { it.readBytes().decodeToString() })
        val n = meta.getInt("samples_per_recording")
        val recs = meta.getJSONArray("recordings")
        val bytes = inst.context.assets.open("eval40_8k.f32").use { it.readBytes() }
        val all = FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }

        val engine = AndroidBuzzAlert.create(target)
        for (i in 0 until recs.length()) {
            val m = recs.getJSONObject(i)
            val clip = AudioClip(all.copyOfRange(i * n, (i + 1) * n), 8_000)
            val t0 = System.nanoTime()
            val out = engine.run(clip, Language.EN, useGuidance = true)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val tr = out.trace
            val calledLlm = tr.events.any { it.stage == "llm" || it.stage == "failover" }
            val row = JSONObject()
                .put("i", i).put("truth", m.getString("truth")).put("species", m.getString("species")).put("site", m.getString("site"))
                .put("mosquito", tr.detection?.mosquito?.name).put("aedes", tr.detection?.aedesAegypti?.name)
                .put("votes", JSONObject((tr.detection?.classVotes ?: emptyMap<String, Float>()).mapValues { it.value.toDouble() }))
                .put("f0", tr.detection?.dominantFrequencyHz?.toDouble())
                .put("outcome", out::class.simpleName).put("provider", tr.provider?.name).put("llm_called", calledLlm).put("ms", ms)
                .put("events", JSONArray(tr.events.filter { it.stage in setOf("llm", "failover", "validate", "validation", "confidence-gate", "fallback") }
                    .map { "${it.stage}: ${it.message}" }))
            if (out is AgentOutcome.AlertReady) {
                val a = out.alert
                row.put("actions", a.draft.actions.size)
                    .put("cited_ok", a.draft.actions.all { it.chunkId in a.citations.keys })
                    .put("gemini_ok", tr.provider == ProviderTierId.GEMINI)
                    .put("summary", a.draft.summary).put("urgency", a.draft.urgency.name)
                    .put("checklist", JSONArray(a.draft.actions.map { "${it.text} [${it.chunkId}]" }))
            }
            Log.i(TAG, row.toString())
            if (calledLlm) delay(4_000)                       // stay far below the free-tier rate limit
        }
    }

    private companion object { const val TAG = "GeminiBatchEval" }
}
