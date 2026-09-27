package com.parlo.app.data

import com.parlo.app.gemini.GeminiApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class ModelsResponse(val models: List<ModelInfo> = emptyList(), val nextPageToken: String? = null)

@Serializable
private data class ModelInfo(
    val name: String,
    val displayName: String? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
)

/**
 * Pure model-discovery logic: paginates the models endpoint, keeps only models advertising
 * `bidiGenerateContent`, and ranks them so the newest native-audio Live model comes first.
 */
class LiveModelDiscovery(
    private val client: OkHttpClient,
    private val modelsUrl: String = GeminiApi.MODELS_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Ranked list of Live-capable model names (without the `models/` prefix). Throws on HTTP failure. */
    fun discover(apiKey: String): List<String> {
        val out = mutableListOf<ModelInfo>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append(modelsUrl).append("?key=").append(apiKey).append("&pageSize=200")
                if (pageToken != null) append("&pageToken=").append(pageToken)
            }
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException(GeminiApi.describeHttpError(resp.code, body))
            val parsed = json.decodeFromString<ModelsResponse>(body)
            out += parsed.models
            pageToken = parsed.nextPageToken
        } while (!pageToken.isNullOrBlank())
        return rank(
            out.filter { LIVE_METHOD in it.supportedGenerationMethods }.map { it.name.removePrefix("models/") },
        )
    }

    companion object {
        const val LIVE_METHOD = "bidiGenerateContent"

        fun rank(names: List<String>): List<String> =
            names.distinct().sortedWith(compareByDescending<String> { score(it) }.thenByDescending { it })

        fun score(name: String): Int {
            val n = name.lowercase()
            var s = 0
            if ("native-audio" in n) s += 100
            if ("live" in n) s += 50
            if ("audio" in n) s += 20
            if ("preview" in n) s += 5
            if ("exp" in n) s -= 10
            Regex("""(\d+)\.(\d+)""").find(n)?.let { m ->
                s += m.groupValues[1].toInt() * 10 + m.groupValues[2].toInt()
            }
            Regex("""(\d{2})-(\d{2})""").find(n)?.let { m -> s += m.groupValues[1].toInt() + m.groupValues[2].toInt() / 4 }
            return s
        }
    }
}
