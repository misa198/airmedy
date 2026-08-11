package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
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
    fun displaysPublishedYearAndTrackCountInHeroMetadata() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(
                    AlbumDetailsUiState(
                        album = LibraryAlbum("album", "Absolution", "Muse", year = 2003),
                        tracks = listOf(
                            LibraryTrack("one", "One", "Muse"),
                            LibraryTrack("two", "Two", "Muse"),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Muse").assertExists()
        composeTestRule.onNodeWithText("2003 · 2 tracks").assertExists()
    }
}
