package me.misa198.airmedy.ui.navigation

import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.BuildConfig
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdaptivePlayerTest {
    @get:Rule val compose = createComposeRule()
    private val item = PlaybackItem("track-1", "Title", "Artist", "/audio.flac")

    @Test fun resizePreservesQueueViewportAndUpdatesLyricsKeepAwake() {
        var size by mutableStateOf(400.dp to 900.dp)
        lateinit var view: View
        lateinit var queueLabel: String
        lateinit var lyricsLabel: String
        var playbackActions = 0
        var currentTrackId by mutableStateOf("track-1")
        compose.setContent {
            view = LocalView.current
            queueLabel = LocalContext.current.getString(R.string.player_queue)
            lyricsLabel = LocalContext.current.getString(R.string.player_lyrics)
            Player(size, currentTrackId = currentTrackId, onTransport = { playbackActions++ })
        }
        compose.onNodeWithTag("phone_player_layout").assertExists()
        compose.onNodeWithContentDescription(queueLabel).performClick()
        compose.onNodeWithTag("full_screen_queue_list").performScrollToIndex(15)
        val queueOffset = queueScrollOffset()
        assertTrue(queueOffset > 0f)
        compose.runOnIdle { size = 500.dp to 500.dp }
        compose.onNodeWithTag("square_player_layout").assertExists()
        compose.onNodeWithTag("phone_player_layout").assertDoesNotExist()
        compose.runOnIdle { size = 700.dp to 800.dp }
        compose.onNodeWithTag("phone_player_layout").assertExists()
        compose.runOnIdle { currentTrackId = "track-30" }
        compose.runOnIdle { size = 1000.dp to 600.dp }
        compose.onNodeWithTag("wide_player_layout").assertExists()
        compose.runOnIdle { size = 400.dp to 900.dp }
        compose.onNodeWithTag("full_screen_queue_list").assertExists()
        assertEquals(queueOffset, queueScrollOffset(), 0.01f)
        compose.onNodeWithContentDescription(lyricsLabel).performClick()
        compose.runOnIdle { assertTrue(view.keepScreenOn) }
        compose.runOnIdle { size = 500.dp to 500.dp }
        compose.onNodeWithTag("square_player_layout").assertExists()
        compose.runOnIdle { assertTrue(view.keepScreenOn) }
        compose.runOnIdle { size = 1000.dp to 600.dp }
        compose.onNodeWithTag("wide_player_layout").assertExists()
        compose.runOnIdle { assertTrue(view.keepScreenOn) }
        compose.runOnIdle { size = 400.dp to 900.dp }
        compose.onNodeWithTag("plain_lyrics_list").assertExists()
        compose.runOnIdle {
            assertTrue(view.keepScreenOn)
            assertEquals(0, playbackActions)
        }
    }

    @Test fun wideKeepsOnePanelAndLeftControlsWithCenteredGrip() {
        var size by mutableStateOf(1100.dp to 1000.dp)
        var playbackActions = 0
        lateinit var playLabel: String
        lateinit var lyricsLabel: String
        lateinit var queueLabel: String
        lateinit var volumeLabel: String
        lateinit var outputLabel: String
        lateinit var seekLabel: String
        compose.setContent {
            val context = LocalContext.current
            lyricsLabel = context.getString(R.string.player_lyrics)
            queueLabel = context.getString(R.string.player_queue)
            volumeLabel = context.getString(R.string.player_volume)
            outputLabel = context.getString(R.string.player_cast)
            seekLabel = context.getString(R.string.player_seek)
            playLabel = context.getString(R.string.player_play)
            Player(size, onTransport = { playbackActions++ })
        }
        compose.onNodeWithContentDescription(lyricsLabel).assertIsSelected().performClick()
        compose.onNodeWithContentDescription(lyricsLabel).assertIsSelected()
        compose.onNodeWithTag("plain_lyrics_list").assertExists()
        compose.onNodeWithContentDescription(volumeLabel).assertExists()
        compose.onNodeWithContentDescription(outputLabel).assertExists()
        val main = compose.onNodeWithTag("wide_player_main").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("wide_player_panel").fetchSemanticsNode().boundsInRoot
        val grip = compose.onNodeWithTag("full_screen_player_drag_handle").fetchSemanticsNode().boundsInRoot
        val artwork = compose.onNodeWithTag(FullScreenPlayerArtworkTestTag).fetchSemanticsNode().boundsInRoot
        val seek = compose.onNodeWithContentDescription(seekLabel).fetchSemanticsNode().boundsInRoot
        assertTrue(main.right <= panel.left)
        assertEquals(main.width, panel.width, 1f)
        assertEquals((main.left + panel.right) / 2f, grip.center.x, 1f)
        assertTrue(artwork.bottom <= seek.top)
        assertTrue(seek.right <= panel.left)
        compose.onNodeWithContentDescription(playLabel).performClick()
        compose.onNodeWithContentDescription(seekLabel).performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(2, playbackActions) }
        compose.onNodeWithContentDescription(queueLabel).performClick().assertIsSelected().performClick()
        compose.onNodeWithContentDescription(queueLabel).assertIsSelected()
        compose.onNodeWithTag("full_screen_queue_list").assertExists()
        compose.onNodeWithTag(FullScreenPlayerControlsTestTag).assertExists()
        compose.onNodeWithContentDescription(lyricsLabel).performClick().assertIsSelected()
        compose.runOnIdle { size = 1400.dp to 600.dp }
        compose.onNodeWithContentDescription(volumeLabel).assertExists()
        compose.onNodeWithContentDescription(outputLabel).assertExists()
        assertFooterAtControlsBottom(outputLabel, compact = true)
    }

    @Test fun wideRestoresQueueAcrossThemeChangesAndDismisses() {
        val restoration = StateRestorationTester(compose)
        var theme by mutableStateOf(ThemeMode.Light)
        var dismissed = false
        lateinit var queueLabel: String
        lateinit var lyricsLabel: String
        restoration.setContent {
            queueLabel = LocalContext.current.getString(R.string.player_queue)
            lyricsLabel = LocalContext.current.getString(R.string.player_lyrics)
            Player(1000.dp to 700.dp, theme, lyricContent = "", onDismiss = { dismissed = true })
        }
        compose.onNodeWithContentDescription(lyricsLabel).assertIsSelected().performClick()
        compose.onNodeWithContentDescription(lyricsLabel).assertIsSelected()
        compose.onNodeWithContentDescription(queueLabel).performClick()
        compose.onNodeWithTag("full_screen_queue_list").performScrollToIndex(15)
        val offset = queueScrollOffset()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription(queueLabel).assertIsSelected()
        assertEquals(offset, queueScrollOffset(), 0.01f)
        compose.runOnIdle { theme = ThemeMode.Dark }
        compose.onNodeWithTag("wide_player_main").assertExists()
        compose.onNodeWithTag(FullScreenPlayerArtworkTestTag).performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun queueSelectionAndViewportSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        lateinit var queueLabel: String
        restoration.setContent {
            queueLabel = LocalContext.current.getString(R.string.player_queue)
            Player(400.dp to 900.dp)
        }
        compose.onNodeWithContentDescription(queueLabel).performClick()
        compose.onNodeWithTag("full_screen_queue_list").performScrollToIndex(15)
        val offset = queueScrollOffset()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("full_screen_queue_list").assertExists()
        assertEquals(offset, queueScrollOffset(), 0.01f)
    }

    @Test fun browsedLyricsViewportSurvivesSquareVisit() {
        var size by mutableStateOf(400.dp to 900.dp)
        lateinit var lyricsLabel: String
        val lyrics = (0..40).joinToString("\n") { index ->
            "[${(index * 5) / 60}:${((index * 5) % 60).toString().padStart(2, '0')}]Line $index"
        }
        compose.setContent {
            lyricsLabel = LocalContext.current.getString(R.string.player_lyrics)
            Player(size, lyricContent = lyrics)
        }
        compose.onNodeWithContentDescription(lyricsLabel).performClick()
        compose.onNodeWithTag("synced_lyrics_list").performTouchInput { swipeUp() }
        val offset = compose.onNodeWithTag("synced_lyrics_list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(offset > 0f)
        compose.runOnIdle { size = 500.dp to 500.dp }
        compose.onNodeWithTag("square_player_layout").assertExists()
        compose.runOnIdle { size = 400.dp to 900.dp }
        val restored = compose.onNodeWithTag("synced_lyrics_list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(offset, restored, 0.01f)
    }

    @Test fun seekConfirmationReconcilesWhilePhoneControlsAreAbsent() {
        var size by mutableStateOf(400.dp to 900.dp)
        var position by mutableStateOf(0L)
        var requested: Long? = null
        lateinit var seekLabel: String
        compose.setContent {
            seekLabel = LocalContext.current.getString(R.string.player_seek)
            Player(size, positionMs = position, onSeekRequested = { requested = it })
        }
        compose.onNodeWithContentDescription(seekLabel).performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(requested != null) }
        val target = requireNotNull(requested)
        compose.runOnIdle { size = 500.dp to 500.dp }
        compose.onNodeWithTag("square_player_layout").assertExists()
        compose.runOnIdle { position = target }
        compose.runOnIdle { position = (target + 5_000L).coerceAtMost(120_000L) }
        compose.runOnIdle { size = 400.dp to 900.dp }
        val seconds = position / 1_000L
        compose.onNodeWithTag(FullScreenPlayerElapsedTimeTestTag)
            .assertTextEquals("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}")
    }

    @Test fun squareDismissesBySwipe() {
        var dismissed = false
        compose.setContent { Player(500.dp to 500.dp, onDismiss = { dismissed = true }) }
        compose.onNodeWithTag("square_player_layout").performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun squareRendersInBothThemes() {
        var mode by mutableStateOf(ThemeMode.Light)
        compose.setContent { Player(500.dp to 500.dp, mode) }
        compose.onNodeWithTag("square_player_main").assertExists()
        compose.runOnIdle { mode = ThemeMode.Dark }
        compose.onNodeWithTag("square_player_main").assertExists()
    }

    @Test fun largeSquareAndPortraitUsePhoneBeforeRotatingToTwoColumns() {
        var size by mutableStateOf(700.dp to 800.dp)
        lateinit var seekLabel: String
        compose.setContent {
            seekLabel = LocalContext.current.getString(R.string.player_seek)
            Player(size)
        }
        compose.onNodeWithTag("phone_player_layout").assertExists()
        compose.onNodeWithTag("phone_player_expanded_metadata").assertExists()
        compose.onNodeWithTag(FullScreenPlayerArtworkTestTag).assertExists()
        compose.onNodeWithContentDescription(seekLabel).assertExists()
        compose.runOnIdle { size = 700.dp to 700.dp }
        compose.onNodeWithTag("phone_player_expanded_metadata").assertExists()
        compose.onNodeWithTag("wide_player_layout").assertDoesNotExist()
        compose.runOnIdle { size = 900.dp to 1200.dp }
        compose.onNodeWithTag("phone_player_expanded_metadata").assertExists()
        compose.onNodeWithTag("wide_player_layout").assertDoesNotExist()
        compose.runOnIdle { size = 1200.dp to 900.dp }
        compose.onNodeWithTag("wide_player_layout").assertExists()
        compose.runOnIdle { size = 900.dp to 400.dp }
        compose.onNodeWithTag("wide_player_layout").assertExists()
        compose.onNodeWithContentDescription(seekLabel).assertExists()
        compose.runOnIdle { size = 1000.dp to 700.dp }
        compose.onNodeWithTag("wide_player_layout").assertExists()
    }

    @Test fun shortLandscapeUsesCompactWideOrSquareWithoutTinyPhoneArtwork() {
        var size by mutableStateOf(900.dp to 400.dp)
        var theme by mutableStateOf(ThemeMode.Light)
        var density = 1f
        lateinit var seekLabel: String
        lateinit var volumeLabel: String
        compose.setContent {
            density = LocalDensity.current.density
            seekLabel = LocalContext.current.getString(R.string.player_seek)
            volumeLabel = LocalContext.current.getString(R.string.player_volume)
            Player(size, theme)
        }
        fun assertCompactWide() {
            compose.onNodeWithTag("wide_player_layout").assertExists()
            compose.onNodeWithTag("phone_player_layout").assertDoesNotExist()
            compose.onNodeWithContentDescription(volumeLabel).assertExists()
            val artwork = compose.onNodeWithTag(FullScreenPlayerArtworkTestTag).fetchSemanticsNode().boundsInRoot
            val metadata = compose.onNodeWithTag(FullScreenPlayerMetadataSwipeTestTag).fetchSemanticsNode().boundsInRoot
            val seek = compose.onNodeWithContentDescription(seekLabel).fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag(FullScreenPlayerControlsTestTag).fetchSemanticsNode().boundsInRoot
            val main = compose.onNodeWithTag("wide_player_main").fetchSemanticsNode().boundsInRoot
            assertTrue("Compact artwork must stay useful", artwork.width >= 96f * density)
            assertTrue(artwork.right <= metadata.left)
            assertTrue(artwork.bottom <= seek.top)
            assertTrue(controls.bottom <= main.bottom + 1f)
        }
        assertCompactWide()
        compose.runOnIdle { theme = ThemeMode.Dark }
        assertCompactWide()
        compose.runOnIdle { size = 700.dp to 400.dp }
        compose.onNodeWithTag("square_player_main").assertExists()
        compose.onNodeWithTag("phone_player_layout").assertDoesNotExist()
        compose.runOnIdle { size = 900.dp to 399.dp }
        compose.onNodeWithTag("square_player_main").assertExists()
        compose.onNodeWithTag("wide_player_layout").assertDoesNotExist()
    }

    @Test fun squareShowsOneModeWithPersistentGripAndFooter() {
        lateinit var lyricsLabel: String
        lateinit var queueLabel: String
        lateinit var seekLabel: String
        lateinit var volumeLabel: String
        lateinit var moreLabel: String
        lateinit var favoriteLabel: String
        lateinit var outputLabel: String
        lateinit var playLabel: String
        lateinit var previousLabel: String
        lateinit var nextLabel: String
        var actions = 0
        var requestedSeek: Long? = null
        compose.setContent {
            val context = LocalContext.current
            lyricsLabel = context.getString(R.string.player_lyrics)
            queueLabel = context.getString(R.string.player_queue)
            seekLabel = context.getString(R.string.player_seek)
            volumeLabel = context.getString(R.string.player_volume)
            moreLabel = context.getString(R.string.player_more)
            favoriteLabel = context.getString(R.string.player_heart)
            outputLabel = context.getString(R.string.player_cast)
            playLabel = context.getString(R.string.player_play)
            previousLabel = context.getString(R.string.player_previous)
            nextLabel = context.getString(R.string.player_next)
            Player(500.dp to 500.dp, onTransport = { actions++ }, onSeekRequested = { requestedSeek = it })
        }
        compose.onNodeWithTag("square_player_main").assertExists()
        compose.onNodeWithTag("square_player_artwork_background").assertExists()
        compose.onNodeWithTag(FullScreenPlayerArtworkTestTag).assertDoesNotExist()
        compose.onNodeWithContentDescription(volumeLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(outputLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(moreLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(favoriteLabel).assertExists()
        compose.onNodeWithContentDescription(lyricsLabel).assertIsNotSelected()
        val mainBounds = compose.onNodeWithTag("square_player_main").fetchSemanticsNode().boundsInRoot
        val metadataBounds = compose.onNodeWithTag(FullScreenPlayerMetadataSwipeTestTag).fetchSemanticsNode().boundsInRoot
        val seekBounds = compose.onNodeWithContentDescription(seekLabel).fetchSemanticsNode().boundsInRoot
        assertTrue(metadataBounds.bottom < mainBounds.center.y)
        assertTrue(seekBounds.top > mainBounds.center.y)
        compose.onNodeWithContentDescription(playLabel).performClick()
        compose.onNodeWithContentDescription(previousLabel).performClick()
        compose.onNodeWithContentDescription(nextLabel).performClick()
        compose.onNodeWithContentDescription(seekLabel).performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(4, actions)
            assertTrue(requestedSeek != null)
        }
        val footerBounds = compose.onNodeWithTag("square_player_footer").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(lyricsLabel).performClick()
        compose.onNodeWithContentDescription(lyricsLabel).assertIsSelected()
        compose.onNodeWithTag("plain_lyrics_list").assertExists()
        compose.onNodeWithTag("square_player_main").assertDoesNotExist()
        compose.onNodeWithTag("square_player_artwork_background").assertDoesNotExist()
        compose.onNodeWithContentDescription(seekLabel).assertDoesNotExist()
        compose.onNodeWithTag("square_player_grip").assertExists()
        assertEquals(footerBounds, compose.onNodeWithTag("square_player_footer").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription(queueLabel).performClick()
        compose.onNodeWithContentDescription(queueLabel).assertIsSelected()
        compose.onNodeWithTag("full_screen_queue_list").assertExists()
        compose.onNodeWithTag("plain_lyrics_list").assertDoesNotExist()
        compose.onNodeWithTag("square_player_artwork_background").assertDoesNotExist()
        compose.onNodeWithTag("square_player_grip").assertExists()
        assertEquals(footerBounds, compose.onNodeWithTag("square_player_footer").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription(queueLabel).performClick()
        compose.onNodeWithTag("square_player_main").assertExists()
        compose.onNodeWithTag("full_screen_queue_list").assertDoesNotExist()
        compose.onNodeWithTag("square_player_artwork_background").assertExists()
    }

    @Test fun phoneCompactsSpacingWhileKeepingVolumeAcrossPanelsAndResize() {
        var size by mutableStateOf(390.dp to 900.dp)
        var density = 1f
        lateinit var volumeLabel: String
        lateinit var outputLabel: String
        lateinit var lyricsLabel: String
        lateinit var queueLabel: String
        compose.setContent {
            density = LocalDensity.current.density
            val context = LocalContext.current
            volumeLabel = context.getString(R.string.player_volume)
            outputLabel = context.getString(R.string.player_cast)
            lyricsLabel = context.getString(R.string.player_lyrics)
            queueLabel = context.getString(R.string.player_queue)
            Player(size)
        }
        compose.onNodeWithContentDescription(volumeLabel).assertExists()
        assertFooterAtControlsBottom(outputLabel)
        val normalHeight = compose.onNodeWithTag(FullScreenPlayerControlsTestTag).fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { size = 475.dp to 751.dp }
        compose.onNodeWithTag("phone_player_layout").assertExists()
        compose.onNodeWithContentDescription(volumeLabel).assertExists()
        compose.onNodeWithContentDescription(outputLabel).assertExists()
        assertFooterAtControlsBottom(outputLabel, compact = true)
        val compactHeight = compose.onNodeWithTag(FullScreenPlayerControlsTestTag).fetchSemanticsNode().boundsInRoot.height
        assertEquals(20f * density, normalHeight - compactHeight, 1f)
        compose.onNodeWithContentDescription(lyricsLabel).performClick()
        compose.onNodeWithContentDescription(volumeLabel).assertExists()
        compose.onNodeWithContentDescription(outputLabel).assertExists()
        assertFooterAtControlsBottom(outputLabel, compact = true)
        compose.onNodeWithContentDescription(queueLabel).performClick()
        compose.onNodeWithContentDescription(volumeLabel).assertExists()
        compose.onNodeWithContentDescription(outputLabel).assertExists()
        assertFooterAtControlsBottom(outputLabel, compact = true)
        compose.runOnIdle { size = 390.dp to 900.dp }
        compose.onNodeWithContentDescription(volumeLabel).assertExists()
        assertFooterAtControlsBottom(outputLabel)
        compose.onNodeWithTag("full_screen_queue_list").assertExists()
    }

    @Test fun layoutModeBadgeOnlyAppearsInDevAndTracksResize() {
        var size by mutableStateOf(390.dp to 900.dp)
        compose.setContent { Player(size) }
        fun assertBadge(mode: String) {
            val badge = compose.onNodeWithTag("player_layout_mode_badge")
            if (BuildConfig.FLAVOR == "dev") badge.assertTextEquals(mode)
            else badge.assertDoesNotExist()
        }
        assertBadge("Phone")
        compose.runOnIdle { size = 500.dp to 500.dp }
        assertBadge("Square")
        compose.runOnIdle { size = 700.dp to 800.dp }
        assertBadge("Phone")
        compose.runOnIdle { size = 900.dp to 400.dp }
        assertBadge("Wide")
        compose.runOnIdle { size = 1000.dp to 700.dp }
        assertBadge("Wide")
    }

    private fun assertFooterAtControlsBottom(outputLabel: String, compact: Boolean = false) {
        val controls = compose.onNodeWithTag(FullScreenPlayerControlsTestTag).fetchSemanticsNode().boundsInRoot
        val output = compose.onNodeWithContentDescription(outputLabel).fetchSemanticsNode().boundsInRoot
        assertEquals(controls.bottom, output.bottom, 1f)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val seek = compose.onNodeWithContentDescription(context.getString(R.string.player_seek)).fetchSemanticsNode().boundsInRoot
        val play = compose.onNodeWithContentDescription(context.getString(R.string.player_play)).fetchSemanticsNode().boundsInRoot
        val density = play.width / 64f
        assertTrue("Seek and playback controls must stay close", (play.top - seek.bottom) in 0f..(64f * density))
        val volume = compose.onNodeWithContentDescription(context.getString(R.string.player_volume)).fetchSemanticsNode().boundsInRoot
        assertEquals((if (compact) 8f else 16f) * density, volume.top - play.bottom, 1f)
        assertEquals((if (compact) 0f else 4f) * density, output.top - volume.bottom, 1f)
    }

    private fun queueScrollOffset(): Float = compose.onNodeWithTag("full_screen_queue_list")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Composable
    private fun Player(
        size: Pair<Dp, Dp>,
        theme: ThemeMode = ThemeMode.Dark,
        folds: List<PlayerFoldingFeature> = emptyList(),
        onTransport: () -> Unit = {},
        onDismiss: () -> Unit = {},
        positionMs: Long = 0L,
        currentTrackId: String = "track-1",
        lyricContent: String = "First line\nSecond line",
        onSeekRequested: (Long) -> Unit = {},
    ) {
        val insets = WindowInsets.safeDrawing
        val horizontalInsets = with(LocalDensity.current) {
            (insets.getLeft(this, LayoutDirection.Ltr) + insets.getRight(this, LayoutDirection.Ltr)).toDp()
        }
        val verticalInsets = with(LocalDensity.current) { (insets.getTop(this) + insets.getBottom(this)).toDp() }
        AirmedyTheme(themeMode = theme) {
            FullScreenPlayer(
                visible = true, dragProgress = 0f, isDragging = false, openingFromMiniPlayerSwipe = false,
                playbackState = PlaybackState.Paused(item.copy(trackId = currentTrackId), positionMs, 120_000L),
                queue = PlaybackQueueSnapshot(activeTrackIds = (1..40).map { "track-$it" }, currentIndex = 0),
                lyrics = lyricContent, keepScreenOnForLyrics = true,
                volume = 0.5f, onSeek = { onTransport(); onSeekRequested(it) }, onVolumeChange = {},
                onPrevious = onTransport, onPlayPause = onTransport, onNext = onTransport,
                onQueueReordered = { onTransport() }, onOpenMediaOutputSwitcher = {}, onDismiss = onDismiss,
                // Test sizes describe available space after system insets, as the resolver expects.
                foldingFeatures = folds, modifier = Modifier.requiredSize(size.first + horizontalInsets, size.second + verticalInsets),
            )
        }
    }
}
