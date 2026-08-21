package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryGenre
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class LibraryGenresContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoGenres() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryGenresContent(LibraryGenresUiState())
            }
        }

        composeTestRule.onNodeWithText("No genres in library").assertExists()
    }

    @Test
    fun displaysGenresInVirtualizedRows() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryGenresContent(
                    LibraryGenresUiState(
                        genres = listOf(
                            LibraryGenre(id = "a", name = "Ambient"),
                            LibraryGenre(id = "b", name = "Electronic"),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Ambient").assertExists()
        composeTestRule.onNodeWithText("Electronic").assertExists()
        composeTestRule.onAllNodesWithTag("genre-row-divider").assertCountEquals(1)
    }

    @Test
    fun genreOverflowUsesProvidedOrderedTrackIds() {
        var nextIds: List<String>? = null
        var addOnly = false
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryGenresContent(
                    LibraryGenresUiState(genres = listOf(LibraryGenre("a", "Ambient"))),
                    orderedTrackIdsForGenre = { listOf("track-1", "track-2") },
                    onGenrePlayNext = { nextIds = it },
                    onTrackContextBottomSheet = { request ->
                        addOnly = (request as TrackContextBottomSheetRequest.Playlist).addOnly
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Ambient").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Play next").performClick()
        assertEquals(listOf("track-1", "track-2"), nextIds)

        composeTestRule.onNodeWithText("Ambient").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Add to playlist").performClick()
        assertEquals(true, addOnly)
    }
}
