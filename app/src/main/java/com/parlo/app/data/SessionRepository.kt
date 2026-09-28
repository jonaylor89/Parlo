package com.parlo.app.data

import com.parlo.app.data.db.SessionDao
import com.parlo.app.data.db.SessionEntity
import com.parlo.app.data.db.SessionWithTurns
import com.parlo.app.data.db.TurnEntity
import com.parlo.app.model.SessionConfig
import com.parlo.app.model.Speaker
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val dao: SessionDao) {

    fun observeSessions(): Flow<List<SessionEntity>> = dao.observeSessions()

    fun observeSessionWithTurns(id: Long): Flow<SessionWithTurns?> = dao.observeSessionWithTurns(id)

    suspend fun startSession(config: SessionConfig, startedAt: Long): Long =
        dao.insertSession(
            SessionEntity(
                startedAt = startedAt,
                language = config.language,
                dialect = config.dialect,
                level = config.level.name,
                scenario = config.scenario.name,
                correctionStyle = config.correctionStyle.name,
            ),
        )

    suspend fun addTurn(sessionId: Long, speaker: Speaker, text: String, timestamp: Long) {
        if (text.isBlank()) return
        dao.insertTurn(TurnEntity(sessionId = sessionId, speaker = speaker.name, text = text.trim(), timestamp = timestamp))
    }

    suspend fun recentTurns(sessionId: Long, limit: Int = 12): List<TurnEntity> =
        dao.getTurns(sessionId).takeLast(limit)

    suspend fun finishSession(sessionId: Long, endedAt: Long, recap: String?, config: SessionConfig) {
        val s = dao.getSession(sessionId) ?: return
        dao.updateSession(
            s.copy(
                endedAt = endedAt,
                durationMs = endedAt - s.startedAt,
                recap = recap?.takeIf { it.isNotBlank() },
                language = config.language,
                dialect = config.dialect,
                level = config.level.name,
            ),
        )
    }

    suspend fun getSession(id: Long): SessionEntity? = dao.getSession(id)

    suspend fun allTurns(sessionId: Long): List<TurnEntity> = dao.getTurns(sessionId)

    suspend fun markMined(id: Long, at: Long = System.currentTimeMillis()) = dao.markMined(id, at)

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun cleanupEmpty() = dao.deleteEmptyUnfinishedSessions()
}
