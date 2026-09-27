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
        for (kw in listOf("save_vocab", "switch_language", "slower", "say it in English", "silent")) {
            assertTrue("missing '$kw'", p.contains(kw))
        }
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
        assertEquals(setOf("save_vocab", "switch_language"), fns.keys)

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
