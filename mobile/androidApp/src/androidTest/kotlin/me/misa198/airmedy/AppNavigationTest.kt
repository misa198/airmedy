package me.misa198.airmedy

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun navigationExposesOneAccessibleTargetPerDestination() {
        composeTestRule.setContent { App() }

        listOf(
            R.string.destination_home,
            R.string.destination_library,
            R.string.destination_search,
            R.string.destination_settings,
        ).forEach { labelRes ->
            composeTestRule.onAllNodesWithContentDescription(string(labelRes)).assertCountEquals(1)
        }
    }

    @Test
    fun selectingLibraryDispatchesIntentAndUpdatesTheVisibleDestination() {
        val harness = AppHarness()
        composeTestRule.setContent { harness.Render() }

        val libraryLabel = string(R.string.destination_library)
        composeTestRule.onNodeWithContentDescription(libraryLabel).performClick()

        composeTestRule.onNodeWithContentDescription(libraryLabel).assertIsSelected()
        composeTestRule.onNodeWithText(string(R.string.library_empty_title)).assertIsDisplayed()
        assertEquals(AppIntent.SelectDestination(AppDestination.Library), harness.intents.last())
    }

    @Test
    fun appearanceOpensAndThemeSelectionDispatchesIntents() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Settings))
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_appearance)).performClick()
        composeTestRule.onNodeWithText(string(R.string.appearance_theme_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.theme_system)).performClick()
        composeTestRule.onNodeWithText(string(R.string.theme_dark)).performClick()

        assertEquals(AppIntent.SetThemeMode(ThemeMode.Dark), harness.intents.last())
    }

    @Test
    fun aboutLinkDispatchesOneTimeExternalUrlIntent() {
        val harness = AppHarness(
            AppUiState(
                selectedDestination = AppDestination.Settings,
                destinationStacks = rootDestinationStacks() + (
                    AppDestination.Settings to listOf(AppStackPage.Root, AppStackPage.SettingsAbout)
                ),
            ),
        )
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.about_github)).performClick()

        assertEquals(
            AppIntent.OpenExternalUrl("https://github.com/misa198/airmedy"),
            harness.intents.last(),
        )
    }

    @Test
    fun homeCanPushAndPopTheSampleDetailPage() {
        val harness = AppHarness()
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithText(string(R.string.home_demo_open_page)).performClick()
        composeTestRule.onNodeWithText(string(R.string.home_sample_page_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        composeTestRule.onNodeWithText(string(R.string.home_demo_open_page)).assertIsDisplayed()
    }

    @Test
    fun systemBackPopsTheCurrentDestinationStack() {
        val harness = AppHarness()
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithText(string(R.string.home_demo_open_page)).performClick()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        composeTestRule.onNodeWithText(string(R.string.home_demo_open_page)).assertIsDisplayed()
    }

    @Test
    fun reselectingHomeRestoresItsRootStackAndScrollsToTheTop() {
        val harness = AppHarness(
            AppUiState(
                destinationStacks = rootDestinationStacks() + (
                    AppDestination.Home to listOf(AppStackPage.Root, AppStackPage.HomeSampleDetail)
                ),
            ),
        )
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.destination_home)).performClick()

        composeTestRule.onNodeWithText(string(R.string.home_demo_title)).assertIsDisplayed()
    }

    private fun string(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)
}

private class AppHarness(initialState: AppUiState = AppUiState()) {
    var state by mutableStateOf(initialState)
        private set
    val intents = mutableListOf<AppIntent>()

    @androidx.compose.runtime.Composable
    fun Render() {
        App(uiState = state, onIntent = ::dispatch)
    }

    private fun dispatch(intent: AppIntent) {
        intents += intent
        state = reduceAppState(state, intent)
    }
}

private fun reduceAppState(state: AppUiState, intent: AppIntent): AppUiState = when (intent) {
    is AppIntent.SelectDestination -> if (intent.destination == state.selectedDestination) {
        state.copy(destinationStacks = state.destinationStacks + (intent.destination to listOf(AppStackPage.Root)))
    } else {
        state.copy(selectedDestination = intent.destination)
    }
    is AppIntent.OpenPage -> {
        val stack = state.stackFor(intent.page.destination)
        state.copy(
            selectedDestination = intent.page.destination,
            destinationStacks = if (stack.lastOrNull() == intent.page) {
                state.destinationStacks
            } else {
                state.destinationStacks + (intent.page.destination to stack + intent.page)
            },
        )
    }
    AppIntent.NavigateBack -> {
        val stack = state.stackFor(state.selectedDestination)
        if (stack.size > 1) {
            state.copy(destinationStacks = state.destinationStacks + (state.selectedDestination to stack.dropLast(1)))
        } else {
            state
        }
    }
    is AppIntent.SetThemeMode -> state.copy(themeMode = intent.themeMode)
    is AppIntent.OpenExternalUrl -> state
}
