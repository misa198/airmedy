package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposerDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysComposerSummaryAndOpensSelectedAlbum() {
        var selectedAlbumId: String? = null
        var playCount = 0
        var shuffleCount = 0
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ComposerDetailsContent(
                    uiState = ComposerDetailsUiState(
                        composer = LibraryComposer("glass", "Philip Glass"),
                        albums = listOf(LibraryAlbum("album", "Glassworks", "Philip Glass")),
                        tracks = List(3) { index -> LibraryTrack("$index", "Track $index", "Philip Glass") },
                    ),
                    listState = rememberLazyListState(),
                    onPlay = { playCount++ },
                    onShuffle = { shuffleCount++ },
                    onAlbumClick = { selectedAlbumId = it.id },
                )
            }
        }

        composeTestRule.onNodeWithText("Philip Glass").assertExists()
        composeTestRule.onNodeWithText("1 album · 3 tracks").assertExists()
        composeTestRule.onNodeWithText("Play").performClick()
        composeTestRule.onNodeWithText("Shuffle").performClick()
        composeTestRule.onNodeWithText("Glassworks").performClick()
        assertEquals(1, playCount)
        assertEquals(1, shuffleCount)
        assertEquals("album", selectedAlbumId)
    }

    @Test
    fun displaysUnavailableStateWhenComposerIsMissing() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                ComposerDetailsContent(ComposerDetailsUiState(), listState = rememberLazyListState())
            }
        }

        composeTestRule.onNodeWithText("Composer unavailable").assertExists()
    }
}
