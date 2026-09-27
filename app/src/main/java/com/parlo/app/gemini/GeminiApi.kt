package com.parlo.app.gemini

import com.parlo.app.model.SessionError

object GeminiApi {
    /** Some Live features occasionally require v1alpha; flip here. */
    const val API_VERSION = "v1beta"
    const val HOST = "generativelanguage.googleapis.com"
    const val LIVE_WS_URL =
        "wss://$HOST/ws/google.ai.generativelanguage.$API_VERSION.GenerativeService.BidiGenerateContent"
    const val MODELS_URL = "https://$HOST/$API_VERSION/models"

    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000
    const val INPUT_MIME = "audio/pcm;rate=$INPUT_SAMPLE_RATE"

    fun describeHttpError(code: Int, body: String): String = when (code) {
        400 -> if ("API key" in body || "API_KEY_INVALID" in body) "Invalid API key" else "Bad request: ${shorten(body)}"
        401, 403 -> "Invalid API key or insufficient permissions"
        404 -> "Model not found"
        429 -> "Quota exceeded"
        else -> "HTTP $code: ${shorten(body)}"
    }

    /** Maps a close reason / HTTP status / error text to a user-facing SessionError. */
    fun classifyError(code: Int?, reason: String, model: String): SessionError {
        val r = reason.lowercase()
        return when {
            code == 401 || code == 403 || "api key not valid" in r || "api_key_invalid" in r || "permission_denied" in r || "unauthenticated" in r ->
                SessionError.InvalidApiKey
            code == 429 || "quota" in r || "resource_exhausted" in r || "rate limit" in r ->
                SessionError.QuotaExceeded
            code == 404 || "not found" in r && "model" in r || "is not found for api version" in r ->
                SessionError.ModelNotFound(model)
            "invalid_argument" in r || "unsupported" in r || "does not support" in r || "not supported" in r || "invalid" in r && "config" in r ->
                SessionError.UnsupportedConfig(shorten(reason))
            "network" in r || "unable to resolve host" in r || "failed to connect" in r || "timeout" in r ->
                SessionError.Network
            else -> SessionError.Unknown(shorten(reason).ifBlank { "connection closed" + (code?.let { " ($it)" } ?: "") })
        }
    }

    private fun shorten(s: String, max: Int = 160) = s.trim().let { if (it.length > max) it.take(max) + "…" else it }
}
