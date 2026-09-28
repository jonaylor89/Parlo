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
import com.parlo.app.model.LanguageCombo
import com.parlo.app.model.LiveSessionState
import com.parlo.app.model.SessionConfig
import com.parlo.app.service.LiveSessionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val live: LiveSessionState = LiveSessionState(),
    /** Settings shown in the pickers. Mirrors live.config while a session is active. */
    val config: SessionConfig = SessionConfig(),
    val recentCombos: List<LanguageCombo> = emptyList(),
    val hasApiKey: Boolean = false,
    val models: List<String> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    val serviceBound: Boolean = false,
    /** Auto-captured words waiting in the Vocab "Suggested" tray. */
    val suggestedVocab: Int = 0,
    val dynamicColor: Boolean = false,
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

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = ParloApp.container(app)
    private val settings = container.settings
    private val models = container.models
    private val vocab = container.vocab

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
    }.combine(models.models) { s, m -> s.copy(models = m) }
        .combine(models.loading) { s, l -> s.copy(modelsLoading = l) }
        .combine(models.error) { s, e -> s.copy(modelsError = e) }
        .combine(vocab.observeAll()) { s, words -> s.copy(suggestedVocab = words.count { it.isSuggested }) }
        .combine(settings.dynamicColor) { s, d -> s.copy(dynamicColor = d) }
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

    fun refreshModels() {
        viewModelScope.launch { models.refresh(settings.apiKey.value) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    override fun onCleared() {
        serviceStateJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
        }
    }
}
