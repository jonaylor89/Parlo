package com.parlo.app.gemini

import com.parlo.app.model.Level
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

@Serializable
data class MinedWord(
    val word: String,
    val translation: String = "",
    val example: String = "",
    val reason: String = "",
)

/**
 * Post-walk pass: sends the finished transcript to a text model (`generateContent`, JSON mode) and asks
 * for the words the learner did not know. Pure network + parsing; persistence lives in VocabCapture.
 */
class VocabMiner(
    private val client: OkHttpClient,
    private val baseUrl: String = GeminiApi.MODELS_URL,
    private val models: List<String> = DEFAULT_MODELS,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param transcript (speaker label, text) pairs in order; speakers are "user" / "tutor".
     * @param knownWords words already in the vocab list for this language, excluded from results.
     */
    fun mine(
        apiKey: String,
        language: String,
        level: Level,
        transcript: List<Pair<String, String>>,
        knownWords: List<String>,
        maxWords: Int = 12,
    ): List<MinedWord> {
        if (transcript.none { it.first.equals("user", true) }) return emptyList()
        val body = requestBody(language, level, transcript, knownWords, maxWords).toString()
        var lastError: Exception? = null
        for (model in models) {
            val url = "$baseUrl/$model:generateContent?key=$apiKey"
            val resp = client.newCall(Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA)).build()).execute()
            val text = resp.body?.string().orEmpty()
            when {
                resp.isSuccessful -> return parse(text, knownWords, maxWords)
                resp.code == 404 -> { lastError = IllegalStateException(GeminiApi.describeHttpError(404, text)); continue }
                else -> throw IllegalStateException(GeminiApi.describeHttpError(resp.code, text))
            }
        }
        throw lastError ?: IllegalStateException("No text model available")
    }

    internal fun requestBody(
        language: String,
        level: Level,
        transcript: List<Pair<String, String>>,
        knownWords: List<String>,
        maxWords: Int,
    ): JsonObject {
        val lines = transcript.joinToString("\n") { (who, text) -> "${who.uppercase()}: $text" }
        val known = if (knownWords.isEmpty()) "none" else knownWords.joinToString(", ")
        val prompt = """
You are reviewing a transcript of a spoken $language practice conversation between a learner (USER) and a tutor (TUTOR). The learner is at ${level.label} level.

Find the $language words and short phrases the learner most likely did NOT know or could not produce. Strong signals: the learner asked what something means or how to say something, the tutor translated or explained a word, the tutor corrected the learner, the learner answered in English or stalled after the tutor used a word, or the tutor introduced a new useful expression.

Rules:
- Return at most $maxWords items, most useful first. Fewer is fine; zero is fine if nothing stands out.
- Skip proper names, numbers, filler words, and anything below the learner's level that they used correctly themselves.
- Skip these words the learner already has saved: $known
- "word" is in $language (dictionary form), "translation" is short English, "example" is the actual sentence from the transcript (or a very short natural one), "reason" is one of: asked_meaning, asked_how_to_say, corrected, struggled, introduced.

Transcript:
$lines
        """.trimIndent()

        return buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("word") { put("type", "STRING") }
                            putJsonObject("translation") { put("type", "STRING") }
                            putJsonObject("example") { put("type", "STRING") }
                            putJsonObject("reason") { put("type", "STRING") }
                        }
                        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("word")); add(kotlinx.serialization.json.JsonPrimitive("translation")) }
                    }
                }
            }
        }
    }

    internal fun parse(responseBody: String, knownWords: List<String>, maxWords: Int): List<MinedWord> {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("")
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return emptyList()
        val array: JsonArray = runCatching { json.parseToJsonElement(stripFences(text)).jsonArray }.getOrElse { return emptyList() }
        val known = knownWords.map { it.trim().lowercase() }.toSet()
        return array.mapNotNull { runCatching { json.decodeFromJsonElement(MinedWord.serializer(), it) }.getOrNull() }
            .map { it.copy(word = it.word.trim(), translation = it.translation.trim(), example = it.example.trim(), reason = it.reason.trim()) }
            .filter { it.word.isNotBlank() && it.word.lowercase() !in known }
            .distinctBy { it.word.lowercase() }
            .take(maxWords)
    }

    private fun stripFences(s: String): String =
        s.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val DEFAULT_MODELS = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-flash-latest")
    }
}
