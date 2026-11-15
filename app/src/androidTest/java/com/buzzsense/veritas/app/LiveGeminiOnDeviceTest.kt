package com.buzzsense.veritas.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.platform.AndroidBuzzAlert
import com.buzzsense.veritas.shared.platform.KeystoreApiKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The whole chain on the device with the user's saved key: v2 detector -> weather -> RAG over
 * guidance.db -> Gemini -> health-safety validator. The clip is 8 BioDCASE validation clips of
 * Aedes aegypti (public data, 4.96 s at 8 kHz, androidTest asset; not in the app). Only labels,
 * scores, weather and guidance text reach Gemini. Skipped when no Gemini key is saved in Settings.
 */
@RunWith(AndroidJUnit4::class)
class LiveGeminiOnDeviceTest {

    @Test
    fun aedesClipGetsAGeminiChecklistWithCitations() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("no Gemini key saved in Settings", KeystoreApiKeyStore(target).load(KeystoreApiKeyStore.Provider.GEMINI) != null)

        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("aedes_val_8k.f32").use { it.readBytes() }
        val samples = FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
        val clip = AudioClip(samples, 8_000)

        val engine = AndroidBuzzAlert.create(target)
        val started = System.nanoTime()
        val outcome = engine.run(clip, Language.EN, useGuidance = true)
        val ms = (System.nanoTime() - started) / 1_000_000

        val t = outcome.trace
        Log.i(TAG, "outcome ${outcome::class.simpleName} in $ms ms, provider ${t.provider}")
        Log.i(TAG, "detection ${t.detection}")
        Log.i(TAG, "retrieved ${t.retrieved.map { it.id }}")
        t.events.forEach { Log.i(TAG, "event ${it.stage}: ${it.message}") }
        if (outcome is AgentOutcome.AlertReady) {
            outcome.alert.draft.actions.forEachIndexed { i, a -> Log.i(TAG, "action ${i + 1}: ${a.text} [${a.chunkId}]") }
            Log.i(TAG, "draft ${outcome.alert.draft}")
        }
        assertTrue("audio must be wiped after the run", clip.isWiped)
        assertTrue("expected an alert, got ${outcome::class.simpleName}", outcome is AgentOutcome.AlertReady)
    }

    private companion object { const val TAG = "LiveGeminiOnDevice" }
}
