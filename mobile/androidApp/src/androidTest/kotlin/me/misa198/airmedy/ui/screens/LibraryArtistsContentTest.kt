package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

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
}
