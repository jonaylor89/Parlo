package com.parlo.app.data

import android.util.Log
import com.parlo.app.data.db.VocabSource
import com.parlo.app.gemini.VocabMiner
import com.parlo.app.model.Level
import com.parlo.app.model.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs the post-walk vocab mining pass and files the results as suggestions. */
class VocabCapture(
    private val sessions: SessionRepository,
    private val vocab: VocabRepository,
    private val settings: SettingsRepository,
    private val miner: VocabMiner,
    private val scope: CoroutineScope,
) {
    sealed interface Outcome {
        data class Found(val count: Int) : Outcome
        data object NothingNew : Outcome
        data object NoApiKey : Outcome
        data object TooShort : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val _mining = MutableStateFlow<Set<Long>>(emptySet())
    /** Session ids currently being mined; the UI uses this to show progress. */
    val mining: StateFlow<Set<Long>> = _mining

    /** Fire-and-forget after a session ends; silent on failure. */
    fun mineInBackground(sessionId: Long): Job = scope.launch {
        val outcome = mine(sessionId)
        Log.i(TAG, "mining session $sessionId -> $outcome")
    }

    suspend fun mine(sessionId: Long, force: Boolean = false): Outcome {
        if (!claim(sessionId)) return Outcome.NothingNew
        try {
            val apiKey = settings.apiKey.value
            if (apiKey.isBlank()) return Outcome.NoApiKey
            val session = sessions.getSession(sessionId) ?: return Outcome.Failed("Session not found")
            if (session.minedAt != null && !force) return Outcome.NothingNew
            val turns = sessions.allTurns(sessionId).filter { it.speaker != Speaker.SYSTEM.name }
            if (turns.count { it.speaker == Speaker.USER.name } < MIN_USER_TURNS) {
                sessions.markMined(sessionId)
                return Outcome.TooShort
            }
            val known = vocab.knownWords(session.language)
            val mined = withContext(Dispatchers.IO) {
                miner.mine(
                    apiKey = apiKey,
                    language = session.language,
                    level = Level.parse(session.level),
                    transcript = turns.map { it.speaker.lowercase() to it.text },
                    knownWords = known,
                )
            }
            var added = 0
            for (m in mined) {
                val id = vocab.suggest(
                    word = m.word,
                    translation = m.translation,
                    example = m.example,
                    language = session.language,
                    sessionId = sessionId,
                    source = VocabSource.MINED,
                    reason = reasonLabel(m.reason),
                )
                if (id != null) added++
            }
            sessions.markMined(sessionId)
            return if (added == 0) Outcome.NothingNew else Outcome.Found(added)
        } catch (e: Exception) {
            Log.w(TAG, "vocab mining failed for session $sessionId", e)
            return Outcome.Failed(e.message ?: "Mining failed")
        } finally {
            _mining.update { it - sessionId }
        }
    }

    private fun claim(id: Long): Boolean {
        var claimed = false
        _mining.update { if (id in it) it else { claimed = true; it + id } }
        return claimed
    }

    companion object {
        private const val TAG = "VocabCapture"
        const val MIN_USER_TURNS = 2

        fun reasonLabel(code: String): String = when (code.lowercase().replace('-', '_').replace(' ', '_')) {
            "asked_meaning" -> "You asked what it means"
            "asked_how_to_say" -> "You asked how to say it"
            "corrected" -> "The tutor corrected you"
            "struggled" -> "You got stuck on it"
            "introduced" -> "New word from the tutor"
            "" -> "Found in your transcript"
            else -> code.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }
}
