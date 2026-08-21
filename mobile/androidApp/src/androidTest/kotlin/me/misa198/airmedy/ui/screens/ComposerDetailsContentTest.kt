package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposerDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysComposerSummaryAndOpensSelectedAlbum() {
        var selectedAlbumId: String? = null
        var playCount = 0
        var shuffleCount = 0
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ComposerDetailsContent(
                    uiState = ComposerDetailsUiState(
                        composer = LibraryComposer("glass", "Philip Glass"),
                        albums = listOf(LibraryAlbum("album", "Glassworks", "Philip Glass"), LibraryAlbum("album-2", "Koyaanisqatsi", "Philip Glass")),
                        tracks = List(3) { index -> LibraryTrack("$index", "Track $index", "Philip Glass") },
                    ),
                    listState = rememberLazyListState(),
                    onPlay = { playCount++ },
                    onShuffle = { shuffleCount++ },
                    onAlbumClick = { selectedAlbumId = it.id },
                )
            }
        }

        composeTestRule.onNodeWithText("Philip Glass").assertExists()
        composeTestRule.onNodeWithText("2 albums · 3 tracks").assertExists()
        composeTestRule.onNodeWithText("Play").performClick()
        composeTestRule.onNodeWithText("Shuffle").performClick()
        composeTestRule.onNodeWithText("Glassworks").performClick()
        composeTestRule.onAllNodesWithTag("composer-detail-album-divider").assertCountEquals(3)
        assertEquals(1, playCount)
        assertEquals(1, shuffleCount)
        assertEquals("album", selectedAlbumId)
    }

    @Test
    fun displaysUnavailableStateWhenComposerIsMissing() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ComposerDetailsContent(ComposerDetailsUiState(), listState = rememberLazyListState())
            }
        }

        composeTestRule.onNodeWithText("Composer unavailable").assertExists()
    }

    @Test
    fun doesNotDisplayAnAlbumDividerForOneAlbum() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ComposerDetailsContent(
                    uiState = ComposerDetailsUiState(
                        composer = LibraryComposer("glass", "Philip Glass"),
                        albums = listOf(LibraryAlbum("album", "Glassworks", "Philip Glass")),
                    ),
                    listState = rememberLazyListState(),
                )
            }
        }

        composeTestRule.onAllNodesWithTag("composer-detail-album-divider").assertCountEquals(2)
    }

    @Test
    fun composerMoreMenuUsesOrderedTracksForPlayNextAndPlaylist() {
        var nextIds: List<String>? = null
        var playlistIds: List<String>? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ComposerDetailsContent(
                    uiState = ComposerDetailsUiState(
                        composer = LibraryComposer("bach", "Bach"),
                        tracks = listOf(
                            LibraryTrack("first", "First", "Artist"),
                            LibraryTrack("second", "Second", "Artist"),
                        ),
                    ),
                    listState = rememberLazyListState(),
                    onPlayNext = { nextIds = it },
                    onTrackContextBottomSheet = { request ->
                        playlistIds = (request as TrackContextBottomSheetRequest.Playlist).trackIds
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Composer options").performClick()
        composeTestRule.onNodeWithText("Play next").performClick()
        assertEquals(listOf("first", "second"), nextIds)

        composeTestRule.onNodeWithText("Composer options").performClick()
        composeTestRule.onNodeWithText("Add to playlist").performClick()
        assertEquals(listOf("first", "second"), playlistIds)
    }
}
