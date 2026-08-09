package me.misa198.airmedy

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun libraryArtistsActionOpensTheArtistList() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Library))
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.library_artists)).performClick()

        composeTestRule.onNodeWithText(string(R.string.artists_empty_title)).assertIsDisplayed()
        assertEquals(AppIntent.OpenPage(AppStackPage.LibraryArtists), harness.intents.last())
    }

    @Test
    fun libraryAlbumsActionOpensTheAlbumList() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Library))
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.library_albums)).performClick()

        composeTestRule.onNodeWithText(string(R.string.albums_empty_title)).assertIsDisplayed()
        assertEquals(AppIntent.OpenPage(AppStackPage.LibraryAlbums), harness.intents.last())
    }

    @Test
    fun libraryGenresActionOpensTheGenreList() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Library))
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.library_genres)).performClick()

        composeTestRule.onNodeWithText(string(R.string.genres_empty_title)).assertIsDisplayed()
        assertEquals(AppIntent.OpenPage(AppStackPage.LibraryGenres), harness.intents.last())
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
    fun appearanceCanEnableReducedTransparency() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Settings))
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_appearance)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.appearance_reduce_transparency)).performClick()

        assertEquals(AppIntent.SetReduceTransparency(true), harness.intents.last())
        assertEquals(true, harness.state.reduceTransparency)
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
    fun fullScreenPlayerOpensAboveNavigationAndClosesWhenDraggedDown() {
        var fullScreenPlayerVisible = false
        composeTestRule.setContent {
            App(
                playbackState = playingState,
                onFullScreenPlayerVisibilityChanged = { fullScreenPlayerVisible = it },
            )
        }

        composeTestRule.onNodeWithText(playingItem.title).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.full_screen_player)).assertIsDisplayed()
        assertEquals(true, fullScreenPlayerVisible)
        composeTestRule.onNodeWithTag("full_screen_player_drag_handle").assertIsDisplayed()
        composeTestRule.onNodeWithText(playingItem.artist).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.player_seek)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.player_volume)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.player_heart)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.player_lyrics)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.player_cast)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.player_queue)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(string(R.string.full_screen_player))
            .performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithContentDescription(string(R.string.full_screen_player)).assertCountEquals(0)
        assertEquals(false, fullScreenPlayerVisible)
    }

    @Test
    fun pullingTheFullScreenPlayerBackUpCancelsItsDismissal() {
        var fullScreenPlayerVisible = false
        composeTestRule.setContent {
            App(
                playbackState = playingState,
                onFullScreenPlayerVisibilityChanged = { fullScreenPlayerVisible = it },
            )
        }

        composeTestRule.onNodeWithText(playingItem.title).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.full_screen_player))
            .performTouchInput {
                down(center)
                moveBy(Offset(x = 0f, y = 500f))
                moveBy(Offset(x = 0f, y = -500f))
                up()
            }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.full_screen_player)).assertIsDisplayed()
        assertEquals(true, fullScreenPlayerVisible)
    }

    @Test
    fun fullScreenPlayerCastButtonRequestsTheMediaOutputSwitcher() {
        var outputSwitcherRequests = 0
        composeTestRule.setContent {
            App(
                playbackState = playingState,
                onOpenMediaOutputSwitcher = { outputSwitcherRequests += 1 },
            )
        }

        composeTestRule.onNodeWithText(playingItem.title).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.player_cast)).performClick()

        assertEquals(1, outputSwitcherRequests)
    }

    @Test
    fun fullScreenPlayerArtworkAndMetadataSwipesDispatchTransport() {
        var nextRequests = 0
        var previousRequests = 0
        composeTestRule.setContent {
            App(
                playbackState = playingState,
                onPlaybackNext = { nextRequests += 1 },
                onPlaybackPrevious = { previousRequests += 1 },
            )
        }

        composeTestRule.onNodeWithText(playingItem.title).performClick()
        composeTestRule.onNodeWithTag("full_screen_player_artwork_swipe_target")
            .performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithTag("full_screen_player_metadata_swipe_target")
            .performTouchInput { swipeRight() }

        assertEquals(1, nextRequests)
        assertEquals(1, previousRequests)
    }

    @Test
    fun fullScreenPlayerKeepsMetadataWithArtwork() {
        composeTestRule.setContent { App(playbackState = playingState) }

        composeTestRule.onNodeWithText(playingItem.title).performClick()
        val artworkBounds = composeTestRule.onNodeWithTag("full_screen_player_artwork")
            .fetchSemanticsNode().boundsInRoot
        val metadataBounds = composeTestRule.onNodeWithTag("full_screen_player_metadata_swipe_target")
            .fetchSemanticsNode().boundsInRoot
        val maximumGapPx = with(composeTestRule.density) { 24.dp.toPx() }

        assertTrue("Metadata must follow the artwork", metadataBounds.top >= artworkBounds.bottom)
        assertTrue(
            "Artwork and metadata must remain in the same visual block",
            metadataBounds.top - artworkBounds.bottom <= maximumGapPx,
        )
    }

    @Test
    fun partialSlowMiniPlayerPullCompletesOpeningTheFullScreenPlayer() {
        composeTestRule.setContent { App(playbackState = playingState) }

        composeTestRule.onNodeWithText(playingItem.title).performTouchInput {
            down(center)
            moveBy(Offset(x = 0f, y = -100f), delayMillis = 500)
            up()
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.full_screen_player))
            .assertTopPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun systemBackClosesFullScreenPlayerBeforeNavigating() {
        composeTestRule.setContent { App(playbackState = playingState) }

        composeTestRule.onNodeWithText(playingItem.title).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.full_screen_player)).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        composeTestRule.onAllNodesWithContentDescription(string(R.string.full_screen_player)).assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription(string(R.string.destination_home)).assertIsSelected()
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

    private companion object {
        val playingItem = PlaybackItem(
            trackId = "track-1",
            title = "Test track",
            artist = "Test artist",
            audioPath = "/audio/track-1.flac",
        )
        val playingState = PlaybackState.Playing(playingItem, positionMs = 0L, durationMs = 120_000L)
    }
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
    is AppIntent.SetReduceTransparency -> state.copy(reduceTransparency = intent.enabled)
    is AppIntent.OpenExternalUrl -> state
}
