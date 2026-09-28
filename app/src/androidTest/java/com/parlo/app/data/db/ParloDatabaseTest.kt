package com.parlo.app.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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

    @Test
    fun suggestionsCanBeFoundKeptAndDismissed() = runTest {
        vocab.insert(VocabEntity(word = "perro", translation = "dog", exampleSentence = "", language = "Spanish", savedAt = 1))
        val s1 = vocab.insert(VocabEntity(word = "gato", translation = "cat", exampleSentence = "", language = "Spanish", savedAt = 2, source = "TUTOR", status = "SUGGESTED", reason = "You asked what it means"))
        vocab.insert(VocabEntity(word = "pan", translation = "bread", exampleSentence = "", language = "Spanish", savedAt = 3, source = "MINED", status = "SUGGESTED"))
        vocab.insert(VocabEntity(word = "chat", translation = "cat", exampleSentence = "", language = "French", savedAt = 4, source = "MINED", status = "SUGGESTED"))

        // lookup is case-insensitive on both language and word
        assertEquals(s1, vocab.find("spanish", "GATO")!!.id)
        assertNull(vocab.find("Spanish", "chat"))
        assertEquals(setOf("perro", "gato", "pan"), vocab.wordsFor("Spanish").toSet())

        vocab.setStatus(s1, "KEPT")
        assertEquals("KEPT", vocab.find("Spanish", "gato")!!.status)
        assertEquals("TUTOR", vocab.find("Spanish", "gato")!!.source)

        vocab.deleteAllSuggested()
        assertEquals(listOf("gato", "perro"), vocab.observeAll().first().map { it.word })

        vocab.insert(VocabEntity(word = "vino", translation = "wine", exampleSentence = "", language = "Spanish", savedAt = 5, source = "MINED", status = "SUGGESTED"))
        vocab.keepAllSuggested()
        assertTrue(vocab.observeAll().first().none { it.isSuggested })
    }

    @Test
    fun sessionsRememberWhenTheyWereMined() = runTest {
        val id = sessions.insertSession(session())
        assertNull(sessions.getSession(id)!!.minedAt)
        sessions.markMined(id, 42)
        assertEquals(42L, sessions.getSession(id)!!.minedAt)
    }
}

@RunWith(AndroidJUnit4::class)
class ParloDatabaseMigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ParloDatabase::class.java)

    @Test
    fun migrate1To2KeepsExistingVocabAsKeptManualEntries() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt, language, dialect, level, scenario, correctionStyle, durationMs, recap) VALUES (1, 100, 200, 'Spanish', 'Madrid Spanish', 'INTERMEDIATE', 'FREE', 'GENTLE', 100, 'ok')")
            execSQL("INSERT INTO vocab (word, translation, exampleSentence, language, savedAt, sessionId) VALUES ('perro', 'dog', 'El perro corre.', 'Spanish', 150, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true)
        db.query("SELECT word, source, status, reason FROM vocab").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("perro", c.getString(0))
            assertEquals("MANUAL", c.getString(1))
            assertEquals("KEPT", c.getString(2))
            assertEquals("", c.getString(3))
        }
        db.query("SELECT minedAt FROM sessions WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
        db.close()
    }
}
