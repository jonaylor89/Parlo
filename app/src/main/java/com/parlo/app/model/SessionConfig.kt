package com.parlo.app.model

import kotlinx.serialization.Serializable

enum class Level(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced");

    companion object {
        fun parse(value: String?): Level =
            entries.firstOrNull { it.name.equals(value, true) || it.label.equals(value, true) } ?: INTERMEDIATE
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
