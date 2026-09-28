package com.parlo.app.gemini

import com.parlo.app.data.VocabRepository
import com.parlo.app.data.FakeVocabDao
import com.parlo.app.data.db.VocabEntity
import com.parlo.app.data.db.VocabSource
import com.parlo.app.data.db.VocabStatus
import com.parlo.app.model.LanguageCombo
import com.parlo.app.model.Level
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolHandlerTest {

    private val dao = FakeVocabDao()
    private val saved = mutableListOf<String>()
    private val noted = mutableListOf<String>()
    private val switched = mutableListOf<LanguageCombo>()

    private val handler = ToolHandler(
        vocab = VocabRepository(dao),
        currentLanguage = { "Spanish" },
        currentDialect = { "Madrid Spanish" },
        currentLevel = { Level.INTERMEDIATE },
        sessionId = { 7L },
        onVocabSaved = { saved += it },
        onLanguageSwitched = { switched += it },
        onVocabNoted = { _, word -> noted += word },
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
        assertEquals(VocabSource.MANUAL.name, row.source)
        assertEquals(VocabStatus.KEPT.name, row.status)
        assertEquals(listOf("perro"), saved)
    }

    @Test
    fun `note_vocab files a silent suggestion and never triggers the spoken confirmation`() = runTest {
        val resp = handler.handle(
            FunctionCall(
                id = "c2", name = "note_vocab",
                args = buildJsonObject {
                    put("word", "la cuenta"); put("translation", "the bill")
                    put("example_sentence", "¿Me trae la cuenta?"); put("reason", "asked_how_to_say")
                },
            ),
        )
        assertEquals("noted", resp.response["result"]!!.jsonPrimitive.content)
        assertTrue(resp.response["note"]!!.jsonPrimitive.content.contains("Do not mention"))
        val row = dao.rows.value.single()
        assertEquals("la cuenta", row.word)
        assertEquals(VocabSource.TUTOR.name, row.source)
        assertEquals(VocabStatus.SUGGESTED.name, row.status)
        assertEquals("You asked how to say it", row.reason)
        assertEquals("Spanish", row.language)
        assertTrue(saved.isEmpty())
        assertEquals(listOf("la cuenta"), noted)
    }

    @Test
    fun `note_vocab de-duplicates case-insensitively and save_vocab promotes a suggestion`() = runTest {
        handler.handle(FunctionCall(name = "note_vocab", args = buildJsonObject { put("word", "Perro"); put("translation", "dog"); put("reason", "introduced") }))
        val again = handler.handle(FunctionCall(name = "note_vocab", args = buildJsonObject { put("word", "perro "); put("translation", "dog"); put("reason", "struggled") }))
        assertEquals("already_known", again.response["result"]!!.jsonPrimitive.content)
        assertEquals(1, dao.rows.value.size)
        assertEquals(listOf("Perro"), noted)

        handler.handle(FunctionCall(name = "save_vocab", args = buildJsonObject { put("word", "perro"); put("translation", "dog") }))
        val row = dao.rows.value.single()
        assertEquals(VocabStatus.KEPT.name, row.status)
        assertEquals(VocabSource.TUTOR.name, row.source) // provenance kept, only status flips
        assertEquals(listOf("perro"), saved)
    }

    @Test
    fun `note_vocab without a word is rejected`() = runTest {
        val resp = handler.handle(FunctionCall(name = "note_vocab", args = buildJsonObject { put("reason", "struggled") }))
        assertTrue(resp.response.containsKey("error"))
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(noted.isEmpty())
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

        // new language, no dialect -> catalog default dialect, canonical name, level kept
        handler.handle(FunctionCall(name = "switch_language", args = buildJsonObject { put("language", "portuguese") }))
        assertEquals(LanguageCombo("Portuguese", "Brazilian Portuguese (São Paulo)", Level.INTERMEDIATE), switched.last())

        // level spelled loosely by the model
        handler.handle(FunctionCall(name = "switch_language", args = buildJsonObject { put("language", "Spanish"); put("level", "super-beginner") }))
        assertEquals(Level.SUPER_BEGINNER, switched.last().level)
        handler.handle(FunctionCall(name = "switch_language", args = buildJsonObject { put("language", "Spanish"); put("level", "absolute beginner") }))
        assertEquals(Level.SUPER_BEGINNER, switched.last().level)

        // unknown language -> passes through as typed
        handler.handle(FunctionCall(name = "switch_language", args = buildJsonObject { put("language", "Klingon") }))
        assertEquals(LanguageCombo("Klingon", "Klingon", Level.INTERMEDIATE), switched.last())

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
