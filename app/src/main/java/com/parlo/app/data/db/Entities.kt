package com.parlo.app.data.db

import androidx.room.ColumnInfo
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
    /** When the transcript was mined for vocab; null until the post-walk pass has run. */
    @ColumnInfo(defaultValue = "NULL") val minedAt: Long? = null,
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
    /** [VocabSource] name. */
    @ColumnInfo(defaultValue = "MANUAL") val source: String = VocabSource.MANUAL.name,
    /** [VocabStatus] name. */
    @ColumnInfo(defaultValue = "KEPT") val status: String = VocabStatus.KEPT.name,
    /** Why the tutor/miner picked it up, e.g. "You asked what it means". */
    @ColumnInfo(defaultValue = "") val reason: String = "",
    /** Jev's probability that the word is worth a card (null when it was never judged). */
    @ColumnInfo(defaultValue = "NULL") val confidence: Double? = null,
) {
    val isSuggested: Boolean get() = status == VocabStatus.SUGGESTED.name
}

enum class VocabSource { MANUAL, TUTOR, MINED }

enum class VocabStatus { KEPT, SUGGESTED }
