package me.misa198.airmedy.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WidePlayerSwipeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun artworkAndMetadataSwipeBothDirectionsInRegularAndCompactWide() {
        var height by mutableStateOf(600.dp)
        var previous = 0
        var next = 0
        compose.setContent { Player(height = height, onPrevious = { previous++ }, onNext = { next++ }) }
        listOf(600.dp, 440.dp).forEach { newHeight ->
            compose.runOnIdle { height = newHeight }
            listOf(FullScreenPlayerArtworkTestTag, FullScreenPlayerMetadataSwipeTestTag).forEach { tag ->
                swipeFromUpperContent(tag, toNext = true)
                swipeFromUpperContent(tag, toNext = false)
            }
        }
        compose.runOnIdle { assertEquals(4, next); assertEquals(4, previous) }
    }

    @Test fun lyricsAndQueueRemainIndependentFromTheLeftSwipeTarget() {
        var next = 0
        var previous = 0
        var reordered: List<String>? = null
        compose.setContent {
            Player(onPrevious = { previous++ }, onNext = { next++ }, onReorder = { reordered = it })
        }
        val targetBounds = compose.onNodeWithTag("wide_player_swipe_target").fetchSemanticsNode().boundsInRoot
        val panelBounds = compose.onNodeWithTag("wide_player_panel").fetchSemanticsNode().boundsInRoot
        val controlsBounds = compose.onNodeWithTag(FullScreenPlayerControlsTestTag).fetchSemanticsNode().boundsInRoot
        assertTrue(targetBounds.right <= panelBounds.left)
        assertTrue(targetBounds.bottom <= controlsBounds.top)
        compose.onNodeWithTag("wide_player_panel").performTouchInput { swipeLeft(); swipeRight() }
        val lyrics = compose.onNodeWithTag("plain_lyrics_list")
        lyrics.performTouchInput { swipeUp() }
        assertTrue(lyrics.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        swipeFromUpperContent(FullScreenPlayerArtworkTestTag, toNext = true)
        compose.onNodeWithContentDescription(label(R.string.player_queue)).performClick()
        compose.onNodeWithTag("wide_player_panel").performTouchInput { swipeLeft(); swipeRight() }
        compose.onNodeWithTag("full_screen_queue_list").performScrollToIndex(10)
        assertTrue(compose.onNodeWithTag("full_screen_queue_list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.onNodeWithTag("full_screen_queue_list").performScrollToIndex(0)
        val first = compose.onNodeWithTag("full_screen_queue_row-track-1").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithTag("full_screen_queue_row-track-3").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("full_screen_queue_list").performTouchInput {
            val start = Offset(width - first.height / 2, first.height / 2)
            down(start)
            advanceEventTime(600)
            moveTo(start + Offset(0f, third.center.y - first.center.y), 500)
            up()
        }
        compose.runOnIdle { assertTrue(reordered != null) }
        swipeFromUpperContent(FullScreenPlayerMetadataSwipeTestTag, toNext = false)
        compose.runOnIdle { assertEquals(1, next); assertEquals(1, previous) }
    }

    @Test fun controlsAndMetadataButtonsKeepTheirActionsWithoutTrackSwipes() {
        var next = 0
        var previous = 0
        var seek = 0
        var volume = 0
        var play = 0
        var favorite = 0
        var output = 0
        compose.setContent {
            Player(onPrevious = { previous++ }, onNext = { next++ }, onSeek = { seek++ },
                onVolume = { volume++ }, onPlay = { play++ }, onFavorite = { favorite++ }, onOutput = { output++ })
        }
        compose.onNodeWithContentDescription(label(R.string.player_seek)).performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription(label(R.string.player_volume)).performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription(label(R.string.player_play)).performClick()
        compose.onNodeWithContentDescription(label(R.string.player_heart)).performClick()
        compose.onNodeWithContentDescription(label(R.string.player_cast)).performClick()
        compose.onNodeWithContentDescription(label(R.string.player_more)).performClick()
        compose.onNodeWithText(label(R.string.track_context_track_info)).assertExists()
        compose.runOnIdle {
            assertEquals(0, next); assertEquals(0, previous)
            assertEquals(1, seek); assertTrue(volume > 0)
            assertEquals(1, play); assertEquals(1, favorite); assertEquals(1, output)
        }
    }

    @Test fun cancelledAndShortSwipesDoNotDispatchAndCallbacksStayCurrent() {
        var generation by mutableStateOf(1)
        var invoked = 0
        compose.setContent {
            val captured = generation
            Player(onNext = { invoked = captured })
        }
        val target = compose.onNodeWithTag("wide_player_swipe_target")
        target.performTouchInput {
            swipe(center, center + Offset(5f, 0f), 400)
            down(center)
            moveTo(Offset(width * 0.1f, center.y))
            cancel()
        }
        compose.runOnIdle { assertEquals(0, invoked); generation = 2 }
        swipeFromUpperContent(FullScreenPlayerArtworkTestTag, toNext = true)
        compose.runOnIdle { assertEquals(2, invoked) }
    }

    @Test fun resizeLayoutChangeAndDismissalCancelAnActiveSwipe() {
        var height by mutableStateOf(600.dp)
        var safeWidth by mutableStateOf(1000.dp)
        var visible by mutableStateOf(true)
        var next = 0
        compose.setContent { Player(safeWidth, height, visible, onNext = { next++ }) }
        listOf<() -> Unit>(
            { height = 440.dp },
            { safeWidth = 800.dp },
            { visible = false },
        ).forEach { interrupt ->
            compose.runOnIdle { safeWidth = 1000.dp; height = 600.dp; visible = true }
            compose.onNodeWithTag("wide_player_swipe_target").performTouchInput {
                down(center)
                moveTo(Offset(width * 0.1f, center.y))
            }
            compose.runOnIdle(interrupt)
            // Finish through the test root even when the player has disappeared.
            compose.onRoot().performTouchInput { up() }
        }
        compose.runOnIdle { assertEquals(0, next) }
    }

    @Test fun swipeDownStillDismissesWide() {
        var dismissed = false
        compose.setContent { Player(onDismiss = { dismissed = true }) }
        compose.onNodeWithTag(FullScreenPlayerArtworkTestTag).performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue(dismissed) }
    }

    private fun swipeFromUpperContent(tag: String, toNext: Boolean) {
        val target = compose.onNodeWithTag("wide_player_swipe_target")
        val targetBounds = target.fetchSemanticsNode().boundsInRoot
        val contentBounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val x = if (toNext) contentBounds.right - 1f else contentBounds.left + 1f
        val start = Offset(x, contentBounds.center.y) - targetBounds.topLeft
        target.performTouchInput {
            swipe(start, Offset(if (toNext) 0f else width - 1f, start.y), 400)
        }
    }

    private fun label(resource: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    @Composable
    private fun Player(
        width: Dp = 1000.dp,
        height: Dp = 600.dp,
        visible: Boolean = true,
        onPrevious: () -> Unit = {},
        onNext: () -> Unit = {},
        onSeek: () -> Unit = {},
        onVolume: () -> Unit = {},
        onPlay: () -> Unit = {},
        onFavorite: () -> Unit = {},
        onOutput: () -> Unit = {},
        onReorder: (List<String>) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        val insets = WindowInsets.safeDrawing
        val horizontalInsets = with(LocalDensity.current) {
            (insets.getLeft(this, LayoutDirection.Ltr) + insets.getRight(this, LayoutDirection.Ltr)).toDp()
        }
        val verticalInsets = with(LocalDensity.current) { (insets.getTop(this) + insets.getBottom(this)).toDp() }
        val ids = (1..40).map { "track-$it" }
        AirmedyTheme(themeMode = ThemeMode.Dark) {
            FullScreenPlayer(
                modifier = Modifier.requiredSize(width + horizontalInsets, height + verticalInsets),
                visible = visible, dragProgress = 0f, isDragging = false, openingFromMiniPlayerSwipe = false,
                playbackState = PlaybackState.Paused(PlaybackItem(ids.first(), "Title", "Artist", "/audio.flac"), 60_000L, 120_000L),
                queue = PlaybackQueueSnapshot(activeTrackIds = ids, currentIndex = 0),
                queueTracks = ids.map { LibraryTrack(id = it, title = it, artists = "Artist") },
                lyrics = (1..60).joinToString("\n") { "Line $it" },
                volume = 0.5f, onSeek = { onSeek() }, onVolumeChange = { onVolume() },
                onPrevious = onPrevious, onNext = onNext, onPlayPause = onPlay,
                onFavoriteToggle = { _, _ -> onFavorite() }, onOpenMediaOutputSwitcher = onOutput,
                onQueueReordered = onReorder, onDismiss = onDismiss,
            )
        }
    }
}
