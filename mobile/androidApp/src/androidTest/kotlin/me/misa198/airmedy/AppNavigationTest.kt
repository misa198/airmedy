package me.misa198.airmedy

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
    fun navigationExposesOneAccessibleTargetPerDestination() {
        composeTestRule.setContent { App() }

        listOf(
            R.string.destination_home,
            R.string.destination_library,
            R.string.destination_search,
            R.string.destination_settings,
        ).forEach { labelRes ->
            composeTestRule
                .onAllNodesWithContentDescription(string(labelRes))
                .assertCountEquals(1)
        }
    }

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
    fun appearanceOpensFromSettingsAndThemeSelectionUpdatesState() {
        var state by mutableStateOf(AppUiState(selectedDestination = AppDestination.Settings))
        composeTestRule.setContent {
            App(
                uiState = state,
                onThemeModeSelected = { themeMode ->
                    state = state.copy(themeMode = themeMode)
                },
                onAppearanceSelected = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Settings to state.stackFor(AppDestination.Settings) + AppStackPage.SettingsAppearance
                        ),
                    )
                },
                onNavigateBack = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Settings to state.stackFor(AppDestination.Settings).dropLast(1)
                        ),
                    )
                },
            )
        }

        val appearanceLabel = string(R.string.settings_appearance)
        val themeSelectorValue = string(R.string.theme_system)
        val darkLabel = string(R.string.theme_dark)

        composeTestRule.onNodeWithContentDescription(appearanceLabel).performClick()
        composeTestRule.onNodeWithText(string(R.string.appearance_theme_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(themeSelectorValue).performClick()
        composeTestRule.onNodeWithText(darkLabel).performClick()
        composeTestRule.onNodeWithText(darkLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_sync)).assertIsDisplayed()
    }

    @Test
    fun syncOpensFromSettingsAndShowsTheEmptyDeviceState() {
        var state by mutableStateOf(AppUiState(selectedDestination = AppDestination.Settings))
        composeTestRule.setContent {
            App(
                uiState = state,
                onSyncSelected = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Settings to state.stackFor(AppDestination.Settings) + AppStackPage.SettingsSync
                        ),
                    )
                },
                onNavigateBack = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Settings to state.stackFor(AppDestination.Settings).dropLast(1)
                        ),
                    )
                },
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_sync)).performClick()

        composeTestRule.onNodeWithText(string(R.string.sync_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.sync_add_device)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_sync)).assertIsDisplayed()
    }

    @Test
    fun connectedSyncDeviceShowsDetailsAndHidesTheAddAction() {
        val device = SyncDevice(name = "Airmedy Desktop", type = SyncDeviceType.Desktop)
        composeTestRule.setContent {
            App(
                uiState = AppUiState(
                    selectedDestination = AppDestination.Settings,
                    destinationStacks = rootDestinationStacks() + (
                        AppDestination.Settings to listOf(AppStackPage.Root, AppStackPage.SettingsSync)
                    ),
                    syncDevice = device,
                ),
            )
        }

        composeTestRule.onNodeWithText(device.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sync_device_type_desktop)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sync_status_connected)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sync_revoke)).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithContentDescription(string(R.string.sync_add_device))
            .assertCountEquals(0)
    }

    @Test
    fun settingsShowsItsActionList() {
        composeTestRule.setContent {
            App(uiState = AppUiState(selectedDestination = AppDestination.Settings))
        }

        listOf(
            R.string.settings_appearance,
            R.string.settings_sync,
            R.string.settings_playback,
            R.string.settings_integration,
            R.string.settings_about,
        ).forEach { labelRes ->
            composeTestRule.onNodeWithText(string(labelRes)).assertIsDisplayed()
        }
    }

    @Test
    fun aboutOpensFromSettingsAndExposesAppDetailsAndLinks() {
        var state by mutableStateOf(AppUiState(selectedDestination = AppDestination.Settings))
        val openedUrls = mutableListOf<String>()
        composeTestRule.setContent {
            App(
                uiState = state,
                onAboutSelected = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Settings to state.stackFor(AppDestination.Settings) + AppStackPage.SettingsAbout
                        ),
                    )
                },
                onOpenExternalUrl = openedUrls::add,
                onNavigateBack = {
                    state = state.copy(
                        destinationStacks = state.destinationStacks + (
                            AppDestination.Settings to state.stackFor(AppDestination.Settings).dropLast(1)
                        ),
                    )
                },
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_about)).performClick()

        composeTestRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.about_description)).assertIsDisplayed()
        composeTestRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.about_version))
            .assertHasNoClickAction()
        composeTestRule.onNodeWithContentDescription(string(R.string.about_github)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.about_license)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        org.junit.Assert.assertEquals(
            listOf(
                "https://github.com/misa198/airmedy",
                "https://github.com/misa198/airmedy/blob/main/LICENSE",
            ),
            openedUrls,
        )
        composeTestRule.onNodeWithText(string(R.string.settings_sync)).assertIsDisplayed()
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
    fun reselectingTheHomeDestinationScrollsItsRootPageToTheTop() {
        composeTestRule.setContent { App() }

        val finalSection = string(R.string.home_demo_section_title, 4)
        val homeTitle = string(R.string.home_demo_title)
        val homeLabel = string(R.string.destination_home)

        composeTestRule.onNodeWithText(finalSection).performScrollTo()
        composeTestRule.onNodeWithText(finalSection).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(homeLabel).performClick()

        composeTestRule.onNodeWithText(homeTitle).assertIsDisplayed()
    }

    @Test
    fun reselectingADestinationWithAnOpenPageReturnsItsStackToRoot() {
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
                    state = if (destination == state.selectedDestination) {
                        state.copy(
                            destinationStacks = state.destinationStacks + (
                                destination to listOf(AppStackPage.Root)
                            ),
                        )
                    } else {
                        state.copy(selectedDestination = destination)
                    }
                },
            )
        }

        composeTestRule.onNodeWithText(string(R.string.home_sample_page_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.destination_home)).performClick()

        composeTestRule.onNodeWithText(string(R.string.home_demo_title)).assertIsDisplayed()
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
    fun homeActionListsOpenAndPopTheSharedFakePage() {
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

        val actionOne = string(R.string.home_action_one)
        val actionTwo = string(R.string.home_action_two)
        val plainTitle = string(R.string.home_action_list_plain_title)

        composeTestRule.onAllNodesWithContentDescription(actionOne)[0].performClick()
        composeTestRule.onNodeWithText(string(R.string.home_sample_page_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        composeTestRule.onNodeWithText(plainTitle).performScrollTo()
        composeTestRule.onAllNodesWithContentDescription(actionTwo)[1].performClick()
        composeTestRule.onNodeWithText(string(R.string.home_sample_page_heading)).assertIsDisplayed()
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
