package com.parlo.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parlo.app.MainActivity
import com.parlo.app.model.CorrectionStyle
import com.parlo.app.model.Level
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

    private fun openWalkConfig() {
        rule.onNodeWithTag("walk_config").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Today's walk")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openLanguagePicker() {
        openWalkConfig()
        rule.onNodeWithTag("language_card").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Search languages, accents, places")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun withoutAKeyTheMainScreenAsksToFinishSetupInsteadOfOfferingStart() {
        rule.onNodeWithText("Parlo").assertIsDisplayed()
        rule.onNodeWithText("Finish setup").assertIsDisplayed()
        rule.onNodeWithTag("setup_card").assertIsDisplayed()
        rule.onNodeWithText("Connect Gemini to start").assertIsDisplayed()
        rule.onAllNodesWithText("Start Walk Session").assertCountEquals(0)
        rule.onAllNodesWithText("Ready", substring = true).assertCountEquals(0)
        rule.onNodeWithTag("walk_config").assertIsDisplayed()
        rule.onAllNodesWithText("Scenario").assertCountEquals(0)
    }

    @Test
    fun walkConfigSheetHoldsLevelAndCorrections() {
        openWalkConfig()
        Level.entries.forEach { rule.onAllNodesWithText(it.shortLabel).onFirst().assertIsDisplayed() }
        CorrectionStyle.entries.forEach { rule.onAllNodesWithText(it.label).onFirst().assertIsDisplayed() }
        rule.onAllNodesWithText("Scenario").assertCountEquals(0)
        rule.onNodeWithText(Level.SUPER_BEGINNER.shortLabel).performClick()
        rule.onAllNodesWithText(Level.SUPER_BEGINNER.description, substring = true).onFirst().assertIsDisplayed()
        rule.onNodeWithText(Level.ADVANCED.shortLabel).performClick()
        rule.onAllNodesWithText(Level.ADVANCED.description, substring = true).onFirst().assertIsDisplayed()
        rule.onAllNodesWithText(Level.SUPER_BEGINNER.description, substring = true).assertCountEquals(0)
        rule.onNodeWithText("Done").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Today's walk")).fetchSemanticsNodes().isEmpty() }
        // Summary card reflects the change.
        rule.onNodeWithText(Level.ADVANCED.label, substring = true).assertIsDisplayed()
    }

    @Test
    fun finishSetupOpensSettingsWithKeyFieldAndFreeKeyLink() {
        rule.onNodeWithText("Finish setup").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Gemini API key")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Gemini API key").assertIsDisplayed()
        rule.onNodeWithText("Get a free key").assertIsDisplayed()
        rule.onNodeWithText("Match wallpaper colours").assertExists()
        rule.onAllNodesWithText("bidiGenerateContent", substring = true).assertCountEquals(0)
    }

    @Test
    fun languagePickerOpensOnLanguagesWithCurrentPinned() {
        openLanguagePicker()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("CURRENT")).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasContentDescription("Back to languages")).assertCountEquals(0)
        rule.onNodeWithTag("language_search").performTextInput("Québec")
        rule.onNodeWithText("Québec French (Montréal)").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Québec French (Montréal)", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Français", substring = true).assertIsDisplayed()
    }

    @Test
    fun languagePickerAcceptsCustomAccent() {
        openLanguagePicker()
        rule.onAllNodesWithText("Spanish").onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Accent")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("language_search").performTextInput("Rural Extremaduran Spanish")
        rule.onNodeWithTag("custom_row").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Rural Extremaduran Spanish", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun vocabAndHistoryScreensAreReachable() {
        rule.onNodeWithContentDescription("Vocabulary").performClick()
        rule.onNodeWithText("Flashcards").assertIsDisplayed()
        rule.onNodeWithText("Vocabulary").assertIsDisplayed()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithContentDescription("Session history").performClick()
        rule.onNodeWithText("Past walks").assertIsDisplayed()
    }
}
