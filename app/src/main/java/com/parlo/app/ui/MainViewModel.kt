package com.parlo.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.parlo.app.ParloApp
import com.parlo.app.audio.AudioPlayer
import com.parlo.app.model.LanguageCombo
import com.parlo.app.model.LiveSessionState
import com.parlo.app.model.SessionConfig
import com.parlo.app.service.LiveSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val live: LiveSessionState = LiveSessionState(),
    /** Settings shown in the pickers. Mirrors live.config while a session is active. */
    val config: SessionConfig = SessionConfig(),
    val recentCombos: List<LanguageCombo> = emptyList(),
    val hasApiKey: Boolean = false,
    val hasJevKey: Boolean = false,
    val models: List<String> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    val serviceBound: Boolean = false,
    /** Auto-captured words waiting in the Vocab "Suggested" tray. */
    val suggestedVocab: Int = 0,
    val dynamicColor: Boolean = false,
    val voicePreview: VoicePreview = VoicePreview(),
) {
    /** True once a walk can actually start: a key is saved and a voice model is known. */
    val ready: Boolean get() = hasApiKey && (config.model.isNotBlank() || models.isNotEmpty())

    val setup: SetupStatus
        get() = when {
            !hasApiKey -> SetupStatus.NO_KEY
            modelsLoading && models.isEmpty() -> SetupStatus.CHECKING
            ready -> SetupStatus.READY
            modelsError?.contains("API key", ignoreCase = true) == true -> SetupStatus.BAD_KEY
            modelsError != null -> SetupStatus.OFFLINE
            else -> SetupStatus.NO_MODEL
        }
}

enum class SetupStatus { NO_KEY, CHECKING, BAD_KEY, OFFLINE, NO_MODEL, READY }

/** Which voice sample is being fetched or played from Settings, if any. */
data class VoicePreview(val loading: String? = null, val playing: String? = null, val error: String? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = ParloApp.container(app)
    private val settings = container.settings
    private val models = container.models
    private val vocab = container.vocab
    private val sampler = container.voiceSampler
    private val preview = MutableStateFlow(VoicePreview())
    private var previewJob: Job? = null
    private val previewPlayer = AudioPlayer(viewModelScope, onSpeakingChanged = { speaking ->
        if (!speaking) preview.update { it.copy(playing = null) }
    })

    private var service: LiveSessionService? = null
    private var serviceStateJob: Job? = null
    private val liveState = MutableStateFlow(LiveSessionState())
    private val bound = MutableStateFlow(false)
    private val localConfig = MutableStateFlow<SessionConfig?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        liveState, settings.config, settings.recentCombos, settings.apiKey, bound,
    ) { live, persisted, combos, key, isBound ->
        MainUiState(
            live = live,
            config = if (live.isActive) live.config else (localConfig.value ?: persisted),
            recentCombos = combos,
            hasApiKey = key.isNotBlank(),
            serviceBound = isBound,
        )
    }.combine(settings.jevApiKey) { s, k -> s.copy(hasJevKey = k.isNotBlank()) }
        .combine(models.models) { s, m -> s.copy(models = m) }
        .combine(models.loading) { s, l -> s.copy(modelsLoading = l) }
        .combine(models.error) { s, e -> s.copy(modelsError = e) }
        .combine(vocab.observeAll()) { s, words -> s.copy(suggestedVocab = words.count { it.isSuggested }) }
        .combine(settings.dynamicColor) { s, d -> s.copy(dynamicColor = d) }
        .combine(preview) { s, p -> s.copy(voicePreview = p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as LiveSessionService.LocalBinder).service
            service = svc
            bound.value = true
            serviceStateJob?.cancel()
            serviceStateJob = viewModelScope.launch { svc.state.collect { liveState.value = it } }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound.value = false
            serviceStateJob?.cancel()
        }
    }

    init {
        viewModelScope.launch {
            models.loadCached()
            settings.apiKey.collect { key -> if (key.isNotBlank()) models.refresh(key) }
        }
        viewModelScope.launch { settings.config.collect { if (localConfig.value == null) localConfig.value = it } }
    }

    fun bind(context: Context) {
        context.bindService(LiveSessionService.bindIntent(context), connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind(context: Context) {
        if (bound.value) runCatching { context.unbindService(connection) }
        bound.value = false
        serviceStateJob?.cancel()
    }

    fun startSession(context: Context) {
        val cfg = uiState.value.config
        viewModelScope.launch { settings.saveConfig(cfg) }
        ContextCompat.startForegroundService(context, LiveSessionService.startIntent(context))
        service?.startSession(cfg)
    }

    fun endSession(skipRecap: Boolean) = service?.endSession(skipRecap)

    fun toggleMute() = service?.toggleMute()

    fun dismissError() = service?.dismissError()

    fun updateConfig(transform: (SessionConfig) -> SessionConfig) {
        val new = transform(uiState.value.config)
        localConfig.value = new
        val svc = service
        if (uiState.value.live.isActive && svc != null) {
            svc.applyConfig(new)
        } else {
            viewModelScope.launch { settings.saveConfig(new) }
        }
    }

    fun selectCombo(combo: LanguageCombo) =
        updateConfig { it.copy(language = combo.language, dialect = combo.dialect, level = combo.level) }

    fun setApiKey(key: String) {
        settings.setApiKey(key)
    }

    fun setJevApiKey(key: String) {
        settings.setJevApiKey(key)
    }

    fun clearJevApiKey() {
        settings.setJevApiKey("")
    }

    fun refreshModels() {
        viewModelScope.launch { models.refresh(settings.apiKey.value) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    /** Plays a short greeting in [voice] via Gemini TTS; tapping the voice already playing stops it. */
    fun previewVoice(voice: String) {
        val key = settings.apiKey.value
        if (key.isBlank() || uiState.value.live.isActive) return
        val cfg = uiState.value.config
        val wasActive = preview.value.playing == voice || preview.value.loading == voice
        stopPreview()
        if (wasActive) return
        preview.value = VoicePreview(loading = voice)
        previewJob = viewModelScope.launch {
            val pcm = runCatching { withContext(Dispatchers.IO) { sampler.sample(key, voice, cfg.language, cfg.dialect) } }
                .getOrElse { e ->
                    preview.value = VoicePreview(error = e.message ?: "Couldn't fetch a sample")
                    return@launch
                }
            previewPlayer.start()
            preview.value = VoicePreview(playing = voice)
            for (off in pcm.indices step PREVIEW_CHUNK) {
                previewPlayer.enqueue(pcm.copyOfRange(off, minOf(off + PREVIEW_CHUNK, pcm.size)))
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel(); previewJob = null
        previewPlayer.stop()
        preview.value = VoicePreview()
    }

    override fun onCleared() {
        stopPreview()
        serviceStateJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val PREVIEW_CHUNK = 4096

        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
        }
    }
}
