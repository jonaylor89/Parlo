package com.parlo.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val language: String,
    val dialect: String,
    val level: String,
    val scenario: String,
    val correctionStyle: String,
    val durationMs: Long = 0,
    val recap: String? = null,
)

@Entity(
    tableName = "turns",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val speaker: String,
    val text: String,
    val timestamp: Long,
)

@Entity(tableName = "vocab", indices = [Index("language")])
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val translation: String,
    val exampleSentence: String,
    val language: String,
    val savedAt: Long,
    val sessionId: Long? = null,
)
