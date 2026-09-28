package com.parlo.app.gemini

import com.parlo.app.data.VocabCapture
import com.parlo.app.data.VocabRepository
import com.parlo.app.data.db.VocabSource
import com.parlo.app.model.LanguageCatalog
import com.parlo.app.model.LanguageCombo
import com.parlo.app.model.Level
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Executes `save_vocab` / `note_vocab` / `switch_language` locally and produces the `toolResponse` payloads. */
class ToolHandler(
    private val vocab: VocabRepository,
    private val currentLanguage: () -> String,
    private val currentDialect: () -> String,
    private val currentLevel: () -> Level,
    private val sessionId: () -> Long?,
    private val onVocabSaved: suspend (word: String) -> Unit,
    private val onLanguageSwitched: suspend (LanguageCombo) -> Unit,
    private val onVocabNoted: suspend (id: Long, word: String) -> Unit = { _, _ -> },
) {
    suspend fun handle(call: FunctionCall): FunctionResponse {
        val args = call.args ?: JsonObject(emptyMap())
        val response: JsonObject = when (call.name) {
            "save_vocab" -> saveVocab(args)
            "note_vocab" -> noteVocab(args)
            "switch_language" -> switchLanguage(args)
            else -> buildJsonObject { put("error", "unknown function ${call.name}") }
        }
        return FunctionResponse(id = call.id, name = call.name, response = response)
    }

    private suspend fun saveVocab(args: JsonObject): JsonObject {
        val word = args.str("word")
        if (word.isBlank()) return buildJsonObject { put("error", "word is required") }
        val id = vocab.save(
            word = word,
            translation = args.str("translation"),
            example = args.str("example_sentence"),
            language = args.str("language").ifBlank { currentLanguage() },
            sessionId = sessionId(),
        )
        onVocabSaved(word)
        return buildJsonObject { put("result", "saved"); put("id", id) }
    }

    /** Silent capture: goes to the Suggested tray, no earcon, and the model is told not to mention it. */
    private suspend fun noteVocab(args: JsonObject): JsonObject {
        val word = args.str("word")
        if (word.isBlank()) return buildJsonObject { put("error", "word is required") }
        val id = vocab.suggest(
            word = word,
            translation = args.str("translation"),
            example = args.str("example_sentence"),
            language = args.str("language").ifBlank { currentLanguage() },
            sessionId = sessionId(),
            source = VocabSource.TUTOR,
            reason = VocabCapture.reasonLabel(args.str("reason")),
        )
        if (id != null) onVocabNoted(id, word)
        return buildJsonObject {
            put("result", if (id != null) "noted" else "already_known")
            put("note", "Do not mention this to the user; continue the conversation.")
        }
    }

    private suspend fun switchLanguage(args: JsonObject): JsonObject {
        val language = LanguageCatalog.canonicalName(args.str("language").ifBlank { currentLanguage() })
        val dialectArg = args.str("dialect")
        val dialect = when {
            dialectArg.isNotBlank() -> dialectArg
            language.equals(currentLanguage(), true) -> currentDialect()
            else -> LanguageCatalog.defaultDialectFor(language)
        }
        val levelArg = args.str("level")
        val level = if (levelArg.isBlank()) currentLevel() else Level.parse(levelArg)
        val combo = LanguageCombo(language, dialect, level)
        onLanguageSwitched(combo)
        return buildJsonObject {
            put("result", "ok")
            put("language", language); put("dialect", dialect); put("level", level.label)
        }
    }

    private fun JsonObject.str(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.trim().orEmpty()
}
