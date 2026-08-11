package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.ui.theme.AirmedyTheme
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
                        albums = listOf(LibraryAlbum("album", "Absolution", "Muse")),
                        tracks = List(3) { index -> me.misa198.airmedy.sync.LibraryTrack("$index", "Track $index", "Muse") },
                    ),
                    listState = rememberLazyListState(),
                    onAlbumClick = { selectedAlbumId = it.id },
                )
            }
        }

        composeTestRule.onNodeWithText("Muse").assertExists()
        composeTestRule.onNodeWithText("1 album · 3 tracks").assertExists()
        composeTestRule.onNodeWithText("Absolution").performClick()
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
}
