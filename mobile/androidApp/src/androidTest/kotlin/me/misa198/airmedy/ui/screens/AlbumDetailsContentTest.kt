package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

class AlbumDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysPublishedYearTrackCountAndTotalDurationInHeroMetadata() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(
                    AlbumDetailsUiState(
                        album = LibraryAlbum("album", "Absolution", "Muse", year = 2003),
                        tracks = listOf(
                            LibraryTrack("one", "One", "Muse", metadataJson = """{"duration":60}"""),
                            LibraryTrack("two", "Two", "Muse", metadataJson = """{"duration":120}"""),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Muse").assertExists()
        composeTestRule.onNodeWithText("2003 · 2 tracks · 3 min").assertExists()
        composeTestRule.onAllNodesWithTag("album-detail-track-divider").assertCountEquals(3)
    }

    @Test
    fun displaysCopyrightBelowTheTrackListWhenPresent() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(
                    AlbumDetailsUiState(
                        album = LibraryAlbum("album", "Absolution", "Muse", copyright = "© 2003 Taste Media"),
                        tracks = listOf(LibraryTrack("one", "One", "Muse")),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("© 2003 Taste Media").assertExists()
    }
}
