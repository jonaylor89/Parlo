package com.parlo.app.data

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveModelDiscoveryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun discovery() = LiveModelDiscovery(OkHttpClient(), server.url("/v1beta/models").toString())

    @Test
    fun `ranking prefers newest native-audio live model`() {
        val ranked = LiveModelDiscovery.rank(
            listOf(
                "gemini-2.0-flash-live-001",
                "gemini-2.5-flash-preview-native-audio-dialog",
                "gemini-2.5-flash-exp-native-audio-thinking-dialog",
                "gemini-live-2.5-flash-preview",
                "gemini-2.0-flash-exp",
            ),
        )
        assertEquals("gemini-2.5-flash-preview-native-audio-dialog", ranked.first())
        assertTrue(ranked.indexOf("gemini-live-2.5-flash-preview") < ranked.indexOf("gemini-2.0-flash-live-001"))
        assertEquals("gemini-2.0-flash-exp", ranked.last())
    }

    @Test
    fun `ranking dedupes and is deterministic for ties`() {
        assertEquals(listOf("b", "a"), LiveModelDiscovery.rank(listOf("a", "b", "a")))
    }

    @Test
    fun `discover filters to bidiGenerateContent, strips prefix and follows pagination`() {
        server.enqueue(
            MockResponse().setBody(
                """{"models":[
                    {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent","countTokens"]},
                    {"name":"models/gemini-2.0-flash-live-001","supportedGenerationMethods":["bidiGenerateContent"],"futureField":1}
                  ],"nextPageToken":"p2"}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"models":[
                    {"name":"models/gemini-2.5-flash-preview-native-audio-dialog","supportedGenerationMethods":["bidiGenerateContent"]},
                    {"name":"models/embedding-001","supportedGenerationMethods":["embedContent"]}
                  ]}""",
            ),
        )

        val models = discovery().discover("test-key")

        assertEquals(listOf("gemini-2.5-flash-preview-native-audio-dialog", "gemini-2.0-flash-live-001"), models)
        val r1 = server.takeRequest()
        assertEquals("/v1beta/models?key=test-key&pageSize=200", r1.path)
        val r2 = server.takeRequest()
        assertTrue(r2.path!!.endsWith("&pageToken=p2"))
    }

    @Test
    fun `discover surfaces api key errors`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid","reason":"API_KEY_INVALID"}}"""))
        val err = runCatching { discovery().discover("bad") }.exceptionOrNull()
        assertEquals("Invalid API key", err?.message)
    }
}
