package com.parlo.app.judge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Minimal client for TypeSafe's System One endpoint (the Jev model).
 * One call = one `state` + a map of typed questions; the answers come back typed with
 * calibrated probabilities. See https://docs.typesafe.ai/introduction/quickstart
 */
class JevClient(
    private val client: OkHttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val model: String = DEFAULT_MODEL,
) {
    sealed interface Answer {
        data class Choice(val choice: String, val confidence: Double, val probabilities: Map<String, Double>) : Answer
        data class Score(val score: Double, val confidence: Double, val probabilities: Map<Int, Double>) : Answer
        data class Noul(val probability: Double) : Answer
    }

    class Answers(private val map: Map<String, Answer>) {
        fun choice(id: String) = map[id] as? Answer.Choice
        fun score(id: String) = map[id] as? Answer.Score
        fun noul(id: String) = map[id] as? Answer.Noul
        val ids: Set<String> get() = map.keys
    }

    class JevException(val code: Int, message: String) : IOException(message)

    /** Blocking; call from an IO dispatcher. */
    fun ask(apiKey: String, state: JsonElement, questions: JsonObject): Answers {
        val body = buildJsonObject {
            put("state", state)
            put("model", model)
            put("questions", questions)
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_TYPE))
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw JevException(resp.code, describeError(resp.code, text))
            return parse(text)
        }
    }

    internal fun parse(text: String): Answers {
        val root = json.parseToJsonElement(text).jsonObject
        val answers = root["answers"]?.jsonObject ?: return Answers(emptyMap())
        val out = mutableMapOf<String, Answer>()
        for ((id, el) in answers) {
            val a = el.jsonObject
            when (a["type"]?.jsonPrimitive?.content) {
                "choice" -> out[id] = Answer.Choice(
                    choice = a["choice"]?.jsonPrimitive?.content ?: continue,
                    confidence = a["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    probabilities = a["probabilities"]?.jsonObject?.mapValues { it.value.jsonPrimitive.double } ?: emptyMap(),
                )
                "score" -> out[id] = Answer.Score(
                    score = a["score"]?.jsonPrimitive?.doubleOrNull ?: continue,
                    confidence = a["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    probabilities = a["probabilities"]?.jsonObject
                        ?.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v.jsonPrimitive.double } }?.toMap()
                        ?: emptyMap(),
                )
                "noul" -> out[id] = Answer.Noul(a["noul"]?.jsonPrimitive?.doubleOrNull ?: continue)
            }
        }
        return Answers(out)
    }

    private fun describeError(code: Int, body: String): String {
        val apiMessage = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            (root["error"]?.let { it.jsonObject["message"] ?: it } ?: root["message"] ?: root["detail"])
                ?.jsonPrimitive?.content
        }.getOrNull()
        return when (code) {
            401, 403 -> "Jev API key rejected" + (apiMessage?.let { ": $it" } ?: "")
            429 -> "Jev rate limit reached" + (apiMessage?.let { ": $it" } ?: "")
            else -> apiMessage ?: "Jev request failed (HTTP $code)"
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.typesafe.ai/v1/systemone"
        const val DEFAULT_MODEL = "jev-latest"
        private val JSON_TYPE = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
