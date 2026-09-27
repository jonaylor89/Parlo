package com.parlo.app.gemini

import android.util.Log
import com.parlo.app.model.SessionError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

sealed interface LiveEvent {
    /** Socket is open and the server acknowledged setup. [resumed] is true when a resumption handle was accepted. */
    data class Ready(val resumed: Boolean) : LiveEvent
    data class Audio(val pcm: ByteArray) : LiveEvent
    data class InputTranscript(val text: String) : LiveEvent
    data class OutputTranscript(val text: String) : LiveEvent
    object TurnComplete : LiveEvent
    object Interrupted : LiveEvent
    object GenerationComplete : LiveEvent
    data class ToolCalls(val calls: List<FunctionCall>) : LiveEvent
    data class ToolCallsCancelled(val ids: List<String>) : LiveEvent
    data class Reconnecting(val attempt: Int, val delayMs: Long) : LiveEvent
    /** Resumption was rejected; the next connection starts with empty server-side memory. */
    object ResumptionLost : LiveEvent
    data class Failed(val error: SessionError) : LiveEvent
    object Closed : LiveEvent
}

/**
 * Owns one logical Live session: WebSocket, setup handshake, resumption handle, and reconnect loop.
 * Feed it a [setupProvider] so voice/model changes are picked up on the next (re)connect.
 */
class GeminiLiveClient(
    private val okHttp: OkHttpClient,
    private val scope: CoroutineScope,
    private val wsUrl: String = GeminiApi.LIVE_WS_URL,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<LiveEvent> = _events

    @Volatile private var apiKey: String = ""
    @Volatile private var setupProvider: (() -> Setup)? = null
    @Volatile private var socket: WebSocket? = null
    private val ready = AtomicBoolean(false)
    private val closedByUser = AtomicBoolean(false)
    private val attempt = AtomicInteger(0)
    private var reconnectJob: Job? = null

    @Volatile var resumeHandle: String? = null
        private set
    @Volatile private var connectingWithHandle: String? = null
    @Volatile private var awaitingSetup = false

    val isReady: Boolean get() = ready.get()

    fun start(apiKey: String, setupProvider: () -> Setup) {
        this.apiKey = apiKey
        this.setupProvider = setupProvider
        closedByUser.set(false)
        attempt.set(0)
        openSocket()
    }

    /** Reconnect with a fresh setup (voice / model change), reusing the resumption handle. */
    fun reconnectWithNewSetup(setupProvider: () -> Setup) {
        this.setupProvider = setupProvider
        reconnectJob?.cancel()
        attempt.set(0)
        socket?.close(1000, "config change")
        socket = null
        ready.set(false)
        openSocket()
    }

    /** Called by the service when the network comes back so we don't wait out a long backoff. */
    fun nudgeReconnect() {
        if (closedByUser.get() || socket != null) return
        reconnectJob?.cancel()
        attempt.set(0)
        openSocket()
    }

    fun sendAudio(pcm: ByteArray, len: Int = pcm.size) {
        if (!ready.get()) return
        val b64 = Base64.getEncoder().encodeToString(if (len == pcm.size) pcm else pcm.copyOf(len))
        send(ClientMessage(realtimeInput = RealtimeInput(audio = Blob(GeminiApi.INPUT_MIME, b64))))
    }

    fun sendText(text: String) {
        if (!ready.get()) return
        send(ClientMessage(clientContent = ClientContent(turns = listOf(Content(role = "user", parts = listOf(Part(text = text)))), turnComplete = true)))
    }

    fun sendToolResponses(responses: List<FunctionResponse>) {
        if (!ready.get() || responses.isEmpty()) return
        send(ClientMessage(toolResponse = ToolResponse(responses)))
    }

    fun close() {
        closedByUser.set(true)
        reconnectJob?.cancel()
        ready.set(false)
        socket?.close(1000, "session ended")
        socket = null
        resumeHandle = null
        _events.tryEmit(LiveEvent.Closed)
    }

    // ---- internals ----

    private fun send(msg: ClientMessage) {
        val s = socket ?: return
        try {
            s.send(json.encodeToString(ClientMessage.serializer(), msg))
        } catch (e: Exception) {
            Log.w(TAG, "send failed", e)
        }
    }

    private fun openSocket() {
        if (closedByUser.get()) return
        val url = "$wsUrl?key=$apiKey"
        val req = Request.Builder().url(url).build()
        awaitingSetup = true
        connectingWithHandle = resumeHandle
        socket = okHttp.newWebSocket(req, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val provider = setupProvider ?: return
            val base = provider()
            val setup = base.copy(sessionResumption = SessionResumptionConfig(handle = resumeHandle))
            try {
                webSocket.send(json.encodeToString(ClientMessage.serializer(), ClientMessage(setup = setup)))
            } catch (e: Exception) {
                Log.e(TAG, "setup send failed", e)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket) return
            handleDrop(code, reason, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket) return
            val body = try { response?.body?.string().orEmpty() } catch (_: Exception) { "" }
            handleDrop(response?.code, body.ifBlank { t.message.orEmpty() }, t)
        }
    }

    private fun handleMessage(text: String) {
        val msg = try {
            json.decodeFromString(ServerMessage.serializer(), text)
        } catch (e: Exception) {
            Log.w(TAG, "unparseable server message: ${text.take(200)}", e)
            return
        }
        msg.setupComplete?.let {
            awaitingSetup = false
            ready.set(true)
            attempt.set(0)
            _events.tryEmit(LiveEvent.Ready(resumed = connectingWithHandle != null))
        }
        msg.serverContent?.let { sc ->
            if (sc.interrupted == true) _events.tryEmit(LiveEvent.Interrupted)
            sc.modelTurn?.parts?.forEach { part ->
                part.inlineData?.let { blob ->
                    if (blob.mimeType.startsWith("audio/pcm")) {
                        runCatching { Base64.getMimeDecoder().decode(blob.data) }
                            .onSuccess { _events.tryEmit(LiveEvent.Audio(it)) }
                    }
                }
            }
            sc.inputTranscription?.text?.takeIf { it.isNotEmpty() }?.let { _events.tryEmit(LiveEvent.InputTranscript(it)) }
            sc.outputTranscription?.text?.takeIf { it.isNotEmpty() }?.let { _events.tryEmit(LiveEvent.OutputTranscript(it)) }
            if (sc.generationComplete == true) _events.tryEmit(LiveEvent.GenerationComplete)
            if (sc.turnComplete == true) _events.tryEmit(LiveEvent.TurnComplete)
        }
        msg.toolCall?.let { if (it.functionCalls.isNotEmpty()) _events.tryEmit(LiveEvent.ToolCalls(it.functionCalls)) }
        msg.toolCallCancellation?.let { _events.tryEmit(LiveEvent.ToolCallsCancelled(it.ids)) }
        msg.sessionResumptionUpdate?.let { u ->
            if (u.resumable == true && !u.newHandle.isNullOrBlank()) resumeHandle = u.newHandle
        }
        msg.goAway?.let {
            Log.i(TAG, "goAway timeLeft=${it.timeLeft}; reconnecting proactively")
            val s = socket
            socket = null
            ready.set(false)
            s?.close(1000, "goAway")
            scheduleReconnect(SessionError.Unknown("server asked to reconnect"))
        }
        msg.error?.let { err ->
            val detail = (err as? JsonObject)?.let { o -> o["message"]?.jsonPrimitive?.content ?: o.toString() } ?: err.toString()
            Log.w(TAG, "server error: $detail")
        }
    }

    private fun handleDrop(code: Int?, reason: String, t: Throwable?) {
        socket = null
        ready.set(false)
        if (closedByUser.get()) return
        Log.w(TAG, "socket dropped code=$code reason=$reason", t)
        val err = GeminiApi.classifyError(code, reason, setupProvider?.invoke()?.model ?: "")
        val fatal = err is SessionError.InvalidApiKey || err is SessionError.QuotaExceeded ||
            err is SessionError.ModelNotFound || err is SessionError.UnsupportedConfig
        if (fatal && connectingWithHandle == null) {
            _events.tryEmit(LiveEvent.Failed(err))
            return
        }
        if (awaitingSetup && connectingWithHandle != null) {
            // Server refused our resumption handle (expired / invalid). Start fresh next time.
            resumeHandle = null
            _events.tryEmit(LiveEvent.ResumptionLost)
        }
        scheduleReconnect(err)
    }

    private fun scheduleReconnect(cause: SessionError) {
        if (closedByUser.get()) return
        val n = attempt.incrementAndGet()
        val delayMs = min(1000L shl (n - 1).coerceAtMost(5), MAX_BACKOFF_MS)
        _events.tryEmit(LiveEvent.Reconnecting(n, delayMs))
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!closedByUser.get() && socket == null) openSocket()
        }
        if (n > MAX_ATTEMPTS_BEFORE_ERROR) _events.tryEmit(LiveEvent.Failed(cause))
    }

    private companion object {
        const val TAG = "GeminiLive"
        const val MAX_BACKOFF_MS = 30_000L
        const val MAX_ATTEMPTS_BEFORE_ERROR = 8
    }
}
