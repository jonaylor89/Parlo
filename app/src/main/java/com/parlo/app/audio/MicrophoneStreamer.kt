package com.parlo.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.parlo.app.gemini.GeminiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Captures 16 kHz / 16-bit / mono PCM in ~30 ms chunks and hands them to [onChunk].
 * [muted] gates delivery without stopping capture so unmute is instant.
 */
class MicrophoneStreamer(
    private val scope: CoroutineScope,
    private val onChunk: (ByteArray, Int) -> Unit,
    private val onLevel: (Float) -> Unit = {},
) {
    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var job: Job? = null
    private val muted = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    val isRunning: Boolean get() = job?.isActive == true

    fun setMuted(value: Boolean) = muted.set(value)
    fun setPaused(value: Boolean) = paused.set(value)

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRunning) return true
        val sampleRate = GeminiApi.INPUT_SAMPLE_RATE
        val chunkBytes = sampleRate * 2 * CHUNK_MS / 1000
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, chunkBytes * 4)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed", e); return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); Log.e(TAG, "AudioRecord not initialized"); return false
        }
        attachEffects(rec.audioSessionId)
        record = rec
        rec.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(chunkBytes)
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (muted.get() || paused.get()) { onLevel(0f); continue }
                onLevel(rms(buf, n))
                onChunk(buf.copyOf(n), n)
            }
        }
        return true
    }

    fun stop() {
        job?.cancel(); job = null
        try { record?.stop() } catch (_: Exception) {}
        record?.release(); record = null
        aec?.release(); aec = null
        ns?.release(); ns = null
    }

    private fun attachEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            aec = runCatching { AcousticEchoCanceler.create(sessionId)?.apply { enabled = true } }.getOrNull()
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = runCatching { NoiseSuppressor.create(sessionId)?.apply { enabled = true } }.getOrNull()
        }
    }

    private fun rms(buf: ByteArray, n: Int): Float {
        var sum = 0L
        var i = 0
        while (i + 1 < n) {
            val s = (buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)
            sum += abs(s.toShort().toInt())
            i += 2
        }
        val avg = sum.toFloat() / (n / 2).coerceAtLeast(1)
        return (avg / 8000f).coerceIn(0f, 1f)
    }

    private companion object {
        const val TAG = "MicStreamer"
        const val CHUNK_MS = 30
    }
}
