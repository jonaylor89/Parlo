package com.parlo.app.data

import com.parlo.app.data.db.VocabDao
import com.parlo.app.data.db.VocabEntity
import com.parlo.app.data.db.VocabSource
import com.parlo.app.data.db.VocabStatus
import kotlinx.coroutines.flow.Flow

class VocabRepository(private val dao: VocabDao) {
    fun observeAll(): Flow<List<VocabEntity>> = dao.observeAll()

    /** Explicit save ("save that word"): always kept, promotes an existing suggestion if there is one. */
    suspend fun save(
        word: String,
        translation: String,
        example: String,
        language: String,
        sessionId: Long?,
    ): Long = upsert(word, translation, example, language, sessionId, VocabSource.MANUAL, VocabStatus.KEPT, reason = "")

    /**
     * Silent capture by the tutor or the post-walk miner. Lands in the "Suggested" tray for the
     * user to keep or dismiss. Returns null when the word is already known (kept or suggested).
     */
    suspend fun suggest(
        word: String,
        translation: String,
        example: String,
        language: String,
        sessionId: Long?,
        source: VocabSource,
        reason: String,
    ): Long? {
        val lang = normalizeLanguage(language)
        if (dao.find(lang, word.trim()) != null) return null
        return upsert(word, translation, example, lang, sessionId, source, VocabStatus.SUGGESTED, reason)
    }

    suspend fun knownWords(language: String): List<String> = dao.wordsFor(normalizeLanguage(language))

    suspend fun keep(vocab: VocabEntity) = dao.setStatus(vocab.id, VocabStatus.KEPT.name)
    suspend fun keepAllSuggested() = dao.keepAllSuggested()
    suspend fun dismissAllSuggested() = dao.deleteAllSuggested()
    suspend fun delete(vocab: VocabEntity) = dao.delete(vocab)

    /** Undo for [delete]/[dismissAllSuggested]: re-inserts rows with their original ids. */
    suspend fun restore(entries: List<VocabEntity>) = entries.forEach { dao.insert(it) }

    /** Undo for [keep]/[keepAllSuggested]. */
    suspend fun unkeep(entries: List<VocabEntity>) = entries.forEach { dao.setStatus(it.id, VocabStatus.SUGGESTED.name) }

    private suspend fun upsert(
        word: String,
        translation: String,
        example: String,
        language: String,
        sessionId: Long?,
        source: VocabSource,
        status: VocabStatus,
        reason: String,
    ): Long {
        val lang = normalizeLanguage(language)
        val existing = dao.find(lang, word.trim())
        if (existing != null) {
            if (existing.isSuggested && status == VocabStatus.KEPT) dao.setStatus(existing.id, status.name)
            return existing.id
        }
        return dao.insert(
            VocabEntity(
                word = word.trim(),
                translation = translation.trim(),
                exampleSentence = example.trim(),
                language = lang,
                savedAt = System.currentTimeMillis(),
                sessionId = sessionId,
                source = source.name,
                status = status.name,
                reason = reason.trim(),
            ),
        )
    }

    private fun normalizeLanguage(language: String) = language.trim().ifBlank { "Unknown" }
}
