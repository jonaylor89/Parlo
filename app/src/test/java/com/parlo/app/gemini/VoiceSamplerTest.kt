package com.parlo.app.gemini

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64

/** Drives the real [VoiceSampler] against a fake `generateContent` endpoint — no API key needed. */
class VoiceSamplerTest {
    private val server = MockWebServer()
    private lateinit var sampler: VoiceSampler
    private val pcm = ByteArray(6000) { (it % 251).toByte() }

    @Before
    fun setUp() {
        server.start()
        sampler = VoiceSampler(
            client = OkHttpClient(),
            baseUrl = server.url("/v1beta/models").toString().trimEnd('/'),
            ttsModels = listOf("tts-a", "tts-b"),
            textModels = listOf("text-a"),
        )
    }

    @After fun tearDown() = server.shutdown()

    private fun textCandidate(text: String) =
        """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}}]}}]}"""

    private fun audioCandidate(bytes: ByteArray): String {
        val half = bytes.size / 2
        val a = Base64.getEncoder().encodeToString(bytes.copyOfRange(0, half))
        val b = Base64.getEncoder().encodeToString(bytes.copyOfRange(half, bytes.size))
        return """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;codec=pcm;rate=24000","data":"$a"}},{"text":"ignored"},{"inlineData":{"mimeType":"audio/L16;codec=pcm;rate=24000","data":"$b"}}]}}]}"""
    }

    @Test
    fun `translates greeting once, then requests TTS with the chosen voice and concatenates audio parts`() {
        server.enqueue(MockResponse().setBody(textCandidate("¡Hola! Soy tu profe. ¿Listo para pasear y charlar?")))
        server.enqueue(MockResponse().setBody(audioCandidate(pcm)))

        val out = sampler.sample("k", "Sulafat", "Spanish", "Rioplatense Spanish (Buenos Aires)")
        assertArrayEquals(pcm, out)

        val translate = server.takeRequest()
        assertTrue(translate.path!!.startsWith("/v1beta/models/text-a:generateContent"))
        assertTrue(translate.body.readUtf8().contains(VoiceSampler.GREETING_EN))

        val tts = server.takeRequest()
        assertTrue(tts.path!!.startsWith("/v1beta/models/tts-a:generateContent"))
        val body = Json.parseToJsonElement(tts.body.readUtf8()).jsonObject
        val gen = body["generationConfig"]!!.jsonObject
        assertEquals("AUDIO", gen["responseModalities"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(
            "Sulafat",
            gen["speechConfig"]!!.jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!.jsonObject["voiceName"]!!.jsonPrimitive.content,
        )
        val prompt = body["contents"]!!.jsonArray.single().jsonObject["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(prompt.contains("¡Hola! Soy tu profe"))
        assertTrue(prompt.contains("Rioplatense"))

        // Second voice, same language: no new translation call, and the sample is cached afterwards.
        server.enqueue(MockResponse().setBody(audioCandidate(pcm)))
        sampler.sample("k", "Puck", "Spanish", "Rioplatense Spanish (Buenos Aires)")
        assertTrue(server.takeRequest().path!!.contains("tts-a"))
        sampler.sample("k", "Puck", "Spanish", "Rioplatense Spanish (Buenos Aires)")
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `English skips translation and a missing TTS model falls back to the next`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"not found"}}"""))
        server.enqueue(MockResponse().setBody(audioCandidate(pcm)))

        val out = sampler.sample("k", "Kore", "English", "General American")
        assertArrayEquals(pcm, out)
        assertTrue(server.takeRequest().path!!.contains("tts-a"))
        assertTrue(server.takeRequest().path!!.contains("tts-b"))
    }

    @Test
    fun `bad key surfaces as a readable error`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid","status":"INVALID_ARGUMENT"}}"""))
        try {
            sampler.sample("bad", "Kore", "English", "General American")
            fail("expected failure")
        } catch (e: IllegalStateException) {
            assertEquals("Invalid API key", e.message)
        }
    }
}
