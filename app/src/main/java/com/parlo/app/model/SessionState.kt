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

/** What the one button on an error card does. */
enum class ErrorAction(val label: String) { UPDATE_KEY("Update key"), OPEN_SETTINGS("Open settings"), REFRESH_MODELS("Refresh"), RETRY("Try again"), START_AGAIN("Start again") }

/**
 * User-facing error: a short [title], a plain-language [body] with what to do next, one [action],
 * and the raw [detail] (kept behind a "Details" expander). [message] is the one-line summary used
 * in logs and the notification.
 */
sealed class SessionError(
    val title: String,
    val body: String,
    val action: ErrorAction,
    val detail: String = "",
) {
    val message: String get() = if (detail.isBlank()) title else "$title — $detail"

    object InvalidApiKey : SessionError(
        "Your Gemini key isn't working",
        "It may have been revoked or copied incompletely. Paste it again from AI Studio.",
        ErrorAction.UPDATE_KEY,
    )

    object QuotaExceeded : SessionError(
        "You've hit today's Gemini limit",
        "Free keys reset daily. Try again later, or check billing in AI Studio.",
        ErrorAction.RETRY,
    )

    class ModelNotFound(model: String) : SessionError(
        "That voice model isn't available",
        "Gemini doesn't recognise the model Parlo tried to use. Switch back to automatic in Settings.",
        ErrorAction.OPEN_SETTINGS,
        detail = "model \"$model\" not found",
    )

    class UnsupportedConfig(detail: String) : SessionError(
        "Gemini rejected this setup",
        "The model didn't accept the voice settings. Try another model in Settings.",
        ErrorAction.OPEN_SETTINGS,
        detail = detail,
    )

    object NoLiveModel : SessionError(
        "Can't find a voice model",
        "Your key works, but no live voice models showed up. Try refreshing.",
        ErrorAction.REFRESH_MODELS,
    )

    object MissingApiKey : SessionError(
        "Connect Gemini to start",
        "Parlo talks through your own Gemini key. It stays encrypted on this phone.",
        ErrorAction.UPDATE_KEY,
    )

    object Network : SessionError(
        "You're offline",
        "Parlo needs a connection to talk. It reconnects on its own once you're back online.",
        ErrorAction.RETRY,
    )

    class Unknown(detail: String) : SessionError(
        "Something went wrong",
        "The walk stopped unexpectedly. Starting again usually fixes it.",
        ErrorAction.START_AGAIN,
        detail = detail,
    )
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
