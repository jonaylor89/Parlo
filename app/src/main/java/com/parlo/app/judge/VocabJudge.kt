package com.parlo.app.judge

import com.parlo.app.model.Level
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Asks Jev whether a candidate word actually deserves a flashcard.
 *
 * Extraction (Gemini Live's `note_vocab`, or the post-walk `VocabMiner`) is generative and
 * over-eager; Jev is the judge. One fan-out call per candidate asks three typed questions and
 * the code — not the model — decides between auto-keep, suggest-for-review, and drop.
 */
class VocabJudge(private val jev: JevClient) {

    data class Candidate(
        val word: String,
        val translation: String,
        val example: String,
        /** Reason code the extractor gave (`asked_meaning`, …) or blank. */
        val claimedReason: String,
    )

    enum class Decision { KEEP, SUGGEST, DROP }

    data class Verdict(
        val decision: Decision,
        /** Reason code Jev picked from the transcript, or null when it wasn't sure. */
        val reason: String?,
        /** Probability that the word is at the top usefulness level. */
        val confidence: Double,
    )

    /** Blocking; call from an IO dispatcher. */
    fun judge(
        apiKey: String,
        language: String,
        level: Level,
        candidate: Candidate,
        transcript: List<Pair<String, String>>,
    ): Verdict {
        val answers = jev.ask(
            apiKey = apiKey,
            state = state(language, level, candidate, transcript),
            questions = questions(language),
        )
        return decide(answers)
    }

    internal fun state(language: String, level: Level, c: Candidate, transcript: List<Pair<String, String>>) = buildJsonObject {
        put("target_language", language)
        put("learner_level", level.label)
        put("level_description", level.description)
        putJsonObject("candidate") {
            put("word", c.word)
            put("translation", c.translation)
            put("example_sentence", c.example)
            if (c.claimedReason.isNotBlank()) put("extractor_claimed_reason", c.claimedReason)
        }
        putJsonArray("conversation_excerpt") {
            for ((speaker, text) in excerpt(c.word, transcript)) {
                add(buildJsonObject { put("from", speaker); put("text", text) })
            }
        }
    }

    internal fun questions(language: String): JsonObject = buildJsonObject {
        putJsonObject(Q_REAL) {
            put("type", "noul")
            put("instructions", "Is the candidate word a real word or phrase in $language, as opposed to a person's name, a place, an English word, a number, a filler sound, or a truncated fragment?")
        }
        putJsonObject(Q_USEFUL) {
            put("type", "score")
            put("instructions", "How much would a flashcard for the candidate word help this learner, given their level and how it came up in the conversation?")
            putJsonArray("criteria") {
                add(JsonPrimitive("Not worth a card: the learner clearly already knows it, or it is trivial, ultra-common for this level, or not a learning item"))
                add(JsonPrimitive("Maybe: a real word the learner might want, but the conversation shows no clear gap or it is only marginally useful"))
                add(JsonPrimitive("Definitely: the conversation shows a real gap (asked, stumbled, was corrected, or newly taught) and the word is useful again"))
            }
        }
        putJsonObject(Q_REASON) {
            put("type", "choice")
            put("instructions", "Why did this word come up for the learner in the conversation excerpt?")
            putJsonObject("criteria") {
                put("asked_meaning", "The learner asked what the word means")
                put("asked_how_to_say", "The learner asked how to say it in $language")
                put("corrected", "The tutor corrected the learner's use or pronunciation of it")
                put("struggled", "The learner stalled, hesitated, or switched to English around it")
                put("introduced", "The tutor introduced it as a new word without the learner asking")
                put("known", "The learner used it fluently; no gap is visible")
            }
        }
    }

    internal fun decide(a: JevClient.Answers): Verdict {
        val real = a.noul(Q_REAL)?.probability ?: 1.0
        val useful = a.score(Q_USEFUL)
        val reasonAns = a.choice(Q_REASON)
        val top = useful?.probabilities?.get(TOP_LEVEL) ?: 0.0
        val score = useful?.score ?: 0.0
        val reason = reasonAns?.takeIf { it.confidence >= REASON_MIN_CONFIDENCE }?.choice

        val decision = when {
            real < REAL_MIN -> Decision.DROP
            reason == "known" && (reasonAns?.confidence ?: 0.0) >= KNOWN_MIN_CONFIDENCE -> Decision.DROP
            top >= KEEP_MIN_TOP_PROBABILITY -> Decision.KEEP
            score >= SUGGEST_MIN_SCORE -> Decision.SUGGEST
            else -> Decision.DROP
        }
        return Verdict(decision, reason?.takeIf { it != "known" }, top)
    }

    /** The lines around the word (± [CONTEXT_LINES]) so the state stays small and cheap. */
    private fun excerpt(word: String, transcript: List<Pair<String, String>>): List<Pair<String, String>> {
        if (transcript.isEmpty()) return emptyList()
        val hits = transcript.indices.filter { transcript[it].second.contains(word, ignoreCase = true) }
        val keep = if (hits.isEmpty()) {
            (transcript.size - MAX_EXCERPT_LINES).coerceAtLeast(0) until transcript.size
        } else {
            hits.flatMap { (it - CONTEXT_LINES)..(it + CONTEXT_LINES) }.filter { it in transcript.indices }.toSortedSet()
        }
        return keep.take(MAX_EXCERPT_LINES).map { transcript[it] }
    }

    companion object {
        internal const val Q_REAL = "is_real_word"
        internal const val Q_USEFUL = "usefulness"
        internal const val Q_REASON = "reason"
        internal const val TOP_LEVEL = 2

        /** Below this probability of being a real target-language word: drop. */
        const val REAL_MIN = 0.5
        /** P(top usefulness level) at or above this: auto-keep without review. */
        const val KEEP_MIN_TOP_PROBABILITY = 0.9
        /** Expected usefulness score (0–2) at or above this: show as a suggestion. */
        const val SUGGEST_MIN_SCORE = 0.75
        const val REASON_MIN_CONFIDENCE = 0.5
        const val KNOWN_MIN_CONFIDENCE = 0.8
        private const val CONTEXT_LINES = 2
        private const val MAX_EXCERPT_LINES = 10
    }
}
