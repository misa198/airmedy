package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.components.PlaylistRow
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class LibraryPlaylistsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysFavoritesWhenTheSyncManifestHasNoPlaylists() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryPlaylistsContent(
                    LibraryPlaylistsUiState(listOf(PlaylistListItem("favorites", ""))),
                    listState = LazyListState(),
                )
            }
        }
        composeTestRule.onNodeWithText("Favorites").assertExists()
    }

    @Test
    fun displaysPlaylistWithLargerArtworkAndChevron() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                PlaylistRow("p", "Night Drive", listOf("a", "b", "c", "d"), Modifier.testTag("playlist-row"))
            }
        }
        composeTestRule.onNodeWithText("Night Drive").assertExists()
        composeTestRule.onNodeWithContentDescription("Open playlist").assertExists()
        composeTestRule.onNodeWithTag("playlist-artwork-mosaic").assertHeightIsEqualTo(110.dp)
    }

    @Test
    fun createDialogRequiresANameAndReturnsTrimmedName() {
        var created: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                CreatePlaylistDialog(onDismiss = {}, onCreate = { created = it })
            }
        }
        composeTestRule.onNodeWithTag("playlist-create-button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("playlist-name-input").performTextInput("  Night Drive  ")
        composeTestRule.onNodeWithTag("playlist-create-button").performClick()
        assertEquals("Night Drive", created)
    }
}
