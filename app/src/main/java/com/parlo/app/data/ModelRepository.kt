package com.parlo.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Holds the discovered Live-capable model list (see [LiveModelDiscovery]) and caches it in settings.
 * Never hardcodes a specific model.
 */
class ModelRepository(
    private val settings: SettingsRepository,
    private val discovery: LiveModelDiscovery,
) {
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
            val live = withContext(Dispatchers.IO) { discovery.discover(apiKey) }
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
}
