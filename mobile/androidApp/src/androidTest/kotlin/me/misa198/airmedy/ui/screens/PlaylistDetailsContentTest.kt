package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

class PlaylistDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysTrackCountAndDesktopStyleDurationWithoutArtistOrCopyright() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                PlaylistDetailsContent(
                    PlaylistDetailsUiState(
                        playlist = LibraryPlaylist("mix", "Night drive", listOf("one", "two"), "{}"),
                        tracks = listOf(
                            LibraryTrack("one", "One", "Artist", metadataJson = """{"duration":60}"""),
                            LibraryTrack("two", "Two", "Artist", metadataJson = """{"duration":120}"""),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Night drive").assertExists()
        composeTestRule.onNodeWithText("2 tracks · 3 min").assertExists()
        composeTestRule.onAllNodesWithTag("playlist-detail-track-divider").assertCountEquals(3)
    }
}
