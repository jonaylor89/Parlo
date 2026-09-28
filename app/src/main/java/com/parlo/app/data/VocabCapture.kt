package com.parlo.app.data

import android.util.Log
import com.parlo.app.data.db.VocabSource
import com.parlo.app.data.db.VocabStatus
import com.parlo.app.gemini.MinedWord
import com.parlo.app.gemini.VocabMiner
import com.parlo.app.judge.VocabJudge
import com.parlo.app.model.Level
import com.parlo.app.model.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Turns raw vocab candidates into flashcards.
 *
 * Two extractors feed it — the tutor's silent `note_vocab` tool during the walk and the
 * post-walk [VocabMiner] over the transcript. Both are generative and over-eager, so when a Jev
 * key is configured every candidate is run through [VocabJudge]: high-confidence words are kept
 * outright, borderline ones go to the Suggested tray, the rest are dropped. Without a Jev key
 * everything lands in the Suggested tray as before.
 */
class VocabCapture(
    private val sessions: SessionRepository,
    private val vocab: VocabRepository,
    private val geminiKey: () -> String,
    private val jevKey: () -> String,
    private val miner: VocabMiner,
    private val judge: VocabJudge,
    private val scope: CoroutineScope,
) {
    constructor(
        sessions: SessionRepository,
        vocab: VocabRepository,
        settings: SettingsRepository,
        miner: VocabMiner,
        judge: VocabJudge,
        scope: CoroutineScope,
    ) : this(sessions, vocab, { settings.apiKey.value }, { settings.jevApiKey.value }, miner, judge, scope)

    sealed interface Outcome {
        /** [kept] were filed straight into the list by the judge; [suggested] await review. */
        data class Found(val kept: Int, val suggested: Int) : Outcome {
            val count get() = kept + suggested
        }
        data object NothingNew : Outcome
        data object NoApiKey : Outcome
        data object TooShort : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val _mining = MutableStateFlow<Set<Long>>(emptySet())
    /** Session ids currently being mined; the UI uses this to show progress. */
    val mining: StateFlow<Set<Long>> = _mining

    private val judgeSlots = Semaphore(MAX_PARALLEL_JUDGEMENTS)

    val hasJudge: Boolean get() = jevKey().isNotBlank()

    /** Fire-and-forget after a session ends; silent on failure. */
    fun mineInBackground(sessionId: Long): Job = scope.launch {
        val outcome = mine(sessionId)
        Log.i(TAG, "mining session $sessionId -> $outcome")
    }

    suspend fun mine(sessionId: Long, force: Boolean = false): Outcome {
        if (!claim(sessionId)) return Outcome.NothingNew
        try {
            val apiKey = geminiKey()
            if (apiKey.isBlank()) return Outcome.NoApiKey
            val session = sessions.getSession(sessionId) ?: return Outcome.Failed("Session not found")
            if (session.minedAt != null && !force) return Outcome.NothingNew
            val turns = sessions.allTurns(sessionId).filter { it.speaker != Speaker.SYSTEM.name }
            if (turns.count { it.speaker == Speaker.USER.name } < MIN_USER_TURNS) {
                sessions.markMined(sessionId)
                return Outcome.TooShort
            }
            val level = Level.parse(session.level)
            val transcript = turns.map { it.speaker.lowercase() to it.text }
            val known = vocab.knownWords(session.language)
            val mined = withContext(Dispatchers.IO) {
                miner.mine(apiKey = apiKey, language = session.language, level = level, transcript = transcript, knownWords = known)
            }
            val verdicts = judgeAll(mined, session.language, level, transcript)
            var kept = 0
            var suggested = 0
            for ((m, verdict) in mined.zip(verdicts)) {
                val status = when (verdict?.decision) {
                    VocabJudge.Decision.DROP -> continue
                    VocabJudge.Decision.KEEP -> VocabStatus.KEPT
                    VocabJudge.Decision.SUGGEST, null -> VocabStatus.SUGGESTED
                }
                val id = vocab.suggest(
                    word = m.word,
                    translation = m.translation,
                    example = m.example,
                    language = session.language,
                    sessionId = sessionId,
                    source = VocabSource.MINED,
                    reason = reasonLabel(verdict?.reason ?: m.reason),
                    status = status,
                    confidence = verdict?.confidence,
                ) ?: continue
                if (status == VocabStatus.KEPT) kept++ else suggested++
            }
            sessions.markMined(sessionId)
            return if (kept + suggested == 0) Outcome.NothingNew else Outcome.Found(kept, suggested)
        } catch (e: Exception) {
            Log.w(TAG, "vocab mining failed for session $sessionId", e)
            return Outcome.Failed(e.message ?: "Mining failed")
        } finally {
            _mining.update { it - sessionId }
        }
    }

    /**
     * Second look at a word the tutor just noted mid-walk. The note is already in the Suggested
     * tray (so the tool call returned instantly); Jev then promotes, relabels, or removes it.
     */
    fun reviewInBackground(vocabId: Long): Job = scope.launch {
        runCatching { review(vocabId) }
            .onSuccess { Log.i(TAG, "review vocab $vocabId -> $it") }
            .onFailure { Log.w(TAG, "review of vocab $vocabId failed", it) }
    }

    suspend fun review(vocabId: Long): VocabJudge.Decision? {
        val key = jevKey()
        if (key.isBlank()) return null
        val v = vocab.get(vocabId) ?: return null
        if (v.source == VocabSource.MANUAL.name || v.confidence != null) return null
        val session = v.sessionId?.let { sessions.getSession(it) }
        val level = session?.let { Level.parse(it.level) } ?: Level.INTERMEDIATE
        val transcript = v.sessionId?.let { sessions.recentTurns(it, limit = REVIEW_CONTEXT_TURNS) }
            ?.filter { it.speaker != Speaker.SYSTEM.name }
            ?.map { it.speaker.lowercase() to it.text }
            ?: emptyList()
        val verdict = withContext(Dispatchers.IO) {
            judge.judge(
                apiKey = key,
                language = v.language,
                level = level,
                candidate = VocabJudge.Candidate(v.word, v.translation, v.exampleSentence, reasonCode(v.reason)),
                transcript = transcript,
            )
        }
        when (verdict.decision) {
            VocabJudge.Decision.DROP -> vocab.deleteById(vocabId)
            VocabJudge.Decision.KEEP -> vocab.review(vocabId, VocabStatus.KEPT, verdict.reason?.let(::reasonLabel), verdict.confidence)
            VocabJudge.Decision.SUGGEST -> vocab.review(vocabId, VocabStatus.SUGGESTED, verdict.reason?.let(::reasonLabel), verdict.confidence)
        }
        return verdict.decision
    }

    /** One Jev call per candidate, a few in flight at once; null entries mean "no judge" or "judge failed". */
    private suspend fun judgeAll(
        mined: List<MinedWord>,
        language: String,
        level: Level,
        transcript: List<Pair<String, String>>,
    ): List<VocabJudge.Verdict?> {
        val key = jevKey()
        if (key.isBlank() || mined.isEmpty()) return mined.map { null }
        return coroutineScope {
            mined.map { m ->
                async(Dispatchers.IO) {
                    judgeSlots.withPermit {
                        runCatching {
                            judge.judge(key, language, level, VocabJudge.Candidate(m.word, m.translation, m.example, m.reason), transcript)
                        }.onFailure { Log.w(TAG, "judge failed for '${m.word}'", it) }.getOrNull()
                    }
                }
            }.awaitAll()
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
        private const val MAX_PARALLEL_JUDGEMENTS = 4
        private const val REVIEW_CONTEXT_TURNS = 8

        private val labels = linkedMapOf(
            "asked_meaning" to "You asked what it means",
            "asked_how_to_say" to "You asked how to say it",
            "corrected" to "The tutor corrected you",
            "struggled" to "You got stuck on it",
            "introduced" to "New word from the tutor",
            "" to "Found in your transcript",
        )

        fun reasonLabel(code: String): String {
            val key = code.lowercase().replace('-', '_').replace(' ', '_')
            return labels[key] ?: code.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }

        /** Inverse of [reasonLabel]; unknown labels come back blank. */
        fun reasonCode(label: String): String = labels.entries.firstOrNull { it.value == label }?.key ?: ""
    }
}
