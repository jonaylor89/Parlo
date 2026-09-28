package com.parlo.app.gemini

import com.parlo.app.model.CorrectionStyle
import com.parlo.app.model.Level
import com.parlo.app.model.SessionConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object PromptBuilder {

    fun systemInstruction(c: SessionConfig): String {
        val dialect = c.dialect.ifBlank { "standard ${c.language}" }
        val level = c.level.label
        val correction = c.correctionStyle.label
        return """
You are a friendly, patient conversation partner helping the user practice ${c.language} with a $dialect accent and vocabulary, at a $level level. The user is walking outdoors, wearing earbuds, and cannot look at a screen.

Conversation style:
- Keep each reply short: usually one to three sentences. This is a conversation, not a lecture.
- End most turns with a simple question or prompt so the user always knows it's their turn.
- Speak at a pace suited to $level.
${Level.entries.joinToString("\n") { "  - " + it.guidance }}
${levelReminder(c.level)}
- Use vocabulary, expressions, and pronunciation typical of $dialect.
- Current scenario: ${c.scenario.label} — ${c.scenario.prompt}. Stay in that scenario unless the user changes topic.
- If the user is silent for a long time, don't fill the silence repeatedly. They may be crossing a street or catching their breath. Wait, then offer at most one gentle prompt.

Corrections ($correction):
- Gentle: don't point out errors directly; naturally repeat the corrected phrase in your reply.
- Explicit: briefly give the correct form in one short sentence, then continue the conversation.
- None: ignore errors unless meaning is unclear.
${correctionReminder(c.correctionStyle)}

Commands the user may say at any time, in English or ${c.language}:
- 'slower' / 'repeat': repeat your last sentence slowly and clearly.
- 'what does X mean?': explain briefly, then return to the conversation.
- 'say it in English': give a short English translation, then continue in ${c.language}.
- 'more corrections' / 'fewer corrections': adjust correction style.
- 'save that word': call save_vocab with the most relevant recent word.
- 'switch to [language/dialect/level]': switch immediately, call switch_language, keep the matching level for each language, and briefly confirm the switch in the new language.

Proactively call save_vocab for genuinely useful new words you introduce, but no more than a few per session.

${opening(c)}
        """.trimIndent()
    }

    private fun levelReminder(level: Level) = "Your current level is ${level.label}." + when (level) {
        Level.SUPER_BEGINNER -> " Keep it tiny: English first, one short phrase at a time, translate everything, repeat a lot, celebrate small wins. When switching level mid-session, a request like 'easier' from a Super Beginner means even shorter phrases and more English, not a new level."
        else -> ""
    }

    private fun opening(c: SessionConfig) = when (c.level) {
        Level.SUPER_BEGINNER ->
            "Begin in English: say hello, tell the user in one sentence that you'll go very slowly and translate everything, then teach one short ${c.language} greeting (say it slowly, give the English, say it slowly again) and invite the user to try saying it."
        else -> "Begin by greeting the user briefly in ${c.language} and opening the scenario with one short question."
    }

    private fun correctionReminder(style: CorrectionStyle) = when (style) {
        CorrectionStyle.GENTLE -> "Your current correction style is Gentle."
        CorrectionStyle.EXPLICIT -> "Your current correction style is Explicit."
        CorrectionStyle.NONE -> "Your current correction style is None."
    }

    /** Text turn sent when the user switches settings from the UI mid-session. */
    fun switchMessage(old: SessionConfig, new: SessionConfig): String {
        val changes = mutableListOf<String>()
        if (old.language != new.language || old.dialect != new.dialect || old.level != new.level) {
            changes += "speak ${new.dialect.ifBlank { new.language }} (${new.language}) at ${new.level.label} level"
            if (old.level != new.level) changes += new.level.guidance
        }
        if (old.scenario != new.scenario) changes += "switch the scenario to \"${new.scenario.label}\": ${new.scenario.prompt}"
        if (old.correctionStyle != new.correctionStyle) changes += "use the ${new.correctionStyle.label} correction style"
        val body = if (changes.isEmpty()) "continue as before" else changes.joinToString("; ")
        val ackLanguage = if (new.level == Level.SUPER_BEGINNER) "English" else new.language
        return "[System] From now on, $body. Briefly acknowledge the switch in $ackLanguage and continue the conversation. Do not call switch_language for this change."
    }

    fun repeatSlowlyMessage() =
        "[System] The user pressed the headset button: repeat your last sentence slowly and clearly, then wait."

    fun recapMessage(c: SessionConfig) =
        "[System] The walk is ending. Give a spoken recap of about 30 seconds in ${if (c.level == Level.SUPER_BEGINNER) "English, repeating each ${c.language} phrase slowly with its meaning," else "a mix of ${c.language} and English"} suited to a ${c.level.label} learner: recurring mistakes you noticed, what went well, and three words or phrases to review. Do not ask a question at the end; finish with a short goodbye."

    fun resumeContextMessage(recent: List<Pair<String, String>>, c: SessionConfig): String {
        val lines = recent.joinToString("\n") { (who, text) -> "$who: $text" }
        return "[System] The connection dropped and was re-established without memory. Continue the same ${c.language} conversation naturally from this recent transcript. Do not mention the reconnection:\n$lines"
    }

    fun toolDeclarations(): List<Tool> = listOf(
        Tool(
            functionDeclarations = listOf(
                FunctionDeclaration(
                    name = "save_vocab",
                    description = "Save a useful word or phrase to the user's vocabulary list. Call when the user says 'save that word' or when you introduce an important new word.",
                    parameters = buildJsonObject {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("word") { put("type", "STRING"); put("description", "The word or phrase in the target language") }
                            putJsonObject("translation") { put("type", "STRING"); put("description", "Short English translation") }
                            putJsonObject("example_sentence") { put("type", "STRING"); put("description", "A short example sentence using the word") }
                            putJsonObject("language") { put("type", "STRING"); put("description", "The language the word is in, e.g. Spanish") }
                        }
                        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("word")); add(kotlinx.serialization.json.JsonPrimitive("translation")); add(kotlinx.serialization.json.JsonPrimitive("language")) }
                    },
                ),
                FunctionDeclaration(
                    name = "switch_language",
                    description = "Report that the user switched target language, dialect, or level by voice so the app can update its settings. Call this whenever you change language, dialect, or level at the user's request.",
                    parameters = buildJsonObject {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("language") { put("type", "STRING"); put("description", "New target language, e.g. Japanese") }
                            putJsonObject("dialect") { put("type", "STRING"); put("description", "New regional accent/dialect, e.g. Kansai Japanese. Reuse the previous one if unchanged.") }
                            putJsonObject("level") { put("type", "STRING"); put("description", "Super Beginner, Beginner, Intermediate, or Advanced") }
                        }
                        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("language")) }
                    },
                ),
            ),
        ),
    )
}
