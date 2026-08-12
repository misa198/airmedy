package me.misa198.airmedy.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class FullScreenPlayerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun queueScrollsCurrentTrackIntoViewWhenPlaybackAdvances() {
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
        composeTestRule.onNodeWithText("Track 11").assertExists()
        composeTestRule.onNodeWithTag("playing_indicator").assertExists()
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
