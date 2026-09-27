package com.parlo.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.parlo.app.gemini.GeminiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Streams 24 kHz / 16-bit mono PCM from Gemini through an [AudioTrack].
 * A Channel acts as a jitter buffer feeding a dedicated playback coroutine.
 */
class AudioPlayer(
    private val scope: CoroutineScope,
    private val onSpeakingChanged: (Boolean) -> Unit,
) {
    private var track: AudioTrack? = null
    private var job: Job? = null
    private var queue = Channel<ByteArray>(capacity = 256)
    private val generation = AtomicInteger(0)
    private val paused = AtomicBoolean(false)
    private val queuedBytes = AtomicInteger(0)

    @Volatile private var lastWriteMs = 0L
    val isSpeaking: Boolean get() = queuedBytes.get() > 0 || System.currentTimeMillis() - lastWriteMs < 250

    fun start() {
        if (job?.isActive == true) return
        val t = buildTrack() ?: return
        track = t
        t.play()
        val myGen = generation.get()
        job = scope.launch(Dispatchers.IO) {
            var speaking = false
            while (isActive) {
                val chunk = withTimeoutOrNull(200) { queue.receive() }
                if (chunk == null) {
                    if (speaking && queuedBytes.get() == 0) { speaking = false; onSpeakingChanged(false) }
                    continue
                }
                queuedBytes.addAndGet(-chunk.size)
                if (generation.get() != myGen) continue
                if (paused.get()) continue
                if (!speaking) { speaking = true; onSpeakingChanged(true) }
                var off = 0
                while (off < chunk.size && isActive) {
                    val w = try { t.write(chunk, off, chunk.size - off, AudioTrack.WRITE_BLOCKING) } catch (e: Exception) { -1 }
                    if (w < 0) { Log.w(TAG, "write error $w"); break }
                    off += w
                }
                lastWriteMs = System.currentTimeMillis()
            }
        }
    }

    fun enqueue(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        queuedBytes.addAndGet(pcm.size)
        if (queue.trySend(pcm).isFailure) queuedBytes.addAndGet(-pcm.size)
    }

    /** Barge-in: drop everything buffered and stop the hardware immediately. */
    fun flush() {
        generation.incrementAndGet()
        drainQueue()
        track?.let {
            try { it.pause(); it.flush(); if (!paused.get()) it.play() } catch (e: Exception) { Log.w(TAG, "flush failed", e) }
        }
        onSpeakingChanged(false)
    }

    fun setPaused(value: Boolean) {
        paused.set(value)
        track?.let { runCatching { if (value) it.pause() else it.play() } }
    }

    fun stop() {
        job?.cancel(); job = null
        drainQueue()
        track?.let { runCatching { it.pause(); it.flush(); it.stop(); it.release() } }
        track = null
        onSpeakingChanged(false)
    }

    private fun drainQueue() {
        while (queue.tryReceive().isSuccess) { /* discard */ }
        queuedBytes.set(0)
    }

    private fun buildTrack(): AudioTrack? {
        val rate = GeminiApi.OUTPUT_SAMPLE_RATE
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, rate * 2 / 5) // ≥200 ms
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed", e); null
        }
    }

    private companion object { const val TAG = "AudioPlayer" }
}
