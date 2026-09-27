package com.parlo.app.gemini

import com.parlo.app.model.SessionError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiTest {

    @Test
    fun `urls are derived from the centralised api version`() {
        assertEquals(
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.${GeminiApi.API_VERSION}.GenerativeService.BidiGenerateContent",
            GeminiApi.LIVE_WS_URL,
        )
        assertEquals("https://generativelanguage.googleapis.com/${GeminiApi.API_VERSION}/models", GeminiApi.MODELS_URL)
        assertEquals("audio/pcm;rate=16000", GeminiApi.INPUT_MIME)
        assertEquals(24_000, GeminiApi.OUTPUT_SAMPLE_RATE)
    }

    @Test
    fun `classifyError maps status codes`() {
        assertEquals(SessionError.InvalidApiKey, GeminiApi.classifyError(401, "", "m"))
        assertEquals(SessionError.InvalidApiKey, GeminiApi.classifyError(403, "", "m"))
        assertEquals(SessionError.QuotaExceeded, GeminiApi.classifyError(429, "", "m"))
        assertTrue(GeminiApi.classifyError(404, "", "gemini-x") is SessionError.ModelNotFound)
        assertTrue(GeminiApi.classifyError(404, "", "gemini-x").message.contains("gemini-x"))
    }

    @Test
    fun `classifyError maps close reasons`() {
        assertEquals(SessionError.InvalidApiKey, GeminiApi.classifyError(1008, "API key not valid. Please pass a valid API key.", "m"))
        assertEquals(SessionError.InvalidApiKey, GeminiApi.classifyError(null, "PERMISSION_DENIED", "m"))
        assertEquals(SessionError.QuotaExceeded, GeminiApi.classifyError(1011, "RESOURCE_EXHAUSTED: quota", "m"))
        assertTrue(GeminiApi.classifyError(1008, "models/foo is not found for API version v1beta", "foo") is SessionError.ModelNotFound)
        assertTrue(GeminiApi.classifyError(1007, "Requested response modality AUDIO is not supported", "m") is SessionError.UnsupportedConfig)
        assertEquals(SessionError.Network, GeminiApi.classifyError(null, "Unable to resolve host generativelanguage.googleapis.com", "m"))
        assertEquals(SessionError.Network, GeminiApi.classifyError(null, "timeout", "m"))
    }

    @Test
    fun `unknown reasons fall through with detail and code`() {
        val e = GeminiApi.classifyError(1006, "", "m")
        assertTrue(e is SessionError.Unknown)
        assertTrue(e.message.contains("1006"))
        val e2 = GeminiApi.classifyError(null, "x".repeat(500), "m")
        assertTrue(e2.message.length < 220)
    }

    @Test
    fun `describeHttpError`() {
        assertEquals("Invalid API key", GeminiApi.describeHttpError(400, """{"error":{"message":"API key not valid","status":"INVALID_ARGUMENT","reason":"API_KEY_INVALID"}}"""))
        assertEquals("Model not found", GeminiApi.describeHttpError(404, ""))
        assertEquals("Quota exceeded", GeminiApi.describeHttpError(429, ""))
        assertTrue(GeminiApi.describeHttpError(500, "boom").startsWith("HTTP 500"))
    }
}
