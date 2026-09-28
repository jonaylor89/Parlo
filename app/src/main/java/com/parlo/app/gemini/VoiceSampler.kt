package com.parlo.app.gemini

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * Fetches a short spoken sample of a prebuilt voice via the Gemini TTS models (`generateContent`
 * with an AUDIO response). Two steps: a text model translates the greeting into the target
 * language once per language, then the TTS model reads it in the requested voice. Both results are
 * cached in memory so skimming through voices costs one small TTS call each.
 */
class VoiceSampler(
    private val client: OkHttpClient,
    private val baseUrl: String = GeminiApi.MODELS_URL,
    private val ttsModels: List<String> = TTS_MODELS,
    private val textModels: List<String> = TEXT_MODELS,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val greetings = HashMap<String, String>()
    private val samples = HashMap<String, ByteArray>()

    /** 24 kHz / 16-bit mono PCM of [voice] greeting the learner in [language]. */
    fun sample(apiKey: String, voice: String, language: String, dialect: String): ByteArray {
        val greeting = greeting(apiKey, language, dialect)
        val cacheKey = "$voice|$language|$dialect"
        samples[cacheKey]?.let { return it }
        val prompt = "Say this warmly and at an easy pace, as a friendly $language tutor with a $dialect accent: $greeting"
        val body = ttsRequest(prompt, voice).toString()
        val response = firstAvailable(apiKey, ttsModels, body)
        val pcm = parseAudio(response)
        if (pcm.isEmpty()) throw IllegalStateException("No audio in response")
        samples[cacheKey] = pcm
        return pcm
    }

    internal fun greeting(apiKey: String, language: String, dialect: String): String {
        val cacheKey = "$language|$dialect"
        greetings[cacheKey]?.let { return it }
        if (language.equals("English", ignoreCase = true)) return GREETING_EN.also { greetings[cacheKey] = it }
        val prompt = "Translate into natural spoken $language as used in $dialect. Reply with the translation only, no quotes or notes:\n$GREETING_EN"
        val text = parseText(firstAvailable(apiKey, textModels, textRequest(prompt).toString())).ifBlank { GREETING_EN }
        greetings[cacheKey] = text
        return text
    }

    private fun firstAvailable(apiKey: String, models: List<String>, body: String): String {
        var lastError: Exception? = null
        for (model in models) {
            val url = "$baseUrl/$model:generateContent?key=$apiKey"
            val resp = client.newCall(Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA)).build()).execute()
            val text = resp.body?.string().orEmpty()
            when {
                resp.isSuccessful -> return text
                resp.code == 404 -> { lastError = IllegalStateException(GeminiApi.describeHttpError(404, text)); continue }
                else -> throw IllegalStateException(GeminiApi.describeHttpError(resp.code, text))
            }
        }
        throw lastError ?: IllegalStateException("No model available")
    }

    internal fun ttsRequest(text: String, voice: String): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", text) }) } })
        }
        putJsonObject("generationConfig") {
            putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
            putJsonObject("speechConfig") {
                putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) } }
            }
        }
    }

    internal fun textRequest(text: String): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", text) }) } })
        }
        putJsonObject("generationConfig") { put("temperature", 0.2) }
    }

    internal fun parseAudio(responseBody: String): ByteArray {
        val parts = json.parseToJsonElement(responseBody).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return ByteArray(0)
        val chunks = parts.mapNotNull { p ->
            val inline = p.jsonObject["inlineData"]?.jsonObject ?: return@mapNotNull null
            val mime = inline["mimeType"]?.jsonPrimitive?.content.orEmpty()
            if (!mime.startsWith("audio/")) return@mapNotNull null
            Base64.getDecoder().decode(inline["data"]?.jsonPrimitive?.content ?: return@mapNotNull null)
        }
        return chunks.fold(ByteArray(0)) { acc, c -> acc + c }
    }

    internal fun parseText(responseBody: String): String =
        json.parseToJsonElement(responseBody).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("")?.trim().orEmpty()

    companion object {
        const val GREETING_EN = "Hi! I'm your tutor. Ready for a walk and a chat?"
        val TTS_MODELS = listOf("gemini-2.5-flash-preview-tts", "gemini-3.1-flash-tts-preview", "gemini-2.5-pro-preview-tts")
        val TEXT_MODELS = listOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.0-flash")
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
