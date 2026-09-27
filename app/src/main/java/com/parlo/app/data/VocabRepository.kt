package com.parlo.app.data

import com.parlo.app.data.db.VocabDao
import com.parlo.app.data.db.VocabEntity
import kotlinx.coroutines.flow.Flow

class VocabRepository(private val dao: VocabDao) {
    fun observeAll(): Flow<List<VocabEntity>> = dao.observeAll()

    suspend fun save(
        word: String,
        translation: String,
        example: String,
        language: String,
        sessionId: Long?,
    ): Long = dao.insert(
        VocabEntity(
            word = word.trim(),
            translation = translation.trim(),
            exampleSentence = example.trim(),
            language = language.trim().ifBlank { "Unknown" },
            savedAt = System.currentTimeMillis(),
            sessionId = sessionId,
        ),
    )

    suspend fun delete(vocab: VocabEntity) = dao.delete(vocab)
}
