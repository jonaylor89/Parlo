package com.parlo.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParloDatabaseTest {
    private lateinit var db: ParloDatabase
    private lateinit var sessions: SessionDao
    private lateinit var vocab: VocabDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ParloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessions = db.sessionDao()
        vocab = db.vocabDao()
    }

    @After fun tearDown() = db.close()

    private fun session(startedAt: Long = 1_000) = SessionEntity(
        startedAt = startedAt, language = "Spanish", dialect = "Madrid Spanish",
        level = "Intermediate", scenario = "Free Conversation", correctionStyle = "Gentle",
    )

    @Test
    fun sessionsAreListedNewestFirstWithTheirTurns() = runTest {
        val old = sessions.insertSession(session(startedAt = 1_000))
        val new = sessions.insertSession(session(startedAt = 2_000))
        sessions.insertTurn(TurnEntity(sessionId = new, speaker = "user", text = "Hola", timestamp = 2_001))
        sessions.insertTurn(TurnEntity(sessionId = new, speaker = "tutor", text = "¡Hola! ¿Qué tal?", timestamp = 2_002))

        assertEquals(listOf(new, old), sessions.observeSessions().first().map { it.id })
        val withTurns = sessions.observeSessionWithTurns(new).first()!!
        assertEquals(listOf("Hola", "¡Hola! ¿Qué tal?"), withTurns.turns.map { it.text })
        assertEquals(2, sessions.getTurns(new).size)
        assertTrue(sessions.getTurns(old).isEmpty())
    }

    @Test
    fun endingASessionPersistsRecapAndDuration() = runTest {
        val id = sessions.insertSession(session())
        sessions.updateSession(sessions.getSession(id)!!.copy(endedAt = 61_000, durationMs = 60_000, recap = "Nice walk"))
        val s = sessions.getSession(id)!!
        assertEquals(60_000L, s.durationMs)
        assertEquals("Nice walk", s.recap)
        assertNotNull(s.endedAt)
    }

    @Test
    fun deletingASessionCascadesToTurns() = runTest {
        val id = sessions.insertSession(session())
        sessions.insertTurn(TurnEntity(sessionId = id, speaker = "user", text = "x", timestamp = 1))
        sessions.deleteSession(id)
        assertNull(sessions.getSession(id))
        assertTrue(sessions.getTurns(id).isEmpty())
    }

    @Test
    fun emptyUnfinishedSessionsAreCleanedUpButOthersKept() = runTest {
        val emptyUnfinished = sessions.insertSession(session())
        val finishedEmpty = sessions.insertSession(session().copy(endedAt = 5))
        val unfinishedWithTurns = sessions.insertSession(session())
        sessions.insertTurn(TurnEntity(sessionId = unfinishedWithTurns, speaker = "user", text = "hi", timestamp = 1))

        sessions.deleteEmptyUnfinishedSessions()

        assertNull(sessions.getSession(emptyUnfinished))
        assertNotNull(sessions.getSession(finishedEmpty))
        assertNotNull(sessions.getSession(unfinishedWithTurns))
    }

    @Test
    fun vocabIsGroupedByLanguageAndSurvivesSessionDeletion() = runTest {
        val id = sessions.insertSession(session())
        vocab.insert(VocabEntity(word = "perro", translation = "dog", exampleSentence = "El perro corre.", language = "Spanish", savedAt = 2, sessionId = id))
        vocab.insert(VocabEntity(word = "gato", translation = "cat", exampleSentence = "", language = "Spanish", savedAt = 3, sessionId = id))
        vocab.insert(VocabEntity(word = "chat", translation = "cat", exampleSentence = "", language = "French", savedAt = 1, sessionId = null))

        assertEquals(2, vocab.countForSession(id))
        assertEquals(listOf("chat", "gato", "perro"), vocab.observeAll().first().map { it.word })

        sessions.deleteSession(id)
        assertEquals(3, vocab.observeAll().first().size) // vocab is not tied to session lifetime

        val gato = vocab.observeAll().first().first { it.word == "gato" }
        vocab.delete(gato)
        assertEquals(listOf("chat", "perro"), vocab.observeAll().first().map { it.word })
    }
}
