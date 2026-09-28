package com.parlo.app.judge

import com.parlo.app.judge.VocabJudge.Decision
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VocabJudgeTest {
    private val server = MockWebServer()
    private lateinit var client: JevClient
    private lateinit var judge: VocabJudge

    @Before fun setUp() {
        server.start()
        client = JevClient(OkHttpClient(), endpoint = server.url("/v1/systemone").toString())
        judge = VocabJudge(client)
    }

    @After fun tearDown() = server.shutdown()

    private fun answers(real: Double, top: Double, reason: String, reasonConf: Double, mid: Double = (1 - top) / 2) = """
        {"answers":{
          "is_real_word":{"type":"noul","noul":$real},
          "usefulness":{"type":"score","score":${top * 2 + mid},"confidence":$top,"probabilities":{"0":${1 - top - mid},"1":$mid,"2":$top}},
          "reason":{"type":"choice","choice":"$reason","confidence":$reasonConf,"probabilities":{"$reason":$reasonConf}}
        }}
    """.trimIndent()

    private val candidate = VocabJudge.Candidate("madrugar", "to get up early", "Mañana tengo que madrugar.", "introduced")
    private val transcript = listOf(
        "tutor" to "¡Hola! ¿Cómo estás hoy?",
        "user" to "Bien, gracias.",
        "tutor" to "¿A qué hora te levantas?",
        "user" to "Me levanto a las seis.",
        "tutor" to "Muy bien, tienes que madrugar entonces.",
        "user" to "Madrugar? What does that mean?",
        "tutor" to "Madrugar means to get up early.",
        "user" to "Ah, sí, tengo que madrugar.",
        "tutor" to "Perfecto.",
    )

    @Test
    fun `state carries level, candidate and only the transcript lines around the word`() {
        server.enqueue(MockResponse().setBody(answers(0.99, 0.95, "asked_meaning", 0.9)))

        judge.judge("k", "Spanish", Level.BEGINNER, candidate, transcript)

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val state = body["state"]!!.jsonObject
        assertEquals("Spanish", state["target_language"]!!.jsonPrimitive.content)
        assertEquals(Level.BEGINNER.label, state["learner_level"]!!.jsonPrimitive.content)
        val cand = state["candidate"]!!.jsonObject
        assertEquals("madrugar", cand["word"]!!.jsonPrimitive.content)
        assertEquals("introduced", cand["extractor_claimed_reason"]!!.jsonPrimitive.content)
        val excerpt = state["conversation_excerpt"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content }
        assertTrue(excerpt.none { it == "¡Hola! ¿Cómo estás hoy?" })
        assertTrue(excerpt.contains("¿A qué hora te levantas?"))
        assertTrue(excerpt.contains("Madrugar? What does that mean?"))
        assertTrue(excerpt.contains("Perfecto."))

        val qs = body["questions"]!!.jsonObject
        assertEquals(setOf("is_real_word", "usefulness", "reason"), qs.keys)
        assertEquals("noul", qs["is_real_word"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(3, qs["usefulness"]!!.jsonObject["criteria"]!!.jsonArray.size)
        assertEquals("choice", qs["reason"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue("asked_meaning" in qs["reason"]!!.jsonObject["criteria"]!!.jsonObject.keys)
    }

    @Test
    fun `high top-level probability keeps outright and adopts Jev's reason`() {
        val v = judge.decide(client.parse(answers(0.99, 0.95, "asked_meaning", 0.9)))
        assertEquals(Decision.KEEP, v.decision)
        assertEquals("asked_meaning", v.reason)
        assertEquals(0.95, v.confidence, 1e-9)
    }

    @Test
    fun `middling usefulness becomes a suggestion and low-confidence reason is discarded`() {
        val v = judge.decide(client.parse(answers(0.95, 0.5, "struggled", 0.3, mid = 0.3)))
        assertEquals(Decision.SUGGEST, v.decision)
        assertNull(v.reason)
    }

    @Test
    fun `low usefulness drops`() {
        val v = judge.decide(client.parse(answers(0.95, 0.1, "introduced", 0.9, mid = 0.2)))
        assertEquals(Decision.DROP, v.decision)
    }

    @Test
    fun `not a real word drops even when useful`() {
        val v = judge.decide(client.parse(answers(0.2, 0.95, "introduced", 0.9)))
        assertEquals(Decision.DROP, v.decision)
    }

    @Test
    fun `confidently known words drop even when the miner liked them`() {
        val v = judge.decide(client.parse(answers(0.99, 0.95, "known", 0.9)))
        assertEquals(Decision.DROP, v.decision)
        assertNull(v.reason)
    }

    @Test
    fun `missing answers fall back to suggest-nothing rather than crash`() {
        val v = judge.decide(client.parse("""{"answers":{}}"""))
        assertEquals(Decision.DROP, v.decision)
        assertNull(v.reason)
        assertEquals(0.0, v.confidence, 1e-9)
    }
}
