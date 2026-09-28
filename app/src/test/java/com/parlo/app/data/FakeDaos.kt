package com.parlo.app.data

import com.parlo.app.data.db.SessionDao
import com.parlo.app.data.db.SessionEntity
import com.parlo.app.data.db.SessionWithTurns
import com.parlo.app.data.db.TurnEntity
import com.parlo.app.data.db.VocabDao
import com.parlo.app.data.db.VocabEntity
import com.parlo.app.data.db.VocabStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeVocabDao : VocabDao {
    val rows = MutableStateFlow<List<VocabEntity>>(emptyList())
    override suspend fun insert(vocab: VocabEntity): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1
        rows.value = rows.value + vocab.copy(id = id)
        return id
    }
    override suspend fun delete(vocab: VocabEntity) { rows.value = rows.value.filterNot { it.id == vocab.id } }
    override fun observeAll(): Flow<List<VocabEntity>> = rows
    override suspend fun countForSession(sessionId: Long) = rows.value.count { it.sessionId == sessionId }
    override suspend fun find(language: String, word: String) =
        rows.value.firstOrNull { it.language.equals(language, true) && it.word.equals(word, true) }
    override suspend fun wordsFor(language: String) = rows.value.filter { it.language.equals(language, true) }.map { it.word }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun update(vocab: VocabEntity) { rows.value = rows.value.map { if (it.id == vocab.id) vocab else it } }
    override suspend fun setStatus(id: Long, status: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(status = status) else it }
    }
    override suspend fun keepAllSuggested() {
        rows.value = rows.value.map { if (it.isSuggested) it.copy(status = VocabStatus.KEPT.name) else it }
    }
    override suspend fun deleteAllSuggested() { rows.value = rows.value.filterNot { it.isSuggested } }
}

class FakeSessionDao : SessionDao {
    val sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val turns = MutableStateFlow<List<TurnEntity>>(emptyList())
    override suspend fun insertSession(session: SessionEntity): Long {
        val id = (sessions.value.maxOfOrNull { it.id } ?: 0L) + 1
        sessions.value = sessions.value + session.copy(id = id)
        return id
    }
    override suspend fun updateSession(session: SessionEntity) {
        sessions.value = sessions.value.map { if (it.id == session.id) session else it }
    }
    override suspend fun insertTurn(turn: TurnEntity): Long {
        val id = (turns.value.maxOfOrNull { it.id } ?: 0L) + 1
        turns.value = turns.value + turn.copy(id = id)
        return id
    }
    override fun observeSessions(): Flow<List<SessionEntity>> = sessions
    override suspend fun getSession(id: Long) = sessions.value.firstOrNull { it.id == id }
    override fun observeSessionWithTurns(id: Long): Flow<SessionWithTurns?> =
        sessions.map { list -> list.firstOrNull { it.id == id }?.let { s -> SessionWithTurns(s, turns.value.filter { it.sessionId == id }) } }
    override suspend fun getTurns(sessionId: Long) = turns.value.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }
    override suspend fun deleteSession(id: Long) { sessions.value = sessions.value.filterNot { it.id == id } }
    override suspend fun deleteEmptyUnfinishedSessions() {}
    override suspend fun markMined(id: Long, at: Long) {
        sessions.value = sessions.value.map { if (it.id == id) it.copy(minedAt = at) else it }
    }
}
