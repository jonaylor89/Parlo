package com.parlo.app.data

import com.parlo.app.gemini.GeminiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
 * Discovers Live-capable models (those advertising `bidiGenerateContent`) and caches them.
 * Never hardcodes a specific model, but ranks candidates so the newest native-audio Live model wins.
 */
class ModelRepository(
    private val settings: SettingsRepository,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    suspend fun loadCached() {
        val cached = settings.cachedModels.first()
        if (cached.isNotEmpty() && _models.value.isEmpty()) _models.value = cached
    }

    suspend fun refresh(apiKey: String) {
        if (apiKey.isBlank()) return
        _loading.value = true
        _error.value = null
        try {
            val discovered = withContext(Dispatchers.IO) { fetchAll(apiKey) }
            val live = discovered
                .filter { "bidiGenerateContent" in it.supportedGenerationMethods }
                .map { it.name.removePrefix("models/") }
                .sortedWith(liveModelComparator)
            if (live.isNotEmpty()) {
                _models.value = live
                settings.saveCachedModels(live)
            } else {
                _error.value = "No Live-capable models found for this key"
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Model discovery failed"
        } finally {
            _loading.value = false
        }
    }

    /** Model to use when the user hasn't overridden: newest native-audio Live model, else first discovered. */
    fun defaultModel(): String? = _models.value.firstOrNull()

    private fun fetchAll(apiKey: String): List<ModelInfo> {
        val out = mutableListOf<ModelInfo>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append(GeminiApi.MODELS_URL).append("?key=").append(apiKey).append("&pageSize=200")
                if (pageToken != null) append("&pageToken=").append(pageToken)
            }
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException(GeminiApi.describeHttpError(resp.code, body))
            val parsed = json.decodeFromString<ModelsResponse>(body)
            out += parsed.models
            pageToken = parsed.nextPageToken
        } while (!pageToken.isNullOrBlank())
        return out
    }

    private val liveModelComparator = compareByDescending<String> { score(it) }.thenByDescending { it }

    private fun score(name: String): Int {
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
