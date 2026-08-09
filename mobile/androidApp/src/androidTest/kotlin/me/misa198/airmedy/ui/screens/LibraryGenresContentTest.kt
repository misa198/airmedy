package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryGenre
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

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
}
