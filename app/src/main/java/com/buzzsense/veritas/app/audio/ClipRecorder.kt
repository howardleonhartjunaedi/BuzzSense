package com.buzzsense.veritas.app.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.buzzsense.veritas.shared.audio.AudioClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Records one clip into memory for the detector. Guardrail "Audio stays local": samples
 * live only in the returned [AudioClip], which the engine wipes after the run; nothing is
 * written to disk or logged.
 *
 * - 8 kHz mono float, the detector's rate (feature_spec.json), so no resampling is needed;
 *   falls back to 16 kHz on devices without 8 kHz capture (the detector resamples).
 * - Prefers the UNPROCESSED source. The default MIC source may apply noise suppression,
 *   which removes a steady wingbeat tone along with fan noise.
 */
class ClipRecorder(private val context: Context) {

    private val stopRequested = AtomicBoolean(false)

    /** Ends the current [record] early; the clip keeps what was captured so far. */
    fun stop() = stopRequested.set(true)

    /**
     * @param onProgress called on the recording thread with 0..1; keep it cheap.
     * @throws IllegalStateException if the microphone cannot be opened.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(seconds: Int, onProgress: (Float) -> Unit = {}): AudioClip = withContext(Dispatchers.IO) {
        stopRequested.set(false)
        val (record, rate) = open()
        val target = FloatArray(seconds * rate)
        var filled = 0
        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "microphone is busy" }
            val chunk = rate / 20 // 50 ms
            while (filled < target.size && !stopRequested.get()) {
                coroutineContext.ensureActive()
                val n = record.read(target, filled, minOf(chunk, target.size - filled), AudioRecord.READ_BLOCKING)
                check(n >= 0) { "microphone read failed ($n)" }
                filled += n
                onProgress(filled.toFloat() / target.size)
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        val samples = if (filled == target.size) target else target.copyOf(filled).also { target.fill(0f) }
        AudioClip(samples, rate)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun open(): Pair<AudioRecord, Int> {
        val source = if (supportsUnprocessed()) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        for (rate in RATES) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            if (min <= 0) continue
            val record = AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(min, rate * 4))
            if (record.state == AudioRecord.STATE_INITIALIZED) return record to rate
            record.release()
        }
        error("microphone cannot record at ${RATES.joinToString("/")} Hz")
    }

    private fun supportsUnprocessed(): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    }

    private companion object {
        val RATES = intArrayOf(8_000, 16_000)
    }
}
