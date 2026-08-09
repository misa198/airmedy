package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryTracksContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoTracks() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = emptyList()),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No tracks in library").assertExists()
    }

    @Test
    fun displaysTracksList() {
        val sampleTracks = listOf(
            LibraryTrack(id = "1", title = "Song A", artists = "Artist A", album = "Album A"),
            LibraryTrack(id = "2", title = "Song B", artists = "Artist B", album = "Album B"),
        )

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(
                        tracks = sampleTracks,
                        sortOption = TrackSortOption.Name,
                        sortOrder = SortOrder.Ascending,
                    ),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Song A").assertExists()
        composeTestRule.onNodeWithText("Song B").assertExists()
    }

    @Test
    fun clickingTrackEmitsTheSelectedTrack() {
        val track = LibraryTrack(id = "selected", title = "Song A", artists = "Artist A", album = "Album A")
        var selectedId: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = listOf(track)),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                    onTrackClick = { selectedId = it.id },
                )
            }
        }

        composeTestRule.onNodeWithText("Song A").performClick()
        assertEquals("selected", selectedId)
    }
}
