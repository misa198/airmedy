package me.misa198.airmedy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import me.misa198.airmedy.settings.ThemeMode

class AppNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectingLibraryUpdatesTheVisibleDestinationAndSelectedTab() {
        var state by mutableStateOf(AppUiState())
        composeTestRule.setContent {
            App(
                uiState = state,
                onDestinationSelected = { destination ->
                    state = state.copy(selectedDestination = destination)
                },
            )
        }

        composeTestRule.onNodeWithContentDescription("Library").performClick()

        composeTestRule.onNodeWithContentDescription("Library").assertIsSelected()
        composeTestRule.onNodeWithText("Your library will appear here.").assertIsDisplayed()
    }

    @Test
    fun settingsThemeControlMarksTheSelectedThemeMode() {
        var state by mutableStateOf(AppUiState(selectedDestination = AppDestination.Settings))
        composeTestRule.setContent {
            App(
                uiState = state,
                onThemeModeSelected = { themeMode ->
                    state = state.copy(themeMode = themeMode)
                },
            )
        }

        composeTestRule.onNodeWithText("Dark").performClick()

        composeTestRule.onNodeWithText("Dark").assertIsSelected()
    }
}
