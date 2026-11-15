package com.buzzsense.veritas.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzzsense.veritas.shared.audio.AudioClip
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.platform.AndroidBuzzAlert
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/**
 * On a device or emulator: the bundled files load exactly as the app loads them.
 * `create()` copies the stores out of the APK, loads jniLibs/<abi>/libvec0.so into the
 * bundled SQLite (AcousticStore refuses to open without vec_version()), and opens Silero.
 */
@RunWith(AndroidJUnit4::class)
class BundledEngineTest {

    @Test
    fun bundledEngineStartsAndRunsTheGraphOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(emptyList<String>(), AndroidBuzzAlert.missingFiles(context))

        val started = System.nanoTime()
        val engine = AndroidBuzzAlert.create(context)
        val startMs = (System.nanoTime() - started) / 1_000_000

        // Wingbeat-like 3 s signal: 600 Hz fundamental plus harmonics.
        val samples = FloatArray(3 * 8_000) { i ->
            (1..4).sumOf { h -> 0.3 / h * sin(2 * PI * h * 600.0 * i / 8_000) }.toFloat()
        }
        val clip = AudioClip(samples, 8_000)
        val runStarted = System.nanoTime()
        val outcome = engine.run(clip, Language.EN)
        val runMs = (System.nanoTime() - runStarted) / 1_000_000

        Log.i(TAG, "engine start ${startMs} ms, run ${runMs} ms, outcome ${outcome::class.simpleName}, " +
            "detection ${outcome.trace.detection}, events ${outcome.trace.events.map { it.stage + ": " + it.message }}")
        assertTrue("audio must be wiped after the run", clip.isWiped)
    }

    private companion object { const val TAG = "BundledEngineTest" }
}
