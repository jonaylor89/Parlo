package com.parlo.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parlo.app.MainActivity
import com.parlo.app.model.CorrectionStyle
import com.parlo.app.model.Level
import com.parlo.app.model.Scenario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the real activity (no API key configured) and walks the main screen.
 * Nothing here talks to Gemini.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenShowsSessionControls() {
        rule.onNodeWithText("Parlo").assertIsDisplayed()
        rule.onNodeWithText("Start Walk Session").assertIsDisplayed()
        Level.entries.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
        CorrectionStyle.entries.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
        rule.onNodeWithText(Scenario.FREE.label).assertIsDisplayed()
        rule.onNodeWithText(Scenario.CAFE.label).assertIsDisplayed()
    }

    @Test
    fun startingWithoutAnApiKeyOpensSettingsInsteadOfConnecting() {
        rule.onNodeWithText("Start Walk Session").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Gemini API key")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Gemini API key").assertIsDisplayed()
        rule.onNodeWithText("Start Walk Session").assertIsDisplayed() // still idle, no session started
    }

    @Test
    fun languagePickerSearchesAcrossAccentsAndAppliesSelection() {
        rule.onNodeWithTag("language_card").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Accent")).fetchSemanticsNodes().isNotEmpty() }
        // Default language is in the catalog, so we land on its accent list; go back to languages.
        rule.onNodeWithContentDescription("Back to languages").performClick()
        rule.onNodeWithTag("language_search").performTextInput("Québec")
        rule.onNodeWithText("Québec French (Montréal)").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Québec French (Montréal)", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Français", substring = true).assertIsDisplayed()
    }

    @Test
    fun languagePickerAcceptsCustomAccent() {
        rule.onNodeWithTag("language_card").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Accent")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("language_search").performTextInput("Rural Extremaduran Spanish")
        rule.onNodeWithTag("custom_row").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Rural Extremaduran Spanish", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun vocabAndHistoryScreensAreReachable() {
        rule.onNodeWithContentDescription("Vocab list").performClick()
        rule.onNodeWithText("Flashcards").assertIsDisplayed()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithContentDescription("Session history").performClick()
        rule.onNodeWithText("Session history").assertIsDisplayed()
    }
}
