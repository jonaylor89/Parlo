package com.parlo.app.gemini

import com.parlo.app.model.CorrectionStyle
import com.parlo.app.model.Level
import com.parlo.app.model.Scenario
import com.parlo.app.model.SessionConfig
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `system instruction reflects language dialect level scenario and correction style`() {
        val c = SessionConfig(
            language = "Japanese", dialect = "Kansai Japanese", level = Level.BEGINNER,
            scenario = Scenario.CAFE, correctionStyle = CorrectionStyle.EXPLICIT,
        )
        val p = PromptBuilder.systemInstruction(c)
        assertTrue(p.contains("practice Japanese"))
        assertTrue(p.contains("Kansai Japanese"))
        assertTrue(p.contains("Beginner level"))
        assertTrue(p.contains(Scenario.CAFE.label))
        assertTrue(p.contains(Scenario.CAFE.prompt))
        assertTrue(p.contains("Your current correction style is Explicit."))
        // voice commands and tool guidance are always present
        for (kw in listOf("save_vocab", "note_vocab", "switch_language", "slower", "say it in English", "silent")) {
            assertTrue("missing '$kw'", p.contains(kw))
        }
    }

    @Test
    fun `super beginner opens in English, translates everything, and recaps in English`() {
        val c = SessionConfig(language = "Korean", dialect = "Seoul Korean", level = Level.SUPER_BEGINNER)
        val p = PromptBuilder.systemInstruction(c)
        assertTrue(p.contains("Super Beginner level"))
        assertTrue(p.contains("Your current level is Super Beginner."))
        assertTrue(p.contains("Begin in English"))
        assertTrue(p.contains("teach one short Korean greeting"))
        assertFalse(p.contains("Begin by greeting the user briefly in Korean"))

        val recap = PromptBuilder.recapMessage(c)
        assertTrue(recap.contains("in English, repeating each Korean phrase slowly"))

        val switch = PromptBuilder.switchMessage(SessionConfig(), SessionConfig().copy(level = Level.SUPER_BEGINNER))
        assertTrue(switch.contains("at Super Beginner level"))
        assertTrue(switch.contains(Level.SUPER_BEGINNER.guidance))
        assertTrue(switch.contains("acknowledge the switch in English"))

        // other levels are unaffected
        val normal = PromptBuilder.systemInstruction(c.copy(level = Level.INTERMEDIATE))
        assertTrue(normal.contains("Begin by greeting the user briefly in Korean"))
        assertFalse(normal.contains("Begin in English"))
    }

    @Test
    fun `level parse is forgiving`() {
        assertEquals(Level.SUPER_BEGINNER, Level.parse("SUPER_BEGINNER"))
        assertEquals(Level.SUPER_BEGINNER, Level.parse("Super Beginner"))
        assertEquals(Level.SUPER_BEGINNER, Level.parse("super-beginner"))
        assertEquals(Level.SUPER_BEGINNER, Level.parse("absolute beginner"))
        assertEquals(Level.BEGINNER, Level.parse("beginner"))
        assertEquals(Level.ADVANCED, Level.parse("Advanced"))
        assertEquals(Level.INTERMEDIATE, Level.parse(null))
        assertEquals(Level.INTERMEDIATE, Level.parse("fluent-ish"))
    }

    @Test
    fun `blank dialect falls back to standard language`() {
        val p = PromptBuilder.systemInstruction(SessionConfig(language = "Dutch", dialect = ""))
        assertTrue(p.contains("standard Dutch"))
    }

    @Test
    fun `switch message only lists what changed`() {
        val old = SessionConfig()
        val onlyScenario = old.copy(scenario = Scenario.DIRECTIONS)
        val m1 = PromptBuilder.switchMessage(old, onlyScenario)
        assertTrue(m1.contains(Scenario.DIRECTIONS.label))
        assertFalse(m1.contains("correction style"))
        assertFalse(m1.contains("level"))

        val everything = old.copy(language = "Italian", dialect = "Roman Italian", level = Level.ADVANCED, correctionStyle = CorrectionStyle.NONE)
        val m2 = PromptBuilder.switchMessage(old, everything)
        assertTrue(m2.contains("Roman Italian (Italian) at Advanced level"))
        assertTrue(m2.contains("None correction style"))
        assertTrue(m2.contains("Do not call switch_language"))

        assertTrue(PromptBuilder.switchMessage(old, old).contains("continue as before"))
    }

    @Test
    fun `resume context message embeds recent transcript`() {
        val m = PromptBuilder.resumeContextMessage(listOf("User" to "Hola", "Tutor" to "¡Hola! ¿Qué tal?"), SessionConfig())
        assertTrue(m.contains("User: Hola"))
        assertTrue(m.contains("Tutor: ¡Hola! ¿Qué tal?"))
        assertTrue(m.contains("Do not mention the reconnection"))
    }

    @Test
    fun `tool declarations match the required schema`() {
        val fns = PromptBuilder.toolDeclarations().single().functionDeclarations.associateBy { it.name }
        assertEquals(setOf("save_vocab", "note_vocab", "switch_language"), fns.keys)

        val note = fns.getValue("note_vocab").parameters!!
        assertEquals(setOf("word", "translation", "example_sentence", "reason", "language"), note["properties"]!!.jsonObject.keys)
        assertEquals(listOf("word", "translation", "reason"), note["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf("asked_meaning", "asked_how_to_say", "corrected", "struggled", "introduced"),
            note["properties"]!!.jsonObject["reason"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(fns.getValue("note_vocab").description!!.contains("Do not tell the user"))

        val save = fns.getValue("save_vocab").parameters!!
        assertEquals("OBJECT", save["type"]!!.jsonPrimitive.content)
        assertEquals(
            setOf("word", "translation", "example_sentence", "language"),
            save["properties"]!!.jsonObject.keys,
        )
        assertEquals(listOf("word", "translation", "language"), save["required"]!!.jsonArray.map { it.jsonPrimitive.content })

        val switch = fns.getValue("switch_language").parameters!!
        assertEquals(setOf("language", "dialect", "level"), switch["properties"]!!.jsonObject.keys)
        assertEquals(listOf("language"), switch["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
}
