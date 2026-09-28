package com.parlo.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.session.MediaButtonReceiver
import com.parlo.app.MainActivity
import com.parlo.app.ParloApp
import com.parlo.app.R
import com.parlo.app.audio.AudioPlayer
import com.parlo.app.audio.AudioRouteManager
import com.parlo.app.audio.Earcons
import com.parlo.app.audio.MicrophoneStreamer
import com.parlo.app.gemini.Content
import com.parlo.app.gemini.GenerationConfig
import com.parlo.app.gemini.GeminiLiveClient
import com.parlo.app.gemini.LiveEvent
import com.parlo.app.gemini.Part
import com.parlo.app.gemini.PrebuiltVoiceConfig
import com.parlo.app.gemini.PromptBuilder
import com.parlo.app.gemini.Setup
import com.parlo.app.gemini.SpeechConfig
import com.parlo.app.gemini.ToolHandler
import com.parlo.app.gemini.VoiceConfig
import com.parlo.app.model.AudioState
import com.parlo.app.model.ConnectionState
import com.parlo.app.model.LanguageCombo
import com.parlo.app.model.LiveSessionState
import com.parlo.app.model.SessionConfig
import com.parlo.app.model.SessionError
import com.parlo.app.model.Speaker
import com.parlo.app.model.TranscriptTurn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service that owns the whole walk session: Gemini socket, mic, speaker, focus, routing,
 * MediaSession (headset buttons), notification, transcript persistence and the end-of-session recap.
 */
class LiveSessionService : LifecycleService() {

    inner class LocalBinder : Binder() {
        val service: LiveSessionService get() = this@LiveSessionService
    }

    private val binder = LocalBinder()

    private val _state = MutableStateFlow(LiveSessionState())
    val state: StateFlow<LiveSessionState> = _state

    private lateinit var container: com.parlo.app.AppContainer
    private lateinit var client: GeminiLiveClient
    private lateinit var player: AudioPlayer
    private lateinit var mic: MicrophoneStreamer
    private lateinit var route: AudioRouteManager
    private lateinit var earcons: Earcons
    private lateinit var toolHandler: ToolHandler
    private var mediaSession: MediaSessionCompat? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var dbSessionId: Long? = null
    private var eventsJob: Job? = null
    private var recapJob: Job? = null
    private var hadReadyOnce = false
    private var pendingResumeContext = false
    private var awaitingRecapTurnComplete = false
    private var recapText = StringBuilder()
    private var tutorSpeaking = false
    private var micLevel = 0f
    private var focusPaused = false
    private val turnIds = AtomicLong(1)
    private val userBuffer = StringBuilder()
    private val tutorBuffer = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        container = ParloApp.container(this)
        client = GeminiLiveClient(container.okHttp, lifecycleScope)
        earcons = Earcons(lifecycleScope)
        player = AudioPlayer(lifecycleScope, onSpeakingChanged = { speaking -> tutorSpeaking = speaking; refreshAudioState() })
        mic = MicrophoneStreamer(
            scope = lifecycleScope,
            onChunk = { bytes, len -> client.sendAudio(bytes, len) },
            onLevel = { lvl -> if ((lvl > 0.06f) != (micLevel > 0.06f)) { micLevel = lvl; refreshAudioState() } else micLevel = lvl },
        )
        route = AudioRouteManager(this, ::onFocusChange)
        toolHandler = ToolHandler(
            vocab = container.vocab,
            currentLanguage = { _state.value.config.language },
            currentDialect = { _state.value.config.dialect },
            currentLevel = { _state.value.config.level },
            sessionId = { dbSessionId },
            onVocabSaved = { word ->
                earcons.play(Earcons.Cue.VOCAB_SAVED)
                appendTurn(Speaker.SYSTEM, "Saved \"$word\" to vocab")
            },
            onLanguageSwitched = ::onVoiceLanguageSwitch,
            onVocabNoted = { word -> appendTurn(Speaker.SYSTEM, "Noted \"$word\" for review") },
        )
        setupMediaSession()
        registerNetworkCallback()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> lifecycleScope.launch { startSession(container.settings.currentConfig()) }
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_END -> endSession(skipRecap = false)
            ACTION_END_NOW -> endSession(skipRecap = true)
            Intent.ACTION_MEDIA_BUTTON -> MediaButtonReceiver.handleIntent(mediaSession, intent)
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ public API

    fun startSession(config: SessionConfig) {
        if (_state.value.isActive) return
        val apiKey = container.settings.apiKey.value
        if (apiKey.isBlank()) { fail(SessionError.MissingApiKey); return }
        val model = config.model.ifBlank { container.models.defaultModel().orEmpty() }
        if (model.isBlank()) { fail(SessionError.NoLiveModel); return }
        val effective = config.copy(model = model)

        hadReadyOnce = false
        pendingResumeContext = false
        userBuffer.clear(); tutorBuffer.clear(); recapText.clear()
        _state.value = LiveSessionState(
            connection = ConnectionState.CONNECTING,
            audio = AudioState.IDLE,
            config = effective,
            sessionStartMs = System.currentTimeMillis(),
            statusText = "Connecting…",
        )
        goForeground()
        lifecycleScope.launch {
            dbSessionId = container.sessions.startSession(effective, _state.value.sessionStartMs)
            container.settings.saveConfig(effective)
        }
        route.begin()
        player.start()
        mediaSession?.isActive = true
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        eventsJob?.cancel()
        eventsJob = lifecycleScope.launch { client.events.collect(::onLiveEvent) }
        client.start(apiKey) { buildSetup(_state.value.config) }
    }

    fun toggleMute() = setMuted(!_state.value.muted)

    fun setMuted(muted: Boolean) {
        if (!_state.value.isActive) return
        mic.setMuted(muted)
        _state.update { it.copy(muted = muted) }
        earcons.play(if (muted) Earcons.Cue.MUTED else Earcons.Cue.UNMUTED)
        refreshAudioState()
        updateNotification()
    }

    /** Apply new settings mid-session. Live-switchable fields go over the wire as a text turn; voice/model reconnect. */
    fun applyConfig(new: SessionConfig) {
        val old = _state.value.config
        val merged = new.copy(model = new.model.ifBlank { old.model })
        if (merged == old) return
        _state.update { it.copy(config = merged) }
        lifecycleScope.launch { container.settings.saveConfig(merged) }
        if (!_state.value.isActive) return
        val needsReconnect = merged.voice != old.voice || merged.model != old.model
        if (needsReconnect) {
            _state.update { it.copy(connection = ConnectionState.RECONNECTING, statusText = "Applying voice/model…") }
            earcons.play(Earcons.Cue.RECONNECTING)
            player.flush()
            client.reconnectWithNewSetup { buildSetup(_state.value.config) }
        } else if (client.isReady) {
            player.flush()
            client.sendText(PromptBuilder.switchMessage(old, merged))
            appendTurn(Speaker.SYSTEM, "Switched to ${merged.dialect.ifBlank { merged.language }} · ${merged.level.label}")
        }
        updateNotification()
    }

    fun switchCombo(combo: LanguageCombo) =
        applyConfig(_state.value.config.copy(language = combo.language, dialect = combo.dialect, level = combo.level))

    fun requestRepeatSlowly() {
        if (!client.isReady) return
        player.flush()
        client.sendText(PromptBuilder.repeatSlowlyMessage())
    }

    fun endSession(skipRecap: Boolean) {
        val s = _state.value
        if (!s.isActive) { stopSelf(); return }
        if (s.connection == ConnectionState.ENDING) {
            if (skipRecap) finishSession()
            return
        }
        if (skipRecap || !client.isReady) { finishSession(); return }
        _state.update { it.copy(connection = ConnectionState.ENDING, recapInProgress = true, statusText = "Recap…") }
        mic.setMuted(true)
        recapText.clear()
        awaitingRecapTurnComplete = true
        player.flush()
        client.sendText(PromptBuilder.recapMessage(s.config))
        appendTurn(Speaker.SYSTEM, "Requesting end-of-walk recap")
        updateNotification()
        recapJob = lifecycleScope.launch {
            withTimeoutOrNull(RECAP_TIMEOUT_MS) {
                while (awaitingRecapTurnComplete) delay(100)
                while (player.isSpeaking) delay(100)
            }
            finishSession()
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null, connection = ConnectionState.IDLE, statusText = "") }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------------ lifecycle

    private fun finishSession() {
        recapJob?.cancel(); recapJob = null
        val recap = recapText.toString().trim()
        flushBuffers()
        val config = _state.value.config
        val id = dbSessionId
        val endedAt = System.currentTimeMillis()
        client.close()
        mic.stop()
        player.stop()
        route.end()
        earcons.play(Earcons.Cue.SESSION_ENDED)
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSession?.isActive = false
        eventsJob?.cancel(); eventsJob = null
        _state.update {
            it.copy(connection = ConnectionState.IDLE, audio = AudioState.IDLE, muted = false, recapInProgress = false, statusText = "")
        }
        lifecycleScope.launch {
            if (id != null) {
                container.sessions.finishSession(id, endedAt, recap.ifBlank { null }, config)
                container.vocabCapture.mineInBackground(id)
            }
            dbSessionId = null
            delay(700)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun fail(error: SessionError) {
        Log.w(TAG, "session failed: ${error.message}")
        earcons.play(Earcons.Cue.ERROR)
        client.close()
        mic.stop()
        player.stop()
        route.end()
        setPlaybackState(PlaybackStateCompat.STATE_ERROR)
        mediaSession?.isActive = false
        eventsJob?.cancel(); eventsJob = null
        flushBuffers()
        val id = dbSessionId
        val config = _state.value.config
        _state.update { it.copy(connection = ConnectionState.ERROR, audio = AudioState.IDLE, error = error, recapInProgress = false, statusText = error.title) }
        lifecycleScope.launch {
            if (id != null) {
                container.sessions.finishSession(id, System.currentTimeMillis(), null, config)
                container.vocabCapture.mineInBackground(id)
            }
            container.sessions.cleanupEmpty()
            dbSessionId = null
        }
        if (isForeground) updateNotification()
    }

    override fun onDestroy() {
        if (_state.value.isActive) {
            client.close(); mic.stop(); player.stop(); route.end()
        }
        networkCallback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
        mediaSession?.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Gemini events

    private suspend fun onLiveEvent(e: LiveEvent) {
        when (e) {
            is LiveEvent.Ready -> {
                val wasReconnect = hadReadyOnce
                hadReadyOnce = true
                _state.update { it.copy(connection = if (it.connection == ConnectionState.ENDING) it.connection else ConnectionState.CONNECTED, error = null, statusText = "") }
                if (!mic.isRunning) mic.start()
                mic.setMuted(_state.value.muted || _state.value.recapInProgress)
                if (wasReconnect) {
                    earcons.play(Earcons.Cue.RECONNECTED)
                    if (pendingResumeContext || !e.resumed) {
                        pendingResumeContext = false
                        sendResumeContext()
                    }
                } else {
                    earcons.play(Earcons.Cue.SESSION_STARTED)
                }
                refreshAudioState(); updateNotification()
            }
            is LiveEvent.Audio -> player.enqueue(e.pcm)
            is LiveEvent.InputTranscript -> {
                if (tutorBuffer.isNotEmpty()) commitTutor()
                userBuffer.append(e.text)
                publishPartial()
            }
            is LiveEvent.OutputTranscript -> {
                if (userBuffer.isNotEmpty()) commitUser()
                tutorBuffer.append(e.text)
                if (_state.value.recapInProgress) recapText.append(e.text)
                publishPartial()
            }
            LiveEvent.Interrupted -> {
                player.flush()
                if (tutorBuffer.isNotEmpty()) { tutorBuffer.append(" …"); commitTutor() }
            }
            LiveEvent.TurnComplete -> {
                flushBuffers()
                if (awaitingRecapTurnComplete) awaitingRecapTurnComplete = false
            }
            LiveEvent.GenerationComplete -> Unit
            is LiveEvent.ToolCalls -> {
                val responses = e.calls.map { call -> toolHandler.handle(call) }
                client.sendToolResponses(responses)
            }
            is LiveEvent.ToolCallsCancelled -> Unit
            is LiveEvent.Reconnecting -> {
                if (_state.value.connection != ConnectionState.RECONNECTING && _state.value.connection != ConnectionState.ENDING) {
                    earcons.play(Earcons.Cue.RECONNECTING)
                }
                player.flush()
                _state.update { it.copy(connection = ConnectionState.RECONNECTING, statusText = "Reconnecting (attempt ${e.attempt})…") }
                refreshAudioState(); updateNotification()
            }
            LiveEvent.ResumptionLost -> pendingResumeContext = true
            is LiveEvent.Failed -> fail(e.error)
            LiveEvent.Closed -> Unit
        }
    }

    private suspend fun sendResumeContext() {
        val id = dbSessionId ?: return
        val recent = container.sessions.recentTurns(id).filter { it.speaker != Speaker.SYSTEM.name }
            .map { (if (it.speaker == Speaker.USER.name) "User" else "Tutor") to it.text }
        if (recent.isEmpty()) return
        client.sendText(PromptBuilder.resumeContextMessage(recent, _state.value.config))
    }

    private suspend fun onVoiceLanguageSwitch(combo: LanguageCombo) {
        val old = _state.value.config
        val new = old.copy(language = combo.language, dialect = combo.dialect, level = combo.level)
        if (new == old) return
        _state.update { it.copy(config = new) }
        container.settings.saveConfig(new)
        appendTurn(Speaker.SYSTEM, "Switched by voice to ${combo.label}")
        updateNotification()
    }

    // ------------------------------------------------------------------ transcript

    private fun publishPartial() {
        _state.update { s ->
            val finals = s.transcript.filter { it.isFinal }
            val partials = buildList {
                if (userBuffer.isNotEmpty()) add(TranscriptTurn(-1, Speaker.USER, userBuffer.toString(), System.currentTimeMillis(), isFinal = false))
                if (tutorBuffer.isNotEmpty()) add(TranscriptTurn(-2, Speaker.TUTOR, tutorBuffer.toString(), System.currentTimeMillis(), isFinal = false))
            }
            s.copy(transcript = finals + partials)
        }
    }

    private fun commitUser() { val t = userBuffer.toString(); userBuffer.clear(); appendTurn(Speaker.USER, t) }
    private fun commitTutor() { val t = tutorBuffer.toString(); tutorBuffer.clear(); appendTurn(Speaker.TUTOR, t) }
    private fun flushBuffers() { if (userBuffer.isNotEmpty()) commitUser(); if (tutorBuffer.isNotEmpty()) commitTutor(); publishPartial() }

    private fun appendTurn(speaker: Speaker, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val turn = TranscriptTurn(turnIds.getAndIncrement(), speaker, clean, now)
        _state.update { s -> s.copy(transcript = s.transcript.filter { it.isFinal } + turn) }
        publishPartial()
        val id = dbSessionId ?: return
        lifecycleScope.launch { container.sessions.addTurn(id, speaker, clean, now) }
    }

    // ------------------------------------------------------------------ audio state / focus

    private fun refreshAudioState() {
        _state.update { s ->
            val audio = when {
                s.connection == ConnectionState.RECONNECTING || s.connection == ConnectionState.CONNECTING -> AudioState.RECONNECTING
                s.connection == ConnectionState.IDLE || s.connection == ConnectionState.ERROR -> AudioState.IDLE
                tutorSpeaking -> AudioState.SPEAKING
                s.muted -> AudioState.MUTED
                micLevel > 0.06f -> AudioState.LISTENING
                else -> AudioState.IDLE
            }
            if (audio == s.audio) s else s.copy(audio = audio)
        }
    }

    private fun onFocusChange(focus: AudioRouteManager.FocusState) {
        when (focus) {
            AudioRouteManager.FocusState.GAINED -> {
                if (focusPaused) {
                    focusPaused = false
                    mic.setPaused(false); player.setPaused(false)
                    _state.update { it.copy(statusText = "") }
                }
            }
            AudioRouteManager.FocusState.LOST_TRANSIENT, AudioRouteManager.FocusState.LOST -> {
                focusPaused = true
                mic.setPaused(true); player.setPaused(true)
                _state.update { it.copy(statusText = "Paused (audio focus lost)") }
                if (focus == AudioRouteManager.FocusState.LOST) {
                    lifecycleScope.launch {
                        delay(3000)
                        if (focusPaused && _state.value.isActive) { route.end(); route.begin() }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ media session (headset buttons)

    private fun setupMediaSession() {
        val mbrIntent = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(this, MediaButtonReceiver::class.java)
        val mbrPending = PendingIntent.getBroadcast(this, 0, mbrIntent, PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaSessionCompat(this, "ParloSession", null, mbrPending).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                // Single tap on most headsets -> play/pause; the compat default handler turns a double tap into onSkipToNext.
                override fun onPlay() = toggleMute()
                override fun onPause() = toggleMute()
                override fun onSkipToNext() = requestRepeatSlowly()
                override fun onSkipToPrevious() = requestRepeatSlowly()
                override fun onStop() = endSession(skipRecap = false)
            })
            setPlaybackState(playbackState(PlaybackStateCompat.STATE_STOPPED))
        }
    }

    private fun playbackState(state: Int) = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP,
        )
        .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
        .build()

    private fun setPlaybackState(state: Int) { mediaSession?.setPlaybackState(playbackState(state)) }

    // ------------------------------------------------------------------ connectivity

    private fun registerNetworkCallback() {
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (_state.value.isActive) client.nudgeReconnect()
            }
        }
        networkCallback = cb
        runCatching {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                cb,
            )
        }
    }

    // ------------------------------------------------------------------ notification

    private var isForeground = false

    private fun goForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        isForeground = true
    }

    private fun updateNotification() {
        if (!isForeground) return
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val c = s.config
        val title = "${c.dialect.ifBlank { c.language }} · ${c.level.label}"
        val text = when {
            s.error != null -> s.error.title
            s.statusText.isNotBlank() -> s.statusText
            s.muted -> "Muted"
            else -> "Listening"
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, LiveSessionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ParloApp.CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(if (s.muted) R.string.action_unmute else R.string.action_mute), action(ACTION_TOGGLE_MUTE, 1))
            .addAction(0, getString(R.string.action_end), action(ACTION_END, 2))
            .build()
    }

    // ------------------------------------------------------------------ setup

    private fun buildSetup(c: SessionConfig): Setup = Setup(
        model = if (c.model.startsWith("models/")) c.model else "models/${c.model}",
        generationConfig = GenerationConfig(
            responseModalities = listOf("AUDIO"),
            speechConfig = SpeechConfig(VoiceConfig(PrebuiltVoiceConfig(c.voice))),
        ),
        systemInstruction = Content(parts = listOf(Part(text = PromptBuilder.systemInstruction(c)))),
        tools = PromptBuilder.toolDeclarations(),
    )

    companion object {
        private const val TAG = "LiveSessionService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.parlo.app.action.START"
        const val ACTION_TOGGLE_MUTE = "com.parlo.app.action.TOGGLE_MUTE"
        const val ACTION_END = "com.parlo.app.action.END"
        const val ACTION_END_NOW = "com.parlo.app.action.END_NOW"
        private const val RECAP_TIMEOUT_MS = 75_000L

        fun startIntent(context: Context) = Intent(context, LiveSessionService::class.java).setAction(ACTION_START)
        fun bindIntent(context: Context) = Intent(context, LiveSessionService::class.java)
    }
}
