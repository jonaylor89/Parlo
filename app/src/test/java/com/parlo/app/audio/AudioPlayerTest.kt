package com.parlo.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList

class AudioPlayerTest {
    private class FakeSink : AudioPlayer.PcmSink {
        val written = ByteArrayOutputStream()
        val events = CopyOnWriteArrayList<String>()
        @Volatile var dead = false
        @Volatile var released = false

        override fun play() { events += "play" }
        override fun pause() { events += "pause" }
        override fun flush() { events += "flush" }
        override fun release() { released = true; events += "release" }
        override fun write(data: ByteArray, offset: Int, size: Int): Int {
            if (dead) return -6 // AudioTrack.ERROR_DEAD_OBJECT
            synchronized(written) { written.write(data, offset, size) }
            return size
        }
        fun bytes(): ByteArray = synchronized(written) { written.toByteArray() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sinks = CopyOnWriteArrayList<FakeSink>()
    private val speaking = CopyOnWriteArrayList<Boolean>()

    private fun player() = AudioPlayer(
        scope,
        onSpeakingChanged = { speaking += it },
        sinkFactory = { FakeSink().also { sinks += it } },
        dispatcher = Dispatchers.Default,
    )

    @After fun tearDown() = scope.cancel()

    private fun pcm(fill: Int, size: Int = 480) = ByteArray(size) { fill.toByte() }

    private suspend fun awaitWritten(sink: FakeSink, bytes: Int) = withTimeout(3_000) {
        while (sink.bytes().size < bytes) delay(10)
    }

    @Test
    fun audioKeepsPlayingAfterABargeInFlush() = runBlocking {
        val p = player()
        p.start()
        val sink = sinks.single()

        p.enqueue(pcm(1))
        awaitWritten(sink, 480)

        p.flush() // user interrupted the tutor
        p.enqueue(pcm(2))
        p.enqueue(pcm(3))
        awaitWritten(sink, 480 * 3)

        val out = sink.bytes()
        assertEquals(2.toByte(), out[480])
        assertEquals(3.toByte(), out[960])
        assertTrue(sink.events.containsAll(listOf("pause", "flush", "play")))
        p.stop()
    }

    @Test
    fun chunksQueuedBeforeAFlushAreDroppedButLaterOnesPlay() = runBlocking {
        val p = player()
        // Don't start yet so the pre-flush chunks sit in the queue.
        repeat(5) { p.enqueue(pcm(9)) }
        p.flush()
        p.enqueue(pcm(4))
        p.start()
        val sink = sinks.single()
        awaitWritten(sink, 480)
        delay(150)
        val out = sink.bytes()
        assertEquals(480, out.size)
        assertEquals(4.toByte(), out[0])
        assertFalse(out.any { it == 9.toByte() })
        p.stop()
    }

    @Test
    fun survivesRepeatedFlushesAcrossManyTurns() = runBlocking {
        val p = player()
        p.start()
        val sink = sinks.single()
        var expected = 0
        repeat(20) { turn ->
            p.flush()
            p.enqueue(pcm(turn))
            expected += 480
            awaitWritten(sink, expected)
        }
        assertEquals(480 * 20, sink.bytes().size)
        p.stop()
    }

    @Test
    fun deadSinkIsRebuiltAndPlaybackContinues() = runBlocking {
        val p = player()
        p.start()
        val first = sinks.single()
        p.enqueue(pcm(1))
        awaitWritten(first, 480)

        first.dead = true // earbuds disconnected: AudioTrack returns ERROR_DEAD_OBJECT
        p.enqueue(pcm(2))
        withTimeout(3_000) { while (sinks.size < 2) delay(10) }
        val second = sinks[1]
        awaitWritten(second, 480)

        assertTrue(first.released)
        assertEquals(1, p.sinkRebuilds.get())
        assertEquals(2.toByte(), second.bytes()[0])
        assertTrue(second.events.contains("play"))
        p.stop()
    }

    @Test
    fun speakingCallbacksBracketPlayback() = runBlocking {
        val p = player()
        p.start()
        val sink = sinks.single()
        p.enqueue(pcm(1))
        awaitWritten(sink, 480)
        withTimeout(3_000) { while (!speaking.contains(false) || speaking.first() != true) delay(10) }
        assertEquals(listOf(true, false), speaking.take(2))
        p.stop()
    }
}
