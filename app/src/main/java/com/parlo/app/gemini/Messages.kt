package com.parlo.app.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ----- Shared -----

@Serializable
data class Blob(val mimeType: String, val data: String)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: Blob? = null,
)

@Serializable
data class Content(val role: String? = null, val parts: List<Part> = emptyList())

// ----- Client -> Server -----

@Serializable
data class PrebuiltVoiceConfig(val voiceName: String)

@Serializable
data class VoiceConfig(val prebuiltVoiceConfig: PrebuiltVoiceConfig)

@Serializable
data class SpeechConfig(val voiceConfig: VoiceConfig)

@Serializable
data class GenerationConfig(
    val responseModalities: List<String> = listOf("AUDIO"),
    val speechConfig: SpeechConfig? = null,
    val temperature: Double? = null,
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null,
)

@Serializable
data class Tool(val functionDeclarations: List<FunctionDeclaration>)

@Serializable
data class SessionResumptionConfig(val handle: String? = null)

@Serializable
data class SlidingWindow(val targetTokens: Long? = null)

@Serializable
data class ContextWindowCompressionConfig(
    val slidingWindow: SlidingWindow = SlidingWindow(),
    val triggerTokens: Long? = null,
)

@Serializable
class AudioTranscriptionConfig

@Serializable
data class Setup(
    val model: String,
    val generationConfig: GenerationConfig,
    val systemInstruction: Content,
    val tools: List<Tool> = emptyList(),
    val sessionResumption: SessionResumptionConfig = SessionResumptionConfig(),
    val contextWindowCompression: ContextWindowCompressionConfig = ContextWindowCompressionConfig(),
    val inputAudioTranscription: AudioTranscriptionConfig = AudioTranscriptionConfig(),
    val outputAudioTranscription: AudioTranscriptionConfig = AudioTranscriptionConfig(),
)

@Serializable
data class RealtimeInput(
    val audio: Blob? = null,
    val text: String? = null,
    val audioStreamEnd: Boolean? = null,
)

@Serializable
data class ClientContent(val turns: List<Content>, val turnComplete: Boolean = true)

@Serializable
data class FunctionResponse(val id: String? = null, val name: String, val response: JsonObject)

@Serializable
data class ToolResponse(val functionResponses: List<FunctionResponse>)

@Serializable
data class ClientMessage(
    val setup: Setup? = null,
    val realtimeInput: RealtimeInput? = null,
    val clientContent: ClientContent? = null,
    val toolResponse: ToolResponse? = null,
)

// ----- Server -> Client -----

@Serializable
data class Transcription(val text: String? = null, val finished: Boolean? = null)

@Serializable
data class ServerContent(
    val modelTurn: Content? = null,
    val turnComplete: Boolean? = null,
    val interrupted: Boolean? = null,
    val generationComplete: Boolean? = null,
    val inputTranscription: Transcription? = null,
    val outputTranscription: Transcription? = null,
)

@Serializable
data class FunctionCall(val id: String? = null, val name: String, val args: JsonObject? = null)

@Serializable
data class ToolCall(val functionCalls: List<FunctionCall> = emptyList())

@Serializable
data class ToolCallCancellation(val ids: List<String> = emptyList())

@Serializable
data class SessionResumptionUpdate(val newHandle: String? = null, val resumable: Boolean? = null)

@Serializable
data class GoAway(val timeLeft: String? = null)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Long? = null,
    val responseTokenCount: Long? = null,
    val totalTokenCount: Long? = null,
)

@Serializable
class SetupComplete

@Serializable
data class ServerMessage(
    val setupComplete: SetupComplete? = null,
    val serverContent: ServerContent? = null,
    val toolCall: ToolCall? = null,
    val toolCallCancellation: ToolCallCancellation? = null,
    val sessionResumptionUpdate: SessionResumptionUpdate? = null,
    val goAway: GoAway? = null,
    val usageMetadata: UsageMetadata? = null,
    val error: JsonElement? = null,
)
