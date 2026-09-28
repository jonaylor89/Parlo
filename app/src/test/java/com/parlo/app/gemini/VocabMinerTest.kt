package com.parlo.app.gemini

import com.parlo.app.model.Level
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Drives the real [VocabMiner] against a fake `generateContent` endpoint — no API key needed. */
class VocabMinerTest {
    private val server = MockWebServer()
    private lateinit var miner: VocabMiner

    private val transcript = listOf(
        "tutor" to "¿Qué te gustaría beber?",
        "user" to "Um... what does beber mean?",
        "tutor" to "Beber means to drink. ¿Quieres un zumo?",
        "user" to "Sí, un zumo de naranja, por favor.",
        "tutor" to "¡Perfecto! Ahora la cuenta, por favor.",
    )

    @Before
    fun setUp() {
        server.start()
        miner = VocabMiner(
            client = OkHttpClient(),
            baseUrl = server.url("/v1beta/models").toString().trimEnd('/'),
            models = listOf("gemini-2.5-flash", "gemini-2.0-flash"),
        )
    }

    @After fun tearDown() = server.shutdown()

    private fun candidate(text: String) = """{"candidates":[{"content":{"role":"model","parts":[{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}}]}}]}"""

    @Test
    fun `sends transcript, level and known words in JSON mode and parses the reply`() {
        server.enqueue(
            MockResponse().setBody(
                candidate(
                    """[{"word":"beber","translation":"to drink","example":"¿Qué te gustaría beber?","reason":"asked_meaning"},
                       {"word":"la cuenta","translation":"the bill","example":"la cuenta, por favor","reason":"introduced"},
                       {"word":"zumo","translation":"juice","example":"","reason":"introduced"}]""",
                ),
            ),
        )

        val result = miner.mine("k", "Spanish", Level.BEGINNER, transcript, knownWords = listOf("Zumo"))

        val req = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent?key=k", req.path)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        val gen = body["generationConfig"]!!.jsonObject
        assertEquals("application/json", gen["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals("ARRAY", gen["responseSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val prompt = body["contents"]!!.jsonArray.single().jsonObject["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(prompt.contains("USER: Um... what does beber mean?"))
        assertTrue(prompt.contains("TUTOR: Beber means to drink."))
        assertTrue(prompt.contains("Beginner level"))
        assertTrue(prompt.contains("already has saved: Zumo"))

        // known word filtered client-side too, order preserved
        assertEquals(listOf("beber", "la cuenta"), result.map { it.word })
        assertEquals("to drink", result[0].translation)
        assertEquals("asked_meaning", result[0].reason)
    }

    @Test
    fun `falls back to the next model when the first is not found`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"models/gemini-2.5-flash is not found"}}"""))
        server.enqueue(MockResponse().setBody(candidate("""[{"word":"hola","translation":"hello"}]""")))

        val result = miner.mine("k", "Spanish", Level.INTERMEDIATE, transcript, emptyList())

        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent?key=k", server.takeRequest().path)
        assertEquals("/v1beta/models/gemini-2.0-flash:generateContent?key=k", server.takeRequest().path)
        assertEquals(listOf("hola"), result.map { it.word })
    }

    @Test
    fun `tolerates fenced or malformed output and blank words`() {
        server.enqueue(MockResponse().setBody(candidate("```json\n[{\"word\":\" gracias \",\"translation\":\"thanks\"},{\"word\":\"\",\"translation\":\"x\"},{\"word\":\"Gracias\",\"translation\":\"dup\"}]\n```")))
        assertEquals(listOf("gracias"), miner.mine("k", "Spanish", Level.INTERMEDIATE, transcript, emptyList()).map { it.word })

        server.enqueue(MockResponse().setBody(candidate("Sorry, I cannot help with that.")))
        assertTrue(miner.mine("k", "Spanish", Level.INTERMEDIATE, transcript, emptyList()).isEmpty())

        server.enqueue(MockResponse().setBody("""{"candidates":[]}"""))
        assertTrue(miner.mine("k", "Spanish", Level.INTERMEDIATE, transcript, emptyList()).isEmpty())
    }

    @Test
    fun `respects the max words cap`() {
        val many = (1..20).joinToString(",") { """{"word":"w$it","translation":"t$it"}""" }
        server.enqueue(MockResponse().setBody(candidate("[$many]")))
        assertEquals(5, miner.mine("k", "Spanish", Level.INTERMEDIATE, transcript, emptyList(), maxWords = 5).size)
    }

    @Test
    fun `skips the network entirely when the learner never spoke`() {
        val result = miner.mine("k", "Spanish", Level.INTERMEDIATE, listOf("tutor" to "¡Hola!"), emptyList())
        assertTrue(result.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `non-404 errors surface as exceptions with the api message`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"Resource has been exhausted"}}"""))
        try {
            miner.mine("k", "Spanish", Level.INTERMEDIATE, transcript, emptyList())
            fail("expected an exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.isNotBlank())
        }
        assertEquals(1, server.requestCount)
    }
}
