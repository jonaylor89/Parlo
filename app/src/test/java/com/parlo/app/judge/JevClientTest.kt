package com.parlo.app.judge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class JevClientTest {
    private val server = MockWebServer()
    private lateinit var client: JevClient

    @Before fun setUp() {
        server.start()
        client = JevClient(OkHttpClient(), endpoint = server.url("/v1/systemone").toString())
    }

    @After fun tearDown() = server.shutdown()

    private val questions = buildJsonObject {
        putJsonObject("q1") { put("type", "noul"); put("instructions", "Is it a word?") }
    }

    @Test
    fun `posts bearer auth, model, state and questions per the TypeSafe schema`() {
        server.enqueue(MockResponse().setBody("""{"answers":{"q1":{"type":"noul","noul":0.9}}}"""))

        client.ask("sk-test", JsonPrimitive("hola"), questions)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/systemone", req.path)
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("hola", body["state"]!!.jsonPrimitive.content)
        assertEquals("jev-latest", body["model"]!!.jsonPrimitive.content)
        assertEquals("noul", body["questions"]!!.jsonObject["q1"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `parses choice, score and noul answers with their probabilities`() {
        val answers = client.parse(
            """
            {"answers":{
              "route":{"type":"choice","choice":"billing","confidence":0.98,"probabilities":{"billing":0.98,"tech":0.02}},
              "quality":{"type":"score","score":1.6,"confidence":0.7,"legend":{"0":"bad","1":"ok","2":"good"},"probabilities":{"0":0.1,"1":0.2,"2":0.7}},
              "ok":{"type":"noul","noul":0.42}
            }}
            """.trimIndent(),
        )
        val route = answers.choice("route")!!
        assertEquals("billing", route.choice)
        assertEquals(0.98, route.confidence, 1e-9)
        assertEquals(0.02, route.probabilities["tech"]!!, 1e-9)
        val quality = answers.score("quality")!!
        assertEquals(1.6, quality.score, 1e-9)
        assertEquals(0.7, quality.probabilities[2]!!, 1e-9)
        assertEquals(0.42, answers.noul("ok")!!.probability, 1e-9)
        assertNull(answers.choice("quality"))
        assertNull(answers.noul("missing"))
    }

    @Test
    fun `unknown answer types and missing answers block are tolerated`() {
        assertEquals(emptySet<String>(), client.parse("""{"id":"x"}""").ids)
        val a = client.parse("""{"answers":{"weird":{"type":"vector","value":[1,2]},"ok":{"type":"noul","noul":0.5}}}""")
        assertEquals(setOf("ok"), a.ids)
    }

    @Test
    fun `http errors surface the API message and status`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid API key"}}"""))
        try {
            client.ask("bad", JsonPrimitive("x"), questions)
            fail("expected JevException")
        } catch (e: JevClient.JevException) {
            assertEquals(401, e.code)
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("rejected") && e.message!!.contains("Invalid API key"))
        }
        server.enqueue(MockResponse().setResponseCode(429).setBody(""))
        try {
            client.ask("k", JsonPrimitive("x"), questions)
            fail("expected JevException")
        } catch (e: JevClient.JevException) {
            assertEquals(429, e.code)
            assertTrue(e.message!!.contains("rate limit"))
        }
    }
}
