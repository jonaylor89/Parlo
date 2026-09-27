package com.parlo.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Short, distinct synthesized tones so the user never has to look at the screen.
 * Generated on the fly (no bundled assets) and played on the voice-communication stream so they follow the earbud route.
 */
class Earcons(private val scope: CoroutineScope) {

    enum class Cue { SESSION_STARTED, RECONNECTING, RECONNECTED, MUTED, UNMUTED, SESSION_ENDED, ERROR, VOCAB_SAVED }

    private data class Note(val freqHz: Double, val ms: Int, val gapMs: Int = 0)

    private fun pattern(cue: Cue): List<Note> = when (cue) {
        Cue.SESSION_STARTED -> listOf(Note(660.0, 90, 30), Note(880.0, 90, 30), Note(1100.0, 140))
        Cue.RECONNECTING -> listOf(Note(520.0, 120, 120), Note(520.0, 120))
        Cue.RECONNECTED -> listOf(Note(700.0, 90, 30), Note(1050.0, 160))
        Cue.MUTED -> listOf(Note(700.0, 80, 40), Note(450.0, 140))
        Cue.UNMUTED -> listOf(Note(450.0, 80, 40), Note(700.0, 140))
        Cue.SESSION_ENDED -> listOf(Note(1100.0, 90, 30), Note(880.0, 90, 30), Note(660.0, 200))
        Cue.ERROR -> listOf(Note(330.0, 200, 80), Note(330.0, 200))
        Cue.VOCAB_SAVED -> listOf(Note(1400.0, 60, 20), Note(1800.0, 60))
    }

    fun play(cue: Cue) {
        scope.launch(Dispatchers.IO) { runCatching { playBlocking(pattern(cue)) } }
    }

    private fun playBlocking(notes: List<Note>) {
        val pcm = render(notes)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val totalMs = notes.sumOf { it.ms + it.gapMs } + 60
            Thread.sleep(totalMs.toLong())
        } finally {
            runCatching { track.stop(); track.release() }
        }
    }

    private fun render(notes: List<Note>): ByteArray {
        val totalSamples = notes.sumOf { (it.ms + it.gapMs) * RATE / 1000 }
        val out = ByteArray(totalSamples * 2)
        var idx = 0
        for (n in notes) {
            val samples = n.ms * RATE / 1000
            val fade = (samples * 0.15).toInt().coerceAtLeast(1)
            for (i in 0 until samples) {
                val env = when {
                    i < fade -> i / fade.toDouble()
                    i > samples - fade -> (samples - i) / fade.toDouble()
                    else -> 1.0
                }
                val v = (sin(2 * PI * n.freqHz * i / RATE) * AMPLITUDE * env).toInt()
                out[idx++] = (v and 0xff).toByte()
                out[idx++] = ((v shr 8) and 0xff).toByte()
            }
            idx += (n.gapMs * RATE / 1000) * 2
        }
        return out
    }

    private companion object {
        const val RATE = 24_000
        const val AMPLITUDE = 9000
    }
}
