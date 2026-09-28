package com.parlo.app.gemini

import com.parlo.app.model.SessionConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    private fun setupFor(config: SessionConfig, model: String = "models/gemini-test-live") = Setup(
        model = model,
        generationConfig = GenerationConfig(
            speechConfig = SpeechConfig(VoiceConfig(PrebuiltVoiceConfig(config.voice))),
        ),
        systemInstruction = Content(parts = listOf(Part(text = PromptBuilder.systemInstruction(config)))),
        tools = PromptBuilder.toolDeclarations(),
    )

    @Test
    fun `setup message has the shape the Live API expects`() {
        val text = json.encodeToString(ClientMessage.serializer(), ClientMessage(setup = setupFor(SessionConfig(voice = "Kore"))))
        val root = json.parseToJsonElement(text).jsonObject
        assertEquals(setOf("setup"), root.keys)
        val setup = root["setup"]!!.jsonObject
        assertEquals("models/gemini-test-live", setup["model"]!!.jsonPrimitive.content)

        val gen = setup["generationConfig"]!!.jsonObject
        assertEquals("AUDIO", gen["responseModalities"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(
            "Kore",
            gen["speechConfig"]!!.jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!.jsonObject["voiceName"]!!.jsonPrimitive.content,
        )
        assertTrue(setup["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray.isNotEmpty())
        assertNotNull(setup["inputAudioTranscription"])
        assertNotNull(setup["outputAudioTranscription"])
        assertNotNull(setup["sessionResumption"])
        assertNotNull(setup["contextWindowCompression"]!!.jsonObject["slidingWindow"])

        val fns = setup["tools"]!!.jsonArray.single().jsonObject["functionDeclarations"]!!.jsonArray
        assertEquals(listOf("save_vocab", "note_vocab", "switch_language"), fns.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        // no nulls / no "temperature" leaking in
        assertFalse(text.contains("null"))
        assertFalse(text.contains("temperature"))
    }

    @Test
    fun `resumption handle is emitted only when present`() {
        val without = json.encodeToString(Setup.serializer(), setupFor(SessionConfig()))
        assertFalse(without.contains("handle"))
        val with = json.encodeToString(
            Setup.serializer(),
            setupFor(SessionConfig()).copy(sessionResumption = SessionResumptionConfig(handle = "abc")),
        )
        assertTrue(with.contains("\"handle\":\"abc\""))
    }

    @Test
    fun `realtime audio input uses the pcm mime type`() {
        val msg = ClientMessage(realtimeInput = RealtimeInput(audio = Blob(GeminiApi.INPUT_MIME, "AAAA")))
        val root = json.parseToJsonElement(json.encodeToString(ClientMessage.serializer(), msg)).jsonObject
        val audio = root["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertEquals("audio/pcm;rate=16000", audio["mimeType"]!!.jsonPrimitive.content)
        assertEquals("AAAA", audio["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `server content with audio and transcription parses`() {
        val text = """
            {"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AQID"}}]},
             "outputTranscription":{"text":"Hola"},"turnComplete":true,"someFutureField":{"x":1}},
             "usageMetadata":{"promptTokenCount":10,"totalTokenCount":42,"unknownCounter":3}}
        """.trimIndent()
        val msg = json.decodeFromString(ServerMessage.serializer(), text)
        val sc = msg.serverContent!!
        assertEquals("audio/pcm;rate=24000", sc.modelTurn!!.parts.single().inlineData!!.mimeType)
        assertEquals("Hola", sc.outputTranscription!!.text)
        assertEquals(true, sc.turnComplete)
        assertNull(sc.interrupted)
        assertEquals(42L, msg.usageMetadata!!.totalTokenCount)
    }

    @Test
    fun `tool call, cancellation, resumption update, goAway and setupComplete parse`() {
        val tool = json.decodeFromString(
            ServerMessage.serializer(),
            """{"toolCall":{"functionCalls":[{"id":"c1","name":"save_vocab","args":{"word":"perro","translation":"dog"}}]}}""",
        ).toolCall!!.functionCalls.single()
        assertEquals("c1", tool.id)
        assertEquals("save_vocab", tool.name)
        assertEquals("perro", tool.args!!["word"]!!.jsonPrimitive.content)

        val cancel = json.decodeFromString(ServerMessage.serializer(), """{"toolCallCancellation":{"ids":["c1","c2"]}}""")
        assertEquals(listOf("c1", "c2"), cancel.toolCallCancellation!!.ids)

        val upd = json.decodeFromString(
            ServerMessage.serializer(),
            """{"sessionResumptionUpdate":{"newHandle":"h-123","resumable":true}}""",
        ).sessionResumptionUpdate!!
        assertEquals("h-123", upd.newHandle)
        assertEquals(true, upd.resumable)

        val goAway = json.decodeFromString(ServerMessage.serializer(), """{"goAway":{"timeLeft":"10s"}}""").goAway!!
        assertEquals("10s", goAway.timeLeft)

        assertNotNull(json.decodeFromString(ServerMessage.serializer(), """{"setupComplete":{}}""").setupComplete)
    }

    @Test
    fun `error payload is kept as raw json`() {
        val msg = json.decodeFromString(
            ServerMessage.serializer(),
            """{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}""",
        )
        assertEquals("API key not valid", msg.error!!.jsonObject["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tool response serializes with id name and response`() {
        val resp = FunctionResponse(
            id = "c1",
            name = "save_vocab",
            response = buildJsonObject { put("result", "saved") },
        )
        val text = json.encodeToString(ClientMessage.serializer(), ClientMessage(toolResponse = ToolResponse(listOf(resp))))
        val fr = json.parseToJsonElement(text).jsonObject["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray.single().jsonObject
        assertEquals("c1", fr["id"]!!.jsonPrimitive.content)
        assertEquals("save_vocab", fr["name"]!!.jsonPrimitive.content)
        assertEquals("saved", fr["response"]!!.jsonObject["result"]!!.jsonPrimitive.content)
    }
}
