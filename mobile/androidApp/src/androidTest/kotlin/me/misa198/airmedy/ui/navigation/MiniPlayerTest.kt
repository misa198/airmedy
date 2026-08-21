package me.misa198.airmedy.ui.navigation

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chrisbanes.haze.rememberHazeState
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun newlyStartedPlaybackSlidesMiniPlayerUpFromNavigation() {
        var playbackState by mutableStateOf<PlaybackState>(PlaybackState.Idle)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            NavigationChromeForTest(playbackState)
        }

        playbackState = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L)
        composeTestRule.waitForIdle()
        val startingTop = composeTestRule.onNodeWithText(item.title).fetchSemanticsNode().boundsInRoot.top

        composeTestRule.mainClock.advanceTimeBy(300)
        val restingTop = composeTestRule.onNodeWithText(item.title).fetchSemanticsNode().boundsInRoot.top

        assertTrue(
            "Mini player should slide upward when playback starts (start=$startingTop, end=$restingTop)",
            restingTop < startingTop,
        )
    }

    @Test
    fun pausedStateUsesPlayAndPreparingDisablesTheTransportToggle() {
        composeTestRule.setContent { NavigationChromeForTest(PlaybackState.Paused(item, 0L, 120_000L)) }
        composeTestRule.onNodeWithContentDescription("Play").assertExists()

        composeTestRule.setContent { NavigationChromeForTest(PlaybackState.Preparing(item)) }
        composeTestRule.onNodeWithContentDescription("Play").assertIsNotEnabled()
    }

    @Test
    fun playPauseControlTransitionsItsAccessibleStateWithPlayback() {
        var playbackState by mutableStateOf<PlaybackState>(PlaybackState.Paused(item, 0L, 120_000L))
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { NavigationChromeForTest(playbackState) }

        composeTestRule.onNodeWithContentDescription("Play").assertExists()
        playbackState = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L)
        composeTestRule.mainClock.advanceTimeBy(260)

        composeTestRule.onNodeWithContentDescription("Pause").assertExists()
    }

    @Test
    fun draggingMiniPlayerDownDismissesIt() {
        var dismisses = 0
        var opens = 0
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onDismiss = { dismisses += 1 },
                onOpenFullScreenPlayer = { opens += 1 },
            )
        }

        composeTestRule.onNodeWithText(item.title).performTouchInput { swipeDown() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { dismisses == 1 }
        composeTestRule.waitForIdle()

        assertEquals(1, dismisses)
        assertEquals(0, opens)
        composeTestRule.onNodeWithText(item.title).assertIsNotDisplayed()
    }

    @Test
    fun reversingADownwardMiniPlayerDragReturnsItToRestWithoutDismissing() {
        var dismisses = 0
        var opens = 0
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onDismiss = { dismisses += 1 },
                onOpenFullScreenPlayer = { opens += 1 },
            )
        }

        composeTestRule.onNodeWithText(item.title).performTouchInput {
            down(center)
            moveBy(Offset(x = 0f, y = 64f))
            moveBy(Offset(x = 0f, y = -64f))
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(0, dismisses)
        assertEquals(0, opens)
        composeTestRule.onNodeWithText(item.title).assertExists()
    }

    @Test
    fun draggingMiniPlayerUpOpensTheFullScreenPlayer() {
        var opens = 0
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onOpenFullScreenPlayer = { opens += 1 },
            )
        }

        composeTestRule.onNodeWithText(item.title).performTouchInput { swipeUp() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { opens == 1 }

        assertEquals(1, opens)
    }

    @Test
    fun reversingAnUpwardMiniPlayerDragToASubSlopRemainderDoesNotOpenFullscreen() {
        var opens = 0
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onOpenFullScreenPlayer = { opens += 1 },
            )
        }

        composeTestRule.onNodeWithText(item.title).performTouchInput {
            down(center)
            moveBy(Offset(x = 0f, y = -64f))
            moveBy(Offset(x = 0f, y = 56f))
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(0, opens)
        composeTestRule.onNodeWithText(item.title).assertExists()
    }

    @Test
    fun swipingMetadataLeftSkipsToTheNextTrackWithNoVerticalPlayerAction() {
        val calls = mutableListOf<String>()
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onNext = { calls += "next" },
                onDismiss = { calls += "dismiss" },
                onOpenFullScreenPlayer = { calls += "open" },
            )
        }

        composeTestRule.onNodeWithText(item.title).performTouchInput { swipeLeft() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { calls.isNotEmpty() }

        assertEquals(listOf("next"), calls)
    }

    @Test
    fun swipingMetadataRightReturnsToThePreviousTrack() {
        val calls = mutableListOf<String>()
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onPrevious = { calls += "previous" },
            )
        }

        composeTestRule.onNodeWithText(item.artist).performTouchInput { swipeRight() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { calls.isNotEmpty() }

        assertEquals(listOf("previous"), calls)
    }

    @Test
    fun shortSlowMetadataDragDoesNotDispatchTransport() {
        val calls = mutableListOf<String>()
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                onPrevious = { calls += "previous" },
                onNext = { calls += "next" },
            )
        }

        composeTestRule.onNodeWithText(item.title).performTouchInput {
            down(center)
            moveBy(Offset(x = 20f, y = 0f), delayMillis = 300)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun metadataSwipeUsesTheLatestQueueNavigationAvailability() {
        var queue by mutableStateOf(PlaybackQueueSnapshot())
        val calls = mutableListOf<String>()
        composeTestRule.setContent {
            NavigationChromeForTest(
                state = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                queue = queue,
                onNext = { calls += "next" },
            )
        }

        // The mini-player remains mounted while playback queue state changes.
        // Swipe must observe the new queue just like the visible Next button.
        queue = PlaybackQueueSnapshot(
            originalTrackIds = listOf("track-1", "track-2"),
            activeTrackIds = listOf("track-1", "track-2"),
            currentIndex = 0,
        )
        composeTestRule.onNodeWithText(item.title).performTouchInput { swipeLeft() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { calls.isNotEmpty() }

        assertEquals(listOf("next"), calls)
    }

    @Test
    fun compactChromeShowsTheActiveDestinationPlayPauseAndNext() {
        var compact by mutableStateOf(true)
        val calls = mutableListOf<String>()
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                NavigationChrome(
                    selectedDestination = AppDestination.Home,
                    playbackState = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                    hazeState = rememberHazeState(),
                    compact = compact,
                    onExpandClick = { compact = false },
                    onDestinationSelected = {},
                    onPreviousClick = { calls += "previous" },
                    onPlayPauseClick = { calls += "pause" },
                    onNextClick = { calls += "next" },
                    onMiniPlayerDismiss = { compact = false },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Home").assertExists()
        composeTestRule.onNodeWithContentDescription("Home").assertWidthIsEqualTo(48.dp)
        composeTestRule.onAllNodes(hasContentDescription("Previous")).assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        assertEquals(listOf("pause", "next"), calls)

        composeTestRule.onNodeWithContentDescription("Home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Previous").assertExists()
        composeTestRule.onNodeWithContentDescription("Next").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun NavigationChromeForTest(
        state: PlaybackState,
        queue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
        onPrevious: () -> Unit = {},
        onPlayPause: () -> Unit = {},
        onNext: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onOpenFullScreenPlayer: () -> Unit = {},
    ) {
        AirmedyTheme(themeMode = ThemeMode.Dark) {
            NavigationChrome(
                selectedDestination = AppDestination.Home,
                playbackState = state,
                playbackQueue = queue,
                hazeState = rememberHazeState(),
                onDestinationSelected = {},
                onPreviousClick = onPrevious,
                onPlayPauseClick = onPlayPause,
                onNextClick = onNext,
                onMiniPlayerDismiss = onDismiss,
                onOpenFullScreenPlayer = onOpenFullScreenPlayer,
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
