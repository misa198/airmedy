package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class LibraryArtistsContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoArtists() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryArtistsContent(LibraryArtistsUiState())
            }
        }

        composeTestRule.onNodeWithText("No artists in library").assertExists()
    }

    @Test
    fun displaysArtistsInVirtualizedRows() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryArtistsContent(
                    LibraryArtistsUiState(
                        artists = listOf(
                            LibraryArtist(id = "a", name = "Artist A"),
                            LibraryArtist(id = "b", name = "Artist B"),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Artist A").assertExists()
        composeTestRule.onNodeWithText("Artist B").assertExists()
        composeTestRule.onAllNodesWithTag("artist-row-divider").assertCountEquals(1)
    }

    @Test
    fun artistLongPressUsesOrderedTracksAndAddOnlyPlaylist() {
        var nextIds: List<String>? = null
        var addOnly = false
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryArtistsContent(
                    LibraryArtistsUiState(artists = listOf(LibraryArtist("a", "Muse"))),
                    orderedTrackIdsForArtist = { listOf("track-1", "track-2") },
                    onArtistPlayNext = { nextIds = it },
                    onTrackContextBottomSheet = { request ->
                        addOnly = (request as TrackContextBottomSheetRequest.Playlist).addOnly
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Muse").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Play next").performClick()
        assertEquals(listOf("track-1", "track-2"), nextIds)

        composeTestRule.onNodeWithText("Muse").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Add to playlist").performClick()
        assertEquals(true, addOnly)
    }
}
