package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArtistDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysArtistSummaryAndOpensSelectedAlbum() {
        var selectedAlbumId: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ArtistDetailsContent(
                    uiState = ArtistDetailsUiState(
                        artist = LibraryArtist("artist", "Muse"),
                        albums = listOf(LibraryAlbum("album", "Absolution", "Muse"), LibraryAlbum("album-2", "Origin of Symmetry", "Muse")),
                        tracks = List(3) { index -> me.misa198.airmedy.sync.LibraryTrack("$index", "Track $index", "Muse") },
                    ),
                    listState = rememberLazyListState(),
                    onAlbumClick = { selectedAlbumId = it.id },
                )
            }
        }

        composeTestRule.onNodeWithText("Muse").assertExists()
        composeTestRule.onNodeWithText("2 albums · 3 tracks").assertExists()
        composeTestRule.onNodeWithText("Absolution").performClick()
        composeTestRule.onAllNodesWithTag("artist-detail-album-divider").assertCountEquals(3)
        assertEquals("album", selectedAlbumId)
    }

    @Test
    fun displaysUnavailableStateWhenArtistIsMissing() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ArtistDetailsContent(ArtistDetailsUiState(), listState = rememberLazyListState())
            }
        }

        composeTestRule.onNodeWithText("Artist unavailable").assertExists()
    }

    @Test
    fun doesNotDisplayAnAlbumDividerForOneAlbum() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ArtistDetailsContent(
                    uiState = ArtistDetailsUiState(
                        artist = LibraryArtist("artist", "Muse"),
                        albums = listOf(LibraryAlbum("album", "Absolution", "Muse")),
                    ),
                    listState = rememberLazyListState(),
                )
            }
        }

        composeTestRule.onAllNodesWithTag("artist-detail-album-divider").assertCountEquals(2)
    }

    @Test
    fun artistMoreMenuUsesOrderedTracksForPlayNextAndPlaylist() {
        var nextIds: List<String>? = null
        var playlistIds: List<String>? = null
        var addOnly = false
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ArtistDetailsContent(
                    uiState = ArtistDetailsUiState(
                        artist = LibraryArtist("artist", "Muse"),
                        tracks = listOf(
                            me.misa198.airmedy.sync.LibraryTrack("first", "First", "Muse"),
                            me.misa198.airmedy.sync.LibraryTrack("second", "Second", "Muse"),
                        ),
                    ),
                    listState = rememberLazyListState(),
                    onPlayNext = { nextIds = it },
                    onTrackContextBottomSheet = { request ->
                        val playlistRequest = request as TrackContextBottomSheetRequest.Playlist
                        playlistIds = playlistRequest.trackIds
                        addOnly = playlistRequest.addOnly
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Artist options").performClick()
        composeTestRule.onNodeWithText("Play next").performClick()
        assertEquals(listOf("first", "second"), nextIds)

        composeTestRule.onNodeWithText("Artist options").performClick()
        composeTestRule.onNodeWithText("Add to playlist").performClick()
        assertEquals(listOf("first", "second"), playlistIds)
        assertEquals(true, addOnly)
    }
}
