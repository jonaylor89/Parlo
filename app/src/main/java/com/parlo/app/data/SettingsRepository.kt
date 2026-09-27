package com.parlo.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.parlo.app.model.CorrectionStyle
import com.parlo.app.model.LanguageCombo
import com.parlo.app.model.Level
import com.parlo.app.model.Scenario
import com.parlo.app.model.SessionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("parlo_settings")

/**
 * API key lives in EncryptedSharedPreferences (AES256-GCM, Keystore-backed master key);
 * everything else is plain Preferences DataStore.
 */
class SettingsRepository(private val context: Context) {

    private val secure: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "parlo_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _apiKey = MutableStateFlow(secure.getString(KEY_API, "") ?: "")
    val apiKey: StateFlow<String> = _apiKey

    fun setApiKey(key: String) {
        secure.edit().putString(KEY_API, key.trim()).apply()
        _apiKey.value = key.trim()
    }

    val config: Flow<SessionConfig> = context.dataStore.data.map { it.toConfig() }

    suspend fun currentConfig(): SessionConfig = config.first()

    suspend fun saveConfig(config: SessionConfig) {
        context.dataStore.edit { p ->
            p[LANGUAGE] = config.language
            p[DIALECT] = config.dialect
            p[LEVEL] = config.level.name
            p[SCENARIO] = config.scenario.name
            p[CORRECTION] = config.correctionStyle.name
            p[VOICE] = config.voice
            p[MODEL] = config.model
        }
        rememberCombo(config.languageCombo)
    }

    val recentCombos: Flow<List<LanguageCombo>> = context.dataStore.data.map { p ->
        p[RECENT_COMBOS]?.let { runCatching { json.decodeFromString(comboListSerializer, it) }.getOrNull() } ?: emptyList()
    }

    private suspend fun rememberCombo(combo: LanguageCombo) {
        if (combo.language.isBlank()) return
        context.dataStore.edit { p ->
            val existing = p[RECENT_COMBOS]?.let { runCatching { json.decodeFromString(comboListSerializer, it) }.getOrNull() } ?: emptyList()
            val updated = (listOf(combo) + existing.filterNot { it == combo }).take(MAX_RECENT)
            p[RECENT_COMBOS] = json.encodeToString(comboListSerializer, updated)
        }
    }

    val cachedModels: Flow<List<String>> = context.dataStore.data.map { p ->
        p[CACHED_MODELS]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun saveCachedModels(models: List<String>) {
        context.dataStore.edit { it[CACHED_MODELS] = models.joinToString("\n") }
    }

    private fun Preferences.toConfig() = SessionConfig(
        language = this[LANGUAGE] ?: "Spanish",
        dialect = this[DIALECT] ?: "Madrid Spanish",
        level = Level.parse(this[LEVEL]),
        scenario = Scenario.parse(this[SCENARIO]),
        correctionStyle = CorrectionStyle.parse(this[CORRECTION]),
        voice = this[VOICE] ?: "Puck",
        model = this[MODEL] ?: "",
    )

    private companion object {
        const val KEY_API = "gemini_api_key"
        const val MAX_RECENT = 6
        val LANGUAGE = stringPreferencesKey("language")
        val DIALECT = stringPreferencesKey("dialect")
        val LEVEL = stringPreferencesKey("level")
        val SCENARIO = stringPreferencesKey("scenario")
        val CORRECTION = stringPreferencesKey("correction")
        val VOICE = stringPreferencesKey("voice")
        val MODEL = stringPreferencesKey("model")
        val RECENT_COMBOS = stringPreferencesKey("recent_combos")
        val CACHED_MODELS = stringPreferencesKey("cached_models")
        val json = Json { ignoreUnknownKeys = true }
        val comboListSerializer = ListSerializer(LanguageCombo.serializer())
    }
}
