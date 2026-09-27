package com.parlo.app.gemini

import com.parlo.app.data.VocabRepository
import com.parlo.app.data.db.VocabDao
import com.parlo.app.data.db.VocabEntity
import com.parlo.app.model.LanguageCombo
import com.parlo.app.model.Level
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolHandlerTest {

    private class FakeVocabDao : VocabDao {
        val rows = MutableStateFlow<List<VocabEntity>>(emptyList())
        override suspend fun insert(vocab: VocabEntity): Long {
            val id = rows.value.size + 1L
            rows.value = rows.value + vocab.copy(id = id)
            return id
        }
        override suspend fun delete(vocab: VocabEntity) { rows.value = rows.value.filterNot { it.id == vocab.id } }
        override fun observeAll(): Flow<List<VocabEntity>> = rows
        override suspend fun countForSession(sessionId: Long) = rows.value.count { it.sessionId == sessionId }
    }

    private val dao = FakeVocabDao()
    private val saved = mutableListOf<String>()
    private val switched = mutableListOf<LanguageCombo>()

    private val handler = ToolHandler(
        vocab = VocabRepository(dao),
        currentLanguage = { "Spanish" },
        currentDialect = { "Madrid Spanish" },
        currentLevel = { Level.INTERMEDIATE },
        sessionId = { 7L },
        onVocabSaved = { saved += it },
        onLanguageSwitched = { switched += it },
    )

    @Test
    fun `save_vocab stores the word and answers the call id`() = runTest {
        val call = FunctionCall(
            id = "call-1", name = "save_vocab",
            args = buildJsonObject { put("word", " perro "); put("translation", "dog"); put("example_sentence", "El perro corre.") },
        )
        val resp = handler.handle(call)

        assertEquals("call-1", resp.id)
        assertEquals("save_vocab", resp.name)
        assertEquals("saved", resp.response["result"]!!.jsonPrimitive.content)
        val row = dao.rows.value.single()
        assertEquals("perro", row.word)
        assertEquals("dog", row.translation)
        assertEquals("Spanish", row.language) // defaulted from the current session
        assertEquals(7L, row.sessionId)
        assertEquals(listOf("perro"), saved)
    }

    @Test
    fun `save_vocab without a word is rejected`() = runTest {
        val resp = handler.handle(FunctionCall(name = "save_vocab", args = buildJsonObject { put("translation", "dog") }))
        assertTrue(resp.response.containsKey("error"))
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `switch_language fills in dialect and level from context`() = runTest {
        // same language, only the level changes -> keep current dialect
        handler.handle(FunctionCall(name = "switch_language", args = buildJsonObject { put("language", "Spanish"); put("level", "beginner") }))
        assertEquals(LanguageCombo("Spanish", "Madrid Spanish", Level.BEGINNER), switched.last())

        // new language, no dialect -> dialect defaults to the language name, level kept
        handler.handle(FunctionCall(name = "switch_language", args = buildJsonObject { put("language", "Portuguese") }))
        assertEquals(LanguageCombo("Portuguese", "Portuguese", Level.INTERMEDIATE), switched.last())

        // explicit dialect wins
        val resp = handler.handle(
            FunctionCall(name = "switch_language", args = buildJsonObject { put("language", "Japanese"); put("dialect", "Kansai Japanese"); put("level", "Advanced") }),
        )
        assertEquals(LanguageCombo("Japanese", "Kansai Japanese", Level.ADVANCED), switched.last())
        assertEquals("ok", resp.response["result"]!!.jsonPrimitive.content)
        assertEquals("Advanced", resp.response["level"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unknown functions get an error response without crashing`() = runTest {
        val resp = handler.handle(FunctionCall(name = "order_pizza"))
        assertNull(resp.id)
        assertTrue(resp.response["error"]!!.jsonPrimitive.content.contains("order_pizza"))
    }
}
