package com.parlo.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.parlo.app.gemini.GeminiApi
import kotlinx.coroutines.CoroutineDispatcher
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
 * Streams 24 kHz / 16-bit mono PCM from Gemini through a [PcmSink] (an [AudioTrack] in production).
 * A Channel acts as a jitter buffer feeding a dedicated playback coroutine.
 *
 * Every chunk is stamped with the flush generation current when it was enqueued; [flush] bumps the
 * generation so chunks belonging to the interrupted turn are dropped while later ones play normally.
 */
class AudioPlayer(
    private val scope: CoroutineScope,
    private val onSpeakingChanged: (Boolean) -> Unit,
    private val sinkFactory: () -> PcmSink? = { AudioTrackSink.create() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Minimal blocking PCM output so the player can be exercised without Android audio. */
    interface PcmSink {
        fun play()
        fun pause()
        fun flush()
        /** Returns bytes written, or a negative error code. */
        fun write(data: ByteArray, offset: Int, size: Int): Int
        fun release()
    }

    private class Chunk(val gen: Int, val pcm: ByteArray)

    private var sink: PcmSink? = null
    private var job: Job? = null
    private val queue = Channel<Chunk>(capacity = 256)
    private val generation = AtomicInteger(0)
    private val paused = AtomicBoolean(false)
    private val queuedBytes = AtomicInteger(0)

    /** Chunks discarded because they belonged to an interrupted turn. Diagnostic only. */
    val droppedChunks = AtomicInteger(0)
    /** Times the sink died mid-session and was rebuilt. Diagnostic only. */
    val sinkRebuilds = AtomicInteger(0)

    @Volatile private var lastWriteMs = 0L
    val isSpeaking: Boolean get() = queuedBytes.get() > 0 || clock() - lastWriteMs < 250

    fun start() {
        if (job?.isActive == true) return
        val s = sinkFactory() ?: return
        sink = s
        s.play()
        job = scope.launch(dispatcher) {
            var speaking = false
            while (isActive) {
                val chunk = withTimeoutOrNull(200) { queue.receive() }
                if (chunk == null) {
                    if (speaking && queuedBytes.get() == 0) { speaking = false; onSpeakingChanged(false) }
                    continue
                }
                queuedBytes.addAndGet(-chunk.pcm.size)
                if (chunk.gen != generation.get()) { droppedChunks.incrementAndGet(); continue }
                if (paused.get()) continue
                if (!speaking) { speaking = true; onSpeakingChanged(true) }
                writeChunk(chunk.pcm)
                lastWriteMs = clock()
            }
        }
    }

    private fun writeChunk(pcm: ByteArray) {
        var off = 0
        var retriedWithNewSink = false
        while (off < pcm.size) {
            val s = sink ?: return
            val w = try { s.write(pcm, off, pcm.size - off) } catch (e: Exception) { Log.w(TAG, "write threw", e); -1 }
            if (w >= 0) { off += w; continue }
            Log.w(TAG, "write error $w")
            if (retriedWithNewSink || !rebuildSink()) return
            retriedWithNewSink = true
        }
    }

    /** The track goes dead when the output device disappears (e.g. earbuds disconnect); rebuild once. */
    private fun rebuildSink(): Boolean {
        sink?.let { runCatching { it.release() } }
        val fresh = sinkFactory() ?: run { sink = null; return false }
        sink = fresh
        if (!paused.get()) runCatching { fresh.play() }
        sinkRebuilds.incrementAndGet()
        return true
    }

    fun enqueue(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        queuedBytes.addAndGet(pcm.size)
        if (queue.trySend(Chunk(generation.get(), pcm)).isFailure) queuedBytes.addAndGet(-pcm.size)
    }

    /** Barge-in: drop everything buffered and stop the hardware immediately. */
    fun flush() {
        generation.incrementAndGet()
        drainQueue()
        sink?.let {
            try { it.pause(); it.flush(); if (!paused.get()) it.play() } catch (e: Exception) { Log.w(TAG, "flush failed", e) }
        }
        onSpeakingChanged(false)
    }

    fun setPaused(value: Boolean) {
        paused.set(value)
        sink?.let { runCatching { if (value) it.pause() else it.play() } }
    }

    fun stop() {
        job?.cancel(); job = null
        drainQueue()
        sink?.let { runCatching { it.pause(); it.flush(); it.release() } }
        sink = null
        onSpeakingChanged(false)
    }

    private fun drainQueue() {
        while (queue.tryReceive().isSuccess) { /* discard */ }
        queuedBytes.set(0)
    }

    private companion object { const val TAG = "AudioPlayer" }
}

class AudioTrackSink private constructor(private val track: AudioTrack) : AudioPlayer.PcmSink {
    override fun play() = track.play()
    override fun pause() = track.pause()
    override fun flush() = track.flush()
    override fun write(data: ByteArray, offset: Int, size: Int) = track.write(data, offset, size, AudioTrack.WRITE_BLOCKING)
    override fun release() { runCatching { track.stop() }; track.release() }

    companion object {
        private const val TAG = "AudioTrackSink"

        fun create(): AudioTrackSink? {
            val rate = GeminiApi.OUTPUT_SAMPLE_RATE
            val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufSize = maxOf(minBuf, rate * 2 / 5) // ≥200 ms
            return try {
                val track = AudioTrack.Builder()
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
                AudioTrackSink(track)
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack init failed", e); null
            }
        }
    }
}
