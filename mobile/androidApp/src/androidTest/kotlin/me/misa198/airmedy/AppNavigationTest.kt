package me.misa198.airmedy

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.ui.screens.LibraryArtistsUiState
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.sync.LibraryGenre
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.AndroidSyncState
import me.misa198.airmedy.ui.screens.LibraryGenresUiState
import me.misa198.airmedy.ui.screens.LibraryComposersUiState
import me.misa198.airmedy.ui.screens.LibraryTracksUiState
import me.misa198.airmedy.ui.screens.InsightTopTrack
import me.misa198.airmedy.ui.screens.InsightUiState
import me.misa198.airmedy.ui.screens.ListeningInsightState
import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.sync.LibraryTrack
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
            R.string.destination_insight,
            R.string.destination_settings,
        ).forEach { labelRes ->
            composeTestRule.onAllNodesWithContentDescription(string(labelRes)).assertCountEquals(1)
        }
    }

    @Test
    fun insufficientStorageAlertIsGlobalAndDismissible() {
        var dismissed = false
        composeTestRule.setContent {
            App(
                destinations = AppDestinationModels(
                    settings = SettingsDestinationModel(
                        syncState = SyncUiState(librarySync = AndroidSyncState.Failed("storage", 2_000_000_000, 1_000_000_000)),
                    ),
                ),
                onDismissSyncFailure = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithText(string(R.string.sync_insufficient_storage_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("1,907.3 MB", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("953.7 MB", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.close)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun settingsRootAndChildPagesAreScrollable() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Settings))
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithTag("settings-page-scroll").assert(hasScrollAction())
        composeTestRule.onNodeWithContentDescription(string(R.string.settings_appearance)).performClick()
        composeTestRule.onNodeWithTag("settings-page-scroll").assert(hasScrollAction())
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
    fun librarySearchActionOpensTheSearchPage() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Library))
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.library_search)).performClick()

        composeTestRule.onNodeWithContentDescription(string(R.string.library_search_placeholder)).assertIsDisplayed()
        assertEquals(AppIntent.OpenPage(AppStackPage.LibrarySearch), harness.intents.last())
    }

    @Test
    fun tappingArtistDispatchesArtistDetailsIntent() {
        val intents = mutableListOf<AppIntent>()
        composeTestRule.setContent {
            App(
                uiState = AppUiState(
                    selectedDestination = AppDestination.Library,
                    destinationStacks = rootDestinationStacks() + (
                        AppDestination.Library to listOf(AppStackPage.Root, AppStackPage.LibraryArtists)
                    ),
                ),
                destinations = AppDestinationModels(
                    library = LibraryDestinationModel(
                        artists = LibraryArtistsModel(
                            state = LibraryArtistsUiState(artists = listOf(LibraryArtist("muse", "Muse"))),
                        ),
                    ),
                ),
                onIntent = intents::add,
            )
        }

        composeTestRule.onNodeWithText("Muse").performClick()

        assertEquals(AppIntent.OpenArtistDetails("muse"), intents.last())
    }

    @Test
    fun libraryTrackActionUsesDestinationModelCallback() {
        var selectedTrackId: String? = null
        composeTestRule.setContent {
            App(
                uiState = AppUiState(
                    selectedDestination = AppDestination.Library,
                    destinationStacks = rootDestinationStacks() + (
                        AppDestination.Library to listOf(AppStackPage.Root, AppStackPage.LibraryTracks)
                    ),
                ),
                destinations = AppDestinationModels(
                    library = LibraryDestinationModel(
                        tracks = LibraryTracksModel(
                            state = LibraryTracksUiState(
                                tracks = listOf(LibraryTrack("track-1", "Model track", "Artist")),
                            ),
                            onTrackClick = { selectedTrackId = it },
                        ),
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithText("Model track").performClick()

        assertEquals("track-1", selectedTrackId)
    }

    @Test
    fun trackPlaylistPickerUsesSyncedPlaylistsOutsidePlaylistDetails() {
        composeTestRule.setContent {
            App(
                uiState = AppUiState(
                    selectedDestination = AppDestination.Library,
                    destinationStacks = rootDestinationStacks() + (
                        AppDestination.Library to listOf(AppStackPage.Root, AppStackPage.LibraryTracks)
                    ),
                ),
                destinations = AppDestinationModels(
                    library = LibraryDestinationModel(
                        tracks = LibraryTracksModel(
                            state = LibraryTracksUiState(
                                tracks = listOf(LibraryTrack("track-1", "Model track", "Artist")),
                            ),
                        ),
                        playlists = LibraryPlaylistsModel(
                            availablePlaylists = listOf(LibraryPlaylist("playlist-1", "Road trip", emptyList(), "{}")),
                        ),
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithText("Model track").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Add to playlist").performClick()

        composeTestRule.onNodeWithText("Road trip").assertIsDisplayed()
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
    fun tappingGenreDispatchesGenreDetailsIntent() {
        val intents = mutableListOf<AppIntent>()
        composeTestRule.setContent {
            App(
                uiState = AppUiState(
                    selectedDestination = AppDestination.Library,
                    destinationStacks = rootDestinationStacks() + (
                        AppDestination.Library to listOf(AppStackPage.Root, AppStackPage.LibraryGenres)
                    ),
                ),
                destinations = AppDestinationModels(
                    library = LibraryDestinationModel(
                        genres = LibraryGenresModel(
                            state = LibraryGenresUiState(genres = listOf(LibraryGenre("electronic", "Electronic"))),
                        ),
                    ),
                ),
                onIntent = intents::add,
            )
        }

        composeTestRule.onNodeWithText("Electronic").performClick()

        assertEquals(AppIntent.OpenGenreDetails("electronic"), intents.last())
    }

    @Test
    fun libraryComposersActionOpensTheComposerList() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Library))
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.library_composers)).performClick()

        composeTestRule.onNodeWithText(string(R.string.composers_empty_title)).assertIsDisplayed()
        assertEquals(AppIntent.OpenPage(AppStackPage.LibraryComposers), harness.intents.last())
    }

    @Test
    fun tappingComposerDispatchesComposerDetailsIntent() {
        val intents = mutableListOf<AppIntent>()
        composeTestRule.setContent {
            App(
                uiState = AppUiState(
                    selectedDestination = AppDestination.Library,
                    destinationStacks = rootDestinationStacks() + (
                        AppDestination.Library to listOf(AppStackPage.Root, AppStackPage.LibraryComposers)
                    ),
                ),
                destinations = AppDestinationModels(
                    library = LibraryDestinationModel(
                        composers = LibraryComposersModel(
                            state = LibraryComposersUiState(composers = listOf(LibraryComposer("glass", "Philip Glass"))),
                        ),
                    ),
                ),
                onIntent = intents::add,
            )
        }

        composeTestRule.onNodeWithText("Philip Glass").performClick()

        assertEquals(AppIntent.OpenComposerDetails("glass"), intents.last())
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
    fun playbackSettingsExposePersistedCrossfadeControls() {
        var requestedSeconds: Int? = null
        var blendArtwork: Boolean? = null
        val settingsState = AppUiState(selectedDestination = AppDestination.Settings)
        composeTestRule.setContent {
            App(
                uiState = settingsState,
                destinations = AppDestinationModels(
                    settings = SettingsDestinationModel(
                        crossfadeSeconds = 0,
                        lastEnabledCrossfadeSeconds = 4,
                        onCrossfadeSecondsChanged = { requestedSeconds = it },
                        blendArtworkDuringCrossfade = true,
                        onBlendArtworkDuringCrossfadeChanged = { blendArtwork = it },
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_playback)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.song_transition_title)).performClick()
        composeTestRule.onAllNodesWithText(string(R.string.playback_crossfade_duration)).assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription(string(R.string.playback_crossfade)).performClick()

        assertEquals(4, requestedSeconds)
        composeTestRule.onNodeWithContentDescription(string(R.string.playback_blend_artwork_during_crossfade)).performClick()
        assertEquals(false, blendArtwork)
    }

    @Test
    fun playbackSettingsOpenAndUpdateEqualizer() {
        var enabled: Boolean? = null
        var preset: String? = null
        composeTestRule.setContent {
            App(
                uiState = AppUiState(selectedDestination = AppDestination.Settings),
                destinations = AppDestinationModels(
                    settings = SettingsDestinationModel(
                        onEqualizerEnabledChanged = { enabled = it },
                        onEqualizerPresetSelected = { preset = it },
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_playback)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.equalizer_title)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.equalizer_enable)).performClick()
        assertEquals(true, enabled)
        composeTestRule.onNodeWithText("Flat").performClick()
        composeTestRule.onNodeWithText("Rock").performClick()
        assertEquals("rock", preset)
    }

    @Test
    fun equalizerHeaderMenuResetsBuiltInProfile() {
        var resetKey: String? = null
        composeTestRule.setContent {
            App(
                uiState = AppUiState(selectedDestination = AppDestination.Settings),
                destinations = AppDestinationModels(
                    settings = SettingsDestinationModel(
                        equalizer = me.misa198.airmedy.player.EqualizerSettings(
                            presetKey = "rock",
                            editedGainsDb = mapOf("rock" to List(10) { 0f }),
                        ),
                        onEqualizerProfileReset = { resetKey = it },
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_playback)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.equalizer_title)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.equalizer_profile_menu)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.equalizer_profile_reset)).performClick()

        assertEquals("rock", resetKey)
    }

    @Test
    fun equalizerHeaderMenuConfirmsDeletingUserProfile() {
        var deletedKey: String? = null
        val profile = me.misa198.airmedy.player.EqualizerProfile("user_test", "Custom", List(10) { 0f }, isDefault = false)
        composeTestRule.setContent {
            App(
                uiState = AppUiState(selectedDestination = AppDestination.Settings),
                destinations = AppDestinationModels(
                    settings = SettingsDestinationModel(
                        equalizer = me.misa198.airmedy.player.EqualizerSettings(presetKey = profile.key, userProfiles = listOf(profile)),
                        onEqualizerProfileDelete = { deletedKey = it },
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_playback)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.equalizer_title)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.equalizer_profile_menu)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.equalizer_profile_delete)).performClick()
        composeTestRule.onNodeWithText(string(R.string.equalizer_profile_delete_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.equalizer_profile_delete)).performClick()

        assertEquals(profile.key, deletedKey)
    }

    @Test
    fun playbackCardsRespectThePageHorizontalInset() {
        val settingsState = AppUiState(selectedDestination = AppDestination.Settings)
        composeTestRule.setContent {
            App(
                uiState = settingsState,
                destinations = AppDestinationModels(
                    settings = SettingsDestinationModel(
                        crossfadeSeconds = 4,
                        lastEnabledCrossfadeSeconds = 4,
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.settings_playback)).performClick()
        val expectedCardLeft = with(composeTestRule.density) { 24.dp.toPx() }
        val songTransitionLeft = composeTestRule
            .onNodeWithContentDescription(string(R.string.song_transition_title))
            .fetchSemanticsNode()
            .boundsInRoot
            .left
        assertEquals("Playback card must respect the page inset", expectedCardLeft, songTransitionLeft, 0.5f)

        composeTestRule.onNodeWithContentDescription(string(R.string.song_transition_title)).performClick()
        val expectedContentLeft = with(composeTestRule.density) { 40.dp.toPx() }
        val durationLeft = composeTestRule
            .onNodeWithText(string(R.string.playback_crossfade_duration_value, 4))
            .fetchSemanticsNode()
            .boundsInRoot
            .left
        assertEquals("Song transition card must respect the page inset", expectedContentLeft, durationLeft, 0.5f)
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
    fun aboutSponsorLinkDispatchesOneTimeExternalUrlIntent() {
        val harness = AppHarness(
            AppUiState(
                selectedDestination = AppDestination.Settings,
                destinationStacks = rootDestinationStacks() + (
                    AppDestination.Settings to listOf(AppStackPage.Root, AppStackPage.SettingsAbout)
                ),
            ),
        )
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.about_sponsor_github)).performClick()

        assertEquals(
            AppIntent.OpenExternalUrl("https://github.com/sponsors/misa198"),
            harness.intents.last(),
        )
    }

    @Test
    fun homeDisplaysSyncPlaceholderWhenThereAreNoTracks() {
        composeTestRule.setContent { App() }

        composeTestRule.onNodeWithText(string(R.string.library_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.library_empty_description)).assertIsDisplayed()
    }

    @Test
    fun homeDisplaysListeningSectionsAndPlaysFromTheirSectionQueue() {
        var playedTrackId: String? = null
        var queuedTrackIds: List<String> = emptyList()
        val keepListening = listOf(
            LibraryTrack(id = "keep-1", title = "Keep listening one", artists = "Artist"),
            LibraryTrack(id = "keep-2", title = "Keep listening two", artists = "Artist"),
        )

        composeTestRule.setContent {
            App(
                destinations = AppDestinationModels(
                    home = HomeDestinationModel(
                        state = me.misa198.airmedy.ui.screens.HomeUiState(
                            keepListeningTracks = keepListening,
                            mostPlayedTracks = listOf(LibraryTrack(id = "most-1", title = "Most played", artists = "Artist")),
                            forgottenTracks = listOf(LibraryTrack(id = "forgotten-1", title = "Forgotten", artists = "Artist")),
                        ),
                        onTrackClick = { tracks, trackId ->
                            queuedTrackIds = tracks.map(LibraryTrack::id)
                            playedTrackId = trackId
                        },
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithText(string(R.string.home_keep_listening)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.home_most_played)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.home_forgotten)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep listening two").performClick()

        assertEquals("keep-2", playedTrackId)
        assertEquals(listOf("keep-1", "keep-2"), queuedTrackIds)
    }

    @Test
    fun longPressingHomeTrackOpensTheTrackContextMenu() {
        composeTestRule.setContent {
            App(
                destinations = AppDestinationModels(
                    home = HomeDestinationModel(
                        state = me.misa198.airmedy.ui.screens.HomeUiState(
                            keepListeningTracks = listOf(LibraryTrack(id = "keep-1", title = "Keep listening one", artists = "Artist")),
                        ),
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithText("Keep listening one").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Track info").assertIsDisplayed()
    }

    @Test
    fun fullScreenPlayerOpensAboveNavigationAndClosesWhenDraggedDown() {
        var fullScreenPlayerVisible = false
        composeTestRule.setContent {
            App(
                playback = PlaybackModel(state = playingState),
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
    fun fullScreenPlayerUpdatesSystemBarVisibilityWhilePaused() {
        val visibilityChanges = mutableListOf<Boolean>()
        val pausedState = PlaybackState.Paused(playingItem, positionMs = 42_000L, durationMs = 120_000L)
        composeTestRule.setContent {
            App(
                playback = PlaybackModel(state = pausedState),
                onFullScreenPlayerVisibilityChanged = visibilityChanges::add,
            )
        }

        composeTestRule.onNodeWithText(playingItem.title).performClick()
        assertEquals(listOf(true), visibilityChanges)

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertEquals(listOf(true, false), visibilityChanges)
    }

    @Test
    fun pullingTheFullScreenPlayerBackUpCancelsItsDismissal() {
        var fullScreenPlayerVisible = false
        composeTestRule.setContent {
            App(
                playback = PlaybackModel(state = playingState),
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
                playback = PlaybackModel(
                    state = playingState,
                    onOpenMediaOutputSwitcher = { outputSwitcherRequests += 1 },
                ),
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
                playback = PlaybackModel(
                    state = playingState,
                    onNext = { nextRequests += 1 },
                    onPrevious = { previousRequests += 1 },
                ),
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
        composeTestRule.setContent { App(playback = PlaybackModel(state = playingState)) }

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
        composeTestRule.setContent { App(playback = PlaybackModel(state = playingState)) }

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
        composeTestRule.setContent { App(playback = PlaybackModel(state = playingState)) }

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
                selectedDestination = AppDestination.Library,
                destinationStacks = rootDestinationStacks() + (
                    AppDestination.Library to listOf(AppStackPage.Root, AppStackPage.LibraryTracks)
                ),
            ),
        )
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.destination_library)).performClick()

        composeTestRule.onNodeWithText(string(R.string.library_empty_title)).assertIsDisplayed()
    }

    @Test
    fun reselectingInsightScrollsToTheTop() {
        composeTestRule.setContent {
            App(
                uiState = AppUiState(selectedDestination = AppDestination.Insight),
                destinations = AppDestinationModels(
                    insight = InsightDestinationModel(
                        state = InsightUiState(
                            listening = ListeningInsightState(
                                topTracks = (1..20).map { index ->
                                    InsightTopTrack(LibraryTrack("track-$index", "Track $index", "Artist"), index, index * 60)
                                },
                            ),
                        ),
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithTag("insight-top-tracks-toggle").performScrollTo()
        composeTestRule.onNodeWithTag("insight-library-size").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.destination_insight)).performClick()
        composeTestRule.onNodeWithTag("insight-library-size").assertIsDisplayed()
    }

    @Test
    fun draggingBackToTheSelectedDestinationDoesNotReselectIt() {
        val harness = AppHarness(
            AppUiState(
                selectedDestination = AppDestination.Settings,
                destinationStacks = rootDestinationStacks() + (
                    AppDestination.Settings to listOf(AppStackPage.Root, AppStackPage.SettingsAppearance)
                ),
            ),
        )
        composeTestRule.setContent { harness.Render() }

        composeTestRule.onNodeWithContentDescription(string(R.string.destination_settings)).performTouchInput {
            down(center)
            moveBy(Offset(x = 240f, y = 0f))
            moveBy(Offset(x = -240f, y = 0f))
            up()
        }

        assertEquals(emptyList<AppIntent>(), harness.intents)
        composeTestRule.onNodeWithText(string(R.string.appearance_title)).assertIsDisplayed()
    }

    @Test
    fun draggingPillMostlyOverTheNextDestinationSelectsIt() {
        val harness = AppHarness()
        composeTestRule.setContent { harness.Render() }

        val homeTarget = composeTestRule.onNodeWithContentDescription(string(R.string.destination_home))
        val dragDistance = homeTarget.fetchSemanticsNode().boundsInRoot.width * 0.9f
        homeTarget.performTouchInput {
            down(center)
            moveBy(Offset(x = dragDistance, y = 0f))
            up()
        }

        assertEquals(AppIntent.SelectDestination(AppDestination.Library), harness.intents.last())
    }

    @Test
    fun changingStackPageKeepsCompactNavigationAndMiniPlayer() {
        val harness = AppHarness(AppUiState(selectedDestination = AppDestination.Library))
        composeTestRule.setContent { harness.Render(playbackState = playingState) }

        composeTestRule.onNodeWithText(string(R.string.library_artists)).performTouchInput {
            swipeUp(endY = center.y - 80f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.destination_library))
            .assertWidthIsEqualTo(48.dp)

        composeTestRule.onNodeWithContentDescription(string(R.string.library_artists)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.artists_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.destination_library))
            .assertWidthIsEqualTo(48.dp)
        composeTestRule.onNodeWithText(playingItem.title).assertIsDisplayed()
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
    fun Render(playbackState: PlaybackState = PlaybackState.Idle) {
        App(uiState = state, playback = PlaybackModel(state = playbackState), onIntent = ::dispatch)
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
    is AppIntent.OpenAlbumDetails -> {
        val stack = state.stackFor(AppDestination.Library)
        state.copy(
            selectedDestination = AppDestination.Library,
            selectedAlbumId = intent.albumId,
            destinationStacks = if (stack.lastOrNull() == AppStackPage.AlbumDetails) state.destinationStacks
            else state.destinationStacks + (AppDestination.Library to stack + AppStackPage.AlbumDetails),
        )
    }
    is AppIntent.OpenPlaylistDetails -> {
        val stack = state.stackFor(AppDestination.Library)
        state.copy(
            selectedDestination = AppDestination.Library,
            selectedPlaylistId = intent.playlistId,
            destinationStacks = if (stack.lastOrNull() == AppStackPage.PlaylistDetails) state.destinationStacks
            else state.destinationStacks + (AppDestination.Library to stack + AppStackPage.PlaylistDetails),
        )
    }
    is AppIntent.OpenArtistDetails -> {
        val stack = state.stackFor(AppDestination.Library)
        state.copy(
            selectedDestination = AppDestination.Library,
            selectedArtistId = intent.artistId,
            destinationStacks = if (stack.lastOrNull() == AppStackPage.ArtistDetails) state.destinationStacks
            else state.destinationStacks + (AppDestination.Library to stack + AppStackPage.ArtistDetails),
        )
    }
    is AppIntent.OpenGenreDetails -> {
        val stack = state.stackFor(AppDestination.Library)
        state.copy(
            selectedDestination = AppDestination.Library,
            selectedGenreId = intent.genreId,
            destinationStacks = if (stack.lastOrNull() == AppStackPage.GenreDetails) state.destinationStacks
            else state.destinationStacks + (AppDestination.Library to stack + AppStackPage.GenreDetails),
        )
    }
    is AppIntent.OpenComposerDetails -> {
        val stack = state.stackFor(AppDestination.Library)
        state.copy(
            selectedDestination = AppDestination.Library,
            selectedComposerId = intent.composerId,
            destinationStacks = if (stack.lastOrNull() == AppStackPage.ComposerDetails) state.destinationStacks
            else state.destinationStacks + (AppDestination.Library to stack + AppStackPage.ComposerDetails),
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
