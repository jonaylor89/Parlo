package com.parlo.app.model

import kotlinx.serialization.Serializable

enum class Level(val label: String, val description: String, val guidance: String) {
    SUPER_BEGINNER(
        "Super Beginner",
        "Mostly English. One tiny phrase at a time, translated and repeated.",
        "Super Beginner: the user understands almost nothing yet. Lead the conversation in English and teach one very short phrase (one to four words) at a time. " +
            "Say the phrase slowly and clearly, then give the English meaning right away, then say the phrase slowly once more and ask the user to repeat it. " +
            "Never say more than one short target-language sentence without an English translation immediately after it. " +
            "Accept one-word answers, English answers, and rough pronunciation warmly; if the user is silent or confused, do not ask a new question — repeat even slower or make it simpler. " +
            "Recycle the same handful of phrases many times before adding a new one.",
    ),
    BEGINNER(
        "Beginner",
        "Slow and simple, brief English help when stuck.",
        "Beginner: slow, clear, simple vocabulary, short sentences, and occasional brief English support.",
    ),
    INTERMEDIATE(
        "Intermediate",
        "Natural pace, English only when needed.",
        "Intermediate: natural pace, English only when the user is stuck.",
    ),
    ADVANCED(
        "Advanced",
        "Native pace, idioms and regional expressions.",
        "Advanced: native pace, idioms and regional expressions, no English unless asked.",
    );

    companion object {
        fun parse(value: String?): Level {
            val v = value?.trim()?.replace('-', ' ')?.replace('_', ' ') ?: return INTERMEDIATE
            return entries.firstOrNull { it.name.replace('_', ' ').equals(v, true) || it.label.equals(v, true) }
                ?: when {
                    v.contains("super", true) || v.contains("absolute", true) || v.contains("total", true) || v.contains("zero", true) -> SUPER_BEGINNER
                    else -> INTERMEDIATE
                }
        }
    }
}

enum class CorrectionStyle(val label: String, val description: String) {
    GENTLE("Gentle", "Natural recasts"),
    EXPLICIT("Explicit", "Brief correction, then continue"),
    NONE("None", "Pure fluency practice");

    companion object {
        fun parse(value: String?): CorrectionStyle =
            entries.firstOrNull { it.name.equals(value, true) } ?: GENTLE
    }
}

enum class Scenario(val label: String, val prompt: String) {
    FREE("Free Conversation", "Free conversation about anything the user wants"),
    CAFE("Café / Ordering Food", "The user is at a café or restaurant ordering food and drinks; you are the server"),
    DIRECTIONS("Asking for Directions", "The user is a visitor asking for directions around town; you are a helpful local"),
    SMALL_TALK("Small Talk", "Casual small talk between two acquaintances who just met"),
    MY_DAY("Tell Me About Your Day", "Ask the user about their day and follow up with genuine curiosity"),
    SURROUNDINGS("Describe Your Surroundings", "The user is walking outdoors; ask them to describe what they see, hear and pass by");

    companion object {
        fun parse(value: String?): Scenario =
            entries.firstOrNull { it.name.equals(value, true) } ?: FREE
    }
}

/** Everything that shapes a tutoring session. Language/dialect/level/scenario/correction can be switched live. */
@Serializable
data class SessionConfig(
    val language: String = "Spanish",
    val dialect: String = "Castilian Spanish (Madrid)",
    val level: Level = Level.INTERMEDIATE,
    val scenario: Scenario = Scenario.FREE,
    val correctionStyle: CorrectionStyle = CorrectionStyle.GENTLE,
    val voice: String = "Puck",
    val model: String = "",
) {
    val languageCombo: LanguageCombo get() = LanguageCombo(language, dialect, level)
}

/** A quick-switch chip: language + dialect + level. */
@Serializable
data class LanguageCombo(
    val language: String,
    val dialect: String,
    val level: Level,
) {
    val label: String get() = buildString {
        append(dialect.ifBlank { language })
        append(" · ")
        append(level.label)
    }
}

object Defaults {
    val languages: List<String> get() = LanguageCatalog.languages.map { it.name }
    val voices = listOf("Puck", "Aoede", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Zephyr")
}
