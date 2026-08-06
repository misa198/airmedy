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
import androidx.test.platform.app.InstrumentationRegistry
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

        val libraryLabel = string(R.string.destination_library)
        val libraryPlaceholder = string(R.string.placeholder_library)

        composeTestRule.onNodeWithContentDescription(libraryLabel).performClick()

        composeTestRule.onNodeWithContentDescription(libraryLabel).assertIsSelected()
        composeTestRule.onNodeWithText(libraryPlaceholder).assertIsDisplayed()
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

        val darkLabel = string(R.string.theme_dark)

        composeTestRule.onNodeWithText(darkLabel).performClick()

        composeTestRule.onNodeWithText(darkLabel).assertIsSelected()
    }

    private fun string(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
