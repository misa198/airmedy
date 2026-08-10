package me.misa198.airmedy.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

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

        composeTestRule.onNodeWithContentDescription("Queue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Queue").assertCountEquals(0)
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
