package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

class LibraryAlbumsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoAlbums() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) { LibraryAlbumsContent(LibraryAlbumsUiState()) }
        }
        composeTestRule.onNodeWithText("No albums in library").assertExists()
    }

    @Test
    fun displaysAlbumsInVirtualizedRowsAndUsesUnknownArtistFallback() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    LibraryAlbumsUiState(
                        albums = listOf(
                            LibraryAlbum(id = "a", title = "Album A", artist = "Artist A"),
                            LibraryAlbum(id = "b", title = "Album B"),
                        ),
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Album A").assertExists()
        composeTestRule.onNodeWithText("Artist A").assertExists()
        composeTestRule.onNodeWithText("Unknown artist").assertExists()
        composeTestRule.onAllNodesWithTag("album-row-divider").assertCountEquals(1)
    }
}
