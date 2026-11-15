package com.buzzsense.veritas.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Plays a recorded clip back from memory ("Recording ready" screen). Guardrail "Audio stays
 * local": the samples go straight into a static [AudioTrack] buffer, nothing touches disk,
 * and the track is released as soon as playback ends or is cancelled.
 */
class ClipPlayer {

    /** Suspends until the clip has played to the end; cancel the caller to stop early. */
    suspend fun play(samples: FloatArray, sampleRateHz: Int) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
            .build()
        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < samples.size) {
                delay(POLL_MS)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object { const val POLL_MS = 50L }
}
