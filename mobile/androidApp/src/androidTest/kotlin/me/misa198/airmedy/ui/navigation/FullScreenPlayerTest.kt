package me.misa198.airmedy.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.components.TrackContextArtist
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class FullScreenPlayerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun favoriteHeartConfirmsOnlyWhenAdding() {
        val haptics = mutableListOf<HapticFeedbackType>()
        var change: Boolean? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { haptics += hapticFeedbackType }
                }) {
                    FullScreenPlayer(
                        visible = true,
                        dragProgress = 0f,
                        isDragging = false,
                        openingFromMiniPlayerSwipe = false,
                        playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                        volume = 0.5f,
                        onSeek = {}, onVolumeChange = {}, onPrevious = {}, onPlayPause = {}, onNext = {},
                        onFavoriteToggle = { _, favorite -> change = favorite },
                        onOpenMediaOutputSwitcher = {}, onDismiss = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Favorite").performClick()
        composeTestRule.runOnIdle {
            assertEquals(true, change)
            assertEquals(listOf(HapticFeedbackType.Confirm), haptics)
        }
    }

    @Test
    fun favoriteHeartDoesNotConfirmWhenRemoving() {
        val haptics = mutableListOf<HapticFeedbackType>()
        var change: Boolean? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { haptics += hapticFeedbackType }
                }) {
                    FullScreenPlayer(
                        visible = true,
                        dragProgress = 0f,
                        isDragging = false,
                        openingFromMiniPlayerSwipe = false,
                        playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                        volume = 0.5f,
                        onSeek = {}, onVolumeChange = {}, onPrevious = {}, onPlayPause = {}, onNext = {},
                        isFavorite = true,
                        onFavoriteToggle = { _, favorite -> change = favorite },
                        onOpenMediaOutputSwitcher = {}, onDismiss = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Favorite").performClick()
        composeTestRule.runOnIdle {
            assertEquals(false, change)
            assertEquals(emptyList<HapticFeedbackType>(), haptics)
        }
    }

    @Test
    fun shortScreenCentersAndCapsArtworkByAvailableHeight() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true,
                    dragProgress = 0f,
                    isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                    volume = 0.5f,
                    onSeek = {},
                    onVolumeChange = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onOpenMediaOutputSwitcher = {},
                    onDismiss = {},
                    modifier = Modifier.width(360.dp).height(568.dp),
                )
            }
        }

        composeTestRule.onNodeWithTag("full_screen_player_artwork")
            .assertHeightIsEqualTo(120.dp)
            .assertLeftPositionInRootIsEqualTo(120.dp)
    }

    @Test
    fun queueStatusBadgeWaitsForQueueButtonBackgroundToFadeOut() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true,
                    dragProgress = 0f,
                    isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                    queue = PlaybackQueueSnapshot(shuffle = true),
                    volume = 0.5f,
                    onSeek = {},
                    onVolumeChange = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onOpenMediaOutputSwitcher = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(FullScreenQueueStatusBadgeTestTag).assertExists()
        composeTestRule.onNodeWithContentDescription("Queue").performClick()
        composeTestRule.onAllNodesWithTag(FullScreenQueueStatusBadgeTestTag).assertCountEquals(0)

        composeTestRule.onNodeWithContentDescription("Queue").performClick()
        composeTestRule.mainClock.advanceTimeBy(QueueStatusBadgeRevealDelayMs.toLong() - 1L)
        composeTestRule.onAllNodesWithTag(FullScreenQueueStatusBadgeTestTag).assertCountEquals(0)

        composeTestRule.mainClock.advanceTimeBy(1L)
        composeTestRule.onNodeWithTag(FullScreenQueueStatusBadgeTestTag).assertExists()
    }

    @Test
    fun swipingDownFromArtworkDismissesFullscreenPlayer() {
        var dismissCount = 0
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true,
                    dragProgress = 0f,
                    isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                    volume = 0.5f,
                    onSeek = {},
                    onVolumeChange = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onOpenMediaOutputSwitcher = {},
                    onDismiss = { dismissCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag("full_screen_player_artwork").performTouchInput {
            swipeDown()
        }
        composeTestRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun swipeUsesTheLatestQueueNavigationAvailabilityAndCallback() {
        var queue by mutableStateOf(PlaybackQueueSnapshot())
        var callbackGeneration by mutableStateOf(1)
        var invokedGeneration: Int? = null
        composeTestRule.setContent {
            val onNext = if (callbackGeneration == 1) {
                { invokedGeneration = 1 }
            } else {
                { invokedGeneration = 2 }
            }
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true,
                    dragProgress = 0f,
                    isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                    queue = queue,
                    volume = 0.5f,
                    onSeek = {},
                    onVolumeChange = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = onNext,
                    onOpenMediaOutputSwitcher = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.runOnIdle {
            queue = PlaybackQueueSnapshot(
                originalTrackIds = listOf(item.trackId, secondItem.trackId),
                activeTrackIds = listOf(item.trackId, secondItem.trackId),
                currentIndex = 0,
            )
            callbackGeneration = 2
        }
        composeTestRule.onNodeWithTag("full_screen_player_artwork_swipe_target")
            .performTouchInput { swipeLeft() }

        composeTestRule.runOnIdle { assertEquals(2, invokedGeneration) }
    }

    @Test
    fun moreOptionsUsesTheTrackContextMenuForTheCurrentTrack() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true,
                    dragProgress = 0f,
                    isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                    queue = PlaybackQueueSnapshot(activeTrackIds = listOf(item.trackId), currentIndex = 0),
                    queueTracks = listOf(LibraryTrack(id = item.trackId, title = item.title, artists = item.artist)),
                    volume = 0.5f,
                    onSeek = {},
                    onVolumeChange = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onOpenMediaOutputSwitcher = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Track info").assertExists()
        composeTestRule.onNodeWithText("Add to playlist").assertExists()
    }

    @Test
    fun queueRowLongPressOpensQueueMenuWithoutAddToQueueOrCurrentTrackPlayNext() {
        var removedTrackId: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenQueuePanel(
                    queue = PlaybackQueueSnapshot(activeTrackIds = listOf("track-1"), currentIndex = 0),
                    tracks = listOf(LibraryTrack(id = "track-1", title = "Track 1", artists = "Artist")),
                    currentTrackId = "track-1",
                    isPlaying = true,
                    onTrackSelected = {},
                    onTrackRemoved = { removedTrackId = it },
                    onReorder = {},
                    onShuffleChange = {},
                    onRepeatModeChange = {},
                    modifier = Modifier.height(160.dp),
                )
            }
        }

        composeTestRule.onNodeWithText("Track 1").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Remove from queue").assertExists()
        composeTestRule.onAllNodesWithText("Add to queue").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Play next").assertCountEquals(0)
        composeTestRule.onNodeWithText("Remove from queue").performClick()
        composeTestRule.runOnIdle { assertEquals("track-1", removedTrackId) }
    }

    @Test
    fun moreOptionsRequestsAnArtistPickerForCollaborations() {
        var bottomSheetRequest: TrackContextBottomSheetRequest? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true,
                    dragProgress = 0f,
                    isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                    queueTracks = listOf(
                        LibraryTrack(
                            id = item.trackId,
                            title = item.title,
                            artists = "Artist A, Artist B",
                            metadataJson = """{\"artists\":[{\"id\":\"artist-a\",\"name\":\"Artist A\"},{\"id\":\"artist-b\",\"name\":\"Artist B\"}]}""",
                        ),
                    ),
                    volume = 0.5f,
                    onSeek = {},
                    onVolumeChange = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onTrackContextBottomSheet = { bottomSheetRequest = it },
                    onOpenMediaOutputSwitcher = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Go to artists").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                TrackContextBottomSheetRequest.Artists(
                    listOf(TrackContextArtist("artist-a", "Artist A"), TrackContextArtist("artist-b", "Artist B")),
                ),
                bottomSheetRequest,
            )
        }
    }

    @Test
    fun lyricsAndQueuePanelsToggleAndPersistAcrossTrackChanges() {
        val playbackState = mutableStateOf<PlaybackState>(PlaybackState.Playing(item, 0L, 120_000L))
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true,
                    dragProgress = 0f,
                    isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = playbackState.value,
                    volume = 0.5f,
                    onSeek = {},
                    onVolumeChange = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onOpenMediaOutputSwitcher = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Lyrics").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Lyrics").assertExists()

        composeTestRule.runOnIdle {
            playbackState.value = PlaybackState.Playing(secondItem, 0L, 120_000L)
        }
        composeTestRule.onNodeWithText("Lyrics").assertExists()

        composeTestRule.onNodeWithContentDescription("Queue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Queue").assertExists()

        composeTestRule.onNodeWithTag("full_screen_player_artwork").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Queue").assertCountEquals(0)

        composeTestRule.onNodeWithContentDescription("Lyrics").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Lyrics").assertExists()

        composeTestRule.onNodeWithTag("full_screen_player_artwork").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Lyrics").assertCountEquals(0)
    }

    @Test
    fun syncedLyricsShowsBilingualLyricsAndSeeksOnLineTap() {
        var seekPositionMs: Long? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayerLyricsPanel(
                    trackId = "track-1",
                    lyrics = "[00:01.00]Primary ^ Translation\n[00:03.00]Next line",
                    currentPositionMs = 1_000L,
                    onSeek = { seekPositionMs = it },
                    modifier = Modifier.height(180.dp),
                )
            }
        }

        composeTestRule.onNodeWithTag("synced_lyrics_list").assertExists()
        composeTestRule.onNodeWithText("Translation").assertExists()
        composeTestRule.onNodeWithTag("synced_lyric_3.0").performClick()
        composeTestRule.runOnIdle { assertEquals(3_000L, seekPositionMs) }
    }

    @Test
    fun syncedLyricsTouchUsesTheLatestSeekCallbackAfterRecomposition() {
        var callbackGeneration by mutableStateOf(1)
        var invokedGeneration: Int? = null
        composeTestRule.setContent {
            val onSeek: (Long) -> Unit = if (callbackGeneration == 1) {
                { invokedGeneration = 1 }
            } else {
                { invokedGeneration = 2 }
            }
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayerLyricsPanel(
                    trackId = "track-1",
                    lyrics = "[00:03.00]Seekable line",
                    currentPositionMs = 1_000L,
                    onSeek = onSeek,
                    modifier = Modifier.height(180.dp),
                )
            }
        }

        composeTestRule.runOnIdle { callbackGeneration = 2 }
        composeTestRule.onNodeWithTag("synced_lyric_3.0").performTouchInput { click() }

        composeTestRule.runOnIdle { assertEquals(2, invokedGeneration) }
    }

    @Test
    fun queueShowsTracksAndDispatchesPlaybackControls() {
        var selectedTrack: String? = null
        var shuffle: Boolean? = null
        var repeat: RepeatMode? = null
        var queue by mutableStateOf(
            PlaybackQueueSnapshot(
                originalTrackIds = listOf("track-1", "track-2"),
                activeTrackIds = listOf("track-1", "track-2"),
                currentIndex = 0,
            ),
        )
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true,
                    dragProgress = 0f,
                    isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = PlaybackState.Playing(item, 0L, 120_000L),
                    queue = queue,
                    queueTracks = listOf(
                        LibraryTrack(id = "track-1", title = "Test title", artists = "Test artist"),
                        LibraryTrack(id = "track-2", title = "Next track", artists = "Next artist"),
                    ),
                    volume = 0.5f,
                    onSeek = {},
                    onVolumeChange = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onQueueTrackSelected = { selectedTrack = it },
                    onShuffleChange = {
                        shuffle = it
                        queue = queue.copy(shuffle = it)
                    },
                    onRepeatModeChange = {
                        repeat = it
                        queue = queue.copy(repeatMode = it)
                    },
                    onOpenMediaOutputSwitcher = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Queue").performClick()
        composeTestRule.onNodeWithTag("full_screen_queue_panel_header")
            .assertLeftPositionInRootIsEqualTo(20.dp)
        composeTestRule.onNodeWithTag("full_screen_queue_row-track-1")
            .assertLeftPositionInRootIsEqualTo(0.dp)
        composeTestRule.onNodeWithTag("full_screen_queue_row_content-track-1")
            .assertLeftPositionInRootIsEqualTo(20.dp)
        composeTestRule.onNodeWithContentDescription("More options").assertExists()
        composeTestRule.onNodeWithTag("playing_indicator").assertExists()
        composeTestRule.onNodeWithText("Next track").performClick()
        composeTestRule.onNodeWithContentDescription("Shuffle").performClick()
        composeTestRule.onNodeWithContentDescription("Repeat off").performClick()
        composeTestRule.onNodeWithContentDescription("Shuffle on").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Repeat all").assertIsSelected()

        composeTestRule.runOnIdle {
            assertEquals("track-2", selectedTrack)
            assertEquals(true, shuffle)
            assertEquals(RepeatMode.All, repeat)
        }
    }

    @Test
    fun queueDoesNotScrollWhenPlaybackAdvances() {
        val trackIds = List(12) { "queue-$it" }
        var queue by mutableStateOf(
            PlaybackQueueSnapshot(
                originalTrackIds = trackIds,
                activeTrackIds = trackIds,
                currentIndex = 0,
            ),
        )
        var currentTrackId by mutableStateOf(trackIds.first())
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenQueuePanel(
                    queue = queue,
                    tracks = trackIds.mapIndexed { index, trackId ->
                        LibraryTrack(id = trackId, title = "Track $index", artists = "Artist")
                    },
                    currentTrackId = currentTrackId,
                    isPlaying = true,
                    onTrackSelected = {},
                    onReorder = {},
                    onShuffleChange = {},
                    onRepeatModeChange = {},
                    modifier = Modifier.height(112.dp),
                )
            }
        }

        composeTestRule.runOnIdle {
            queue = queue.copy(currentIndex = trackIds.lastIndex)
            currentTrackId = trackIds.last()
        }
        composeTestRule.onNodeWithText("Track 11").assertDoesNotExist()
    }

    @Test
    fun queueScrollsCurrentTrackIntoViewWhenPanelFirstOpens() {
        val trackIds = List(12) { "queue-$it" }
        val currentTrackId = trackIds.last()
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenQueuePanel(
                    queue = PlaybackQueueSnapshot(
                        originalTrackIds = trackIds,
                        activeTrackIds = trackIds,
                        currentIndex = trackIds.lastIndex,
                    ),
                    tracks = trackIds.mapIndexed { index, trackId ->
                        LibraryTrack(id = trackId, title = "Track $index", artists = "Artist")
                    },
                    currentTrackId = currentTrackId,
                    isPlaying = true,
                    onTrackSelected = {},
                    onReorder = {},
                    onShuffleChange = {},
                    onRepeatModeChange = {},
                    modifier = Modifier.height(112.dp),
                )
            }
        }

        composeTestRule.onNodeWithText("Track 11").assertExists()
    }

    @Test
    fun queueExposesASeparateReorderHandle() {
        var selectedTrack: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                FullScreenQueuePanel(
                    queue = PlaybackQueueSnapshot(activeTrackIds = listOf("track-1"), currentIndex = 0),
                    tracks = listOf(LibraryTrack(id = "track-1", title = "Track 1", artists = "Artist")),
                    currentTrackId = "track-1",
                    isPlaying = false,
                    onTrackSelected = { selectedTrack = it },
                    onReorder = {},
                    onShuffleChange = {},
                    onRepeatModeChange = {},
                    modifier = Modifier.height(160.dp),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Reorder track").assertExists()
        composeTestRule.onNodeWithText("Track 1").performClick()
        composeTestRule.runOnIdle { assertEquals("track-1", selectedTrack) }
    }

    private companion object {
        val item = PlaybackItem(
            trackId = "track-1",
            title = "Test title",
            artist = "Test artist",
            audioPath = "/audio/track-1.flac",
        )
        val secondItem = PlaybackItem(
            trackId = "track-2",
            title = "Next track",
            artist = "Next artist",
            audioPath = "/audio/track-2.flac",
        )
    }
}
