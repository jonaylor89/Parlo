package com.parlo.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SessionWithTurns(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val turns: List<TurnEntity>,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Insert
    suspend fun insertTurn(turn: TurnEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSessionWithTurns(id: Long): Flow<SessionWithTurns?>

    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getTurns(sessionId: Long): List<TurnEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM sessions WHERE endedAt IS NULL AND id NOT IN (SELECT DISTINCT sessionId FROM turns)")
    suspend fun deleteEmptyUnfinishedSessions()
}

@Dao
interface VocabDao {
    @Insert
    suspend fun insert(vocab: VocabEntity): Long

    @Delete
    suspend fun delete(vocab: VocabEntity)

    @Query("SELECT * FROM vocab ORDER BY language ASC, savedAt DESC")
    fun observeAll(): Flow<List<VocabEntity>>

    @Query("SELECT COUNT(*) FROM vocab WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int
}
