package me.misa198.airmedy.ui.navigation

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.chrisbanes.haze.rememberHazeState
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MiniPlayerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hidesWhenPlaybackIsIdleOrFailed() {
        composeTestRule.setContent { NavigationChromeForTest(PlaybackState.Idle) }
        composeTestRule.onAllNodesWithText(item.title).assertCountEquals(0)

        composeTestRule.setContent { NavigationChromeForTest(PlaybackState.Failed(item.trackId, "unavailable")) }
        composeTestRule.onAllNodesWithText(item.title).assertCountEquals(0)
    }

    @Test
    fun displaysMetadataAndDispatchesTransportControls() {
        val calls = mutableListOf<String>()
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onPrevious = { calls += "previous" },
                onPlayPause = { calls += "pause" },
                onNext = { calls += "next" },
            )
        }

        composeTestRule.onNodeWithText(item.title).assertExists()
        composeTestRule.onNodeWithText(item.artist).assertExists()
        composeTestRule.onNodeWithContentDescription("Previous").performClick()
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        composeTestRule.onNodeWithContentDescription("Next").performClick()

        assertEquals(listOf("previous", "pause", "next"), calls)
    }

    @Test
    fun pausedStateUsesPlayAndPreparingDisablesTheTransportToggle() {
        composeTestRule.setContent { NavigationChromeForTest(PlaybackState.Paused(item, 0L, 120_000L)) }
        composeTestRule.onNodeWithContentDescription("Play").assertExists()

        composeTestRule.setContent { NavigationChromeForTest(PlaybackState.Preparing(item)) }
        composeTestRule.onNodeWithContentDescription("Play").assertIsNotEnabled()
    }

    @Test
    fun draggingMiniPlayerDownDismissesIt() {
        var dismisses = 0
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onDismiss = { dismisses += 1 },
            )
        }

        composeTestRule.onNodeWithText(item.title).performTouchInput { swipeDown() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { dismisses == 1 }

        assertEquals(1, dismisses)
    }

    @androidx.compose.runtime.Composable
    private fun NavigationChromeForTest(
        state: PlaybackState,
        onPrevious: () -> Unit = {},
        onPlayPause: () -> Unit = {},
        onNext: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        AirmedyTheme(themeMode = ThemeMode.Dark) {
            NavigationChrome(
                selectedDestination = AppDestination.Home,
                playbackState = state,
                hazeState = rememberHazeState(),
                onDestinationSelected = {},
                onPreviousClick = onPrevious,
                onPlayPauseClick = onPlayPause,
                onNextClick = onNext,
                onMiniPlayerDismiss = onDismiss,
            )
        }
    }

    private companion object {
        val item = PlaybackItem(
            trackId = "track-1",
            title = "A very long title that should move across the mini player",
            artist = "A very long artist name that should also move",
            audioPath = "/audio/track-1.flac",
        )
    }
}
