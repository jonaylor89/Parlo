package com.parlo.app.model

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, RECONNECTING, ENDING, ERROR }

enum class AudioState { IDLE, LISTENING, SPEAKING, MUTED, RECONNECTING }

enum class Speaker { USER, TUTOR, SYSTEM }

data class TranscriptTurn(
    val id: Long,
    val speaker: Speaker,
    val text: String,
    val timestampMs: Long,
    val isFinal: Boolean = true,
)

sealed class SessionError(val message: String) {
    object InvalidApiKey : SessionError("Invalid Gemini API key. Check Settings.")
    object QuotaExceeded : SessionError("Gemini quota exceeded. Try again later or check billing.")
    class ModelNotFound(model: String) : SessionError("Model \"$model\" not found. Pick another model in Settings.")
    class UnsupportedConfig(detail: String) : SessionError("Unsupported session config: $detail")
    object MissingApiKey : SessionError("Add your Gemini API key in Settings to start.")
    object Network : SessionError("No network connection.")
    class Unknown(detail: String) : SessionError("Session error: $detail")
}

/** Snapshot of the live session, published by the service and mirrored in the ViewModel. */
data class LiveSessionState(
    val connection: ConnectionState = ConnectionState.IDLE,
    val audio: AudioState = AudioState.IDLE,
    val muted: Boolean = false,
    val config: SessionConfig = SessionConfig(),
    val transcript: List<TranscriptTurn> = emptyList(),
    val error: SessionError? = null,
    val sessionStartMs: Long = 0L,
    val recapInProgress: Boolean = false,
    val statusText: String = "",
) {
    val isActive: Boolean get() = connection != ConnectionState.IDLE && connection != ConnectionState.ERROR
}
