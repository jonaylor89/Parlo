package com.parlo.app.gemini

import com.parlo.app.model.SessionConfig
import com.parlo.app.model.SessionError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Drives [GeminiLiveClient] against an in-process fake of the Gemini Live WebSocket endpoint.
 * No API key or network access needed; exercises the setup handshake, audio/text/tool traffic,
 * resumption handles, goAway, and fatal-error classification end to end through OkHttp.
 */
class GeminiLiveClientIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val okHttp = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Server side of one accepted WebSocket. */
    private class FakeLive : WebSocketListener() {
        val opened = LinkedBlockingQueue<WebSocket>()
        val received = LinkedBlockingQueue<String>()
        val closes = LinkedBlockingQueue<Int>()
        lateinit var socket: WebSocket
        var onSetup: (WebSocket, String) -> Unit = { ws, _ -> ws.send("""{"setupComplete":{}}""") }

        override fun onOpen(webSocket: WebSocket, response: Response) { socket = webSocket; opened.add(webSocket) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            received.add(text)
            if (text.contains("\"setup\"")) onSetup(webSocket, text)
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason); closes.add(code) }

        fun next(): String = received.poll(5, TimeUnit.SECONDS) ?: error("timed out waiting for client message")
    }

    private fun setupProvider(voice: String = "Puck") = {
        Setup(
            model = "models/fake-live",
            generationConfig = GenerationConfig(speechConfig = SpeechConfig(VoiceConfig(PrebuiltVoiceConfig(voice)))),
            systemInstruction = Content(parts = listOf(Part(text = PromptBuilder.systemInstruction(SessionConfig(voice = voice))))),
            tools = PromptBuilder.toolDeclarations(),
        )
    }

    private fun enqueueLive(): FakeLive = FakeLive().also { server.enqueue(MockResponse().withWebSocketUpgrade(it)) }

    /** Client plus an eagerly-subscribed replaying tap on its events so nothing is missed. */
    private inner class Harness {
        val client = GeminiLiveClient(okHttp, scope, wsUrl = server.url("/ws/live").toString().replace("http", "ws"))
        private val _recorded = MutableSharedFlow<LiveEvent>(replay = 1_000)
        val recorded: SharedFlow<LiveEvent> = _recorded

        init {
            // UNDISPATCHED: the collector subscribes before start() can emit anything.
            scope.launch(start = CoroutineStart.UNDISPATCHED) { client.events.collect { _recorded.emit(it) } }
        }

        /** Number of events recorded so far; pass to [await] to ignore earlier events. */
        fun mark(): Int = recorded.replayCache.size

        suspend inline fun <reified T : LiveEvent> await(since: Int = 0, timeoutMs: Long = 5_000): T =
            withTimeout(timeoutMs) { recorded.drop(since).filterIsInstance<T>().first() }
    }

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    @Test
    fun `handshake sends setup with api key in url and becomes ready`() = runBlocking<Unit> {
        val live = enqueueLive()
        val h = Harness()
        h.client.start("secret-key", setupProvider("Kore"))

        val setupText = live.next()
        val setup = json.parseToJsonElement(setupText).jsonObject["setup"]!!.jsonObject
        assertEquals("models/fake-live", setup["model"]!!.jsonPrimitive.content)
        assertEquals(
            "Kore",
            setup["generationConfig"]!!.jsonObject["speechConfig"]!!.jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!.jsonObject["voiceName"]!!.jsonPrimitive.content,
        )
        assertFalse("first connect must not carry a resumption handle", setupText.contains("handle"))

        assertFalse(h.await<LiveEvent.Ready>().resumed)
        assertTrue(h.client.isReady)
        assertEquals("/ws/live?key=secret-key", server.takeRequest().path)
        h.client.close()
        h.await<LiveEvent.Closed>()
    }

    @Test
    fun `audio text and tool responses are forwarded only once ready`() = runBlocking<Unit> {
        val live = enqueueLive()
        val h = Harness()
        h.client.sendText("too early") // dropped: not connected
        h.client.start("k", setupProvider())
        live.next() // setup
        h.await<LiveEvent.Ready>()

        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
        h.client.sendAudio(pcm, len = 4)
        val audio = json.parseToJsonElement(live.next()).jsonObject["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertEquals(GeminiApi.INPUT_MIME, audio["mimeType"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)), audio["data"]!!.jsonPrimitive.content)

        h.client.sendText("Hola")
        val cc = json.parseToJsonElement(live.next()).jsonObject["clientContent"]!!.jsonObject
        assertEquals(true, cc["turnComplete"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("Hola", cc["turns"]!!.jsonArray.single().jsonObject["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)

        h.client.sendToolResponses(listOf(FunctionResponse("id1", "save_vocab", buildJsonObject { put("result", "saved") })))
        val fr = json.parseToJsonElement(live.next()).jsonObject["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray.single().jsonObject
        assertEquals("id1", fr["id"]!!.jsonPrimitive.content)
        h.client.close()
    }

    @Test
    fun `server events are decoded into LiveEvents and the resumption handle is tracked`() = runBlocking<Unit> {
        val live = enqueueLive()
        val h = Harness()
        h.client.start("k", setupProvider())
        live.next()
        h.await<LiveEvent.Ready>()

        live.socket.send("""{"sessionResumptionUpdate":{"newHandle":"H1","resumable":true}}""")
        live.socket.send("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AQID"}}]},"outputTranscription":{"text":"Hola"}}}""")
        live.socket.send("""{"serverContent":{"inputTranscription":{"text":"hola"}}}""")
        live.socket.send("""{"toolCall":{"functionCalls":[{"id":"c1","name":"save_vocab","args":{"word":"gato"}}]}}""")
        live.socket.send("""{"toolCallCancellation":{"ids":["c1"]}}""")
        live.socket.send("""{"serverContent":{"interrupted":true}}""")
        live.socket.send("""{"totallyNewMessageType":{"x":1}}""") // must be ignored
        live.socket.send("""not json at all""") // must be ignored
        live.socket.send("""{"serverContent":{"generationComplete":true,"turnComplete":true}}""")

        assertTrue(h.await<LiveEvent.Audio>().pcm.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals("Hola", h.await<LiveEvent.OutputTranscript>().text)
        assertEquals("hola", h.await<LiveEvent.InputTranscript>().text)
        val call = h.await<LiveEvent.ToolCalls>().calls.single()
        assertEquals("save_vocab", call.name)
        assertEquals("gato", call.args!!["word"]!!.jsonPrimitive.content)
        assertEquals(listOf("c1"), h.await<LiveEvent.ToolCallsCancelled>().ids)
        h.await<LiveEvent.Interrupted>()
        h.await<LiveEvent.GenerationComplete>()
        h.await<LiveEvent.TurnComplete>()

        assertEquals("H1", h.client.resumeHandle)
        assertTrue(h.client.isReady)
        h.client.close()
        assertNull(h.client.resumeHandle)
    }

    @Test
    fun `goAway reconnects and resumes with the stored handle`() = runBlocking<Unit> {
        val first = enqueueLive()
        val second = enqueueLive()
        val h = Harness()
        h.client.start("k", setupProvider())
        first.next()
        h.await<LiveEvent.Ready>()
        first.socket.send("""{"sessionResumptionUpdate":{"newHandle":"H-resume","resumable":true}}""")
        h.waitForHandle("H-resume")

        val m = h.mark()
        first.socket.send("""{"goAway":{"timeLeft":"5s"}}""")
        val rec = h.await<LiveEvent.Reconnecting>()
        assertEquals(1, rec.attempt)
        assertEquals(1000L, rec.delayMs)

        val setup2 = second.next()
        assertTrue("second connect should carry the handle", setup2.contains("\"handle\":\"H-resume\""))
        assertTrue(h.await<LiveEvent.Ready>(since = m).resumed)
        h.client.close()
    }

    @Test
    fun `reconnectWithNewSetup swaps the voice and keeps the handle`() = runBlocking<Unit> {
        val first = enqueueLive()
        val second = enqueueLive()
        val h = Harness()
        h.client.start("k", setupProvider("Puck"))
        assertTrue(first.next().contains("\"voiceName\":\"Puck\""))
        h.await<LiveEvent.Ready>()
        first.socket.send("""{"sessionResumptionUpdate":{"newHandle":"H2","resumable":true}}""")
        h.waitForHandle("H2")

        val m = h.mark()
        h.client.reconnectWithNewSetup(setupProvider("Aoede"))
        val setup2 = second.next()
        assertTrue(setup2.contains("\"voiceName\":\"Aoede\""))
        assertTrue(setup2.contains("\"handle\":\"H2\""))
        assertTrue(h.await<LiveEvent.Ready>(since = m).resumed)
        assertEquals(1000, first.closes.poll(5, TimeUnit.SECONDS))
        h.client.close()
    }

    @Test
    fun `rejected resumption handle falls back to a fresh session`() = runBlocking<Unit> {
        val first = enqueueLive()
        val second = enqueueLive().apply {
            // server rejects the handle: close during setup instead of acknowledging
            onSetup = { ws, _ -> ws.close(1008, "session resumption handle expired") }
        }
        val third = enqueueLive()
        val h = Harness()
        h.client.start("k", setupProvider())
        first.next()
        h.await<LiveEvent.Ready>()
        first.socket.send("""{"sessionResumptionUpdate":{"newHandle":"OLD","resumable":true}}""")
        h.waitForHandle("OLD")

        val m = h.mark()
        first.socket.send("""{"goAway":{}}""")
        assertTrue(second.next().contains("\"handle\":\"OLD\""))
        h.await<LiveEvent.ResumptionLost>(since = m, timeoutMs = 10_000)
        assertNull(h.client.resumeHandle)

        val setup3 = third.next()
        assertFalse(setup3.contains("handle"))
        assertFalse(h.await<LiveEvent.Ready>(since = m, timeoutMs = 10_000).resumed)
        h.client.close()
    }

    @Test
    fun `http 403 on connect is a fatal InvalidApiKey and does not retry`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"PERMISSION_DENIED"}}"""))
        val h = Harness()
        h.client.start("bad-key", setupProvider())
        assertEquals(SessionError.InvalidApiKey, h.await<LiveEvent.Failed>().error)
        delay(300)
        assertEquals(1, server.requestCount)
        assertFalse(h.client.isReady)
        assertTrue(h.recorded.replayCache.none { it is LiveEvent.Reconnecting })
    }

    @Test
    fun `transient drop schedules an exponential reconnect`() = runBlocking<Unit> {
        val first = enqueueLive()
        val second = enqueueLive()
        val h = Harness()
        h.client.start("k", setupProvider())
        first.next()
        h.await<LiveEvent.Ready>()

        val m = h.mark()
        first.socket.close(1011, "internal error")
        assertEquals(1, h.await<LiveEvent.Reconnecting>().attempt)

        second.next()
        h.await<LiveEvent.Ready>(since = m, timeoutMs = 10_000)
        assertEquals(2, server.requestCount)
        h.client.close()
        assertEquals(1000, second.closes.poll(5, TimeUnit.SECONDS))
    }

    private suspend fun Harness.waitForHandle(handle: String) =
        withTimeout(5_000) { while (client.resumeHandle != handle) delay(10) }
}
