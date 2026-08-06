package me.misa198.airmedy

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun homeContentScrollsWhileFloatingNavigationRemainsAvailable() {
        composeTestRule.setContent { App(onHomeSampleDetailSelected = {}) }

        val finalSection = string(R.string.home_demo_section_title, 4)
        val homeLabel = string(R.string.destination_home)

        composeTestRule.onNodeWithText(finalSection).performScrollTo()

        composeTestRule.onNodeWithText(finalSection).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(homeLabel).assertIsDisplayed()
    }

    @Test
    fun homeCanPushAndPopTheSampleDetailPage() {
        var state by mutableStateOf(AppUiState())
        composeTestRule.setContent {
            App(
                uiState = state,
                onHomeSampleDetailSelected = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Home to state.stackFor(AppDestination.Home) + AppStackPage.HomeSampleDetail
                        ),
                    )
                },
                onNavigateBack = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Home to state.stackFor(AppDestination.Home).dropLast(1)
                        ),
                    )
                },
            )
        }

        composeTestRule.onNodeWithText(string(R.string.home_demo_open_page)).performClick()

        composeTestRule.onNodeWithText(string(R.string.home_sample_page_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        composeTestRule.onNodeWithText(string(R.string.home_demo_open_page)).assertIsDisplayed()
    }

    @Test
    fun systemBackPopsTheCurrentDestinationStack() {
        var state by mutableStateOf(AppUiState())
        composeTestRule.setContent {
            App(
                uiState = state,
                onHomeSampleDetailSelected = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Home to state.stackFor(AppDestination.Home) + AppStackPage.HomeSampleDetail
                        ),
                    )
                },
                onNavigateBack = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Home to state.stackFor(AppDestination.Home).dropLast(1)
                        ),
                    )
                },
            )
        }

        composeTestRule.onNodeWithText(string(R.string.home_demo_open_page)).performClick()
        composeTestRule.onNodeWithText(string(R.string.home_sample_page_heading)).assertIsDisplayed()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        composeTestRule.onNodeWithText(string(R.string.home_demo_open_page)).assertIsDisplayed()
    }

    @Test
    fun switchingDestinationsRetainsTheHomeStack() {
        var state by mutableStateOf(
            AppUiState(
                destinationStacks = rootDestinationStacks() + (
                    AppDestination.Home to listOf(AppStackPage.Root, AppStackPage.HomeSampleDetail)
                ),
            ),
        )
        composeTestRule.setContent {
            App(
                uiState = state,
                onDestinationSelected = { destination ->
                    state = state.copy(selectedDestination = destination)
                },
            )
        }

        composeTestRule.onNodeWithText(string(R.string.home_sample_page_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.destination_library)).performClick()
        composeTestRule.onNodeWithText(string(R.string.placeholder_library)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.destination_home)).performClick()

        composeTestRule.onNodeWithText(string(R.string.home_sample_page_heading)).assertIsDisplayed()
    }

    private fun string(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)
}
